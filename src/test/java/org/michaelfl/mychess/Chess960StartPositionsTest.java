package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Michael Fleischhauer
 */
class Chess960StartPositionsTest {

    private static final String STANDARD_CHESS_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1";

    private static final String FIRST_POSITION_FEN =
            "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";

    private static final String LAST_POSITION_FEN =
            "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1";

    @Test
    void count_isExactly960() {
        assertEquals(960, Chess960StartPositions.COUNT, "Chess960 position count");
    }

    @Test
    void standardChessId_returnsStandardChessStartFen() {
        assertEquals(STANDARD_CHESS_FEN,
                Chess960StartPositions.fenById(Chess960StartPositions.STANDARD_CHESS_ID),
                "FEN at standard chess Scharnagl ID 518");
    }

    @Test
    void fenById_firstPosition_returnsBBQNNRKR() {
        assertEquals(FIRST_POSITION_FEN, Chess960StartPositions.fenById(0),
                "FEN at Scharnagl ID 0");
    }

    @Test
    void fenById_lastPosition_returnsRKRNNQBB() {
        assertEquals(LAST_POSITION_FEN, Chess960StartPositions.fenById(959),
                "FEN at Scharnagl ID 959");
    }

    @Test
    void fenById_negativeId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> Chess960StartPositions.fenById(-1),
                "negative ID must throw");
    }

    @Test
    void fenById_idEqualToCount_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> Chess960StartPositions.fenById(Chess960StartPositions.COUNT),
                "ID equal to COUNT must throw");
    }

    /**
     * Walks every one of the 960 positions and asserts the Chess960 invariants:
     * <ul>
     *   <li>FEN field structure</li>
     *   <li>Back-rank composition (RR BB NN Q K)</li>
     *   <li>King is between the two rooks</li>
     *   <li>Bishops are on opposite-colored squares</li>
     *   <li>Black back rank mirrors white (lowercase)</li>
     * </ul>
     * Acts as the load-bearing data-integrity test for the CSV resource.
     */
    @Test
    void allPositions_satisfyChess960Invariants() {
        var seenBackRanks = new HashSet<String>();

        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            String fen = Chess960StartPositions.fenById(id);
            String[] fields = fen.split(" ");
            String[] ranks = fields[0].split("/");
            String whiteBackRank = ranks[7];

            assertFenHasStartingPositionStructure(fields, ranks, id);
            assertNonBackRanksHaveStartingPositionLayout(ranks, id);
            assertBackRankComposition(whiteBackRank, id);
            assertChess960PiecePlacementRules(whiteBackRank, id);
            assertBlackMirrorsWhite(whiteBackRank, ranks[0], id);
            assertShredderCastlingMatchesBackRank(whiteBackRank, fields[2], id);

            assertTrue(seenBackRanks.add(whiteBackRank),
                    "duplicate back rank " + whiteBackRank + " at ID " + id);
        }

        assertEquals(Chess960StartPositions.COUNT, seenBackRanks.size(),
                "expected exactly " + Chess960StartPositions.COUNT + " distinct back ranks");
    }

    private static void assertFenHasStartingPositionStructure(String[] fields, String[] ranks, int id) {
        assertEquals(6, fields.length, "FEN field count for ID " + id);
        assertEquals(8, ranks.length, "rank count for ID " + id);
        assertEquals("w", fields[1], "side-to-move for ID " + id);
        assertEquals("-", fields[3], "en-passant for ID " + id);
        assertEquals("0", fields[4], "halfmove clock for ID " + id);
        assertEquals("1", fields[5], "fullmove number for ID " + id);
    }

    private static void assertNonBackRanksHaveStartingPositionLayout(String[] ranks, int id) {
        assertEquals("pppppppp", ranks[1], "black pawn rank for ID " + id);
        assertEquals("PPPPPPPP", ranks[6], "white pawn rank for ID " + id);
        for (int r = 2; r <= 5; r++) {
            assertEquals("8", ranks[r], "empty rank " + (8 - r) + " for ID " + id);
        }
    }

    private static void assertBackRankComposition(String backRank, int id) {
        assertEquals(8, backRank.length(),
                "back rank length for ID " + id + ": " + backRank);
        assertEquals(2L, countChar(backRank, 'R'), "rook count for ID " + id);
        assertEquals(2L, countChar(backRank, 'B'), "bishop count for ID " + id);
        assertEquals(2L, countChar(backRank, 'N'), "knight count for ID " + id);
        assertEquals(1L, countChar(backRank, 'Q'), "queen count for ID " + id);
        assertEquals(1L, countChar(backRank, 'K'), "king count for ID " + id);
    }

    private static void assertChess960PiecePlacementRules(String backRank, int id) {
        int kingIdx = backRank.indexOf('K');
        int firstRookIdx = backRank.indexOf('R');
        int lastRookIdx = backRank.lastIndexOf('R');
        assertTrue(firstRookIdx < kingIdx && kingIdx < lastRookIdx,
                "king must sit between the two rooks (ID " + id + "): " + backRank);

        int firstBishopIdx = backRank.indexOf('B');
        int lastBishopIdx = backRank.lastIndexOf('B');
        assertNotEquals(firstBishopIdx % 2, lastBishopIdx % 2,
                "bishops must be on opposite-colored squares (ID " + id + "): " + backRank);
    }

    private static void assertBlackMirrorsWhite(String whiteBackRank, String blackBackRank, int id) {
        assertEquals(whiteBackRank.toLowerCase(), blackBackRank,
                "black back rank must mirror white (lowercase) for ID " + id);
    }

    /**
     * Castling rights are in Shredder notation, derived from the king and
     * rook files in the back rank: kingside-rook-file first, then
     * queenside-rook-file, uppercase for white, lowercase for black.
     */
    private static void assertShredderCastlingMatchesBackRank(String backRank, String castling, int id) {
        int queensideRookFile = backRank.indexOf('R');
        int kingsideRookFile = backRank.lastIndexOf('R');
        char expectedKingsideUpper = (char) ('A' + kingsideRookFile);
        char expectedQueensideUpper = (char) ('A' + queensideRookFile);

        assertEquals(4, castling.length(),
                "castling field length for ID " + id + ": " + castling);
        assertEquals(expectedKingsideUpper, castling.charAt(0),
                "white kingside file for ID " + id);
        assertEquals(expectedQueensideUpper, castling.charAt(1),
                "white queenside file for ID " + id);
        assertEquals(Character.toLowerCase(expectedKingsideUpper), castling.charAt(2),
                "black kingside file for ID " + id);
        assertEquals(Character.toLowerCase(expectedQueensideUpper), castling.charAt(3),
                "black queenside file for ID " + id);
    }

    @Test
    void randomFen_neverYieldsStandardChessPosition() {
        // 5_000 draws with a 1/960 inclusion probability would yield ≈ 5 hits
        // if the exclusion were broken; 0 hits is a strong signal.
        String standardFen = Chess960StartPositions.fenById(Chess960StartPositions.STANDARD_CHESS_ID);
        var rng = new Random(0);
        for (int i = 0; i < 5_000; i++) {
            assertNotEquals(standardFen, Chess960StartPositions.randomFen(rng),
                    "randomFen must never yield the standard chess starting position (draw " + i + ")");
        }
    }

    @Test
    void randomFen_withSameSeed_yieldsSameFen() {
        String first = Chess960StartPositions.randomFen(new Random(42));
        String second = Chess960StartPositions.randomFen(new Random(42));
        assertEquals(first, second, "same seed must yield same FEN");
    }

    @Test
    void randomFen_distributesWidelyAcrossManyDraws() {
        var rng = new Random(0);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5_000; i++) {
            seen.add(Chess960StartPositions.randomFen(rng));
        }

        // With 5_000 uniform draws from a 960-element pool the coupon-collector
        // expectation lands well above 900 distinct positions. 500 is a very
        // loose lower bound to keep the test stable across PRNG implementations.
        assertTrue(seen.size() > 500,
                "expected wide spread across the 960 positions, only got " + seen.size());
    }

    @Test
    void toShredderStartFen_standardChess_yieldsHAha() {
        String classic = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String expected = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1";
        assertEquals(expected, Chess960StartPositions.toShredderStartFen(classic),
                "standard chess castling field rewrite");
    }

    @Test
    void toShredderStartFen_rooksAtFAndH_yieldsHFhf() {
        // Scharnagl ID 0: king on g1, rooks on f1 and h1
        String classic = "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1";
        String expected = "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";
        assertEquals(expected, Chess960StartPositions.toShredderStartFen(classic),
                "rooks adjacent to king on kingside half");
    }

    @Test
    void toShredderStartFen_rooksAtAAndC_yieldsCAca() {
        // Scharnagl ID 959: king on b1, rooks on a1 and c1
        String classic = "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w KQkq - 0 1";
        String expected = "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1";
        assertEquals(expected, Chess960StartPositions.toShredderStartFen(classic),
                "rooks adjacent to king on queenside half");
    }

    @Test
    void toShredderStartFen_cutechessExample_yieldsFAfa() {
        // The FEN cutechess actually sent on the first 960 handshake attempt
        String classic = "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w KQkq - 0 1";
        String expected = "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1";
        assertEquals(expected, Chess960StartPositions.toShredderStartFen(classic),
                "cutechess sample position FAfa");
    }

    @Test
    void toShredderStartFen_wrongFieldCount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Chess960StartPositions.toShredderStartFen("rnbqkbnr/8/8/8/8/8/8/RNBQKBNR w KQkq -"),
                "five fields must throw");
    }

    @Test
    void toShredderStartFen_nonClassicalCastlingField_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> Chess960StartPositions.toShredderStartFen(
                        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1"),
                "already-Shredder castling field must throw (precondition guard)");
    }

    @Test
    void toShredderStartFen_kingNotBetweenRooks_throws() {
        // K on a-file, both rooks to its right — violates 960 invariant
        assertThrows(IllegalArgumentException.class,
                () -> Chess960StartPositions.toShredderStartFen(
                        "krbbnnqr/pppppppp/8/8/8/8/PPPPPPPP/KRBBNNQR w KQkq - 0 1"),
                "king outside the two rooks must throw");
    }

    private static long countChar(String s, char c) {
        return s.chars().filter(ch -> ch == c).count();
    }
}
