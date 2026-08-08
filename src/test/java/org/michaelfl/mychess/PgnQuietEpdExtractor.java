package org.michaelfl.mychess;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a Texel training set from myChess's own game PGNs (e.g. the cutechess
 * output in {@code test-results/}) by replaying each game and sampling
 * <b>quiet</b> positions labeled with the game result. The output is the same
 * {@code <FEN> c9 "<result>";} EPD format as the Zurichess {@code quiet-labeled}
 * dataset, so it feeds the existing {@code *TexelData} adapters and
 * {@link org.michaelfl.mychess.tuning.TexelTuner} unchanged. A "healthy mix" is
 * then just {@code cat zurichess.epd mychess-selfplay.epd > hybrid.epd}.
 *
 * <p>The point is to close the proxy≠Elo gap: tuning on positions myChess itself
 * reaches aligns the objective with its real play distribution. (Caveat: it
 * cannot teach behavior myChess never exhibits — hence the hybrid with a
 * stronger-engine set.)
 *
 * <p>Sampling rules (see {@link Config}, defaults chosen for this project):
 * <ul>
 *   <li>skip the opening plies (book/opening noise) and the last plies
 *       (result-obvious / adjudication endgames);</li>
 *   <li>keep only <b>quiet</b> positions — side to move not in check and no
 *       <em>winning</em> capture ({@code SEE > 0}, i.e. no hanging piece or
 *       profitable tactic pending; equal exchanges are fine), so the static eval
 *       is meaningful, mirroring the Zurichess "quiet-labeled" filter;</li>
 *   <li>space samples at least {@code minPlyGap} apart, then evenly subsample to
 *       {@code maxPerGame} across the whole game so endgames stay represented;</li>
 *   <li>optionally de-duplicate exact positions across the whole run.</li>
 * </ul>
 *
 * <p>Chess960 games are handled: their positions are emitted with
 * Shredder-castling FENs (rook-file letters) so the castling field round-trips.
 * The eval the adapters compute is purely positional, and {@link Fen#importFEN}
 * parses both classical and Shredder castling notation identically (the
 * Chess960 flag only affects move generation, not FEN parsing or evaluation),
 * so the adapters read these lines unchanged. The result label is White-POV
 * ({@code 1-0}/{@code 1/2-1/2}/{@code 0-1}), matching the tuner.
 *
 * <p><b>Known limitation (this "L0" quiet filter).</b> The quiet test only
 * inspects the <em>side-to-move's own</em> captures; it is blind to the
 * <em>opponent's</em> threats. A position where the side to move is not in check
 * and has no winning capture, yet is losing material to an unavoidable opponent
 * combination on the next move, is still accepted as "quiet" — the static eval
 * over-rates it while the game-result label tells the truth, so it adds noise.
 * Note a quiescence-delta filter would miss this too (a q-search from the side to
 * move never lets the opponent move when the side to move has no capture);
 * catching it needs a shallow real search. Accepted here as a cheap,
 * Zurichess-comparable approximation whose residual noise averages out over a
 * large set — see {@code PgnQuietEpdExtractorTest} for a documented example.
 *
 * <p>Usage: {@code PgnQuietEpdExtractor [outputEpd] [maxGames] [pgnFile...]} —
 * defaults to {@code tuning-data/mychess-selfplay.epd}, all games, and every
 * {@code test-results/*.pgn}.
 *
 * @author Michael Fleischhauer
 */
public final class PgnQuietEpdExtractor {

    /** Sampling and filtering knobs. */
    public record Config(int skipOpeningPlies, int skipEndingPlies, int minPlyGap, int maxPerGame, boolean dedup) {

        public Config {
            if (skipOpeningPlies < 0 || skipEndingPlies < 0 || minPlyGap < 1 || maxPerGame < 1) {
                throw new IllegalArgumentException("invalid extractor config");
            }
        }

        /** Project defaults: skip 8/8 plies, samples >= 4 plies apart, <= 8 per game, dedup on. */
        public static Config defaults() {
            return new Config(8, 8, 4, 8, true);
        }
    }

    private static final Path DEFAULT_OUTPUT = Path.of("tuning-data", "mychess-selfplay.epd");
    private static final Path DEFAULT_PGN_DIR = Path.of("test-results");
    private static final int PROGRESS_EVERY = 5_000;

    private PgnQuietEpdExtractor() {
        // entry point / static utility
    }

    /**
     * A position is quiet when the side to move is not in check and has no
     * capture with a positive static exchange evaluation (no profitable tactic
     * pending). Equal exchanges ({@code SEE == 0}) are allowed — they are
     * ubiquitous in normal middlegames and do not destabilize the static eval.
     */
    static boolean isQuiet(Board board, MoveGenerator moveGenerator, StaticExchangeEvaluation see) {
        if (board.isKingChecked()) {
            return false;
        }

        Moves moves = moveGenerator.calculateMoves(board);
        if (moves.isIllegal()) {
            return false;
        }

        see.init(board);
        int[] plainMoves = moves.getMoves();
        int count = moves.count();

        for (int i = 0; i < count; i++) {
            int move = plainMoves[i];

            if (Move.getCapturedPiece(move) != Board.empty && see.see(move) > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Replay one game and return the sampled quiet EPD lines. {@code seen}
     * carries the cross-game de-dup state (only positions actually emitted are
     * added to it). Games without a decisive/drawn result and games that fail to
     * replay are skipped (returning an empty list). Chess960 games are replayed
     * like any other; their positions are emitted with Shredder-castling FENs.
     */
    static List<String> extractGame(Pgn pgn, Config config, Set<String> seen) {
        String label = resultLabel(pgn.result);
        if (label == null) {
            return List.of();
        }

        Board board = startBoard(pgn);
        if (board == null) {
            return List.of();
        }

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var see = new StaticExchangeEvaluation();
        List<MoveDescription> moves = pgn.moves;
        int total = moves.size();
        int lastSampledPly = -config.minPlyGap();
        var candidateFens = new ArrayList<String>();

        for (int ply = 0; ply < total; ply++) {
            try {
                MoveDescription resolved = board.resolveMoveDescription(moves.get(ply), moveGenerator);
                Move move = board.moveDescriptionToMove(resolved);
                board.makeMove(move.move());
            } catch (RuntimeException _) {
                // Unresolvable / illegal move (malformed PGN) — stop this game here.
                break;
            }

            int plyIndex = ply + 1;                                // positions are numbered after the applied ply
            if (plyIndex <= config.skipOpeningPlies() || plyIndex > total - config.skipEndingPlies()) {
                continue;
            }
            if (plyIndex - lastSampledPly < config.minPlyGap()) {
                continue;
            }
            if (!isQuiet(board, moveGenerator, see)) {
                continue;
            }

            String fullFen = pgn.isChess960() ? Fen.exportShredderFEN(board) : board.exportFEN();
            String fen = firstFourFenFields(fullFen);
            if (config.dedup() && (seen.contains(fen) || candidateFens.contains(fen))) {
                continue;
            }

            candidateFens.add(fen);
            lastSampledPly = plyIndex;
        }

        List<String> chosen = evenSubsample(candidateFens, config.maxPerGame());
        var lines = new ArrayList<String>(chosen.size());

        for (String fen : chosen) {
            if (config.dedup()) {
                seen.add(fen);
            }
            lines.add(fen + " c9 \"" + label + "\";");
        }

        return lines;
    }

    /** Extract quiet EPD lines from {@code pgnFiles} into {@code output}, capped at {@code maxGames}. */
    public static void extractToFile(List<Path> pgnFiles, Path output, Config config, int maxGames) {
        Set<String> seen = new HashSet<>();
        long games = 0;
        long emitted = 0;
        long chess960Games = 0;

        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                for (Path pgnFile : pgnFiles) {
                    var iterator = Pgn.parse(pgnFile.toFile(), true).iterator();

                    while (iterator.hasNext() && games < maxGames) {
                        Pgn pgn = iterator.next();
                        games++;

                        if (pgn.isChess960()) {
                            chess960Games++;
                        }

                        for (String line : extractGame(pgn, config, seen)) {
                            writer.write(line);
                            writer.newLine();
                            emitted++;
                        }

                        if (games % PROGRESS_EVERY == 0) {
                            System.out.printf("  games=%,d  positions=%,d  (incl. Chess960=%,d)%n", games, emitted, chess960Games);
                        }
                    }

                    if (games >= maxGames) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("extraction failed", e);
        }

        System.out.printf("done: %,d games -> %,d quiet positions in %s (incl. %,d Chess960 games)%n",
                games, emitted, output, chess960Games);
    }

    static void main(String[] args) throws IOException {
        Path output = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        int maxGames = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        List<Path> pgnFiles = new ArrayList<>();
        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                pgnFiles.add(Path.of(args[i]));
            }
        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(DEFAULT_PGN_DIR, "*.pgn")) {
                stream.forEach(pgnFiles::add);
            }
            pgnFiles.sort(null);
        }

        System.out.printf("Extracting quiet positions from %,d PGN file(s) -> %s%n", pgnFiles.size(), output);
        extractToFile(pgnFiles, output, Config.defaults(), maxGames);
    }

    private static String resultLabel(Pgn.Result result) {
        return switch (result) {
            case WHITE_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            case DRAW -> "1/2-1/2";
            case ONGOING, UNKNOWN -> null;
        };
    }

    private static Board startBoard(Pgn pgn) {
        try {
            String startFen = pgn.getStartFen();
            if (startFen == null) {
                return Board.createNewGame();
            }
            return pgn.isChess960() ? Fen.importChess960FEN(startFen) : Fen.importFEN(startFen);
        } catch (RuntimeException _) {
            return null;
        }
    }

    /** The piece-placement, side, castling and en-passant fields of a FEN (drops the two move counters). */
    static String firstFourFenFields(String fen) {
        String[] fields = fen.trim().split("\\s+");
        int keep = Math.min(4, fields.length);

        return String.join(" ", List.of(fields).subList(0, keep));
    }

    /** Return at most {@code max} elements, evenly spread across {@code items} (preserves whole-game spread). */
    static <T> List<T> evenSubsample(List<T> items, int max) {
        int size = items.size();
        if (size <= max) {
            return items;
        }

        var chosen = new ArrayList<T>(max);
        for (int i = 0; i < max; i++) {
            chosen.add(items.get((int) ((long) i * size / max)));
        }

        return chosen;
    }
}
