package org.michaelfl.mychess;

/**
 * A Chess960-only evaluation variant for the offline check of the piece-square arms
 * ({@code myChess-lab/preregistration/chess960-pst-offline-check.md}). It changes the first rank
 * of the knight, bishop and king midgame tables and the weight of the castling term, and only in
 * Chess960 games: standard chess always uses the unchanged tables.
 *
 * <p>The variant is chosen once per JVM by the system property {@value #PROPERTY}, a list of arm
 * names joined by {@code +}:
 * <ul>
 *   <li>{@code base} - nothing changes (also the default when the property is not set)</li>
 *   <li>{@code N} - knight: the whole first rank gets the value of b1/g1 (-40)</li>
 *   <li>{@code L} - bishop: the whole first rank gets the value of c1/f1 (-26)</li>
 *   <li>{@code A}, {@code B}, {@code C}, {@code D} - king: a flat first rank of 16, 9, 0 or 50,
 *       with b2/g2 and a2/h2 lowered where they would otherwise be worth more than the first rank</li>
 *   <li>{@code E} - king: like {@code D} (b1-g1 at 50), but a1 and h1 keep their standard value,
 *       because the king never starts there in Chess960 (it always stands between the rooks)</li>
 *   <li>{@code R} - castling term switched off</li>
 * </ul>
 * For example {@code -Dmychess.c960arm=N+L+B+R}. At most one king arm may be given.
 *
 * <p>This class exists for the measurement only and lives on the branch
 * {@code chess960-pst-variants}; whatever passes is rebuilt without the switch.
 *
 * @param flatKnightRank whether the knight's first rank is flattened to {@link #KNIGHT_RANK_VALUE}
 * @param flatBishopRank whether the bishop's first rank is flattened to {@link #BISHOP_RANK_VALUE}
 * @param king           the king arm, or {@code null} for today's king table
 * @param noCastlingTerm whether the castling term is switched off
 * @author Michael Fleischhauer
 */
record Chess960Variant(boolean flatKnightRank, boolean flatBishopRank, KingArm king, boolean noCastlingTerm) {

    /** System property that selects the variant. */
    static final String PROPERTY = "mychess.c960arm";

    /** Arm name for "no change". */
    static final String BASE = "base";

    /** Knight first-rank value of arm N: the value of b1/g1 in the standard table. */
    static final short KNIGHT_RANK_VALUE = -40;

    /** Bishop first-rank value of arm L: the value of c1/f1 in the standard table. */
    static final short BISHOP_RANK_VALUE = -26;

    /** The variant that changes nothing. */
    static final Chess960Variant NONE = new Chess960Variant(false, false, null, false);

    /** The variant of this JVM, read once from {@value #PROPERTY}. */
    static final Chess960Variant ACTIVE = parse(System.getProperty(PROPERTY, BASE));

    /**
     * A king arm: the value of the first rank and of the squares b2/g2 and a2/h2.
     *
     * @param firstRank   the value of the first-rank squares (all eight, or b1-g1 with {@code keepCorners})
     * @param b2g2        the value of b2 and g2
     * @param a2h2        the value of a2 and h2
     * @param keepCorners whether a1 and h1 keep their standard value
     */
    record KingArm(short firstRank, short b2g2, short a2h2, boolean keepCorners) {
    }

    /** Thrown for an arm list that names an unknown arm or more than one king arm. */
    static final class UnknownChess960VariantException extends IllegalArgumentException {

        UnknownChess960VariantException(String message) {
            super(message);
        }
    }

    /**
     * Parses an arm list such as {@code "N+L+B+R"}.
     *
     * @param arms the arm names joined by {@code +}; {@code base} or blank means no change
     * @return the variant
     * @throws UnknownChess960VariantException if an arm is unknown or two king arms are given
     */
    static Chess960Variant parse(String arms) {
        boolean knight = false;
        boolean bishop = false;
        KingArm king = null;
        boolean noCastling = false;

        for (String token : arms.split("\\+")) {
            final String arm = token.trim();

            switch (arm) {
                case "", BASE -> {
                    // no change
                }
                case "N" -> knight = true;
                case "L" -> bishop = true;
                case "R" -> noCastling = true;
                case "A", "B", "C", "D", "E" -> {
                    if (king != null) {
                        throw new UnknownChess960VariantException("more than one king arm in '%s'".formatted(arms));
                    }

                    king = kingArm(arm);
                }
                default -> throw new UnknownChess960VariantException("unknown Chess960 arm '%s' in '%s'".formatted(arm, arms));
            }
        }

        return new Chess960Variant(knight, bishop, king, noCastling);
    }

    private static KingArm kingArm(String arm) {
        return switch (arm) {
            case "A" -> new KingArm((short) 16, (short) 16, (short) 9, false);
            case "B" -> new KingArm((short) 9, (short) 9, (short) 9, false);
            case "C" -> new KingArm((short) 0, (short) 0, (short) 0, false);
            case "D" -> new KingArm((short) 50, (short) 16, (short) 9, false);
            case "E" -> new KingArm((short) 50, (short) 16, (short) 9, true);
            default -> throw new UnknownChess960VariantException("unknown king arm '%s'".formatted(arm));
        };
    }

    /**
     * Applies this variant to a white midgame table and returns the changed copy. Tables of
     * pieces the variant does not touch come back unchanged (as a copy).
     *
     * @param whitePiece the white piece constant the table belongs to
     * @param table      the white midgame table, indexed by field
     * @return the table to use in Chess960
     */
    short[] applyToWhiteMidGameTable(byte whitePiece, short[] table) {
        final short[] result = table.clone();

        if (whitePiece == Board.whiteKnight && flatKnightRank) {
            setFirstRank(result, KNIGHT_RANK_VALUE);
        } else if (whitePiece == Board.whiteBishop && flatBishopRank) {
            setFirstRank(result, BISHOP_RANK_VALUE);
        } else if (whitePiece == Board.whiteKing && king != null) {
            final short a1 = table[Board.a1];
            final short h1 = table[Board.h1];

            setFirstRank(result, king.firstRank());

            if (king.keepCorners()) {
                result[Board.a1] = a1;
                result[Board.h1] = h1;
            }

            result[Board.b2] = king.b2g2();
            result[Board.g2] = king.b2g2();
            result[Board.a2] = king.a2h2();
            result[Board.h2] = king.a2h2();
        }

        return result;
    }

    /**
     * The weight of the castling term in Chess960 for this variant.
     *
     * @return 0 when the castling term is switched off, otherwise 1
     */
    float castlingScale() {
        return noCastlingTerm ? 0f : 1f;
    }

    private static void setFirstRank(short[] table, short value) {
        for (int col = 0; col < 8; col++) {
            table[ChessUtil.getFieldFromColAndRow(col, 0)] = value;
        }
    }
}
