package org.michaelfl.mychess;

import java.util.Arrays;

import static org.michaelfl.mychess.Assert.__assert;

//    132           ...             143
//    120           ...             131
//    108   110(a8) ... 117(h8) 118 119
//           98(a7) ... 105(h7)
//           86(a6) ...  93(h6)
//           74(a5) ...  81(h5)
//           62(a4) ...  69(h4)
//           50(a3) ...  57(h3)
//           38(a2) ...  45(h2)  46  47
//    24 25  26(a1) ...  33(h1)  34  35
//    12 13         ...          22  23
//    00 01         ...          10  11

/**
 * Static position evaluation in centipawn units: material plus
 * {@link PieceSquareTables} bonus plus per-piece capture/threat heuristics
 * (pawn structure including en-passant target, king safety, ...). Returns a
 * white-positive score that the engine negates at the boundary for black.
 * The {@code CHECKMATE_*} and {@code ILLEGAL_*} constants are sentinel
 * ranges above any normal material delta.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings({"StatementWithEmptyBody", "Duplicates", "PointlessArithmeticExpression",
                   "java:S115", "java:S2386", "java:S3358"})
public final class WeightingFunction {

    public static final int MIN_ALPHA = -Integer.MAX_VALUE;
    public static final int MAX_BETA = Integer.MAX_VALUE;
    public static final int ILLEGAL_WEIGHT_NEG = -1_000_000;
    public static final int ILLEGAL_WEIGHT_POS = 1_000_000;
    public static final int CHECKMATE_WEIGHT_LOW = 100_000;
    public static final int CHECKMATE_WEIGHT_HIGH = 200_000;

    private static final String DELTA_STR = ", delta=";
    private static final String WEIGHT_STR = ", weight=";
    private static final byte ATTACK_MARK_BIT = 32;
    private static final byte WHITE_KING_ATTACKED = Board.whiteKing | ATTACK_MARK_BIT;
    private static final byte BLACK_KING_ATTACKED = Board.blackKing | ATTACK_MARK_BIT;

    private static final byte[] PAWN = new byte[] { Board.whitePawn, Board.blackPawn };
    private static final byte[] FORWARD_OFFSET = new byte[] { Board.LENGTH, -Board.LENGTH };

    /** Piece weight in centi pawns. */
    public static final int[] weightOfPiece = new int[Board.blackKing + 1];
    static {
        weightOfPiece[Board.whitePawn]   = 100;
        weightOfPiece[Board.whiteKnight] = 300;
        weightOfPiece[Board.whiteBishop] = 300;
        weightOfPiece[Board.whiteRook]   = 500;
        weightOfPiece[Board.whiteQueen]  = 900;
        weightOfPiece[Board.whiteKing]   = 0;
        weightOfPiece[Board.blackPawn]   = 100;
        weightOfPiece[Board.blackKnight] = 300;
        weightOfPiece[Board.blackBishop] = 300;
        weightOfPiece[Board.blackRook]   = 500;
        weightOfPiece[Board.blackQueen]  = 900;
        weightOfPiece[Board.blackKing]   = 0;
    }

    private static final int[] mobilityWeightOfPiece = new int[Board.blackKing + 1];
    static {
        mobilityWeightOfPiece[Board.whitePawn]   = 5;
        mobilityWeightOfPiece[Board.whiteKnight] = 40;
        mobilityWeightOfPiece[Board.whiteBishop] = 30;
        mobilityWeightOfPiece[Board.whiteRook]   = 20;
        mobilityWeightOfPiece[Board.whiteQueen]  = 3;
        mobilityWeightOfPiece[Board.whiteKing]   = 0;
        mobilityWeightOfPiece[Board.blackPawn]   = 5;
        mobilityWeightOfPiece[Board.blackKnight] = 40;
        mobilityWeightOfPiece[Board.blackBishop] = 30;
        mobilityWeightOfPiece[Board.blackRook]   = 20;
        mobilityWeightOfPiece[Board.blackQueen]  = 3;
        mobilityWeightOfPiece[Board.blackKing]   = 0;
    }

    /**
     * Attack-unit weight per piece kind: how strongly a piece bearing on the
     * enemy king zone contributes to the king-attack pressure. Heavier pieces
     * weigh more; the king contributes nothing (and is thus never counted as an
     * attacker). These weights are summed into {@link #attackUnit} and used to
     * index {@link #KING_ATTACK_PENALTY}.
     */
    private static final int ATTACK_UNIT_KING = 0;
    private static final int ATTACK_UNIT_PAWN = 1;
    private static final int ATTACK_UNIT_KNIGHT = 2;
    private static final int ATTACK_UNIT_BISHOP = 2;
    private static final int ATTACK_UNIT_ROOK = 3;
    private static final int ATTACK_UNIT_QUEEN = 5;

    /** Attack-unit weight indexed by piece constant; see {@link #ATTACK_UNIT_KING}. */
    private static final int[] ATTACK_UNIT_OF_PIECE = new int[Board.blackKing + 1];
    static {
        ATTACK_UNIT_OF_PIECE[Board.whitePawn] = ATTACK_UNIT_PAWN;
        ATTACK_UNIT_OF_PIECE[Board.whiteKnight] = ATTACK_UNIT_KNIGHT;
        ATTACK_UNIT_OF_PIECE[Board.whiteBishop] = ATTACK_UNIT_BISHOP;
        ATTACK_UNIT_OF_PIECE[Board.whiteRook] = ATTACK_UNIT_ROOK;
        ATTACK_UNIT_OF_PIECE[Board.whiteQueen] = ATTACK_UNIT_QUEEN;
        ATTACK_UNIT_OF_PIECE[Board.whiteKing] = ATTACK_UNIT_KING;
        ATTACK_UNIT_OF_PIECE[Board.blackPawn] = ATTACK_UNIT_PAWN;
        ATTACK_UNIT_OF_PIECE[Board.blackKnight] = ATTACK_UNIT_KNIGHT;
        ATTACK_UNIT_OF_PIECE[Board.blackBishop] = ATTACK_UNIT_BISHOP;
        ATTACK_UNIT_OF_PIECE[Board.blackRook] = ATTACK_UNIT_ROOK;
        ATTACK_UNIT_OF_PIECE[Board.blackQueen] = ATTACK_UNIT_QUEEN;
        ATTACK_UNIT_OF_PIECE[Board.blackKing] = ATTACK_UNIT_KING;
    }

    /**
     * King-attack penalty in centipawns, indexed by the attacking side's
     * accumulated {@link #attackUnit}. The table grows progressively, so
     * piling several pieces onto the king zone is punished far more than the
     * sum of the individual attackers would suggest. Applied only once at least
     * two distinct pieces attack, and the index is clamped to the last entry
     * (see {@link #calcKingAttackPenalty}).
     */
    static final int[] KING_ATTACK_PENALTY = {
            0,    //  0
            0,    //  1
            5,    //  2
            5,    //  3
            10,   //  4
            10,   //  5
            15,   //  6
            15,   //  7
            25,   //  8 +15 -- noticeable increase from 8 to 10 (Q+R, Q+B+K)
            35,   //  9 +20
            50,   // 10 +30
            65,   // 11 +30
            85,   // 12 +40 -- now heavily progressive
            105,  // 13 +40
            130,  // 14 +50
            160,  // 15 +60 -- severe king weakness
            195,  // 16 +70
            235,  // 17 +80
            285,  // 18 +100
            340,  // 19 +110
            400   // 20 +120
    };

    @FunctionalInterface
    private interface CalculateWeight {
        void calculate(WeightingFunction generator, int field, int color);
    }

    private static final CalculateWeight[] calculationFunctions = new CalculateWeight[Board.blackKing + 1];
    static {
        calculationFunctions[Board.whitePawn]   = WeightingFunction::_calculateForWhitePawn;
        calculationFunctions[Board.whiteKnight] = WeightingFunction::_calculateForKnight;
        calculationFunctions[Board.whiteBishop] = WeightingFunction::_calculateForBishop;
        calculationFunctions[Board.whiteRook]   = WeightingFunction::_calculateForRook;
        calculationFunctions[Board.whiteQueen]  = WeightingFunction::_calculateForQueen;
        calculationFunctions[Board.whiteKing]   = WeightingFunction::_calculateForKing;
        calculationFunctions[Board.blackPawn]   = WeightingFunction::_calculateForBlackPawn;
        calculationFunctions[Board.blackKnight] = WeightingFunction::_calculateForKnight;
        calculationFunctions[Board.blackBishop] = WeightingFunction::_calculateForBishop;
        calculationFunctions[Board.blackRook]   = WeightingFunction::_calculateForRook;
        calculationFunctions[Board.blackQueen]  = WeightingFunction::_calculateForQueen;
        calculationFunctions[Board.blackKing]   = WeightingFunction::_calculateForKing;
    }

    private static final int[] ownTurn = new int[] { GameStatus.TURN_WHITE, GameStatus.TURN_BLACK };
    private static final int[] oppositeTurn = new int[] { GameStatus.TURN_BLACK, GameStatus.TURN_WHITE };
    private static final int[] oppositeKing = new int[] { Board.blackKing, Board.whiteKing };

    private static final float mobilityFactor = 0.1f;
    private static final float positionFactor = 0.5f;
    private static final float threadWeightFactor = 0.02f;
    private static final float chessFactor = 0.25f;
    private static final float castlingFactor = 0.25f;
    /**
     * Per-doubled-pair penalty in pawn units, applied directly in the
     * final-weight formula.
     *
     * <p>The standard chess-theory value is around -0.15 pawns per doubled
     * pair; we use that value here.
     */
    private static final float doublePawnFactor = -0.15f;
    /**
     * Per-hanging-piece penalty in pawn units, applied directly in the
     * final-weight formula. A piece counts as "hanging" when it is
     * simultaneously attacked by an opposing piece AND has no own-color
     * defender (kings excluded). Tracked via the {@link #ATTACK_MARK_BIT}
     * marker on {@link #tempBoard} during the per-piece scan: every
     * {@code capture} call sets the marker on the attacked square; every
     * {@link #defend(int)} call wipes the entire square (clearing both
     * piece bits and any marker), so a defended piece never satisfies the
     * "marker bit set AND piece bits set" predicate counted in
     * {@link #calculateUndefendedPiecesCount()}.
     *
     * <p>The sign convention mirrors {@link #doublePawnFactor}: the
     * factor is negative and applied to {@code white_count - black_count},
     * so more own-side hanging pieces decrease this side's score.
     */
    private static final float undefendedPiecesFactor = -0.1f;
    /**
     * Scales the pawn-shield delta (white - black, in the raw centipawn units
     * of {@link #PAWN_SHIELD_WEIGHTS}) into the final position weight.
     */
    private static final float pawnShieldFactor = 0.005f;
    /**
     * Scales the king-attack penalty delta (white - black, from
     * {@link #KING_ATTACK_PENALTY}) into the final position weight.
     */
    private static final float kingAttackFactor = 0.01f;

    private GameStatus game;
    private int turn; // 0 = white, 1 = black
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private final byte[] tempBoard = new byte[Board.LENGTH * Board.LENGTH];
    private final int[] chessCount = new int[2];
    private final float[] piecesWeight = new float[2];
    private final int[] mobilityWeight = new int[2];
    private final int[] positionWeight = new int[2];
    private final int[] threadWeight = new int[2];
    private boolean containsIllegalMove;
    private final int[] castlingState = new int[2];
    private final int[] doublePawnCount = new int[2];
    private final int[] undefendedPiecesCount = new int[2];
    /** Raw pawn-shield weight per color (see {@link #calculatePawnShieldWeight}); index 0 = white, 1 = black. */
    private final int[] pawnShieldWeight = new int[2];
    private final int[] kingCoverUnit = new int[2];
    /**
     * Per-color mask of the squares forming that color's king zone (the king's
     * square plus its eight neighbors), indexed by board field. Rebuilt each
     * {@link #calculate(Board)} by {@link #fillKingZone}.
     */
    private final boolean[][] isKingZoneField = new boolean[2][Board.LENGTH * Board.LENGTH];
    /** Accumulated attack units bearing on the enemy king zone, per attacking color. */
    private final int[] attackUnit = new int[2];
    /** Number of distinct pieces bearing on the enemy king zone, per attacking color. */
    private final int[] kingAttackerCount = new int[2];
    /**
     * Deduplication guard for {@link #increaseAttackUnit}, keyed by the
     * attacker's origin square: guarantees each piece is counted at most once,
     * no matter how many king-zone squares it attacks. Cleared each
     * {@link #calculate(Board)}.
     */
    private final boolean[] isKingAttackerCounted = new boolean[Board.LENGTH * Board.LENGTH];

    /** Material weight (delta white - black) in centi pawns. */
    public static int calculateMaterialWeight(Board theBoard) {
        final byte[] board = theBoard.getRawBoard();
        final int[] piecesWeight = new int[2];

        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
            }
        }

        return piecesWeight[0] - piecesWeight[1];
    }

    /** Get material weight of move in centi pawns. The returned value is always a positive number. */
    public static int getMaterialWeightOfMove(int move) {
        if (move == 0) {
            return 0;
        }

        final int capturedWeight = WeightingFunction.weightOfPiece[Move.getCapturedPiece(move)];
        final byte moveType = Move.getMoveType(move);
        if (moveType == Move.typeNormal || moveType == Move.typeEnPassant) {
            return capturedWeight;
        }

        final int pawnWeight = weightOfPiece[Board.whitePawn];
        return switch (moveType) {
            case Move.typePawnPromotionQueen -> weightOfPiece[Board.whiteQueen] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionKnight -> weightOfPiece[Board.whiteKnight] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionRook -> weightOfPiece[Board.whiteRook] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionBishop -> weightOfPiece[Board.whiteBishop] - pawnWeight + capturedWeight;
            default -> 0;
        };
    }

    /** Calculate weight of position in centi pawns. */
    public int calculate(Board theBoard) {
        this.game = theBoard.getGameStatus();
        this.turn = game.getTurn() == GameStatus.TURN_WHITE ? 0 : 1;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.chessCount[0] = 0;
        this.chessCount[1] = 0;
        this.piecesWeight[0] = 0;
        this.piecesWeight[1] = 0;
        this.mobilityWeight[0] = 0;
        this.mobilityWeight[1] = 0;
        this.positionWeight[0] = 0;
        this.positionWeight[1] = 0;
        this.threadWeight[0] = 0;
        this.threadWeight[1] = 0;
        this.containsIllegalMove = false;
        this.castlingState[0] = 0;
        this.castlingState[1] = 0;
        this.doublePawnCount[0] = 0;
        this.doublePawnCount[1] = 0;
        this.undefendedPiecesCount[0] = 0;
        this.undefendedPiecesCount[1] = 0;
        this.pawnShieldWeight[0] = 0;
        this.pawnShieldWeight[1] = 0;
        this.attackUnit[0] = 0;
        this.attackUnit[1] = 0;
        this.kingAttackerCount[0] = 0;
        this.kingAttackerCount[1] = 0;
        this.kingCoverUnit[0] = 0;
        this.kingCoverUnit[1] = 0;

        System.arraycopy(board, 0, this.tempBoard, 0, Board.LENGTH * Board.LENGTH);
        Arrays.fill(isKingZoneField[0], false);
        Arrays.fill(isKingZoneField[1], false);
        Arrays.fill(isKingAttackerCounted, false);

        final int stopField = Board.h8 + 1;
        final boolean isEndGame = game.isEndGame();

        // Find and mark king zone
        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece == Board.whiteKing) {
                fillKingZone(field, 0);
            } else if (piece == Board.blackKing) {
                fillKingZone(field, 1);
            }
        }

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
                if (!(isEndGame && Board.isKing(piece))) {
                    positionWeight[color] += PieceSquareTables.getPieceSquareWeight(piece, field);
                }

                calculationFunctions[piece].calculate(this, field, color);
            }
        }

        calculateCastlingState();
        calculateUndefendedPiecesCount();

        return calculatePositionWeight();
    }

    /**
     * Board-index offsets from a king's square to the nine squares of its king
     * zone (the king itself and its eight neighbors), used to populate
     * {@link #isKingZoneField}.
     */
    private static final int[] KING_ZONE_OFFSETS = new int[] {
            Board.LENGTH - 1, Board.LENGTH, Board.LENGTH + 1,
            -1, 0, 1,
            -Board.LENGTH - 1, -Board.LENGTH, -Board.LENGTH + 1
    };

    /**
     * Marks the nine squares of {@code color}'s king zone (the king on
     * {@code kingField} plus its eight neighbors) in {@link #isKingZoneField}.
     * Off-board neighbors of an edge or corner king land on the board's illegal
     * border cells, so no bounds check is needed.
     *
     * @param kingField board index of the king
     * @param color king's color (0 = white, 1 = black)
     */
    private void fillKingZone(int kingField, int color) {
        for (int off : KING_ZONE_OFFSETS) {
            isKingZoneField[color][kingField + off] = true;
        }
    }

    private int calculatePositionWeight() {
        if (containsIllegalMove)
            return turn == 0 ? ILLEGAL_WEIGHT_POS : ILLEGAL_WEIGHT_NEG;

        return roundSymmetric((
                  (piecesWeight[0] - piecesWeight[1]) / 100f
                + (positionWeight[0] - positionWeight[1]) / 100f * positionFactor
                + (mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor
                + (threadWeight[0] - threadWeight[1]) / 100f * threadWeightFactor
                + (castlingState[0] - castlingState[1]) * castlingFactor
                + (chessCount[0] - chessCount[1]) * chessFactor
                + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor
                + (pawnShieldWeight[0] - pawnShieldWeight[1]) * pawnShieldFactor
                + (undefendedPiecesCount[0] - undefendedPiecesCount[1]) * undefendedPiecesFactor
                + (calcKingAttackPenalty(0) - calcKingAttackPenalty(1)) * kingAttackFactor
        ) * 100);
    }

    /**
     * King-attack penalty (in centipawns) for the given attacking color, looked
     * up in {@link #KING_ATTACK_PENALTY} by that side's accumulated
     * {@link #attackUnit}.
     *
     * <p>Gated on at least two distinct attackers ({@link #kingAttackerCount}):
     * a lone attacker cannot mount a real mating threat and scores zero. The
     * attack-unit index is clamped to the last table entry.
     *
     * @param color attacking color (0 = white, 1 = black)
     * @return the penalty the enemy king incurs, as a positive centipawn value
     */
    float calcKingAttackPenalty(int color) {
        return kingAttackerCount[color] < 2 ?
            0 :
            KING_ATTACK_PENALTY[Math.min(attackUnit[color], KING_ATTACK_PENALTY.length - 1)] * kingCoverAttackMultiplier(color^1);
    }

    private static final float MIN_ATTACK_MULTIPLIER = 0.3f;

    private float kingCoverAttackMultiplier(int defendingColor) {
        float coverRatio = kingCoverUnit[defendingColor] / (float) MAX_KING_COVER;
        return MIN_ATTACK_MULTIPLIER + (1f - coverRatio) * (1f - MIN_ATTACK_MULTIPLIER);
    }

    // --- Package-private accessors for king-safety unit tests. The arrays are
    // populated by calculate(Board); index 0 = white, 1 = black. ---

    int[] getPawnShieldWeight() {
        return pawnShieldWeight;
    }

    int[] getAttackUnit() {
        return attackUnit;
    }

    int[] getKingAttackerCount() {
        return kingAttackerCount;
    }

    boolean isInKingZone(int color, int field) {
        return isKingZoneField[color][field];
    }

    /**
     * Round half-values away from zero in both directions, so the rounding
     * is antisymmetric: {@code roundSymmetric(-x) == -roundSymmetric(x)}.
     * Java's {@link Math#round(float)} rounds halves toward positive
     * infinity, which would turn an inner-eval pair of +45.5 / -45.5 into
     * +46 / -45 and silently break the white-vs-black antisymmetry of the
     * evaluation. See {@code MirrorEvalTest} for the invariant this
     * preserves.
     */
    private static int roundSymmetric(float value) {
        return value >= 0f ? Math.round(value) : -Math.round(-value);
    }

    void print() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "piecesWeight:          w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + DELTA_STR + (piecesWeight[0] - piecesWeight[1]) + WEIGHT_STR + round((piecesWeight[0] - piecesWeight[1]) / 100f) + '\n' +
               "positionWeight:        w=" + positionWeight[0] + ", b=" + positionWeight[1] + DELTA_STR + (positionWeight[0] - positionWeight[1]) + WEIGHT_STR + round((positionWeight[0] - positionWeight[1]) / 100f * positionFactor) + '\n' +
               "mobilityWeight:        w=" + mobilityWeight[0] + ", b=" + mobilityWeight[1] + DELTA_STR + (mobilityWeight[0] - mobilityWeight[1]) + WEIGHT_STR + round((mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor) + '\n' +
               "threadWeight:          w=" + threadWeight[0] + ", b=" + threadWeight[1] + DELTA_STR + (threadWeight[0] - threadWeight[1]) + WEIGHT_STR + round((threadWeight[0] - threadWeight[1])  / 100f * threadWeightFactor) + '\n' +
               "castlingState:         w=" + castlingState[0] + ", b=" + castlingState[1] + DELTA_STR + (castlingState[0] - castlingState[1]) + WEIGHT_STR + round((castlingState[0] - castlingState[1]) * castlingFactor) + '\n' +
               "doublePawnCount:       w=" + doublePawnCount[0] + ", b=" + doublePawnCount[1] + DELTA_STR + (doublePawnCount[0] - doublePawnCount[1]) + WEIGHT_STR + round((doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor) + '\n' +
               "chessCount:            w=" + chessCount[0] + ", b=" + chessCount[1] + DELTA_STR + (chessCount[0] - chessCount[1]) + WEIGHT_STR + round((chessCount[0] - chessCount[1]) * chessFactor) + '\n' +
               "undefendedPiecesCount: w=" + undefendedPiecesCount[0] + ", b=" + undefendedPiecesCount[1] + DELTA_STR + (undefendedPiecesCount[0] - undefendedPiecesCount[1]) + WEIGHT_STR + round((undefendedPiecesCount[0] - undefendedPiecesCount[1]) * undefendedPiecesFactor) + '\n' +
               "pawnShieldWeight:      w=" + pawnShieldWeight[0] + ", b=" + pawnShieldWeight[1] + DELTA_STR + (pawnShieldWeight[0] - pawnShieldWeight[1]) + WEIGHT_STR + round((pawnShieldWeight[0] - pawnShieldWeight[1]) * pawnShieldFactor) + '\n' +
               "attackUnit:            w=" + attackUnit[0] + ", b=" + attackUnit[1] + DELTA_STR + (attackUnit[0] - attackUnit[1]) + WEIGHT_STR + round((calcKingAttackPenalty(0) - calcKingAttackPenalty(1)) * kingAttackFactor) + '\n' +
               "weight: " + calculatePositionWeight() / 100f;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    /**
     * Records that the piece on {@code fromField} bears on {@code toField}.
     * When {@code toField} lies in the enemy king zone, the piece's
     * {@link #ATTACK_UNIT_OF_PIECE attack-unit weight} is added to
     * {@link #attackUnit} and {@link #kingAttackerCount} is incremented.
     *
     * <p>Each piece is counted at most once per evaluation, regardless of how
     * many king-zone squares it attacks: {@link #isKingAttackerCounted}, keyed
     * by the origin square, absorbs the repeated calls a sliding piece makes
     * along its rays. Two like pieces on different squares still count
     * separately. The king itself has zero weight and is therefore never
     * counted as an attacker.
     *
     * @param color attacking color (0 = white, 1 = black)
     * @param fromField origin square of the attacking piece
     * @param toField attacked square
     * @param piece the attacking piece
     */
    private void increaseAttackUnit(final int color, final int fromField, final int toField, final byte piece) {
        if (isKingZoneField[color^1][toField] && !isKingAttackerCounted[fromField]) {
            final int score = ATTACK_UNIT_OF_PIECE[piece];
            if (score > 0) {
                isKingAttackerCounted[fromField] = true;
                kingAttackerCount[color]++;
                attackUnit[color] += score;
            }
        }
    }

    private void calculateForWhitePawn(int field, int color) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
        }

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
            }
        }

        // capture right
        captureOrDefendWithPawn(field, field + Board.LENGTH + 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

        // capture left
        captureOrDefendWithPawn(field, field + Board.LENGTH - 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH) {
                    capture(field - 1, Board.whitePawn, color, Board.blackPawn);
                } else if (board[field + 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH) {
                    capture(field + 1, Board.whitePawn, color, Board.blackPawn);
                }
            }
        }

        // Is a doubled pawn? Searching ahead (toward 8th rank for white)
        // means only the LOWER pawn in a pair finds its partner, so each
        // pair is counted exactly once.
        for (var f = field + Board.LENGTH; board[f] != Board.illegal; f += Board.LENGTH) {
            if (board[f] == Board.whitePawn) {
                doublePawnCount[color]++;
                break;
            }
        }
    }

    private void captureOrDefendWithPawn(final int from, final int to, final int myTurn, final int oppositeTurn, final byte movingPawn, final int color) {
        increaseAttackUnit(color, from, to, movingPawn);

        if ((board[to] & oppositeTurn) == oppositeTurn) {
            capture(to, movingPawn, color, board[to]);
        } else if ((board[to] & myTurn) == myTurn) {
            defend(to);
        }
    }

    private static int fieldToRow(int field) {
        return field / Board.LENGTH - 2;
    }

    private static void _calculateForBlackPawn(WeightingFunction generator, int field, int color) {
        generator.calculateForBlackPawn(field, color);
    }

    private void calculateForBlackPawn(int field, int color) {
        // single step
        int to = field - Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
        }

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field - Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
            }
        }

        // capture right
        to = field - Board.LENGTH + 1;
        captureOrDefendWithPawn(field, to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

        // capture left
        to = field - Board.LENGTH - 1;
        captureOrDefendWithPawn(field, to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH) {
                    capture(field - 1, Board.blackPawn, color, Board.whitePawn);
                } else if (board[field + 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH) {
                    capture(field + 1, Board.blackPawn, color, Board.whitePawn);
                }
            }
        }

        // Is a doubled pawn? Searching ahead (toward 1st rank for black)
        // means only the UPPER pawn in a pair finds its partner, so each
        // pair is counted exactly once.
        for (var f = field - Board.LENGTH; board[f] != Board.illegal; f -= Board.LENGTH) {
            if (board[f] == Board.blackPawn) {
                doublePawnCount[color]++;
                break;
            }
        }
    }

    private static void _calculateForKnight(WeightingFunction generator, int field, int color) {
        generator.calculateForKnight(field, color);
    }

    private void calculateForKnight(int field, int color) {
        final byte myPiece = board[field];

        move(myPiece, field, field + 2 * Board.LENGTH + 1, color);
        move(myPiece, field, field + 1 * Board.LENGTH + 2, color);
        move(myPiece, field, field - 1 * Board.LENGTH + 2, color);
        move(myPiece, field, field - 2 * Board.LENGTH + 1, color);
        move(myPiece, field, field - 2 * Board.LENGTH - 1, color);
        move(myPiece, field, field - 1 * Board.LENGTH - 2, color);
        move(myPiece, field, field + 1 * Board.LENGTH - 2, color);
        move(myPiece, field, field + 2 * Board.LENGTH - 1, color);
    }

    private static void _calculateForBishop(WeightingFunction generator, int field, int color) {
        generator.calculateForBishop(field, color);
    }

    private void calculateForBishop(int field, int color) {
        final byte myPiece = board[field];

        // move up-right
        for (int to = field + Board.LENGTH + 1; move(myPiece, field, to, color); to += Board.LENGTH + 1);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(myPiece, field, to, color); to = to - Board.LENGTH + 1);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(myPiece, field, to, color); to = to - Board.LENGTH - 1);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(myPiece, field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForRook(WeightingFunction generator, int field, int color) {
        generator.calculateForRook(field, color);
    }

    private void calculateForRook(int field, int color) {
        final byte myPiece = board[field];
        final int rankWeight = mobilityWeightOfPiece[myPiece] / 2;

        // move up — file mobility (full weight)
        for (int to = field + Board.LENGTH; move(myPiece, field, to, color); to += Board.LENGTH);
        // move down — file mobility (full weight)
        for (int to = field - Board.LENGTH; move(myPiece, field, to, color); to -= Board.LENGTH);
        // move left — rank mobility (half weight)
        for (int to = field - 1; move(myPiece, field, to, color, rankWeight); to--);
        // move right — rank mobility (half weight)
        for (int to = field + 1; move(myPiece, field, to, color, rankWeight); to++);
    }

    private static void _calculateForQueen(WeightingFunction generator, int field, int color) {
        generator.calculateForQueen(field, color);
    }

    private void calculateForQueen(int field, int color) {
        final byte myPiece = board[field];

        // move up
        for (int to = field + Board.LENGTH; move(myPiece, field, to, color); to += Board.LENGTH);
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(myPiece, field, to, color); to += Board.LENGTH + 1);
        // move right
        for (int to = field + 1; move(myPiece, field, to, color); to++);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(myPiece, field, to, color); to = to - Board.LENGTH + 1);
        // move down
        for (int to = field - Board.LENGTH; move(myPiece, field, to, color); to -= Board.LENGTH);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(myPiece, field, to, color); to = to - Board.LENGTH - 1);
        // move left
        for (int to = field - 1; move(myPiece, field, to, color); to--);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(myPiece, field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForKing(WeightingFunction generator, int field, int color) {
        generator.calculateForKing(field, color);
    }

    private void calculateForKing(int field, int color) {
        final byte myPiece = board[field];

        // move up
        move(myPiece, field, field + Board.LENGTH, color);
        // move up-right
        move(myPiece, field, field + Board.LENGTH + 1, color);
        // move right
        move(myPiece, field, field + 1, color);
        // move down-right
        move(myPiece, field, field - Board.LENGTH + 1, color);
        // move down
        move(myPiece, field, field - Board.LENGTH, color);
        // move down-left
        move(myPiece, field, field - Board.LENGTH - 1, color);
        // move left
        move(myPiece, field, field - 1, color);
        // move up-left
        move(myPiece, field, field + Board.LENGTH - 1, color);

        pawnShieldWeight[color] = calculatePawnShieldWeight(field, color);
        kingCoverUnit[color] = calculateKingCover(field, color);
    }

    /**
     * Centipawn bonus for a friendly pawn on each square around the king, laid
     * out as a 5-wide grid over the two ranks in front of the king plus the
     * king's own rank (the king's square itself is skipped):
     * <pre>
     *     05 10 10 10 05   (two ranks ahead)
     *     05 15 15 15 05   (one rank ahead)
     *     05 05  K  05 05  (king's rank)
     * </pre>
     * Pawns directly shielding the king (the 15s) score highest. Parallel to
     * {@link #PAWN_SHIELD_OFFSETS}.
     */
    private static final int[] PAWN_SHIELD_WEIGHTS = new int[] {
            5, 10, 10, 10, 5,
            5, 15, 15, 15, 5,
            5,  5,      5, 5
    };
    /**
     * Board-index offsets, parallel to {@link #PAWN_SHIELD_WEIGHTS}, from the
     * king's square to each shield square. Index 0 = white (the shield extends
     * up the board), index 1 = black (mirrored, downward).
     */
    private static final int[][] PAWN_SHIELD_OFFSETS = new int[][] {
            {
                2 * Board.LENGTH - 2, 2 * Board.LENGTH - 1, 2 * Board.LENGTH, 2 * Board.LENGTH + 1, 2 * Board.LENGTH + 2,
                Board.LENGTH - 2, Board.LENGTH - 1, Board.LENGTH, Board.LENGTH + 1, Board.LENGTH + 2,
                -2, -1, 1, 2
            },
            {
                -2 * Board.LENGTH - 2, -2 * Board.LENGTH - 1, -2 * Board.LENGTH, -2 * Board.LENGTH + 1, -2 * Board.LENGTH + 2,
                -Board.LENGTH - 2, -Board.LENGTH - 1, -Board.LENGTH, -Board.LENGTH + 1, -Board.LENGTH + 2,
                -2, -1, 1, 2
            },
    };

    /** Test accessor for the static shield-offset table (index 0 = white, 1 = black). */
    static int[][] getPawnShieldOffsets() {
        return PAWN_SHIELD_OFFSETS;
    }

    /**
     * Sums the {@link #PAWN_SHIELD_WEIGHTS} of every square around the king on
     * {@code field} that is occupied by a friendly pawn — a bonus rewarding an
     * intact pawn cover in front of the king.
     *
     * @param field board index of the king
     * @param color king's color (0 = white, 1 = black)
     * @return the raw pawn-shield weight in centipawns (later scaled by
     *         {@link #pawnShieldFactor})
     */
    private int calculatePawnShieldWeight(final int field, final int color) {
        // TODO temporary disabled for measurement
        if (true) return 0;

        final byte myPawn = PAWN[color];
        int weight = 0;

        for (int i = 0; i < PAWN_SHIELD_WEIGHTS.length; i++) {
            final int off = PAWN_SHIELD_OFFSETS[color][i];
            if (board[field + off] == myPawn) {
                weight += PAWN_SHIELD_WEIGHTS[i];
            }
        }

        return weight;
    }

    private static final int MAX_KING_COVER = 6;

    private int calculateKingCover(final int kingField, final int color) {
        final byte myPawn = PAWN[color];
        final int myTurn = ownTurn[color];
        final int forward = FORWARD_OFFSET[color];

        return coverOf(kingField + forward - 1, myPawn, myTurn)
            + coverOf(kingField + forward,     myPawn, myTurn)
            + coverOf(kingField + forward + 1, myPawn, myTurn);
    }

    private int coverOf(final int field, final byte myPawn, final int myTurn) {
        final byte piece = board[field];

        if (piece == myPawn) {
            return 2;
        }
        if ((piece & myTurn) == myTurn) {
            return 1;
        }

        return 0;
    }

    private boolean move(final byte movingPiece, final int from, final int to, int color) {
        return move(movingPiece, from, to, color, mobilityWeightOfPiece[movingPiece]);
    }

    @SuppressWarnings({"unused", "java:S1117"})
    private boolean move(final byte movingPiece, final int from, final int to, final int color, final int weight) {
        final byte piece = board[to];
        final int oppositeTurn = WeightingFunction.oppositeTurn[color];

        if (piece == Board.illegal) {
            return false;
        }

        increaseAttackUnit(color, from, to, movingPiece);

        if (piece == Board.empty) {
            mobilityWeight[color] += weight;
            return true;
        } else if ((piece & oppositeTurn) == oppositeTurn) {
            capture(to, color, piece, weight);
            return false;
        } else { // own color
            defend(to);
            return false;
        }
    }

    private void capture(final int to, final byte movingPiece, final int color, final byte piece) {
        capture(to, color, piece, mobilityWeightOfPiece[movingPiece]);
    }

    private void capture(final int field, final int color, final byte piece, final int weight) {
        if (piece == oppositeKing[color]) {
            if (turn == color) {
                containsIllegalMove = true;
            } else {
                chessCount[color]++;
                threadWeight[color] += 4; // ok, give some weight to the attacked king as well (since weightOfPiece(king) is 0)
            }
        }

        mobilityWeight[color] += weight;
        threadWeight[color] += weightOfPiece[piece];
        tempBoard[field] |= ATTACK_MARK_BIT;
    }

    private void defend(final int field) {
        this.tempBoard[field] = Board.empty;
    }

    private void calculateCastlingState() {
        if (game.hasWhiteCastled())
            castlingState[0] = 0;
        else if (game.isWhiteCastlingQueenSidePossible() && game.isWhiteCastlingKingSidePossible())
            castlingState[0] = -1;
        else if (game.isWhiteCastlingQueenSidePossible() || game.isWhiteCastlingKingSidePossible())
            castlingState[0] = -2;
        else
            castlingState[0] = -4;

        if (game.hasBlackCastled())
            castlingState[1] = 0;
        else if (game.isBlackCastlingQueenSidePossible() && game.isBlackCastlingKingSidePossible())
            castlingState[1] = -1;
        else if (game.isBlackCastlingQueenSidePossible() || game.isBlackCastlingKingSidePossible())
            castlingState[1] = -2;
        else
            castlingState[1] = -4;
    }

    /**
     * Number of hanging pieces (attacked AND undefended, kings excluded)
     * found for the given color during the most recent {@link #calculate}
     * call. Package-private test hook — production callers should consume
     * this contribution via the final weight returned by {@code calculate}.
     *
     * @param color {@code 0} for white, {@code 1} for black
     */
    int getHangingPiecesCount(int color) {
        return undefendedPiecesCount[color];
    }

    private void calculateUndefendedPiecesCount() {
        for (int field = Board.a1; field <= Board.h8; field++) {
            final byte piece = tempBoard[field];
            if ((piece & ATTACK_MARK_BIT) == ATTACK_MARK_BIT) {
                if ((piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE && piece != WHITE_KING_ATTACKED) {
                    undefendedPiecesCount[0]++;
                } else if ((piece & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK && piece != BLACK_KING_ATTACKED) {
                    undefendedPiecesCount[1]++;
                }
            }
        }
    }

    public static boolean isIllegalWeight(int weightCenti) {
        return weightCenti == ILLEGAL_WEIGHT_NEG || weightCenti == ILLEGAL_WEIGHT_POS;
    }

    public static float checkmateIn(int depth) {
        return (WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth * 100) / 100f;
    }

    public static int checkmateInCenti(int depth) {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth * 100;
    }

    public static int checkmateInCenti() {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH;
    }

    /** Returns true if given weight is a checkmate score (positive or negative). */
    public static boolean isCheckmateWeight(float weight) {
        final int w = (int) (Math.abs(weight) * 100);
        return w >= CHECKMATE_WEIGHT_LOW && w <= CHECKMATE_WEIGHT_HIGH;
    }

    /** Returns true if given weight (in centi pawns) is a checkmate score (positive or negative). */
    public static boolean isCheckmateWeight(int weightCenti) {
        final int w = Math.abs(weightCenti);
        return w >= CHECKMATE_WEIGHT_LOW && w <= CHECKMATE_WEIGHT_HIGH;
    }

    /**
     * Returns the number of plies (depth) that corresponds to given checkmate score,
     * i.e. mate in X half moves.
     * Precondition: Given weight must be a checkmate score.
     */
    public static int checkmateWeightToPlies(float weight) {
        final int w = (int) (Math.abs(weight) * 100);
        return (CHECKMATE_WEIGHT_HIGH - w) / 100;
    }

    /**
     * Returns the number of plies (depth) that corresponds to given checkmate score (in centi pawns),
     * i.e. mate in X half moves.
     * Precondition: Given weight must be a checkmate score.
     */
    public static int checkmateWeightToPlies(int weightCenti) {
        final int w = Math.abs(weightCenti);
        return (CHECKMATE_WEIGHT_HIGH - w) / 100;
    }

    /**
     * Translates a mate score from "mate-at-depth-d-from-root" form into
     * the position-relative "mate-in-K-plies-from-here" form used by the
     * transposition table. Subtracts {@code depth} from the encoded plies
     * and preserves the sign of the score; non-mate scores pass through
     * unchanged.
     *
     * <p>The {@code plies >= depth} assertion catches inconsistent
     * states early — if a positive mate score's encoded ply count is
     * smaller than the current depth, the resulting stored score would
     * underflow the mate sentinel range and start looking like an
     * ordinary positional score on lookup.
     *
     * <p>Despite the "TT" in the name, this is a pure score-coordinate
     * transformation with no transposition-table dependency. It lives
     * here next to the other mate-score helpers because that is the
     * coordinate system it operates on.
     */
    public static int scoreToTT(int score, int depth) {
        if (isCheckmateWeight(score)) {
            int plies = checkmateWeightToPlies(score);
            __assert(() -> plies >= depth, () -> String.format("checkmate plies=%s, depth=%s", plies, depth));
            int checkmateCenti = checkmateInCenti(plies - depth);
            return score >= 0 ? checkmateCenti : -checkmateCenti;
        }

        return score;
    }

    /**
     * Inverse of {@link #scoreToTT(int, int)}: translates a TT-stored
     * mate score back to the current search depth. The TT stores mate
     * scores relative to the cached position ("mate-in-K plies from
     * here"), independent of how deep in the tree that position was
     * when the score was computed. On lookup at depth {@code d} from
     * the root, we add {@code d} so the returned value reads as "mate
     * at depth {@code d + K} of the current tree", which is what the
     * negamax caller compares against alpha/beta and what an
     * iteration's PV propagates up.
     *
     * <p>Sign preservation matters: a stored negative mate stays a
     * negative mate. The earlier private implementation in
     * {@code PositionSearch} dropped the sign, and the regression that
     * surfaced this was {@code GameStatusTest.testWhiteCheckmate}
     * flipping its expected mate move because a "we are mated" entry
     * came back from the TT as "we are mating".
     *
     * <p>Non-mate scores are positional and depth-independent in
     * storage, so they pass through unchanged.
     */
    public static int scoreFromTT(int score, int depth) {
        if (isCheckmateWeight(score)) {
            int plies = checkmateWeightToPlies(score);
            int checkmateCenti = checkmateInCenti(depth + plies);
            return score >= 0 ? checkmateCenti : -checkmateCenti;
        }

        return score;
    }
}
