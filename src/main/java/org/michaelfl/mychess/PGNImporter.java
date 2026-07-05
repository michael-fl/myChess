package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MyChessEngine;

/**
 * {@link GameImporter} that replays a parsed {@link Pgn} into a {@link Game}.
 * The no-arg variant disables threefold-repetition and the 50-move rule so
 * that draw-by-rule does not interrupt importing finished games.
 *
 * <p><b>Starting position.</b> Three ways to pick the initial board, in
 * increasing precedence:
 * <ol>
 *   <li><b>Standard initial position</b> — used when neither of the two
 *       below applies.</li>
 *   <li><b>PGN {@code [FEN "..."]} tag pair</b> — the parser stores the
 *       tag on {@link Pgn}; the {@link #PGNImporter(Pgn)} single-arg
 *       constructor honors it. May be any legal FEN, including mid-game
 *       snapshots (this is what the PGN standard allows). Chess960
 *       castling rules are enabled when {@link Pgn#isChess960()} is true
 *       (i.e. the header also carries {@code [Variant "Chess960"]} or
 *       {@code "Fischerandom"}).</li>
 *   <li><b>Explicit {@code startFen} parameter</b> — the
 *       {@link #PGNImporter(Pgn, String)} constructor takes a FEN string
 *       and overrides any {@code [FEN "..."]} tag pair in the PGN header.
 *       Unlike the header-tag path, this parameter is strict: only
 *       genuine game-starting positions are accepted (standard chess or
 *       one of the 960 Chess960 setups). Mid-game FENs, Black-to-move
 *       positions, partial castling rights, or non-zero move counters
 *       are rejected via {@link Fen#requireStartFen(String)}. Chess960
 *       vs. standard castling rules are auto-detected from the FEN's
 *       back-rank layout — {@code RNBQKBNR} on both sides is standard
 *       chess, anything else is Chess960.</li>
 * </ol>
 *
 * @author Michael Fleischhauer
 */
final class PGNImporter implements GameImporter {

    private static final String STD_WHITE_BACK_RANK = "RNBQKBNR";
    private static final String STD_BLACK_BACK_RANK = "rnbqkbnr";

    private final Pgn pgn;
    private final String startFenOverride;

    PGNImporter(Pgn pgn) {
        this(pgn, null);
    }

    /**
     * Construct an importer that starts the game from the given FEN,
     * ignoring any {@code [FEN "..."]} tag pair carried in the PGN header.
     * Pass {@code startFen = null} to fall back to the tag pair (or the
     * standard start, when no tag is present) — this behaves identically
     * to {@link #PGNImporter(Pgn)}.
     *
     * <p>When {@code startFen} is non-null it is validated via
     * {@link Fen#requireStartFen(String)}: only actual game-starting
     * positions are accepted. Mid-game FENs must go through the PGN
     * header instead. Chess960 castling rules are auto-detected from
     * the back-rank layout — see {@link #isChess960StartFen(String)}.
     *
     * @param pgn      parsed PGN carrying at least the move list
     * @param startFen starting-position FEN, or {@code null} to defer to
     *                 the PGN header
     * @throws IllegalArgumentException when {@code startFen} is non-null
     *         but is not a valid game starting position
     */
    PGNImporter(Pgn pgn, String startFen) {
        if (startFen != null) {
            Fen.requireStartFen(startFen);
        }
        this.pgn = pgn;
        this.startFenOverride = startFen;
    }

    @Override
    public Game importGame() {
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false).enableFiftyMovesRule(false).build());

        return importGame(config);
    }

    @Override
    public Game importGame(GameConfig config) {
        String effectiveStartFen;
        boolean effectiveChess960;
        if (startFenOverride != null) {
            effectiveStartFen = startFenOverride;
            effectiveChess960 = isChess960StartFen(startFenOverride);
        } else {
            effectiveStartFen = pgn.getStartFen();
            effectiveChess960 = pgn.isChess960();
        }

        if (effectiveStartFen == null) {
            return new Game(config, pgn.moves);
        }

        var initialBoard = effectiveChess960
                ? Fen.importChess960FEN(effectiveStartFen)
                : Fen.importFEN(effectiveStartFen);

        return new Game(config, initialBoard, pgn.moves);
    }

    /**
     * Chess960 detection for a validated starting-position FEN (see
     * {@link Fen#requireStartFen(String)}). Two independent signals
     * classify the FEN as Chess960:
     * <ul>
     *   <li>a non-standard back-rank layout (anything other than the
     *       classical {@code RNBQKBNR} / {@code rnbqkbnr} order), or</li>
     *   <li>Shredder / X-FEN spelling of the castling-rights field (file
     *       letters like {@code HAha} instead of the shorthand
     *       {@code KQkq}) — accepting the standard-chess back rank with
     *       Shredder rights and routing it through the classical FEN
     *       parser would break, since that parser does not understand
     *       file-letter castling notation.</li>
     * </ul>
     */
    private static boolean isChess960StartFen(String fen) {
        var fields = fen.trim().split("\\s+");
        var ranks = fields[0].split("/");
        boolean nonStandardBackRank =
                !STD_BLACK_BACK_RANK.equals(ranks[0]) || !STD_WHITE_BACK_RANK.equals(ranks[7]);
        boolean shredderCastlingRights = !"KQkq".equals(fields[2]);

        return nonStandardBackRank || shredderCastlingRights;
    }
}
