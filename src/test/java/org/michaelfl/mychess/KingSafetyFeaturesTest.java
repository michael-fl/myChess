package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link KingSafetyFeatures} against positions counted by hand.
 *
 * <p>Same reason as {@link KingAttackUnitsTest}: these quantities exist to be fitted, so an error
 * in them would not fail loudly — it would produce a plausible screening result for the wrong
 * feature, and a wrong screening result is worse than none, because it decides what gets built
 * next.
 *
 * @author Michael Fleischhauer
 */
class KingSafetyFeaturesTest {

    /** White king g1, black pawns on g3 and h2; black king far away with no white pawns near it. */
    private static final String STORMED_KINGSIDE = "4k3/8/8/8/8/6p1/7p/6K1 w - - 0 1";

    /** The same two kings with an empty board between them. */
    private static final String BARE = "4k3/8/8/8/8/8/8/6K1 w - - 0 1";

    /** White king g1 behind an intact f2/g2/h2 shelter. */
    private static final String SHELTERED = "4k3/8/8/8/8/8/5PPP/6K1 w - - 0 1";

    @Test
    void stormSumsTheNearestEnemyPawnOnEachOfThreeFiles() {
        var board = Fen.importFEN(STORMED_KINGSIDE);

        // King g1 is rank 0, file g. The h2 pawn is one rank away (3), the g3 pawn two (2),
        // the f-file is empty (0).
        assertEquals(5, KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE),
                "h2 at distance 1 scores 3, g3 at distance 2 scores 2, f-file nothing");
        assertEquals(0, KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_BLACK),
                "white has no pawns at all, so nothing storms the black king");
    }

    @Test
    void stormIgnoresPawnsTooFarAwayAndOnOtherFiles() {
        // Black pawns on a4 (wrong file) and g7 (six ranks from the white king on g1).
        var board = Fen.importFEN("4k3/6p1/8/8/p7/8/8/6K1 w - - 0 1");

        assertEquals(0, KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE),
                "neither pawn is within three ranks on one of the king's three files");
    }

    /**
     * The engine's own pawns must not register as a storm.
     *
     * <p>The distinction this whole feature rests on: the shelved −57.5 Elo term scored the
     * advancement of the king's <em>own</em> shelter pawns, this one scores the
     * <em>enemy's</em>. A version that confused the two would screen as the term that already
     * failed.
     */
    @Test
    void stormCountsEnemyPawnsOnly() {
        var board = Fen.importFEN(SHELTERED);

        assertEquals(0, KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE),
                "f2/g2/h2 are white's own shelter, not a storm against white");
    }

    @Test
    void stormIsClampedToTheFittedRange() {
        // Three black pawns adjacent to the white king's rank: 3 + 3 + 3 = 9, above the cap.
        var board = Fen.importFEN("4k3/8/8/8/8/8/5ppp/6K1 w - - 0 1");

        assertEquals(KingSafetyFeatures.MAX_STORM,
                KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE),
                "the raw sum is 9 and clamps onto the last index the fit places");
    }

    @Test
    void virtualQueenMobilityCountsOpenLinesFromTheKingSquare() {
        var board = Fen.importFEN(BARE);

        // From g1: seven up the g-file, one up-right (h2), one right (h1), six left along rank 1,
        // six up the a7-g1 diagonal; the three downward directions hit the border at once.
        assertEquals(21, KingSafetyFeatures.virtualQueenMobility(board, GameStatus.TURN_WHITE),
                "an unobstructed king on g1 sees 21 squares as a queen would");
    }

    @Test
    void shelterPawnsLowerTheExposure() {
        int bare = KingSafetyFeatures.virtualQueenMobility(Fen.importFEN(BARE), GameStatus.TURN_WHITE);
        int sheltered = KingSafetyFeatures.virtualQueenMobility(Fen.importFEN(SHELTERED), GameStatus.TURN_WHITE);

        assertEquals(7, sheltered,
                "f2/g2/h2 block the up, up-right and up-left rays, leaving h1 and the rank to a1");
        assertTrue(sheltered < bare,
                "the whole point of the metric: shelter lowers exposure. bare " + bare
                        + ", sheltered " + sheltered);
    }

    @Test
    void bothFeaturesAreZeroWhenTheKingIsMissing() {
        // A board without a white king: the helpers must not read a stale or negative square.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/8 b - - 0 1");

        assertEquals(0, KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE),
                "no white king, no storm against it");
        assertEquals(0, KingSafetyFeatures.virtualQueenMobility(board, GameStatus.TURN_WHITE),
                "no white king, no exposure");
    }

    /**
     * The dense encoding must be non-zero where the sparse one is silent.
     *
     * <p>That is the whole reason it exists: the first encoding reached its upper indices in
     * 0.5 % of positions, in self-play and human games alike, so its top coefficient rested on
     * nothing measurable. A starting position has enemy pawns six ranks away and should register
     * a little rather than nothing.
     */
    @Test
    void denseStormRegistersWhereTheSparseOneIsSilent() {
        var start = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals(0, KingSafetyFeatures.stormAgainst(start, GameStatus.TURN_WHITE),
                "the three-rank cutoff sees nothing in the starting position");
        assertEquals(1, KingSafetyFeatures.denseStormAgainst(start, GameStatus.TURN_WHITE),
                "three enemy pawns six ranks away score 1 each, bucketed in twos");
    }

    @Test
    void denseStormAgreesWithTheSparseOneWhereBothSpeak() {
        var board = Fen.importFEN(STORMED_KINGSIDE);

        // h2 at distance 1 scores 6, g3 at distance 2 scores 5, f-file nothing: 11 / 2 = 5.
        assertEquals(5, KingSafetyFeatures.denseStormAgainst(board, GameStatus.TURN_WHITE),
                "an actual storm lands at the same index under both encodings");
    }

    @Test
    void denseStormCountsEnemyPawnsOnly() {
        assertEquals(0, KingSafetyFeatures.denseStormAgainst(Fen.importFEN(SHELTERED), GameStatus.TURN_WHITE),
                "f2/g2/h2 are white's own shelter under either encoding");
    }

    // ------------------------------------------------------------------------------------
    // fileDangerAround — open and half-open files toward the king, the classical term.
    // Every level of the scale is pinned by a position built for it, because the scale is
    // ordinal: an error that swaps two neighboring levels would still fit smoothly and would
    // never fail loudly.
    // ------------------------------------------------------------------------------------

    @Test
    void fileDangerIsZeroWhenEveryFileHasAnOwnPawn() {
        var start = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerAround(start, GameStatus.TURN_WHITE),
                "d, e and f all carry a white pawn — fully sheltered");
        assertEquals(0, KingSafetyFeatures.fileDangerAround(start, GameStatus.TURN_BLACK),
                "and the same for black");
    }

    @Test
    void fileDangerCountsThreeOpenFiles() {
        var board = Fen.importFEN(BARE);

        assertEquals(9, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "f, g and h are open with no heavy piece: 3 + 3 + 3");
    }

    @Test
    void anEnemyHeavyPieceRaisesAnOpenFileByOne() {
        // Black rook on g4: the g-file is open AND occupied by a heavy piece.
        var board = Fen.importFEN("6k1/8/8/8/6r1/8/8/6K1 w - - 0 1");

        assertEquals(10, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "f and h open (3 each), g open with a rook on it (4)");
        assertEquals(9, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_BLACK),
                "the rook is black's own, so it does not endanger the black king");
    }

    @Test
    void aHalfOpenFileScoresLessThanAnOpenOne() {
        // White's g-pawn is gone, black's g-pawn still stands on g7 — far from the white king.
        var board = Fen.importFEN("6k1/6p1/8/8/8/8/5P1P/6K1 w - - 0 1");

        assertEquals(1, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "f and h are sheltered, g is half-open with the enemy pawn still on its own half");
    }

    /**
     * The level that distinguishes a half-open file from a dangerous one.
     *
     * <p>"Past the middle" is read from the defending king's side: for a white king, a black pawn
     * on rank 4 or below has crossed onto white's half. Getting that comparison backwards would
     * score a pawn on its own starting square as a storm, which is the kind of error a smooth fit
     * absorbs without complaining.
     */
    @Test
    void anEnemyPawnPastTheMiddleRaisesAHalfOpenFile() {
        var board = Fen.importFEN("6k1/8/8/8/8/6p1/5P1P/6K1 w - - 0 1");

        assertEquals(2, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "the black g-pawn stands on g3, on white's half, so the half-open g-file scores 2");
    }

    @Test
    void anOwnPawnShelterBeatsEverythingElseOnThatFile() {
        // Black queen on the g-file, but white still has a pawn on g2.
        var board = Fen.importFEN("6k1/8/8/8/6q1/8/5PPP/6K1 w - - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "a pawn on the file is shelter regardless of what stands behind it");
    }

    @Test
    void fileDangerIsZeroWhenTheKingIsMissing() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/8 b - - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "no white king, no files to judge");
    }

    // ------------------------------------------------------------------------------------
    // fileDangerSplitAround — the six-level variant. Measured against the five-level scale on
    // 39,619 positions it came out at +0.100 pp explained residual variance, 90% interval
    // [-0.061, +0.246] under a paired block bootstrap: not separable from zero. It is kept as a
    // screen candidate so that result stays reproducible, and it is tested for the same reason —
    // an untested encoding cannot support a published negative any more than a positive.
    // ------------------------------------------------------------------------------------

    @Test
    void splitScaleCallsAFileWithBothPawnsTrulyClosed() {
        var start = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerSplitAround(start, GameStatus.TURN_WHITE),
                "own and enemy pawn on all three files: nothing can come down them");
    }

    /** The one case the five-level scale cannot express, and the whole reason for the variant. */
    @Test
    void splitScaleSeparatesAnOwnPawnAloneFromBothPawns() {
        // White keeps f2/g2/h2; black has no pawns at all, so each file is half-open the other way.
        var board = Fen.importFEN("6k1/8/8/8/8/8/5PPP/6K1 w - - 0 1");

        assertEquals(3, KingSafetyFeatures.fileDangerSplitAround(board, GameStatus.TURN_WHITE),
                "three files at level 1 — shelter stands, but nothing blocks it from the far side");
        assertEquals(0, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "the five-level scale reads the very same position as fully sheltered");
    }

    @Test
    void splitScaleShiftsTheOpenLevelsUpByOne() {
        var bare = Fen.importFEN(BARE);

        assertEquals(12, KingSafetyFeatures.fileDangerSplitAround(bare, GameStatus.TURN_WHITE),
                "three open files at level 4");

        var withRook = Fen.importFEN("6k1/8/8/8/6r1/8/8/6K1 w - - 0 1");

        assertEquals(13, KingSafetyFeatures.fileDangerSplitAround(withRook, GameStatus.TURN_WHITE),
                "f and h at 4, g open with a rook on it at 5");
    }

    @Test
    void splitScaleKeepsTheStormDistinction() {
        var quiet = Fen.importFEN("6k1/6p1/8/8/8/8/5P1P/6K1 w - - 0 1");

        assertEquals(4, KingSafetyFeatures.fileDangerSplitAround(quiet, GameStatus.TURN_WHITE),
                "f and h at 1, g half-open with the enemy pawn still on its own half at 2");

        var stormed = Fen.importFEN("6k1/8/8/8/8/6p1/5P1P/6K1 w - - 0 1");

        assertEquals(5, KingSafetyFeatures.fileDangerSplitAround(stormed, GameStatus.TURN_WHITE),
                "the same files, but the black pawn has crossed onto white's half: g rises to 3");
    }

    @Test
    void splitScaleIsZeroWhenTheKingIsMissing() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/8 b - - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerSplitAround(board, GameStatus.TURN_WHITE),
                "no white king, no files to judge");
    }

    // ------------------------------------------------------------------------------------
    // placeboFileDanger — the control. Its whole value is that it can move independently of the
    // real feature, so the tests are two positions where one is at its maximum and the other at
    // zero. A control that tracked the feature would be worthless and these tests would fail.
    // ------------------------------------------------------------------------------------

    @Test
    void theControlWindowSitsFourFilesFromTheKing() {
        // White king g1 (file 6) -> control centered on file (6+4)%8 = 2, the c-file: b, c, d.
        var board = Fen.importFEN("6k1/8/8/8/8/8/1PPP4/6K1 w - - 0 1");

        assertEquals(9, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "f, g and h are wide open in front of the king");
        assertEquals(0, KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_WHITE),
                "b, c and d each carry a white pawn, so the control reads nothing");
    }

    @Test
    void theControlCanBeLoudWhileTheKingIsSafe() {
        var board = Fen.importFEN("6k1/8/8/8/8/8/5PPP/6K1 w - - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "the king sits behind an intact three-pawn shelter");
        assertEquals(9, KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_WHITE),
                "while b, c and d are empty — exactly the case the control exists to catch");
    }

    /**
     * A king on the e-file puts the control on the a-file, where the window has two files, not
     * three. Recorded rather than corrected: the king's own window narrows on the edge files in
     * the same way, so the two stay comparable, but the control's reachable maximum is lower and
     * a reader comparing top indices needs to know that.
     */
    @Test
    void theControlWindowNarrowsWhenItFallsOnAnEdgeFile() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/4K3 w - - 0 1");

        assertEquals(6, KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_WHITE),
                "control centered on the a-file covers only a and b: two open files, not three");
    }

    @Test
    void theControlIsZeroWhenTheKingIsMissing() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/8 b - - 0 1");

        assertEquals(0, KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_WHITE),
                "no white king, no rank and no offset to read from");
    }

    /**
     * A pawn the enemy has already walked past is not cover, and the scale must not call it that.
     *
     * <p>White king g1, black pawn g2, white pawn g3. Reading the file for "is there an own pawn
     * on it" answers yes and scores the file as fully sheltered — the safest level on the scale —
     * for what is close to the most dangerous arrangement the three files can hold. The shelter
     * test is therefore ordered: an own pawn only counts while nothing hostile stands between it
     * and the king.
     *
     * <p>This occurs on 0.073 % of king files in the 39,619-position corpus, seven times rarer
     * than the occupancy that already made a fitted coefficient void, so it is far below what any
     * fit or match could resolve. It is fixed on correctness grounds rather than measured ones,
     * and deliberately not given a level of its own: a pawn on g2 is a promotion threat one ply
     * deep, which the search reads better than any static table.
     */
    @Test
    void anEnemyPawnThatHasPassedTheShieldPawnIsNotShelter() {
        var board = Fen.importFEN("6k1/8/8/8/8/5PP1/5PpP/6K1 w - - 0 1");

        assertEquals(2, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "f and h are sheltered, g is half-open with the black pawn on white's half");
        assertEquals(5, KingSafetyFeatures.fileDangerSplitAround(board, GameStatus.TURN_WHITE),
                "the six-level scale must agree about what counts as cover: f and h at level 1 "
                        + "(own pawn, no enemy pawn), g at 3 (its shelter does not count)");
    }

    @Test
    void aShieldPawnInFrontOfTheEnemyPawnStillShelters() {
        // Mirror of the case above: the white pawn on g2 stands between the king and the black
        // pawn on g4, which is what a shield is.
        var board = Fen.importFEN("6k1/8/8/8/6p1/8/5PPP/6K1 w - - 0 1");

        assertEquals(0, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE),
                "the g-pawn is where it belongs, so the file is closed");
    }

    @Test
    void theOrderedShelterTestWorksFromBlacksSideToo() {
        // Black king g8, white pawn g7, black pawn g6 — the same overtaking, mirrored.
        var board = Fen.importFEN("6k1/5pPp/5pp1/8/8/8/8/6K1 w - - 0 1");

        assertEquals(2, KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_BLACK),
                "the scan must run outward from black's own back rank, not from white's");
    }
}
