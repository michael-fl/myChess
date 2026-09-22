package org.michaelfl.mychess;

import java.util.Collection;

/**
 * Stateless conversions between board fields, pieces, packed moves and their
 * string representations (field names like {@code "e4"}, piece symbols, move
 * notation, evaluation weights).
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("unused")
public final class ChessUtil {

    public static final int KING_ZONE_ATTACK_CHEBYSHEV_DISTANCE_PAWN = 2;
    public static final int KING_ZONE_ATTACK_CHEBYSHEV_DISTANCE_KNIGHT = 3;

    private ChessUtil() {
        // class cannot be instantiated
    }

    public static int getFieldFromColAndRow(int col, int row) {
        return (row + 2) * 12 + col + 2;
    }

    public static int[] getColAndRowFromString(String fieldString) {
        if (fieldString.length() != 2)
            throw new IllegalArgumentException("Wrong field notation: " + fieldString);
        char colChar = fieldString.charAt(0);
        char rowChar = fieldString.charAt(1);
        if (colChar < 'a' || colChar > 'h' || rowChar < '1' || rowChar > '8')
            throw new IllegalArgumentException("Wrong field notation: " + fieldString);

        return new int[] {
            colChar - 'a',
            rowChar - '1'
        };
    }

    public static int colAndRowToField(int col, int row) {
        return Board.LENGTH * (2 + row) + 2 + col;
    }

    public static int getRowOfField(int field) {
        int row = field / Board.LENGTH;
        if (row < 2 || row > 9) // TODO check if those checks are actually required. Otherwise remove them.
            return -1;
        return row - 2;
    }

    public static int getColOfField(int field) {
        int col = field % Board.LENGTH;
        if (col < 2 || col > 9)
            return -1;
        return col - 2;
    }

    public static String fieldToString(int field) {
        int row = getRowOfField(field);
        int col = getColOfField(field);
        return String.valueOf((char) ('a' + col)) + (row + 1);
    }

    public static String moveToString(int fromField, int toField) {
        return fieldToString(fromField) + "-" + fieldToString(toField);
    }

    public static String moveToString(int move) {
        if (move == 0)
            return "nil";

        return moveToString(Move.getFromField(move), Move.getToField(move))
                + getPawnPromotionSymbol(move);
    }

    private static String getPawnPromotionSymbol(int move) {
        byte moveType = Move.getMoveType(move);
        if (moveType == Move.typePawnPromotionKnight)
            return "N";
        else if (moveType == Move.typePawnPromotionQueen)
            return "Q";
        else if (moveType == Move.typePawnPromotionRook)
            return "R";
        else if (moveType == Move.typePawnPromotionBishop)
            return "B";

        return "";
    }

    public static String moveToString(Move move, Board board) {
        return moveToString(move.move(), board);
    }

    public static String moveToString(int move, Board board) {
        if (move == 0)
            return "nil";
        byte piece = board.get(Move.getFromField(move));
        return (piece == Board.whitePawn || piece == Board.blackPawn ? "" : pieceToString(piece))
                + fieldToString(Move.getFromField(move))
                + (Move.getCapturedPiece(move) != 0 ? "x" : "")
                + fieldToString(Move.getToField(move))
                + getPawnPromotionSymbol(move);
    }

    public static String movesToString(Collection<Integer> moves) {
        StringBuilder buf = new StringBuilder();
        buf.append(moves.size()).append("# ");

        int i = 0;
        for (var move : moves) {
            if (i++ > 0)
                buf.append(" ");
            buf.append(ChessUtil.moveToString(move));
        }

        return buf.toString();
    }

    public static String movesToString(int[] moves, int length) {
        StringBuilder buf = new StringBuilder();
        buf.append(length).append("# ");

        for (int i = 0; i < length; i ++) {
            if (i > 0)
                buf.append(" ");
            buf.append(ChessUtil.moveToString(moves[i]));
        }

        return buf.toString();
    }

    public static String weightToString(int weight) {
        return weightToString(weight, 1);
    }

    public static String weightToString(int weight, int factor) {
        if (WeightingFunction.isIllegalWeight(weight))
            return "illegal";
        if (weight != 0) {
            weight *= factor;
        }

        if (WeightingFunction.isCheckmateWeight(weight)) {
            int plies = WeightingFunction.checkmateWeightToPlies(weight);
            return (weight < 0 ? "-" : "") + "M" + plies;
        }

        return String.valueOf(weight / 100f);
    }

    public static String weightToString(float weight) {
        return weightToString(weight, 1);
    }

    public static String weightToString(float weight, int factor) {
        return weightToString(Math.round(weight * 100f), factor);
    }

    public static String pathToString(int[] path) {
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < path.length; i++) {
            if (path[i] == 0)
                break;
            if (i > 0)
                buf.append(' ');
            buf.append(moveToString(path[i]));
        }

        return buf.toString();
    }

    /** Map the given piece to its corresponding number from 0 to 11. 0 = white pawn, 11 = black king. */
    public static int getPieceNumber12(byte piece) {
        return piece >= Board.blackPawn ? 6 + piece - Board.blackPawn : piece - Board.whitePawn;
    }

    /** Map the given field to its corresponding number from 0 to 63, where a1 = 0, b1 = 1, ... h8 = 63. */
    public static int getFieldNumber64(final int field) {
        return Board.FIELD_12_TO_8[field];
    }

    public static int setBit(int bitSet, int bit, boolean set) {
        return set ? bitSet | bit : bitSet & ~bit;
    }

    public static int setBit(int bitSet, int bit) {
        return bitSet | bit;
    }

    public static byte symbolToPiece(char symbol, int turn) {
        final boolean isWhite = turn == GameStatus.TURN_WHITE;

        return switch (symbol) {
            case 'P' -> isWhite ? Board.whitePawn : Board.blackPawn;
            case 'N' -> isWhite ? Board.whiteKnight : Board.blackKnight;
            case 'B' -> isWhite ? Board.whiteBishop : Board.blackBishop;
            case 'R' -> isWhite ? Board.whiteRook : Board.blackRook;
            case 'Q' -> isWhite ? Board.whiteQueen : Board.blackQueen;
            case 'K' -> isWhite ? Board.whiteKing : Board.blackKing;
            default -> throw new IllegalArgumentException("Unknown symbol: " + symbol);
        };
    }

    public static String pieceToString(byte piece) {
        return switch (piece) {
            case Board.empty -> "";
            case Board.whitePawn, Board.blackPawn -> "P";
            case Board.whiteKnight, Board.blackKnight -> "N";
            case Board.whiteBishop, Board.blackBishop -> "B";
            case Board.whiteRook, Board.blackRook -> "R";
            case Board.whiteQueen, Board.blackQueen -> "Q";
            case Board.whiteKing, Board.blackKing -> "K";
            default -> throw new IllegalArgumentException("Unknown piece: " + piece);
        };
    }

    public static String pieceToDebugString(byte piece) {
        return switch (piece) {
            case Board.empty -> "empty";
            case Board.illegal -> "illegal";
            case Board.whitePawn -> "whitePawn";
            case Board.whiteKnight -> "whiteKnight";
            case Board.whiteBishop -> "whiteBishop";
            case Board.whiteRook -> "whiteRook";
            case Board.whiteQueen -> "whiteQueen";
            case Board.whiteKing -> "whiteKing";
            case Board.blackPawn -> "blackPawn";
            case Board.blackKnight -> "blackKnight";
            case Board.blackBishop -> "blackBishop";
            case Board.blackRook -> "blackRook";
            case Board.blackQueen -> "blackQueen";
            case Board.blackKing -> "blackKing";
            default -> "unknown#" + piece;
        };
    }

    /**
     * Returns the column (0..7) of the first occurrence of {@code piece} on the
     * given {@code row} (0..7, row 0 = rank 1), or {@code -1} if {@code piece}
     * is not present in that row.
     *
     * <p>The sentinel is {@code -1}, not {@code Board.empty}: {@code Board.empty}
     * equals {@code 0}, which collides with the valid column index for the
     * a-file. With Chess960 a king or rook may actually start on the a-file —
     * using {@code 0} as "not found" would silently confuse the two cases.
     */
    public static int findColOfPieceOnRow(byte[] rawBoard, int piece, int row) {
        final int startField = getFieldFromColAndRow(0, row);
        final int stopField = startField + 8;

        for (int field = startField; field < stopField; field++) {
            if (rawBoard[field] == piece) {
                return getColOfField(field);
            }
        }

        return -1;
    }

    public static boolean isKing(final byte piece) {
        return piece == Board.whiteKing || piece == Board.blackKing;
    }

    public static boolean isBishop(final byte piece) {
        return piece == Board.whiteBishop || piece == Board.blackBishop;
    }

    public static boolean isKnight(final byte piece) {
        return piece == Board.whiteKnight || piece == Board.blackKnight;
    }

    public static boolean isRook(final byte piece) {
        return piece == Board.whiteRook || piece == Board.blackRook;
    }

    public static boolean isQueen(final byte piece) {
        return piece == Board.whiteQueen || piece == Board.blackQueen;
    }

    public static boolean isWhitePiece(final byte piece) {
        return piece >= Board.whitePawn && piece <= Board.whiteKing;
    }

    public static boolean isBlackPiece(byte piece) {
        return piece >= Board.blackPawn && piece <= Board.blackKing;
    }

    /**
     * Could this piece bear on the 3x3 zone around {@code kingFieldCorrected} at all?
     *
     * <p>A cheap pre-filter for the king-attack term, which otherwise asks per attacked square
     * whether that square lies in the zone. This asks once per piece instead, from its origin.
     *
     * <p><b>Blockers are ignored</b>, which is what "pseudo" means here, as in pseudo-legal:
     * a {@code true} is a "maybe", only a {@code false} is reliable. That is the safe side - a
     * false positive costs one wasted zone test, a false negative would silently drop an
     * attacker from the count and no single position would make that obvious.
     *
     * @param piece              the attacking piece; anything that is not a piece reaches nothing
     * @param fromField          the square the piece stands on
     * @param kingFieldCorrected the zone's center, as {@code calcKingFieldCorrected} yields it
     * @return {@code false} only when the piece provably cannot reach the zone
     */
    public static boolean pseudoAttacksKingZone(byte piece, int fromField, int kingFieldCorrected) {
        return switch (piece) {
            case Board.whitePawn, Board.blackPawn,
                 Board.whiteKing, Board.blackKing -> chebyshevDistance(fromField, kingFieldCorrected) <= KING_ZONE_ATTACK_CHEBYSHEV_DISTANCE_PAWN;
            case Board.whiteKnight, Board.blackKnight -> chebyshevDistance(fromField, kingFieldCorrected) <= KING_ZONE_ATTACK_CHEBYSHEV_DISTANCE_KNIGHT;
            case Board.whiteBishop, Board.blackBishop -> canReachKingZoneDiagonal(fromField, kingFieldCorrected);
            case Board.whiteRook, Board.blackRook -> canReachKingZoneOrthogonal(fromField, kingFieldCorrected);
            case Board.whiteQueen, Board.blackQueen -> canReachKingZoneOrthogonal(fromField, kingFieldCorrected)
                    || canReachKingZoneDiagonal(fromField, kingFieldCorrected);
            default -> false;
        };
    }

    /**
     * King-move distance between two squares: {@code max(|file delta|, |rank delta|)}.
     *
     * <p>Also called the chessboard distance. A diagonal step costs the same as a straight one,
     * which is what a king does and what makes this the right metric for the zone. Manhattan
     * distance answers a different question and would give 6 where a king needs 3.
     *
     * @param field1 one square
     * @param field2 the other
     * @return the number of king moves between them, 0 for a square and itself
     */
    public static int chebyshevDistance(int field1, int field2) {
        final int fileDelta = Math.abs(field1 % Board.LENGTH - field2 % Board.LENGTH);
        final int rankDelta = Math.abs(field1 / Board.LENGTH - field2 / Board.LENGTH);

        return Math.max(fileDelta, rankDelta);
    }

    /**
     * Does a diagonal through {@code fromField} cross the 3x3 zone around the king?
     *
     * <p>A diagonal is identified by an invariant that every one of its squares shares:
     * {@code file - rank} in one direction, {@code file + rank} in the other. The zone's squares
     * differ from its center by at most one file and one rank, so their invariants span the
     * center's plus or minus two. Sharing an invariant means sharing a diagonal, which settles
     * square color by itself.
     *
     * <p>Blockers are ignored; see {@link #pseudoAttacksKingZone} for what that costs.
     *
     * @param fromField          the square the bishop or queen stands on
     * @param kingFieldCorrected the zone's center
     * @return {@code false} only when neither diagonal touches the zone
     */
    public static boolean canReachKingZoneDiagonal(int fromField, int kingFieldCorrected) {
        final int r1 = fromField / Board.LENGTH; // offset -2 is equal for all 4 variables and can be omitted
        final int f1 = fromField % Board.LENGTH;
        final int r2 = kingFieldCorrected / Board.LENGTH;
        final int f2 = kingFieldCorrected % Board.LENGTH;

        return Math.abs((f1 - r1) - (f2 - r2)) <= 2 || Math.abs((f1 + r1) - (f2 + r2)) <= 2;
    }

    /**
     * Does the file or the rank through {@code fromField} cross the 3x3 zone around the king?
     *
     * <p>The zone spans three files and three ranks, so a rook or queen touches it exactly when
     * its own file or its own rank is within one of the center's.
     *
     * <p>Blockers are ignored; see {@link #pseudoAttacksKingZone}.
     *
     * @param fromField          the square the rook or queen stands on
     * @param kingFieldCorrected the zone's center
     * @return {@code false} only when neither the file nor the rank touches the zone
     */
    public static boolean canReachKingZoneOrthogonal(int fromField, int kingFieldCorrected) {
        final int fileDelta = Math.abs(fromField % Board.LENGTH - kingFieldCorrected % Board.LENGTH);
        if (fileDelta <= 1) {
            return true;
        }

        final int rankDelta = Math.abs(fromField / Board.LENGTH - kingFieldCorrected / Board.LENGTH);
        return rankDelta <= 1;
    }

    public static final byte DISTANCE_SENTINEL = Byte.MAX_VALUE;
    public static final int INDEX_UP = 0;
    public static final int INDEX_RIGHT = 1;
    public static final int INDEX_DOWN = 2;
    public static final int INDEX_LEFT = 3;

    public static final int INDEX_UP_RIGHT = 0;
    public static final int INDEX_DOWN_RIGHT = 1;
    public static final int INDEX_DOWN_LEFT = 2;
    public static final int INDEX_UP_LEFT = 3;

    public static final int ALL_SENTINELS_ENCODED = BitOps.createWord(DISTANCE_SENTINEL, DISTANCE_SENTINEL, DISTANCE_SENTINEL, DISTANCE_SENTINEL);

    /**
     * How far the 3x3 king zone is from {@code fromField} along each of the four rook rays,
     * packed into one {@code int} as {@code [up|right|down|left]}.
     *
     * <p>Read a byte out with {@link BitOps#getByte0} through {@link BitOps#getByte3}, in that
     * order. Each byte counts the steps to the first square that lies inside the zone, so 1
     * means the very next square along that ray.
     *
     * <p>Three answers are possible, and they must not be confused:
     * <ul>
     *   <li>a step count, for a direction that reaches the zone;
     *   <li>{@link #DISTANCE_SENTINEL} for a direction that never does - it is
     *       {@link Byte#MAX_VALUE} rather than 0 precisely so it cannot be mistaken for one;
     *   <li>a plain {@code 0}, meaning {@code fromField} lies in the zone already, which makes
     *       every direction zero steps. That is not the same word as
     *       {@link #ALL_SENTINELS_ENCODED}.
     * </ul>
     *
     * <p>At most one direction ever carries a count. A rook reaches the zone by moving along
     * its file only when it already shares a file with it, and then the other three rays lead
     * away or run parallel past it.
     *
     * @param fromField          the square the rook or queen stands on
     * @param kingFieldCorrected the zone's center; corrected, so never on the a or h file
     * @return the four distances packed as {@code [up|right|down|left]}
     */
    public static int getKingZoneDistancesOrthogonalEncoded(final int fromField, final int kingFieldCorrected) {
        final int fromFile = fromField % Board.LENGTH - 2;
        final int fromRank = fromField / Board.LENGTH - 2;
        final int kingFile = kingFieldCorrected % Board.LENGTH - 2;
        final int kingRank = kingFieldCorrected / Board.LENGTH - 2;

        if (Math.abs(kingFile - fromFile) <= 1) {
            return horizontalKingZoneDistancesEncoded(kingRank, fromRank);
        } else if (Math.abs(kingRank - fromRank) <= 1) {
            return verticalKingZoneDistancesEncoded(kingFile, fromFile);
        } else { // cannot reach zone
            return ALL_SENTINELS_ENCODED;
        }
    }

    private static int verticalKingZoneDistancesEncoded(int kingFile, int fromFile) {
        final int zoneFile1 = kingFile - 1;
        final int zoneFile2 = kingFile + 1;

        if (fromFile > zoneFile2) { // right of zone
            return BitOps.createWord(
                    DISTANCE_SENTINEL,    // up
                    DISTANCE_SENTINEL,    // right
                    DISTANCE_SENTINEL,    // down
                    (byte) (fromFile - zoneFile2));
        } else if (fromFile < zoneFile1) { // left of zone
            return BitOps.createWord(
                    DISTANCE_SENTINEL,    // up
                    (byte) (zoneFile1 - fromFile), // right
                    DISTANCE_SENTINEL,    // down
                    DISTANCE_SENTINEL);
        } else { // within zone
            return 0;
        }
    }

    private static int horizontalKingZoneDistancesEncoded(int kingRank, int fromRank) {
        final int zoneRank1 = kingRank - 1;
        final int zoneRank2 = kingRank + 1;

        if (fromRank > zoneRank2) { // above zone
            return BitOps.createWord(
                    DISTANCE_SENTINEL,    // up
                    DISTANCE_SENTINEL,    // right
                    (byte) (fromRank - zoneRank2), // down
                    DISTANCE_SENTINEL);
        } else if (fromRank < zoneRank1) { // below zone
            return BitOps.createWord(
                    (byte) (zoneRank1 - fromRank), // up
                    DISTANCE_SENTINEL,    // right
                    DISTANCE_SENTINEL,    // down
                    DISTANCE_SENTINEL);
        } else { // within zone
            return 0;
        }
    }

    /** Highest file and rank index of a real square, the board being eight by eight. */
    private static final int LAST_FILE_OR_RANK = 7;

    /**
     * How far the 3x3 king zone is from {@code fromField} along each of the four bishop rays,
     * packed into one {@code int} as {@code [up-right|down-right|down-left|up-left]}.
     *
     * <p>The diagonal counterpart of {@link #getKingZoneDistancesOrthogonalEncoded}, with the
     * same three possible answers per byte: a step count, {@link #DISTANCE_SENTINEL} for a ray
     * that never arrives, or a plain {@code 0} for a piece that stands in the zone already.
     *
     * <p>Unlike the orthogonal case, more than one ray can carry a count: a bishop beside the
     * zone on a diagonal may enter it going one way and leave it going the other.
     *
     * @param fromField          the square the bishop or queen stands on
     * @param kingFieldCorrected the zone's center; corrected, so never on the a or h file
     * @return the four distances packed as {@code [up-right|down-right|down-left|up-left]}
     */
    public static int getKingZoneDistancesDiagonalEncoded(final int fromField, final int kingFieldCorrected) {
        final int fromFile = fromField % Board.LENGTH - 2;
        final int fromRank = fromField / Board.LENGTH - 2;
        final int kingFile = kingFieldCorrected % Board.LENGTH - 2;
        final int kingRank = kingFieldCorrected / Board.LENGTH - 2;

        return BitOps.createWord(
                stepsAlongDiagonal(fromFile, fromRank, kingFile, kingRank, 1, 1),    // up-right
                stepsAlongDiagonal(fromFile, fromRank, kingFile, kingRank, 1, -1),   // down-right
                stepsAlongDiagonal(fromFile, fromRank, kingFile, kingRank, -1, -1),  // down-left
                stepsAlongDiagonal(fromFile, fromRank, kingFile, kingRank, -1, 1));  // up-left
    }

    /**
     * Steps along one diagonal until the first square inside the zone, or the sentinel.
     *
     * <p>After {@code k} steps the piece stands on {@code (file + k * fileStep, rank + k *
     * rankStep)}, and that square is in the zone when it is within one of the center on both
     * axes. Solving each axis for {@code k} gives an interval two wide, so the answer is the
     * smallest non-negative value both intervals contain - and no value at all when they do
     * not overlap.
     *
     * <p><b>The bounds check is not redundant</b>, which is the one way this differs from the
     * orthogonal sibling. There the two axes are independent and a solution always lands on the
     * board. Here they are coupled, and the intersection can name a {@code k} whose square is
     * off the edge: a king on e1 and a bishop on a3 going down-right solves at three steps,
     * where the rank would be minus one. Walking off the board is never a way into the zone.
     * One check suffices because a ray moves monotonically, so if the nearest solution is off
     * the board, every later one is further off.
     */
    private static byte stepsAlongDiagonal(final int fromFile, final int fromRank,
                                           final int kingFile, final int kingRank,
                                           final int fileStep, final int rankStep) {
        final int fileOffset = -fileStep * (fromFile - kingFile);
        final int rankOffset = -rankStep * (fromRank - kingRank);

        final int first = Math.max(Math.max(fileOffset - 1, rankOffset - 1), 0);
        final int last = Math.min(fileOffset + 1, rankOffset + 1);

        if (first > last) {
            return DISTANCE_SENTINEL;
        }

        final int targetFile = fromFile + first * fileStep;
        final int targetRank = fromRank + first * rankStep;

        if (targetFile < 0 || targetFile > LAST_FILE_OR_RANK
                || targetRank < 0 || targetRank > LAST_FILE_OR_RANK) {
            return DISTANCE_SENTINEL;
        }

        return (byte) first;
    }

    public static int getKingZoneDistancesDiagonalEncodedNew(final int fromField, final int kingFieldCorrected) {
        final int fromFile = fromField % Board.LENGTH - 2;
        final int fromRank = fromField / Board.LENGTH - 2;
        final int kingFile = kingFieldCorrected % Board.LENGTH - 2;
        final int kingRank = kingFieldCorrected / Board.LENGTH - 2;
        final int zoneFile1 = kingFile - 1;
        final int zoneFile2 = kingFile + 1;
        final int zoneRank1 = Math.max(kingRank - 1, 0);
        final int zoneRank2 = Math.min(kingRank + 1, 7);

        // Within zone
        if (zoneFile1 <= fromFile && fromFile <= zoneFile2
                && zoneRank1 <= fromRank && fromRank <= zoneRank2) {
            return 0;
        }

        return BitOps.createWord(
                (byte) distanceNorthEast(fromFile, fromRank, zoneFile1, zoneRank1, zoneFile2, zoneRank2), // up-right
                (byte) distanceSouthEast(fromFile, fromRank, zoneFile1, zoneRank1, zoneFile2, zoneRank2), // down-right
                (byte) distanceSouthWest(fromFile, fromRank, zoneFile1, zoneRank1, zoneFile2, zoneRank2), // down-left
                (byte) distanceNorthWest(fromFile, fromRank, zoneFile1, zoneRank1, zoneFile2, zoneRank2)  // up-left
        );
    }

    private static int distanceSouthEast(final int fromFile, final int fromRank, final int zoneFile1, final int zoneRank1, final int zoneFile2, final int zoneRank2) {
        // left edge of zone
        if (zoneFile1 > fromFile) {
            final int fileDelta = zoneFile1 - fromFile;
            final int r1 = zoneRank1 + fileDelta;
            final int r2 = zoneRank2 + fileDelta;
            if (r1 <= fromRank && fromRank <= r2) {
                return fileDelta;
            }
        }

        // top edge of zone
        if (zoneRank2 < fromRank) {
            final int rankDelta = fromRank - zoneRank2;
            final int f1 = zoneFile1 - rankDelta;
            final int f2 = zoneFile2 - rankDelta;
            if (f1 <= fromFile && fromFile <= f2) {
                return rankDelta;
            }
        }

        return DISTANCE_SENTINEL;
    }

    private static int distanceSouthWest(final int fromFile, final int fromRank, final int zoneFile1, final int zoneRank1, final int zoneFile2, final int zoneRank2) {
        // right edge of zone
        if (zoneFile2 < fromFile) {
            final int fileDelta = fromFile - zoneFile2;
            final int r1 = zoneRank1 + fileDelta;
            final int r2 = zoneRank2 + fileDelta;
            if (r1 <= fromRank && fromRank <= r2) {
                return fileDelta;
            }
        }

        // top edge of zone
        if (zoneRank2 < fromRank) {
            final int rankDelta = fromRank - zoneRank2;
            final int f1 = zoneFile1 + rankDelta;
            final int f2 = zoneFile2 + rankDelta;
            if (f1 <= fromFile && fromFile <= f2) {
                return rankDelta;
            }
        }

        return DISTANCE_SENTINEL;
    }

    private static int distanceNorthWest(final int fromFile, final int fromRank, final int zoneFile1, final int zoneRank1, final int zoneFile2, final int zoneRank2) {
        // right edge of zone
        if (zoneFile2 < fromFile) {
            final int fileDelta = fromFile - zoneFile2;
            final int r1 = zoneRank1 - fileDelta;
            final int r2 = zoneRank2 - fileDelta;
            if (r1 <= fromRank && fromRank <= r2) {
                return fileDelta;
            }
        }

        // bottom edge of zone
        if (zoneRank1 > fromRank) {
            final int rankDelta = zoneRank1 - fromRank;
            final int f1 = zoneFile1 + rankDelta;
            final int f2 = zoneFile2 + rankDelta;
            if (f1 <= fromFile && fromFile <= f2) {
                return rankDelta;
            }
        }

        return DISTANCE_SENTINEL;
    }

    private static int distanceNorthEast(final int fromFile, final int fromRank, final int zoneFile1, final int zoneRank1, final int zoneFile2, final int zoneRank2) {
        // left edge of zone
        if (zoneFile1 > fromFile) {
            final int fileDelta = zoneFile1 - fromFile;
            final int r1 = zoneRank1 - fileDelta;
            final int r2 = zoneRank2 - fileDelta;
            if (r1 <= fromRank && fromRank <= r2) {
                return fileDelta;
            }
        }

        // bottom edge of zone
        if (zoneRank1 > fromRank) {
            final int rankDelta = zoneRank1 - fromRank;
            final int f1 = zoneFile1 - rankDelta;
            final int f2 = zoneFile2 - rankDelta;
            if (f1 <= fromFile && fromFile <= f2) {
                return rankDelta;
            }
        }

        return DISTANCE_SENTINEL;
    }
}
