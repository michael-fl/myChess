package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Bench.Suite;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Properties of {@link Suite} and of the position sets behind it, without running a search.
 *
 * <p>Two distinct purposes, kept apart because they carry very different weight.
 *
 * <p><b>(1) The {@code Suite} enum's own behavior</b> — sizes, labels, the Chess960 flag, and
 * that the {@code boolean} overloads of {@link Bench#suiteSize(boolean)} still resolve to the
 * same sets they always did. This is the part that can break from a code change.
 *
 * <p><b>(2) The composition of {@link Suite#CASTLING_MIX}.</b> Over a frozen, tracked file
 * these assertions cannot fail on their own — they are a one-time verification kept as a test,
 * and their regression value is catching an <i>edit or replacement of the asset</i>. That
 * matters more here than for the other two suites: {@code CASTLING_MIX} exists solely because
 * of a property of its contents (half its positions uncastled with real castling rights, and
 * no stress position), and a change that quietly broke the 50:50 balance would leave the suite
 * running fine while measuring something else. The JavaDoc says so plainly rather than
 * implying the engine is under test.
 *
 * <p>{@link Suite#STANDARD} deliberately gets only its size asserted. Its contents are pinned
 * far more strongly than any test here could manage — by the node signature series in
 * {@code docs/bench-history.md}, which changes if a single position does.
 *
 * @author Michael Fleischhauer
 */
class BenchSuiteTest {

    private static final int STANDARD_SIZE = 55;
    private static final int CHESS960_SIZE = 10;
    private static final int CASTLING_MIX_SIZE = 60;

    /** Exactly half of {@link Suite#CASTLING_MIX}, which is the whole point of that suite. */
    private static final int UNCASTLED_COUNT = 30;

    /**
     * Of the 30 uncastled positions, the six whose king sits centrally with <b>no</b> castling
     * right left — the case that separates a term gated on "has castled" from one gated on
     * "rights lost". 10 % against a 7 % natural frequency in real games.
     */
    private static final int NO_RIGHTS_LEFT_COUNT = 6;

    /** Fewest pieces admitted when the set was built, so no position is an endgame. */
    private static final int MIN_PIECES = 14;

    private static final String[] STRESS_BOARDS = {
            "k7/2n1n3/1nbNbn2/2NbRBn1/1nbRQR2/2NBRBN1/3N1N2/7K",
            "K7/8/8/BNQNQNB1/N5N1/R1Q1q2r/n5n1/bnqnqnbk"
    };

    /**
     * File of the side-to-move king, 0 = a … 7 = h, read straight out of the FEN's board field
     * rather than through {@link Board}: this test is about the asset, so it must not depend on
     * the code that consumes it.
     */
    private static int kingFile(String fen) {
        String[] fields = fen.split("\\s+");
        char king = "w".equals(fields[1]) ? 'K' : 'k';

        for (String row : fields[0].split("/")) {
            int file = 0;

            for (int i = 0; i < row.length(); i++) {
                char piece = row.charAt(i);

                if (Character.isDigit(piece)) {
                    file += piece - '0';
                } else if (piece == king) {
                    return file;
                } else {
                    file++;
                }
            }
        }

        throw new IllegalStateException("no side-to-move king in FEN: " + fen);
    }

    /**
     * Whether the king is on a file it could only have reached by castling — or at least sits
     * where a castled king sits. Files a-c and g-h; d-f is "central", i.e. uncastled.
     */
    private static boolean isCastledFile(int file) {
        return file <= 2 || file >= 6;
    }

    private static boolean hasOwnCastlingRight(String fen) {
        String[] fields = fen.split("\\s+");
        String rights = fields[2];
        boolean white = "w".equals(fields[1]);

        return white
                ? rights.indexOf('K') >= 0 || rights.indexOf('Q') >= 0
                : rights.indexOf('k') >= 0 || rights.indexOf('q') >= 0;
    }

    private static int pieceCount(String fen) {
        int count = 0;
        String board = fen.split("\\s+")[0];

        for (int i = 0; i < board.length(); i++) {
            char piece = board.charAt(i);

            if (Character.isLetter(piece)) {
                count++;
            }
        }

        return count;
    }

    @Test
    void suiteSizesAreTheExpectedPositionCounts() {
        assertEquals(STANDARD_SIZE, Bench.suiteSize(Suite.STANDARD),
                "standard suite = 49 Stockfish positions + 6 myChess middlegames");
        assertEquals(CHESS960_SIZE, Bench.suiteSize(Suite.CHESS960), "Chess960 suite size");
        assertEquals(CASTLING_MIX_SIZE, Bench.suiteSize(Suite.CASTLING_MIX), "castling-mix suite size");
    }

    @Test
    void booleanOverloadsResolveToTheHistoricalSuites() {
        assertEquals(Bench.suiteSize(Suite.STANDARD), Bench.suiteSize(false),
                "suiteSize(false) must still be the standard suite");
        assertEquals(Bench.suiteSize(Suite.CHESS960), Bench.suiteSize(true),
                "suiteSize(true) must still be the Chess960 suite");
    }

    @Test
    void onlyTheChess960SuiteIsFlaggedAs960() {
        assertFalse(Suite.STANDARD.isChess960(), "standard suite is not Chess960");
        assertTrue(Suite.CHESS960.isChess960(), "Chess960 suite is Chess960");
        assertFalse(Suite.CASTLING_MIX.isChess960(), "castling-mix is standard chess");
    }

    @Test
    void labelsAreLowerCaseAndHyphenated() {
        assertEquals("standard", Suite.STANDARD.label(), "label of STANDARD");
        assertEquals("chess960", Suite.CHESS960.label(), "label of CHESS960");
        assertEquals("castling-mix", Suite.CASTLING_MIX.label(), "label of CASTLING_MIX");
    }

    @Test
    void everySuitePositionIsAWellFormedSixFieldFen() {
        for (Suite suite : Suite.values()) {
            for (String fen : suite.fens()) {
                assertEquals(6, fen.split("\\s+").length,
                        "FEN field count in " + suite.label() + ": " + fen);

                Board board = suite.isChess960() ? Fen.importChess960FEN(fen) : Fen.importFEN(fen);

                assertNotNull(board, "imported board for " + fen);
            }
        }
    }

    @Test
    void castlingMixHasNoDuplicates() {
        List<String> fens = Suite.CASTLING_MIX.fens();
        var unique = new HashSet<>(fens);

        assertEquals(fens.size(), unique.size(), "distinct FENs in the castling-mix suite");
    }

    @Test
    void castlingMixIsHalfUncastledByKingPlacement() {
        int castled = 0;
        int uncastled = 0;

        for (String fen : Suite.CASTLING_MIX.fens()) {
            if (isCastledFile(kingFile(fen))) {
                castled++;
            } else {
                uncastled++;
            }
        }

        assertEquals(UNCASTLED_COUNT, uncastled, "positions with a central side-to-move king");
        assertEquals(CASTLING_MIX_SIZE - UNCASTLED_COUNT, castled, "positions with a castled side-to-move king");
    }

    @Test
    void mostUncastledPositionsStillCarryTheRightToCastle() {
        int withRights = 0;
        int withoutRights = 0;

        for (String fen : Suite.CASTLING_MIX.fens()) {
            if (isCastledFile(kingFile(fen))) {
                continue;
            }

            if (hasOwnCastlingRight(fen)) {
                withRights++;
            } else {
                withoutRights++;
            }
        }

        assertEquals(UNCASTLED_COUNT - NO_RIGHTS_LEFT_COUNT, withRights,
                "uncastled positions where the side to move may still castle");
        assertEquals(NO_RIGHTS_LEFT_COUNT, withoutRights,
                "uncastled positions that have already lost every castling right");
    }

    @Test
    void theStandardSuiteIsTheOneThatLacksCastlingRights() {
        // The reason CASTLING_MIX exists at all, asserted rather than only written down: a term
        // gated on castling state can leave the standard suite's node signature bit-identical
        // because that suite barely contains the state in which the term acts.
        long mixWithRights = Suite.CASTLING_MIX.fens().stream().filter(BenchSuiteTest::hasOwnCastlingRight).count();
        long standardWithRights = Suite.STANDARD.fens().stream().filter(BenchSuiteTest::hasOwnCastlingRight).count();

        assertTrue(mixWithRights >= UNCASTLED_COUNT - NO_RIGHTS_LEFT_COUNT,
                "castling-mix sides to move that may castle, was " + mixWithRights);
        assertTrue(standardWithRights * 4 < mixWithRights,
                "standard suite must be far poorer in castling rights, was "
                        + standardWithRights + " vs " + mixWithRights);
    }

    @Test
    void castlingMixExcludesTheStressPositions() {
        // One of them alone is 87 % of the standard suite's nodes at both depth 6 and depth 8,
        // so a single position would decide every comparison made with this set.
        for (String fen : Suite.CASTLING_MIX.fens()) {
            String board = fen.split("\\s+")[0];

            for (String stress : STRESS_BOARDS) {
                assertFalse(stress.equals(board), "stress position must not be in the castling mix: " + fen);
            }
        }
    }

    @Test
    void castlingMixPositionsAreQuietMiddlegames() {
        for (String fen : Suite.CASTLING_MIX.fens()) {
            assertTrue(pieceCount(fen) >= MIN_PIECES,
                    "piece count must keep this a middlegame, was " + pieceCount(fen) + " in " + fen);

            Board board = Fen.importFEN(fen);

            assertFalse(board.isKingChecked(),
                    "side to move must not already be in check: " + fen);
        }
    }
}
