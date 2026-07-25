package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the sliding-piece battery terms in
 * {@link WeightingFunction}. The full position score also contains material,
 * PST, castling, and other factors; these tests intentionally inspect the
 * factor breakdown so they only pin down the battery's mobility and threat
 * contribution.
 */
class BatteryEvalTest {

    private static final int MOBILITY_FEATURE = 1;
    private static final int THREAD_FEATURE = 2;

    @Test
    void rookBatteryOnOpenFile_getsMoreMobilityThanBlockedBattery() {
        double openFileMobility = feature(
                "4k3/8/8/8/8/1N6/R7/R3K3 w - - 0 1",
                MOBILITY_FEATURE);
        double blockedFileMobility = feature(
                "4k3/8/8/8/8/N7/R7/R3K3 w - - 0 1",
                MOBILITY_FEATURE);

        assertTrue(openFileMobility > blockedFileMobility,
                "rook battery on an open file should receive more mobility than the same battery blocked by an own knight. "
                        + "open=" + openFileMobility + ", blocked=" + blockedFileMobility);
    }

    @Test
    void bishopBatteryOnOpenDiagonal_getsMoreMobilityThanBlockedBattery() {
        double openDiagonalMobility = feature(
                "4k3/8/8/5N2/8/2B5/1B6/4K3 w - - 0 1",
                MOBILITY_FEATURE);
        double blockedDiagonalMobility = feature(
                "4k3/8/8/8/4N3/2B5/1B6/4K3 w - - 0 1",
                MOBILITY_FEATURE);

        assertTrue(openDiagonalMobility > blockedDiagonalMobility,
                "bishop battery on an open diagonal should receive more mobility than the same battery blocked by an own knight. "
                        + "open=" + openDiagonalMobility + ", blocked=" + blockedDiagonalMobility);
    }

    @Test
    void rookBehindRook_addsDampedThreadAgainstTargetBehindFrontRook() {
        double batteryThread = feature(
                "4k3/n7/8/8/8/8/R7/R3K3 w - - 0 1",
                THREAD_FEATURE);
        double frontRookOnlyThread = feature(
                "4k3/n7/8/8/8/8/R7/4K3 w - - 0 1",
                THREAD_FEATURE);

        assertTrue(batteryThread > frontRookOnlyThread,
                "rear rook should add a damped threat bonus when the front rook owns the line to the target. "
                        + "battery=" + batteryThread + ", frontOnly=" + frontRookOnlyThread);
    }

    @Test
    void rookBehindRookGetsBatteryThread_rookBehindKnightDoesNot() {
        double rookFrontThread = feature(
                "4k3/n7/8/8/8/8/R7/R3K3 w - - 0 1",
                THREAD_FEATURE);
        double knightFrontThread = feature(
                "4k3/n7/8/8/8/8/N7/R3K3 w - - 0 1",
                THREAD_FEATURE);

        assertTrue(rookFrontThread > knightFrontThread,
                "rear rook should get battery threat only through a same-direction front piece, not through an own knight. "
                        + "rookFront=" + rookFrontThread + ", knightFront=" + knightFrontThread);
    }

    @Test
    void queenBehindBishop_addsDampedThreadOnDiagonalTarget() {
        double batteryThread = feature(
                "4k3/8/5n2/8/8/2B5/1Q6/4K3 w - - 0 1",
                THREAD_FEATURE);
        double frontBishopOnlyThread = feature(
                "4k3/8/5n2/8/8/2B5/8/4K3 w - - 0 1",
                THREAD_FEATURE);

        assertTrue(batteryThread > frontBishopOnlyThread,
                "rear queen should add a damped threat bonus through the front bishop on the same diagonal. "
                        + "battery=" + batteryThread + ", frontOnly=" + frontBishopOnlyThread);
    }

    private static double feature(String fen, int featureIndex) {
        return new WeightingFunction().analyzeFactors(Fen.importFEN(fen)).features()[featureIndex];
    }
}
