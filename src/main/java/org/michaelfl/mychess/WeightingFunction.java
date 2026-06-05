package org.michaelfl.mychess;

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

    private static final int[] oppositeColor = new int[] { GameStatus.TURN_BLACK, GameStatus.TURN_WHITE };
    private static final int[] oppositeKing = new int[] { Board.blackKing, Board.whiteKing };

    private static final float mobilityFactor = 0.1f;
    private static final float positionFactor = 0.5f;
    private static final float threadWeightFactor = 0.05f;
    private static final float chessFactor = 0.25f;
    private static final float castlingFactor = 0.25f;
    private static final float doublePawnFactor = -0.1f;

    private GameStatus game;
    private int turn; // 0 = white, 1 = black
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private final int[] chessCount = new int[2];
    private final float[] piecesWeight = new float[2];
    private final int[] mobilityWeight = new int[2];
    private final int[] positionWeight = new int[2];
    private final int[] threadWeight = new int[2];
    private boolean containsIllegalMove;
    private final int[] castlingState = new int[2];
    private final int[] doublePawnCount = new int[2];

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

    /** Get material weight of move in centi pawns. */
    public static int getMaterialWeightOfMove(int move) {
        final int capturedWeight = WeightingFunction.weightOfPiece[Move.getCapturedPiece(move)];
        final byte moveType = Move.getMoveType(move);
        if (moveType == Move.typeNormal)
            return capturedWeight;

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

        final int stopField = Board.h8 + 1;
        final boolean isEndGame = game.isEndGame();

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

        return calculatePositionWeight();
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
                + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor) * 100);
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
        return "piecesWeight:       w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + DELTA_STR + (piecesWeight[0] - piecesWeight[1]) + WEIGHT_STR + round((piecesWeight[0] - piecesWeight[1]) / 100f) + '\n' +
               "positionWeight:     w=" + positionWeight[0] + ", b=" + positionWeight[1] + DELTA_STR + (positionWeight[0] - positionWeight[1]) + WEIGHT_STR + round((positionWeight[0] - positionWeight[1]) / 100f * positionFactor) + '\n' +
               "mobilityWeight:     w=" + mobilityWeight[0] + ", b=" + mobilityWeight[1] + DELTA_STR + (mobilityWeight[0] - mobilityWeight[1]) + WEIGHT_STR + round((mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor) + '\n' +
               "threadWeight:       w=" + threadWeight[0] + ", b=" + threadWeight[1] + DELTA_STR + (threadWeight[0] - threadWeight[1]) + WEIGHT_STR + round((threadWeight[0] - threadWeight[1])  / 100f * threadWeightFactor) + '\n' +
               "castlingState:      w=" + castlingState[0] + ", b=" + castlingState[1] + DELTA_STR + (castlingState[0] - castlingState[1]) + WEIGHT_STR + round((castlingState[0] - castlingState[1]) * castlingFactor) + '\n' +
               "doublePawnCount:    w=" + doublePawnCount[0] + ", b=" + doublePawnCount[1] + DELTA_STR + (doublePawnCount[0] - doublePawnCount[1]) + WEIGHT_STR + round((doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor) + '\n' +
               "chessCount:         w=" + chessCount[0] + ", b=" + chessCount[1] + DELTA_STR + (chessCount[0] - chessCount[1]) + WEIGHT_STR + round((chessCount[0] - chessCount[1]) * chessFactor) + '\n' +
               "weight: " + calculatePositionWeight() / 100f;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    @SuppressWarnings("java:S1871")
    private void calculateForWhitePawn(int field, int color) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
        } else if (board[to] == Board.whitePawn) {
            doublePawnCount[color]++; // double pawn
        }

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
            }
        }

        // capture right
        to = field + Board.LENGTH + 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            capture(Board.whitePawn, color, board[to]);
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            capture(Board.whitePawn, color, board[to]);
        }

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH) {
                    capture(Board.whitePawn, color, Board.blackPawn);
                } else if (board[field + 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH) {
                    capture(Board.whitePawn, color, Board.blackPawn);
                }
            }
        }
    }

    private static int fieldToRow(int field) {
        return field / Board.LENGTH - 2;
    }

    private static void _calculateForBlackPawn(WeightingFunction generator, int field, int color) {
        generator.calculateForBlackPawn(field, color);
    }

    @SuppressWarnings("java:S1871")
    private void calculateForBlackPawn(int field, int color) {
        // single step
        int to = field - Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
        } else if (board[to] == Board.blackPawn) {
            doublePawnCount[color]++; // double pawn
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
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            capture(Board.blackPawn, color, board[to]);
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            capture(Board.blackPawn, color, board[to]);
        }

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH) {
                    capture(Board.blackPawn, color, Board.whitePawn);
                } else if (board[field + 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH) {
                    capture(Board.blackPawn, color, Board.whitePawn);
                }
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
    }

    private boolean move(final byte movingPiece, final int from, final int to, int color) {
        return move(movingPiece, from, to, color, mobilityWeightOfPiece[movingPiece]);
    }

    @SuppressWarnings({"unused", "java:S1117"})
    private boolean move(final byte movingPiece, final int from, final int to, final int color, final int weight) {
        final byte piece = board[to];
        final int oppositeColor = WeightingFunction.oppositeColor[color];

        if (piece == Board.illegal)
            return false;

        if (piece == Board.empty) {
            mobilityWeight[color] += weight;
            return true;
        } else if ((piece & oppositeColor) == oppositeColor) {
            capture(color, piece, weight);
            return false;
        } else { // own color
            return false;
        }
    }

    private void capture(final byte movingPiece, final int color, final byte piece) {
        capture(color, piece, mobilityWeightOfPiece[movingPiece]);
    }

    private void capture(final int color, final byte piece, final int weight) {
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
}
