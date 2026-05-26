package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chess960 castling-move-generation tests.
 *
 * <p>Covers two complementary surfaces:
 *
 * <ol>
 *   <li>Bulk coverage of every legal {@code (king-file, queenside-rook-file,
 *       kingside-rook-file)} triple on the back rank — 56 distinct
 *       configurations, each tested for both kingside and queenside
 *       castling from a sparse "only king + 2 rooks + lone black king"
 *       position so that path-clear and king-not-attacked conditions are
 *       trivially satisfied.</li>
 *   <li>Spot tests for the four castling-rule edge cases the user called
 *       out: king-in-check, king-path-square-attacked,
 *       rook-attacked (must still be legal in 960), and
 *       rook-only-path-square-attacked (must also still be legal in 960).</li>
 * </ol>
 *
 * <p>The tests fail by design against the current move generator, which
 * hard-codes the standard-chess king and rook starting squares
 * ({@code e1}, {@code a1}, {@code h1} and mirrors). Only the single
 * configuration that matches standard chess
 * ({@code kingFile = 4}, {@code qrFile = 0}, {@code krFile = 7}) is
 * expected to pass before the 960 castling rules are implemented.
 *
 * @author Michael Fleischhauer
 */
class Chess960CastlingTest {

    private static MoveGenerator newGen() {
        return new MoveGenerator(MoveSorter.defaultImplementation());
    }

    /**
     * Looks for a castling move of the given type in the generated
     * move list that <em>also</em> originates on the king's actual
     * square and targets the 960-correct destination ({@code g1}/{@code g8}
     * for kingside, {@code c1}/{@code c8} for queenside). A generator
     * that emits a hard-coded castling move from {@code e1} regardless
     * of the king's true square therefore does not pass for any
     * non-{@code e}-file king.
     */
    private static boolean canCastle(Board board, byte castlingType) {
        int turn = board.getGameStatus().getTurn();
        int kingRow = (turn == GameStatus.TURN_WHITE) ? 0 : 7;
        byte kingPiece = (turn == GameStatus.TURN_WHITE) ? Board.whiteKing : Board.blackKing;

        int kingFile = ChessUtil.findColOfPieceOnRow(board.getRawBoard(), kingPiece, kingRow);
        int kingSource = ChessUtil.getFieldFromColAndRow(kingFile, kingRow);
        int kingTargetFile = (castlingType == Move.typeCastlingKingSide) ? 6 : 2;
        int kingTarget = ChessUtil.getFieldFromColAndRow(kingTargetFile, kingRow);

        Moves moves = newGen().calculateMoves(board);
        int[] moveArray = moves.getMoves();
        for (int i = 0; i < moves.count(); i++) {
            int move = moveArray[i];
            if (Move.getMoveType(move) == castlingType
                    && Move.getFromField(move) == kingSource
                    && Move.getToField(move) == kingTarget) {
                return true;
            }
        }

        return false;
    }

    /** Compresses a rank string of letters and {@code '1'} placeholders
     *  (one per empty square) into FEN run-length form. */
    private static String compressFenRank(String rank) {
        StringBuilder result = new StringBuilder();
        int run = 0;
        for (int i = 0; i < rank.length(); i++) {
            char c = rank.charAt(i);
            if (c == '1') {
                run++;
            } else {
                if (run > 0) {
                    result.append(run);
                    run = 0;
                }
                result.append(c);
            }
        }
        if (run > 0) {
            result.append(run);
        }

        return result.toString();
    }

    /**
     * Builds a minimal castling-test FEN: white king and both white
     * rooks on rank 1 at the given files; the lone black king on the
     * <em>same file as the white king</em>, rank 8; no pawns; empty
     * middle of the board. White to move; Shredder castling rights set
     * for both white slots so the parser populates
     * {@code castlingRookFiles} correctly.
     *
     * <p>The black king's file is chosen to match white's: in 960 the
     * king is always strictly between the two rooks, so neither white
     * rook stands on the king's file. The black king therefore never
     * sits on an open rook file and is not in check — keeping the
     * position legal with white to move. The two kings are on the same
     * file but seven ranks apart, so they don't attack each other
     * either. With no other pieces, castling is constrained solely by
     * the 960 mechanics under test, not by accidental attacks.
     */
    private static String sparseFen(int kingFile, int qrFile, int krFile) {
        StringBuilder rank1 = new StringBuilder("11111111");
        rank1.setCharAt(qrFile, 'R');
        rank1.setCharAt(kingFile, 'K');
        rank1.setCharAt(krFile, 'R');

        StringBuilder rank8 = new StringBuilder("11111111");
        rank8.setCharAt(kingFile, 'k');

        char krLetter = (char) ('A' + krFile);
        char qrLetter = (char) ('A' + qrFile);
        String castling = "" + krLetter + qrLetter;

        return compressFenRank(rank8.toString()) + "/8/8/8/8/8/8/"
                + compressFenRank(rank1.toString())
                + " w " + castling + " - 0 1";
    }

    // ---- Bulk: every (kingFile, qrFile, krFile) triple ----

    /**
     * Generates all 56 legal back-rank triples (king strictly between
     * the two rooks, both rooks on the board).
     */
    static Stream<Arguments> allKingRookConfigurations() {
        List<Arguments> list = new ArrayList<>();
        for (int kingFile = 1; kingFile <= 6; kingFile++) {
            for (int queensideRookFile = 0; queensideRookFile < kingFile; queensideRookFile++) {
                for (int kingsideRookFile = kingFile + 1; kingsideRookFile < 8; kingsideRookFile++) {
                    list.add(Arguments.of(kingFile, queensideRookFile, kingsideRookFile));
                }
            }
        }

        return list.stream();
    }

    @ParameterizedTest(name = "kingFile={0} qrFile={1} krFile={2}")
    @MethodSource("allKingRookConfigurations")
    void kingsideCastling_inEveryConfiguration(int kingFile, int qrFile, int krFile) {
        String fen = sparseFen(kingFile, qrFile, krFile);
        Board board = Fen.importFEN(fen);

        boolean expected = !queensideRookBlocksKingsideCastle(qrFile, krFile);
        assertEquals(expected, canCastle(board, Move.typeCastlingKingSide),
                "kingside castle expected " + (expected ? "legal" : "blocked")
                        + " — kingFile=" + kingFile + ", qrFile=" + qrFile + ", krFile=" + krFile
                        + "; FEN: " + fen);
    }

    @ParameterizedTest(name = "kingFile={0} qrFile={1} krFile={2}")
    @MethodSource("allKingRookConfigurations")
    void queensideCastling_inEveryConfiguration(int kingFile, int qrFile, int krFile) {
        String fen = sparseFen(kingFile, qrFile, krFile);
        Board board = Fen.importFEN(fen);

        boolean expected = !kingsideRookBlocksQueensideCastle(kingFile, qrFile, krFile);
        assertEquals(expected, canCastle(board, Move.typeCastlingQueenSide),
                "queenside castle expected " + (expected ? "legal" : "blocked")
                        + " — kingFile=" + kingFile + ", qrFile=" + qrFile + ", krFile=" + krFile
                        + "; FEN: " + fen);
    }

    /**
     * In the sparse "only K + 2R" setup, returns true iff the queenside
     * rook sits on a square the kingside rook needs to traverse during
     * kingside castling, making the castle structurally impossible. The
     * king's path ({@code kingFile..g1}) never contains the queenside
     * rook because {@code qrFile < kingFile}, so only the rook's path
     * matters: the kingside rook travels from {@code krFile} to {@code f1}.
     */
    private static boolean queensideRookBlocksKingsideCastle(int qrFile, int krFile) {
        final int kingsideRookTargetFile = 5; // f-file
        int pathMin = Math.min(krFile, kingsideRookTargetFile);
        int pathMax = Math.max(krFile, kingsideRookTargetFile);
        return qrFile >= pathMin && qrFile <= pathMax;
    }

    /**
     * In the sparse "only K + 2R" setup, returns true iff the kingside
     * rook sits on a square either the king or the queenside rook needs
     * to traverse during queenside castling. Targets are {@code c1}
     * (king) and {@code d1} (queenside rook).
     */
    private static boolean kingsideRookBlocksQueensideCastle(int kingFile, int qrFile, int krFile) {
        final int kingTargetFile = 2; // c-file
        final int queensideRookTargetFile = 3; // d-file

        int kingPathMin = Math.min(kingFile, kingTargetFile);
        int kingPathMax = Math.max(kingFile, kingTargetFile);
        int rookPathMin = Math.min(qrFile, queensideRookTargetFile);
        int rookPathMax = Math.max(qrFile, queensideRookTargetFile);

        boolean onKingPath = krFile >= kingPathMin && krFile <= kingPathMax;
        boolean onRookPath = krFile >= rookPathMin && krFile <= rookPathMax;
        return onKingPath || onRookPath;
    }

    // ---- Spot tests for the four castling-rule edge cases ----

    @Test
    void castling_isIllegalWhenKingIsInCheck_chess960() {
        // Setup: white king on b1, queenside rook on a1, kingside rook on c1.
        // Black rook on b8 attacks b1 via the open b-file → white king in check.
        // Black king parked on e8 — off both open white-rook files (a- and c-),
        // so the position is legal with white to move.
        // Castling out of check is illegal in chess (standard and 960).
        Board board = Fen.importFEN("1r2k3/8/8/8/8/8/8/RKR5 w CA - 0 1");

        assertTrue(board.isKingChecked(),
                "sanity: white king must be in check from the b8 rook");

        assertFalse(canCastle(board, Move.typeCastlingKingSide),
                "castling kingside out of check is illegal");
        assertFalse(canCastle(board, Move.typeCastlingQueenSide),
                "castling queenside out of check is illegal");
    }

    @Test
    void castling_isIllegalWhenKingPathSquareIsAttacked_chess960() {
        // Setup: white king on b1, queenside rook on a1, kingside rook on c1.
        // Kingside castle path for the king: b1 → c1 → d1 → e1 → f1 → g1.
        // Black rook on f8 attacks f1 via the open f-file. King would cross
        // an attacked square → illegal.
        // Black king on e8 (adjacent to its own f8 rook) — off both
        // white-rook files (a- and c-) so the position is legal.
        Board board = Fen.importFEN("4kr2/8/8/8/8/8/8/RKR5 w CA - 0 1");

        assertFalse(canCastle(board, Move.typeCastlingKingSide),
                "castling kingside is illegal when f1 (on the king's path) is attacked");
    }

    @Test
    void castling_isLegalWhenRookIsAttacked_chess960() {
        // Setup: white king on b1, queenside rook on a1, kingside rook on h1.
        // Black rook on h8 attacks h1 via the open h-file. The h-file is NOT
        // on the king's path (king crosses b1..g1, never h1).
        // Chess960 rule: an attacked rook does not prevent castling.
        // Black king on e8 — off the a- and h-files so the position is legal.
        Board board = Fen.importFEN("4k2r/8/8/8/8/8/8/RK5R w HA - 0 1");

        assertTrue(canCastle(board, Move.typeCastlingKingSide),
                "castling kingside must remain legal when only the rook (on h1) is attacked");
    }

    @Test
    void castling_isLegalWhenRookOnlyPathSquareIsAttacked_chess960() {
        // Setup: white king on g1, queenside rook on a1, kingside rook on h1.
        // Queenside castle: king g1 → c1 (king path g1, f1, e1, d1, c1),
        //                   rook a1 → d1 (rook path a1, b1, c1, d1).
        //   King-only: g1, f1, e1
        //   Common:    c1, d1
        //   Rook-only: a1, b1       ← only the rook traverses these squares
        // Black rook on b8 attacks b1 via the open b-file. The king does
        // not cross b1 — the attack is on a rook-only path square.
        // Chess960 rule: attacks on rook-only path squares do not prevent
        // castling. (This answers the user's "gibt es so eine Situation
        // überhaupt?" — yes, it's the configuration with king and rook on
        // opposite ends of the back rank.)
        // Black king on e8 — off the a- and h-files so the position is legal.
        Board board = Fen.importFEN("1r2k3/8/8/8/8/8/8/R5KR w HA - 0 1");

        assertTrue(canCastle(board, Move.typeCastlingQueenSide),
                "castling queenside must remain legal when only a rook-only path square (b1) is attacked");
    }
}
