package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Probes whether {@link WeightingFunction#calculate(Board)} is antisymmetric
 * under color flip — i.e. whether
 * {@code eval(position) == -eval(mirror(position))} for every legal position.
 *
 * <p>This is the canonical invariant for a symmetric chess evaluation: a
 * position rotated 180° around the board center with all piece colors
 * swapped must score with the opposite sign of the original. Any deviation
 * indicates either an asymmetric eval term or an asymmetric piece-square
 * table — both of which would systematically bias engine play toward one
 * color.
 *
 * <p>The mirror constructed here is FEN-level: piece-letter cases are
 * swapped and ranks reversed, castling rights swap KQ ↔ kq, the en-passant
 * square's rank flips, the side-to-move flips. Half-move clock and
 * full-move number stay the same. Boards imported via {@link Fen#importFEN}
 * carry {@code lastMove == 0}, so the en-passant detection inside
 * {@code WeightingFunction} never fires (it gates on {@code lastMove != 0});
 * both the original and the mirror enter that gate the same way (i.e. not
 * at all), so the absence is itself symmetric.
 *
 * @author Michael Fleischhauer
 */
class MirrorEvalTest {

    static Stream<Arguments> mirrorPositions() {
        return Stream.of(
                // Starting position — trivially symmetric.
                Arguments.of("starting position",
                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),

                // After 1. e4 — White made an asset move. Mirror is the same
                // shape with Black on move and the e-pawn on e5; eval should
                // flip sign.
                Arguments.of("after 1. e4",
                        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),

                // Asymmetric opening: 1.e4 c5 — Sicilian.
                Arguments.of("Sicilian: 1. e4 c5",
                        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2"),

                // King's Indian setup, both sides developed.
                Arguments.of("King's Indian-like middlegame",
                        "r1bq1rk1/ppp1ppbp/2np1np1/8/2BPP3/2N2N2/PPP2PPP/R1BQ1RK1 w - - 4 6"),

                // White up a clean queen (no king tension).
                Arguments.of("white up a queen",
                        "4k3/8/8/8/8/8/4P3/3QK3 w - - 0 1"),

                // Black up a rook and a bishop (mirror: white up rook+bishop).
                Arguments.of("black up a rook and a bishop",
                        "r3kb2/8/8/8/8/8/8/4K3 w - - 0 1"),

                // Position with a doubled-pawn on each side at different files.
                Arguments.of("doubled pawns: White c-file, Black f-file",
                        "4k3/8/5p2/5p2/8/2P5/2P5/4K3 w - - 0 1"),

                // Castling rights asymmetry: White still can, Black cannot.
                Arguments.of("white can castle, black cannot",
                        "r3k2r/pppqpppp/2n2n2/3p4/3P4/2N2N2/PPPQPPPP/R3K2R w KQ - 0 1"),

                // En-passant square set, no lastMove (so EP-eval-bonus does not fire,
                // but the GameStatus carries the field — checks that import + mirror agree).
                Arguments.of("en-passant target set: e6 for White, e3 for Black after mirror",
                        "rnbqkbnr/pp1ppppp/8/2pP4/8/8/PPP1PPPP/RNBQKBNR w KQkq c6 0 3"),

                // A mid-game-ish FEN with mixed material and a king in the center.
                Arguments.of("mixed middlegame with mobility imbalance",
                        "r2qkb1r/ppp2ppp/2n1pn2/3p4/3P4/2NBPN2/PPP2PPP/R1BQK2R w KQkq - 0 6"),

                // Some other positions; taken from EngineTest
                Arguments.of("FEN1", "8/1R4pp/3r1pk1/p4N2/5R2/8/7r/6K1 w - - 0 43"),
                Arguments.of("FEN2", "8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 0 45"),
                Arguments.of("FEN3", "2kr4/1R5p/2P3p1/3B4/8/P3p3/5b1P/7K w - - 0 47"),
                Arguments.of("FEN4", "1rbr2k1/4bppp/p4n2/1pp1B3/8/2N2B2/PPP2PPP/R3R1K1 b - - 0 17"),
                Arguments.of("FEN5", "2br2k1/4bppp/p4n2/4B3/Nr6/1P3B2/2P2PPP/3RR1K1 w - - 0 24"),
                Arguments.of("FEN6", "1r5r/k4ppp/2B1p3/pQ2Nq2/Pb1P4/6B1/5PPP/5RK1 w - - 3 26"),
                Arguments.of("FEN7", "2R5/1p2bqBk/p2p4/3Ppr2/3p2Q1/P2P3P/1P3PP1/6K1 b - - 2 23"),
                Arguments.of("FEN8", "2R5/1p2b1Bk/p2p2q1/3Ppr2/3p2Q1/P2P3P/1P3PP1/6K1 w - - 3 24"),

                // -- Chess960 starting positions -------------------------
                //
                // Each 960 start is structurally symmetric (rank 1 white
                // pieces have the same files as rank 8 black pieces) and
                // the castling rights mirror cleanly under case swap, so
                // the antisymmetry check exercises the eval on
                // non-standard back ranks without depending on any
                // mid-game state.

                // Scharnagl ID 518 (= standard chess) in Shredder-FEN form.
                // Same shape as the very first test case above, but
                // exercises the Shredder-letter castling path.
                Arguments.of("Chess960 Scharnagl 518 (Shredder FEN)",
                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1"),

                // Scharnagl ID 0 — extreme: BBQNNRKR. King on g, kingside
                // rook on h, queenside rook on f.
                Arguments.of("Chess960 Scharnagl 0 (BBQNNRKR)",
                        "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1"),

                // Scharnagl ID 959 — extreme other end: RKRNNQBB. King on
                // b, queenside rook on a, kingside rook on c (closely
                // adjacent castling).
                Arguments.of("Chess960 Scharnagl 959 (RKRNNQBB)",
                        "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1"),

                // RKBBNRNQ — the cutechess sample 960 position used
                // throughout the rest of the test suite. King on b,
                // queenside rook on a, kingside rook on f.
                Arguments.of("Chess960 RKBBNRNQ (cutechess sample)",
                        "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mirrorPositions")
    void evalIsAntisymmetricUnderColorFlip(String name, String fen) {
        var board = Fen.importFEN(fen);
        var mirroredBoard = Fen.importFEN(mirrorFen(fen));

        int eval = new WeightingFunction().calculate(board);
        int mirrorEval = new WeightingFunction().calculate(mirroredBoard);

        assertEquals(eval, -mirrorEval,
                () -> "Eval should be antisymmetric for " + name + "\n"
                        + "  original FEN:  " + fen + "  → eval=" + eval + "\n"
                        + "  mirrored FEN:  " + mirrorFen(fen) + "  → eval=" + mirrorEval + "\n"
                        + "  difference:    eval + mirrorEval = " + (eval + mirrorEval) + " centipawns\n"
                        + "  expected zero (eval == -mirrorEval).");
    }

    @Test
    void mirrorOfMirrorIsIdentity() {
        // Sanity check on the FEN mirror utility itself: mirror(mirror(fen)) == fen
        // for every FEN that's well-formed and round-tripable.
        for (Arguments arg : mirrorPositions().toList()) {
            String fen = (String) arg.get()[1];
            String name = (String) arg.get()[0];
            assertEquals(fen, mirrorFen(mirrorFen(fen)),
                    "mirror(mirror(...)) should be identity for " + name);
        }
    }

    // ---- FEN mirror utility ----

    /**
     * Returns a FEN representing the original position rotated 180° around the
     * board center with all piece colors swapped. Castling rights swap
     * {@code KQ ↔ kq}; the en-passant square's rank flips; the side to move
     * flips; half-move clock and full-move number stay unchanged.
     */
    static String mirrorFen(String fen) {
        String[] parts = fen.split("\\s+");
        return mirrorBoardField(parts[0]) + " "
                + (parts[1].equals("w") ? "b" : "w") + " "
                + mirrorCastlingField(parts[2]) + " "
                + mirrorEnPassantField(parts[3]) + " "
                + parts[4] + " "
                + parts[5];
    }

    private static String mirrorBoardField(String boardField) {
        String[] ranks = boardField.split("/");
        var sb = new StringBuilder();

        // FEN lists ranks top-to-bottom (rank 8 first, rank 1 last). After
        // mirroring, the new rank 8 contains what was rank 1, with piece
        // colors swapped.
        for (int i = ranks.length - 1; i >= 0; i--) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            for (char c : ranks[i].toCharArray()) {
                if (Character.isUpperCase(c)) {
                    sb.append(Character.toLowerCase(c));
                } else if (Character.isLowerCase(c)) {
                    sb.append(Character.toUpperCase(c));
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Mirrors a castling-rights FEN field by swapping the case of each
     * letter: {@code K↔k}, {@code Q↔q}, and for Shredder/X-FEN
     * {@code A-H ↔ a-h}. Castle rights belong to the side whose pieces
     * sit on the back rank, so a 180° board rotation that swaps colors
     * must also swap the case of every right.
     */
    private static String mirrorCastlingField(String castling) {
        if ("-".equals(castling)) {
            return "-";
        }
        var sb = new StringBuilder();
        for (char c : castling.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLowerCase(c)) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }

        return sb.isEmpty() ? "-" : sb.toString();
    }

    private static String mirrorEnPassantField(String ep) {
        if ("-".equals(ep)) {
            return "-";
        }
        char file = ep.charAt(0);
        int rank = ep.charAt(1) - '0';

        return "" + file + (9 - rank);
    }
}
