package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generates a Chess960 opening book of lightly-randomized, roughly-balanced
 * starting positions for self-play data generation.
 *
 * <p>Each entry is produced by drawing a random Chess960 start position (one of
 * the 959 non-standard Scharnagl setups), playing {@value #RANDOM_PLIES} random
 * <em>legal</em> half-moves on top of it, and keeping the result only if a
 * shallow depth-{@value #FILTER_DEPTH} search rates it within
 * &plusmn;{@value #MAX_ABS_EVAL_CENTIPAWNS} centipawns. The balance filter
 * discards openings in which the forced random half-moves already blundered a
 * pawn or a piece — 960 back-ranks frequently leave a pawn or minor undefended
 * after a single careless move, and such positions would give the self-play
 * game a result label driven by the opening blunder rather than by the
 * position, poisoning the tuning data.
 *
 * <p>The output is one Shredder-castling FEN per line, suitable for
 * {@code cutechess-cli -variant fischerandom -openings file=... format=epd
 * plies=0}, which starts each game directly from the given position.
 *
 * <p>Test-scope tooling (like {@code PgnQuietEpdExtractor} and the Texel
 * adapters); not part of the shipped engine. Deterministic for a fixed seed.
 *
 * @author Michael Fleischhauer
 */
public final class Chess960OpeningBookGenerator {

    /** A randomized opening is rejected if its shallow eval exceeds this (centipawns, absolute). */
    static final int MAX_ABS_EVAL_CENTIPAWNS = 120;

    /** Depth of the balance-filter search. */
    static final int FILTER_DEPTH = 6;

    /** Random legal half-moves played on top of each Chess960 start position. */
    static final int RANDOM_PLIES = 2;

    /** Centipawns per pawn — the engine reports weights in pawns. */
    private static final float CENTIPAWNS_PER_PAWN = 100.0f;

    private static final long FILTER_TIMEOUT_SECONDS = 60L;
    private static final int PROGRESS_EVERY = 500;

    private static final Path DEFAULT_OUTPUT = Path.of("book-960.epd");
    private static final int DEFAULT_COUNT = 10_000;
    private static final long DEFAULT_SEED = 20260807L;

    private Chess960OpeningBookGenerator() {
        // static utility / entry point
    }

    /**
     * Outcome of a book-generation run: the accepted FENs plus rejection counts
     * for observability (acceptance rate diagnoses whether the filter or the
     * randomization is off).
     */
    record Result(List<String> fens,
                  int candidates,
                  int rejectedUnbalanced,
                  int rejectedDuplicate,
                  int rejectedTerminal) {

        double acceptanceRate() {
            return candidates == 0 ? 0.0 : (double) fens.size() / candidates;
        }
    }

    /**
     * Generates {@code count} distinct, balanced Chess960 opening FENs.
     *
     * @param count number of accepted book entries to produce
     * @param seed  RNG seed — the same seed yields the same book
     * @return the accepted FENs and rejection statistics
     */
    static Result generate(int count, long seed) {
        var rng = new Random(seed);
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        Set<String> fens = LinkedHashSet.newLinkedHashSet(count);

        int candidates = 0;
        int rejectedUnbalanced = 0;
        int rejectedDuplicate = 0;
        int rejectedTerminal = 0;

        var tt = TestSupport.createTestTT();
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(FILTER_DEPTH)
                .setTranspositionTable(tt)
                .silent(true)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        try {
            while (fens.size() < count) {
                candidates++;

                String startFen = Chess960StartPositions.randomFen(rng);
                Board board = Fen.importChess960FEN(startFen);

                if (!playRandomPlies(board, moveGenerator, rng)) {
                    rejectedTerminal++;
                    continue;
                }

                String fen = Fen.exportShredderFEN(board);

                if (fens.contains(fen)) {
                    rejectedDuplicate++;
                    continue;
                }

                if (!isBalanced(fen, gameConfig)) {
                    rejectedUnbalanced++;
                    continue;
                }

                fens.add(fen);

                if (fens.size() % PROGRESS_EVERY == 0) {
                    System.out.printf("  accepted=%,d / candidates=%,d  (%.1f%% accept)%n",
                            fens.size(), candidates, 100.0 * fens.size() / candidates);
                }
            }
        } finally {
            tt.close();
        }

        return new Result(new ArrayList<>(fens), candidates, rejectedUnbalanced, rejectedDuplicate, rejectedTerminal);
    }

    /**
     * Plays {@value #RANDOM_PLIES} random legal half-moves on {@code board}.
     * Returns {@code false} if a side has no legal move (checkmate/stalemate),
     * which cannot occur this early but is handled defensively.
     */
    private static boolean playRandomPlies(Board board, MoveGenerator moveGenerator, Random rng) {

        for (int ply = 0; ply < RANDOM_PLIES; ply++) {
            List<Integer> legalMoves = legalMoves(board, moveGenerator);

            if (legalMoves.isEmpty()) {
                return false;
            }

            int move = legalMoves.get(rng.nextInt(legalMoves.size()));
            board.makeMove(move);
        }

        return true;
    }

    /**
     * Filters the pseudo-legal move list down to fully legal moves by making
     * each move and rejecting those that leave the mover's own king en prise.
     */
    private static List<Integer> legalMoves(Board board, MoveGenerator moveGenerator) {
        Moves pseudoLegal = moveGenerator.calculateMoves(board);
        int[] moves = pseudoLegal.getMoves();
        int count = pseudoLegal.count();
        List<Integer> legal = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int move = moves[i];

            board.makeMove(move);
            boolean leavesKingInCheck = board.canCaptureOpposingKing();
            board.revertMove();

            if (!leavesKingInCheck) {
                legal.add(move);
            }
        }

        return legal;
    }

    /**
     * Runs the shallow balance-filter search on {@code fen} and returns whether
     * its absolute evaluation is within {@value #MAX_ABS_EVAL_CENTIPAWNS}
     * centipawns.
     */
    private static boolean isBalanced(String fen, GameConfig gameConfig) {
        return Math.abs(shallowEvalCentipawns(fen, gameConfig)) <= MAX_ABS_EVAL_CENTIPAWNS;
    }

    /**
     * Evaluates {@code fen} with a depth-{@value #FILTER_DEPTH} search and
     * returns the white-POV score in centipawns. Package-visible so tests can
     * assert the filter behavior directly.
     */
    static float shallowEvalCentipawns(String fen, GameConfig gameConfig) {
        var game = new Game(gameConfig, Fen.importChess960FEN(fen));

        try {
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(FILTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return move.weight() * CENTIPAWNS_PER_PAWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Balance-filter search interrupted for FEN: " + fen, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Balance-filter search failed for FEN: " + fen, e);
        } finally {
            game.shutdown();
        }
    }

    /**
     * Writes the book to {@code output}, one FEN per line, creating parent
     * directories as needed.
     */
    static void writeBook(Path output, List<String> fens) {
        try {
            Path parent = output.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(output, fens);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write opening book to " + output, e);
        }
    }

    /**
     * Entry point: {@code Chess960OpeningBookGenerator [output] [count] [seed]}.
     * Defaults to {@value #DEFAULT_COUNT} entries at {@code book-960.epd} with a
     * fixed seed.
     */
    public static void main(String[] args) {
        Path output = args.length > 0 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        int count = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_COUNT;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : DEFAULT_SEED;

        System.out.printf("Generating %,d Chess960 openings (%d random plies, |eval| <= %d cp, depth %d, seed %d)%n",
                count, RANDOM_PLIES, MAX_ABS_EVAL_CENTIPAWNS, FILTER_DEPTH, seed);

        Result result = generate(count, seed);

        writeBook(output, result.fens());

        System.out.printf("done: %,d openings -> %s%n", result.fens().size(), output);
        System.out.printf("  candidates=%,d  accept=%.1f%%  rejected: unbalanced=%,d duplicate=%,d terminal=%,d%n",
                result.candidates(), 100.0 * result.acceptanceRate(),
                result.rejectedUnbalanced(), result.rejectedDuplicate(), result.rejectedTerminal());
    }
}
