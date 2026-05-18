package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class KillerMovesTest {

    @Test
    void addMove_recordsFirstSlot() {
        var killers = new KillerMoves();
        killers.addMove(42, 0);
        assertTrue(killers.isKillerMove(42, 0), "move just added must be a killer at the same depth");
        assertFalse(killers.isKillerMove(42, 1), "killer at depth 0 must not match at depth 1");
    }

    @Test
    void addMove_addingSecondMoveShiftsFirstToSlotTwo() {
        var killers = new KillerMoves();
        killers.addMove(11, 3);
        killers.addMove(22, 3);
        assertTrue(killers.isKillerMove(11, 3), "previous killer must still match");
        assertTrue(killers.isKillerMove(22, 3), "new killer must match");
    }

    @Test
    void addMove_addingThirdMoveDropsOldest() {
        var killers = new KillerMoves();
        killers.addMove(11, 5);
        killers.addMove(22, 5);
        killers.addMove(33, 5);
        assertTrue(killers.isKillerMove(33, 5), "newest killer must match");
        assertTrue(killers.isKillerMove(22, 5), "second-newest killer must match");
        assertFalse(killers.isKillerMove(11, 5),
                "oldest killer must have been displaced by the 2-slot table");
    }

    @Test
    void addMove_duplicateAddDoesNotDisplaceSecondSlot() {
        var killers = new KillerMoves();
        killers.addMove(11, 0);
        killers.addMove(22, 0); // [22, 11]
        killers.addMove(22, 0); // identical to slot 0 → no shift
        assertTrue(killers.isKillerMove(22, 0), "newest killer remains");
        assertTrue(killers.isKillerMove(11, 0),
                "Adding the same move twice must not push the older killer out of slot 1");
    }

    @Test
    void isKillerMove_unmatchedMoveReturnsFalse() {
        var killers = new KillerMoves();
        killers.addMove(7, 2);
        assertFalse(killers.isKillerMove(8, 2));
        assertFalse(killers.isKillerMove(7, 3));
    }
}
