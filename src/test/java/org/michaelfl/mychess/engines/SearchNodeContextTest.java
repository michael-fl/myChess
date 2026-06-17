package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Board;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused unit test for {@link SearchNodeContext#writeTTCachedPv(int)}.
 *
 * <p>The behavior is heavily documented in the method's JavaDoc with a
 * diagonal/copy-up diagram. This test is the literal translation of that
 * diagram into assertions, so a future regression in the PV-table layout
 * (diagonal indexing, row-tail truncation, parent-row copy-up) points
 * directly here instead of surfacing through a higher-level search
 * regression like the original {@code EngineSmokeTest.testPosition1}
 * stale-PV failure.
 *
 * @author Michael Fleischhauer
 */
class SearchNodeContextTest {

    private static final int MAX_DEPTH = 4;
    private static final int PV_MAX_LENGTH = MAX_DEPTH + 1;   // 5
    private static final int DEPTH = 2;
    private static final int TT_MOVE = 0x4321;
    private static final int SENTINEL = 0xDEAD;

    @Test
    void writeTTCachedPv_writesDiagonalTruncatesTailAndCopiesUpToParent() {
        // PV table at maxDepth=4 → 5x5 = 25 ints, all pre-filled with a
        // sentinel so any collateral write is visible in the assertions.
        int[] pvTable = new int[PV_MAX_LENGTH * PV_MAX_LENGTH];

        Arrays.fill(pvTable, SENTINEL);

        var ctx = new SearchNodeContext(
                DEPTH, MAX_DEPTH,
                null,         // bestKnownPath — not read by writeTTCachedPv
                +1,           // weightFactor   — not read
                0, 0,         // materialWeight, materialDelta — not read
                Board.createNewGame(),
                pvTable);

        ctx.writeTTCachedPv(TT_MOVE);

        // Expected layout after the call (rows go top-to-bottom by depth,
        // unmodified slots keep the SENTINEL).
        //
        //   col           0    1    2    3    4
        //               +----+----+----+----+----+
        //   row 0 (d=0) | .  | .  | .  | .  | .  |   <- entirely untouched
        //               +----+----+----+----+----+
        //   row 1 (d=1) | .  | .  | TT |  0 |  0 |   <- parent: col d/d+1/d+2 from copyUpPV
        //               +----+----+----+----+----+
        //   row 2 (d=2) | .  | .  | TT |  0 |  0 |   <- own:    diagonal + truncated tail
        //               +----+----+----+----+----+
        //   row 3 (d=3) | .  | .  | .  | .  | .  |   <- below this depth — untouched
        //               +----+----+----+----+----+
        //   row 4 (d=4) | .  | .  | .  | .  | .  |   <- ditto
        //               +----+----+----+----+----+

        // Own row (depth 2): diagonal slot = TT_MOVE, tail = 0.
        int pvIndex = DEPTH * PV_MAX_LENGTH + DEPTH;            // 12
        assertEquals(TT_MOVE, pvTable[pvIndex], "own diagonal slot must hold the TT move");
        assertEquals(0, pvTable[pvIndex + 1], "own row tail slot d+1 must be zeroed");
        assertEquals(0, pvTable[pvIndex + 2], "own row tail slot d+2 must be zeroed");

        // Parent row (depth 1): cols d...d+2 mirror the own row via copyUpPV.
        int pvParentIndex = (DEPTH - 1) * PV_MAX_LENGTH + DEPTH;   // 7
        assertEquals(TT_MOVE, pvTable[pvParentIndex],
                "parent diagonal-of-child slot (row d-1, col d) must receive the TT move");
        assertEquals(0, pvTable[pvParentIndex + 1],
                "parent col d+1 must be zeroed by copyUpPV");
        assertEquals(0, pvTable[pvParentIndex + 2],
                "parent col d+2 must be zeroed by copyUpPV");

        // Every slot outside the modification windows must retain its
        // SENTINEL value — catches stray writes that exceed the
        // diagonal/tail/parent-row ranges.
        int[] writtenIndices = {
                pvIndex, pvIndex + 1, pvIndex + 2,
                pvParentIndex, pvParentIndex + 1, pvParentIndex + 2,
        };

        for (int i = 0; i < pvTable.length; i++) {
            if (containsIndex(writtenIndices, i)) {
                continue;
            }

            assertEquals(SENTINEL, pvTable[i],
                    "slot " + i + " must remain untouched");
        }
    }

    private static boolean containsIndex(int[] indices, int target) {
        for (int idx : indices) {
            if (idx == target) {
                return true;
            }
        }

        return false;
    }
}
