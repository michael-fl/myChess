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

        return around(squares, findKing(squares, enemyKing), attackerIsWhite, null);
    }

    /**
     * Units {@code attackerColor} accumulates on a 3×3 zone around an arbitrary square.
     *
     * <p>Exists for the placebo control of {@code tools/king-attack-vs-stockfish.py}. Bearing on
     * the king zone also means having active pieces deep in enemy territory, and a regression
     * cannot separate the two on its own; running the identical computation over a zone at the
     * same rank and the same depth, but not where the king stands, isolates the part that is
     * about the king. Whatever the placebo also earns is activity rather than danger.
     *
     * @param board         the position
     * @param attackerColor the side doing the attacking
     * @param centerField   the zone's center square, or a negative value for none
     * @return the summed attack units
     */
    static int ofZone(Board board, int attackerColor, int centerField) {
        return around(board.getRawBoard(), centerField,
                attackerColor == GameStatus.TURN_WHITE, null);
    }

    /**
     * The placebo zone's center: the enemy king's rank, {@link #PLACEBO_FILE_SHIFT} files away.
     *
     * <p>Four files guarantees the two 3×3 zones never overlap while keeping the control at the
     * same rank and the same depth in enemy territory as the thing it controls for.
     *
     * @param board         the position
     * @param attackerColor the side doing the attacking
     * @return the center square, or −1 if the attacked king is not on the board
     */
    static int placeboCenter(Board board, int attackerColor) {
        final byte[] squares = board.getRawBoard();
        final boolean attackerIsWhite = attackerColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, attackerIsWhite ? Board.blackKing : Board.whiteKing);

        if (kingField < 0) {
            return -1;
        }

        final int relative = kingField - Board.a1;

        return Board.a1 + relative / Board.LENGTH * Board.LENGTH
                + (relative % Board.LENGTH + PLACEBO_FILE_SHIFT) % FILES;
    }

    /** Files the placebo zone is shifted by; 4 keeps the two 3×3 zones from overlapping. */
    private static final int PLACEBO_FILE_SHIFT = 4;

    private static final int FILES = 8;

    /**
     * Distinct pieces {@code attackerColor} has bearing on the other king's zone.
     *
     * <p>This is the quantity {@code WeightingFunction.calcKingAttackPenalty} gates on, and it is
     * not derivable from the unit sum: five units is one queen or a rook and a knight, and the
     * gate treats those differently. Measuring the gate's effect needs both numbers, which is why
     * this exists alongside {@link #of}.
     *
     * @param board         the position
     * @param attackerColor the side doing the attacking
     * @return the number of distinct attacking pieces
     */
    static int attackersOf(Board board, int attackerColor) {
        final byte[] squares = board.getRawBoard();
        final boolean attackerIsWhite = attackerColor == GameStatus.TURN_WHITE;
        final byte enemyKing = attackerIsWhite ? Board.blackKing : Board.whiteKing;
        final int[] attackers = new int[1];

        around(squares, findKing(squares, enemyKing), attackerIsWhite, attackers);

        return attackers[0];
    }

    /**
     * The shared walk: units over the 3×3 zone around {@code centerField}.
     *
     * @param counter when non-null, receives the number of distinct attackers in element 0
     */
    private static int around(byte[] squares, int centerField, boolean attackerIsWhite, int[] counter) {
        if (centerField < 0) {
            return 0;
        }

        // Read from the production class rather than repeated here, exactly as the unit weights
        // are: the curve is fitted through this class and applied by the production term, so a
        // disagreement about which squares belong to the zone would index the table by a quantity
        // nobody calibrated. Applied to whatever centre this method is given, the placebo centre
        // included, so the control keeps obeying the same rules as the real zone.
        final int corrected = centerField
                + WeightingFunction.KING_FIELD_CORRECTION_OFFSET[centerField % Board.LENGTH - 2];

        // Deduplication by origin square: a rook bearing on three zone squares is one attacker.
        final boolean[] counted = new boolean[Board.LENGTH * Board.LENGTH];
        int units = unitsOn(squares, corrected, attackerIsWhite, counted);

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            final int zoneField = corrected + offset;

            if (squares[zoneField] != Board.illegal) {
                units += unitsOn(squares, zoneField, attackerIsWhite, counted);
            }
        }

        if (counter != null) {
            // The dedup mask also marks the attacking king, which carries zero units and which
            // the production gate does not count -- it increments only for a positive weight.
            // Counting mask entries naively would report one attacker too many whenever a king
            // stands next to the enemy king's zone, and the gate's threshold is two.
            final byte king = attackerIsWhite ? Board.whiteKing : Board.blackKing;

            for (int field = 0; field < counted.length; field++) {
                if (counted[field] && squares[field] != king) {
                    counter[0]++;
                }
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
