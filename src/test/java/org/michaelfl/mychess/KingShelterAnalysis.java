package org.michaelfl.mychess;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Does the king-line danger term actually keep myChess's own king shelter intact? Measured from
 * the match PGNs alone.
 *
 * <h2>The question this answers, and the one it does not</h2>
 *
 * <p>The term measured −28.9 Elo and was shelved. That says the package cost strength; it does not
 * say whether the mechanism worked. Those are different questions, and the second one is worth
 * knowing before anyone builds a seventh king-safety attempt: a term that never changed the
 * behavior it targets failed for a different reason than one that changed it and still lost.
 *
 * <h2>Three measurements, from least circular to most</h2>
 *
 * <p><b>1. Shelter-opening moves.</b> A move by the side to move, on one of the three files at or
 * beside its own king, after which that king's own danger is strictly higher than before. This is
 * a count of concrete actions, not of the evaluation's own quantity, so it is the one number here
 * that is not circular by construction. Restricted to pawn moves, since a pawn is what a shelter
 * is made of.
 *
 * <p><b>2. Any move that raises own danger.</b> The same, without the pawn restriction — captures
 * the king walking out of its own cover as well.
 *
 * <p><b>3. Mean and peak own danger.</b> The danger index of the mover's own king after each of
 * its moves, averaged and maximised per game. <b>Circular on purpose:</b> this is precisely the
 * quantity the term penalises, so a lower figure for the candidate is expected rather than
 * evidence. It is reported because the reverse would be damning — a term whose own quantity does
 * not fall has not reached the search at all — and because the size of the drop says how hard the
 * term pulled.
 *
 * <p>All figures are per engine over its own moves in both colors, so the first-move advantage and
 * the opening book affect both sides equally.
 *
 * <p>A measuring instrument, not a test: it asserts nothing and the suite never runs it.
 *
 * <pre>
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *      org.michaelfl.mychess.KingShelterAnalysis test-results/sprt-king-line.pgn
 * </pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingShelterAnalysis {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /** Danger index from which the fitted table's loud half begins (77 cp and up). */
    private static final int LOUD_DANGER = 4;

    private KingShelterAnalysis() {
        // measurement driver
    }

    /** Per-engine tallies over that engine's own moves. */
    private static final class Tally {
        long games;
        long moves;
        long pawnMoves;
        long shelterOpeningPawnMoves;
        long anyDangerRaisingMoves;
        long dangerSum;
        long loudPlies;
        final List<Integer> peaks = new ArrayList<>();

        double per100(long value) {
            return moves == 0 ? 0 : 100.0 * value / moves;
        }

        double meanDanger() {
            return moves == 0 ? 0 : (double) dangerSum / moves;
        }

        double meanPeak() {
            return peaks.isEmpty() ? 0 : peaks.stream().mapToInt(Integer::intValue).average().orElse(0);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: KingShelterAnalysis <pgn-file>");
            return;
        }

        var tallies = new LinkedHashMap<String, Tally>();
        int skipped = 0;

        for (Pgn pgn : (Iterable<Pgn>) Pgn.parse(new File(args[0]), true)::iterator) {
            if (!analyse(pgn, tallies)) {
                skipped++;
            }
        }

        report(tallies, skipped);
    }

    private static boolean analyse(Pgn pgn, Map<String, Tally> tallies) {
        String[] players = {pgn.getTag("White"), pgn.getTag("Black")};

        if (players[WHITE] == null || players[BLACK] == null
                || pgn.result == Pgn.Result.ONGOING || pgn.result == Pgn.Result.UNKNOWN) {
            return false;
        }

        Board board = pgn.getStartFen() != null || pgn.isChess960() ? null : Board.createNewGame();

        if (board == null) {
            return false;
        }

        var tally = new Tally[] {tallies.computeIfAbsent(players[WHITE], _ -> new Tally()),
                                 tallies.computeIfAbsent(players[BLACK], _ -> new Tally())};
        tally[WHITE].games++;
        tally[BLACK].games++;
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var evaluator = new WeightingFunction();
        var peak = new int[] {0, 0};

        for (int ply = 0; ply < pgn.moves.size(); ply++) {
            final int mover = ply % 2 == 0 ? WHITE : BLACK;
            final int before = dangerOf(evaluator, board, mover);
            final int kingFile = fileOfKing(board, mover);
            final int from;
            final byte moved;

            try {
                int packed = board.moveDescriptionToMove(
                        board.resolveMoveDescription(pgn.moves.get(ply), moveGenerator)).move();
                from = Move.getFromField(packed);
                // Read the piece off the board rather than from the MoveDescription, whose byte
                // encoding is not obvious from its type; the from-square is unambiguous.
                moved = board.getRawBoard()[from];
                board.makeMove(packed);
            } catch (RuntimeException _) {
                break; // unreplayable from here on; keep what we have
            }

            final int after = dangerOf(evaluator, board, mover);
            final boolean wasPawn = moved == Board.whitePawn || moved == Board.blackPawn;

            tally[mover].moves++;
            tally[mover].dangerSum += after;

            if (after >= LOUD_DANGER) {
                tally[mover].loudPlies++;
            }

            peak[mover] = Math.max(peak[mover], after);

            if (wasPawn) {
                tally[mover].pawnMoves++;
            }

            if (after > before) {
                tally[mover].anyDangerRaisingMoves++;

                if (wasPawn && kingFile >= 0 && onKingFile(from, kingFile)) {
                    tally[mover].shelterOpeningPawnMoves++;
                }
            }
        }

        tally[WHITE].peaks.add(peak[WHITE]);
        tally[BLACK].peaks.add(peak[BLACK]);

        return true;
    }

    /** The production classifier's summed danger for {@code color}'s own king. */
    private static int dangerOf(WeightingFunction evaluator, Board board, int color) {
        evaluator.calculate(board);

        return evaluator.getKingLineDanger()[color];
    }

    private static int fileOfKing(Board board, int color) {
        final byte[] squares = board.getRawBoard();
        final byte king = color == WHITE ? Board.whiteKing : Board.blackKing;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                if (squares[Board.a1 + rank * Board.LENGTH + file] == king) {
                    return file;
                }
            }
        }

        return -1;
    }

    private static boolean onKingFile(int field, int kingFile) {
        final int file = (field - Board.a1) % Board.LENGTH;

        return Math.abs(file - kingFile) <= 1;
    }

    private static void report(Map<String, Tally> tallies, int skipped) {
        System.out.printf("games skipped (no result / unreplayable): %d%n%n", skipped);
        System.out.printf("%-14s%8s%8s%12s%12s%12s%10s%9s%n",
                "engine", "games", "moves", "shelter/100", "raises/100", "loud%", "meanDgr", "peak");
        System.out.println("-".repeat(97));

        for (var entry : tallies.entrySet()) {
            Tally t = entry.getValue();
            System.out.printf("%-14s%8d%8d%12.2f%12.2f%12.2f%10.2f%9.2f%n",
                    entry.getKey(), t.games, t.moves,
                    t.per100(t.shelterOpeningPawnMoves), t.per100(t.anyDangerRaisingMoves),
                    t.per100(t.loudPlies), t.meanDanger(), t.meanPeak());
        }

        System.out.println("""

                shelter/100 = pawn moves per 100 own moves, on one of the three files at or beside
                              the own king, after which that king's own danger rose. The one
                              non-circular number here: a count of actions, not of the term's own
                              quantity.
                raises/100  = any move per 100 that raised own king danger, pawn or not.
                loud%       = share of own moves after which own danger stands at 4 or more, where
                              the fitted table reaches 77 cp and up.
                meanDgr     = mean own king danger (0-12) after each own move.
                peak        = mean over games of the worst own danger reached in that game.

                CIRCULARITY, stated rather than hidden: meanDgr, peak and loud% are the quantity
                the candidate's search minimises, so lower figures for it are expected by
                construction and are not evidence that the idea is sound. What they can show is the
                reverse - a term whose own quantity did not fall never reached the search - and how
                hard the term pulled. Only shelter/100 counts behavior rather than belief.""");
    }
}
