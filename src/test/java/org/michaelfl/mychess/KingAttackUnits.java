package org.michaelfl.mychess;

/**
 * Attack units bearing on a king's 3×3 zone, computed from a {@link Board} alone.
 *
 * <p>This is the quantity {@link WeightingFunction#KING_ATTACK_PENALTY} is indexed by: every
 * enemy piece that attacks at least one square of the king's zone contributes its weight, and
 * each piece counts <b>once</b> however many zone squares it bears on.
 *
 * <p><b>The weights are read from {@link WeightingFunction} rather than repeated here.</b> They
 * were duplicated while the term lived only on an unmerged branch, which left the calibration
 * resting on two copies of six numbers staying equal — a coupling nothing enforced and a test
 * had to assert. Referencing the production constants removes the failure mode instead of
 * guarding it.
 *
 * <p><b>Why this duplicates logic that already exists.</b> On the branch the units fall out of
 * {@code WeightingFunction}'s per-piece scan for free, and nothing here would be needed. But the
 * curve has to be fitted <em>before</em> the branch is merged — placing the table is the reason
 * the branch would be worth merging at all — and on master neither the scan nor an
 * attacker-set API exists: {@link Board#isFieldAttackedBy} answers "is this square attacked",
 * which loses both the per-piece weight and the deduplication. So the computation is rebuilt
 * here, in test sources, where it costs the production evaluation nothing.
 *
 * <p>It is written as the mirror image of {@code Board.isFieldAttackedBy}: same offsets, same
 * ray walks, same reliance on the illegal-border ring making bounds checks unnecessary. The one
 * difference is that it records <em>which</em> piece attacks rather than stopping at the first
 * hit, which is exactly why it cannot simply call that method.
 *
 * <p>Correctness is pinned two ways in {@link KingAttackUnitsTest}: hand-built positions with
 * counted-by-hand answers, and agreement with {@code tools/king-attack-curve.py}, an independent
 * implementation on a different board representation.
 *
 * @author Michael Fleischhauer
 */
final class KingAttackUnits {

    private KingAttackUnits() {
        // utility
    }

    /**
     * Units {@code attackerColor} accumulates on the other king's zone.
     *
     * @param board         the position
     * @param attackerColor {@link GameStatus#TURN_WHITE} or {@link GameStatus#TURN_BLACK} — the
     *                      side doing the attacking, not the side whose king is under fire
     * @return the summed attack units, 0 if the attacked king is not on the board
     */
    static int of(Board board, int attackerColor) {
        final byte[] squares = board.getRawBoard();
        final boolean attackerIsWhite = attackerColor == GameStatus.TURN_WHITE;
        final byte enemyKing = attackerIsWhite ? Board.blackKing : Board.whiteKing;
        final int kingField = findKing(squares, enemyKing);

        if (kingField < 0) {
            return 0;
        }

        // Deduplication by origin square: a rook bearing on three zone squares is one attacker.
        final boolean[] counted = new boolean[Board.LENGTH * Board.LENGTH];
        int units = 0;

        units += unitsOn(squares, kingField, attackerIsWhite, counted);

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            final int zoneField = kingField + offset;

            if (squares[zoneField] != Board.illegal) {
                units += unitsOn(squares, zoneField, attackerIsWhite, counted);
            }
        }

        return units;
    }

    /** Units contributed by attackers of {@code field} not yet counted. */
    private static int unitsOn(byte[] squares, int field, boolean attackerIsWhite, boolean[] counted) {
        int units = 0;

        // Pawns. A white pawn attacks up-diagonally, so its origin sits one rank below.
        final byte pawn = attackerIsWhite ? Board.whitePawn : Board.blackPawn;
        final int back = attackerIsWhite ? -Board.LENGTH : Board.LENGTH;

        for (int side : new int[] {-1, 1}) {
            units += take(squares, field + back + side, pawn, WeightingFunction.ATTACK_UNIT_PAWN, counted);
        }

        final byte knight = attackerIsWhite ? Board.whiteKnight : Board.blackKnight;

        for (int offset : Board.KNIGHT_OFFSETS) {
            units += take(squares, field + offset, knight, WeightingFunction.ATTACK_UNIT_KNIGHT, counted);
        }

        final byte king = attackerIsWhite ? Board.whiteKing : Board.blackKing;

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            units += take(squares, field + offset, king, WeightingFunction.ATTACK_UNIT_KING, counted);
        }

        final byte bishop = attackerIsWhite ? Board.whiteBishop : Board.blackBishop;
        final byte rook = attackerIsWhite ? Board.whiteRook : Board.blackRook;
        final byte queen = attackerIsWhite ? Board.whiteQueen : Board.blackQueen;

        units += alongRays(squares, field, Board.DIAGONAL_RAY_DIRS, bishop, WeightingFunction.ATTACK_UNIT_BISHOP, queen, counted);
        units += alongRays(squares, field, Board.ORTHOGONAL_RAY_DIRS, rook, WeightingFunction.ATTACK_UNIT_ROOK, queen, counted);

        return units;
    }

    /**
     * Walks each ray to its first occupied square and counts a slider standing there.
     *
     * <p>Deliberately stops at the first piece: a rook behind another rook (a battery) does not
     * count. The Audax fork continues the ray through friendly sliders and through one enemy
     * piece, which makes the term fire far more often; that widening belongs to its style goal
     * and is not part of what is being fitted here.
     */
    private static int alongRays(byte[] squares, int field, int[] directions,
                                 byte slider, int sliderUnits, byte queen, boolean[] counted) {
        int units = 0;

        for (int direction : directions) {
            int to = field + direction;

            while (squares[to] == Board.empty) {
                to += direction;
            }

            units += take(squares, to, slider, sliderUnits, counted);
            units += take(squares, to, queen, WeightingFunction.ATTACK_UNIT_QUEEN, counted);
        }

        return units;
    }

    /** Counts the piece on {@code field} if it is {@code wanted} and not already counted. */
    private static int take(byte[] squares, int field, byte wanted, int units, boolean[] counted) {
        if (squares[field] != wanted || counted[field]) {
            return 0;
        }

        counted[field] = true;

        return units;
    }

    private static int findKing(byte[] squares, byte king) {
        for (int field = Board.a1; field <= Board.h8; field++) {
            if (squares[field] == king) {
                return field;
            }
        }

        return -1;
    }
}
