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

@SuppressWarnings({"StatementWithEmptyBody", "Duplicates", "PointlessArithmeticExpression"})
public final class WeightingFunction {

    public final static float ILLEGAL_WEIGHT = Float.NEGATIVE_INFINITY;
    public final static float CHECKMATE_WEIGHT = 1000f;

    public final static float[] weightOfPiece = new float[Board.blackKing + 1];
    static {
        weightOfPiece[Board.whitePawn]   = 1.0f;
        weightOfPiece[Board.whiteKnight] = 3.0f;
        weightOfPiece[Board.whiteBishop] = 3.0f;
        weightOfPiece[Board.whiteRook]   = 5.0f;
        weightOfPiece[Board.whiteQueen]  = 9.0f;
        weightOfPiece[Board.whiteKing]   = 0.0f;
        weightOfPiece[Board.blackPawn]   = 1.0f;
        weightOfPiece[Board.blackKnight] = 3.0f;
        weightOfPiece[Board.blackBishop] = 3.0f;
        weightOfPiece[Board.blackRook]   = 5.0f;
        weightOfPiece[Board.blackQueen]  = 9.0f;
        weightOfPiece[Board.blackKing]   = 0.0f;
    }

    private final static float[] mobilityWeightOfPiece = new float[Board.blackKing + 1];
    static {
        mobilityWeightOfPiece[Board.whitePawn]   = 0.2f;
        mobilityWeightOfPiece[Board.whiteKnight] = 0.5f;
        mobilityWeightOfPiece[Board.whiteBishop] = 0.3f;
        mobilityWeightOfPiece[Board.whiteRook]   = 0.1f;
        mobilityWeightOfPiece[Board.whiteQueen]  = 0.05f;
        mobilityWeightOfPiece[Board.whiteKing]   = 0.0f;
        mobilityWeightOfPiece[Board.blackPawn]   = 0.2f;
        mobilityWeightOfPiece[Board.blackKnight] = 0.5f;
        mobilityWeightOfPiece[Board.blackBishop] = 0.3f;
        mobilityWeightOfPiece[Board.blackRook]   = 0.1f;
        mobilityWeightOfPiece[Board.blackQueen]  = 0.05f;
        mobilityWeightOfPiece[Board.blackKing]   = 0.0f;
    }

    /*
      8| 6 6 6 6 6 6 6 6
      7| 2 2 4 5 5 4 2 2
      6| 1 2 3 4 4 3 2 1
      5| 1 2 3 4 4 3 2 1
      4| 0 0 2 4 4 2 0 0
      3| 0 0 1 1 1 1 0 0
      2| 0 0 0 0 0 0 0 0
      1| 0 0 0 0 0 0 0 0
         a b c d e f g h
     */
    private final static byte[] weightOfFieldForWhite = Board.createEmptyBoard().getRawBoard();
    static {
        weightOfFieldForWhite[Board.c3] = 1;
        weightOfFieldForWhite[Board.d3] = 1;
        weightOfFieldForWhite[Board.e3] = 1;
        weightOfFieldForWhite[Board.f3] = 1;
        weightOfFieldForWhite[Board.c4] = 2;
        weightOfFieldForWhite[Board.d4] = 4;
        weightOfFieldForWhite[Board.e4] = 4;
        weightOfFieldForWhite[Board.f4] = 2;
        weightOfFieldForWhite[Board.a5] = 1;
        weightOfFieldForWhite[Board.b5] = 2;
        weightOfFieldForWhite[Board.c5] = 3;
        weightOfFieldForWhite[Board.d5] = 4;
        weightOfFieldForWhite[Board.e5] = 4;
        weightOfFieldForWhite[Board.f5] = 3;
        weightOfFieldForWhite[Board.g5] = 2;
        weightOfFieldForWhite[Board.h5] = 1;
        weightOfFieldForWhite[Board.a6] = 1;
        weightOfFieldForWhite[Board.b6] = 2;
        weightOfFieldForWhite[Board.c6] = 3;
        weightOfFieldForWhite[Board.d6] = 4;
        weightOfFieldForWhite[Board.e6] = 4;
        weightOfFieldForWhite[Board.f6] = 3;
        weightOfFieldForWhite[Board.g6] = 2;
        weightOfFieldForWhite[Board.h6] = 1;
        weightOfFieldForWhite[Board.a7] = 2;
        weightOfFieldForWhite[Board.b7] = 2;
        weightOfFieldForWhite[Board.c7] = 4;
        weightOfFieldForWhite[Board.d7] = 5;
        weightOfFieldForWhite[Board.e7] = 5;
        weightOfFieldForWhite[Board.f7] = 4;
        weightOfFieldForWhite[Board.g7] = 2;
        weightOfFieldForWhite[Board.h7] = 2;
        weightOfFieldForWhite[Board.a8] = 6;
        weightOfFieldForWhite[Board.b8] = 6;
        weightOfFieldForWhite[Board.c8] = 6;
        weightOfFieldForWhite[Board.d8] = 6;
        weightOfFieldForWhite[Board.e8] = 6;
        weightOfFieldForWhite[Board.f8] = 6;
        weightOfFieldForWhite[Board.g8] = 6;
        weightOfFieldForWhite[Board.h8] = 6;
    }

    private final static byte[] weightOfFieldForBlack = Board.createEmptyBoard().getRawBoard();
    static {
        weightOfFieldForBlack[Board.c6] = 1;
        weightOfFieldForBlack[Board.d6] = 1;
        weightOfFieldForBlack[Board.e6] = 1;
        weightOfFieldForBlack[Board.f6] = 1;
        weightOfFieldForBlack[Board.c5] = 2;
        weightOfFieldForBlack[Board.d5] = 4;
        weightOfFieldForBlack[Board.e5] = 4;
        weightOfFieldForBlack[Board.f5] = 2;
        weightOfFieldForBlack[Board.a4] = 1;
        weightOfFieldForBlack[Board.b4] = 2;
        weightOfFieldForBlack[Board.c4] = 3;
        weightOfFieldForBlack[Board.d4] = 4;
        weightOfFieldForBlack[Board.e4] = 4;
        weightOfFieldForBlack[Board.f4] = 3;
        weightOfFieldForBlack[Board.g4] = 2;
        weightOfFieldForBlack[Board.h4] = 1;
        weightOfFieldForBlack[Board.a3] = 1;
        weightOfFieldForBlack[Board.b3] = 2;
        weightOfFieldForBlack[Board.c3] = 3;
        weightOfFieldForBlack[Board.d3] = 4;
        weightOfFieldForBlack[Board.e3] = 4;
        weightOfFieldForBlack[Board.f3] = 3;
        weightOfFieldForBlack[Board.g3] = 2;
        weightOfFieldForBlack[Board.h3] = 1;
        weightOfFieldForBlack[Board.a2] = 2;
        weightOfFieldForBlack[Board.b2] = 2;
        weightOfFieldForBlack[Board.c2] = 4;
        weightOfFieldForBlack[Board.d2] = 5;
        weightOfFieldForBlack[Board.e2] = 5;
        weightOfFieldForBlack[Board.f2] = 4;
        weightOfFieldForBlack[Board.g2] = 2;
        weightOfFieldForBlack[Board.h2] = 2;
        weightOfFieldForBlack[Board.a1] = 6;
        weightOfFieldForBlack[Board.b1] = 6;
        weightOfFieldForBlack[Board.c1] = 6;
        weightOfFieldForBlack[Board.d1] = 6;
        weightOfFieldForBlack[Board.e1] = 6;
        weightOfFieldForBlack[Board.f1] = 6;
        weightOfFieldForBlack[Board.g1] = 6;
        weightOfFieldForBlack[Board.h1] = 6;
    }

    @FunctionalInterface
    private interface CalculateWeight {
        void calculate(WeightingFunction generator, int field, int color);
    }

    private final static CalculateWeight[] calculationFunctions = new CalculateWeight[Board.blackKing + 1];
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

    private final static int[] oppositeColor = new int[] { GameStatus.TURN_BLACK, GameStatus.TURN_WHITE };
    private final static int[] oppositeKing = new int[] { Board.blackKing, Board.whiteKing };

    private final static float mobilityFactor = 0.04f;
    private final static float threadWeightFactor = 0.02f;
    private final static float fieldDominanceWeightFactor = 0.01f;
    private final static float chessFactor = 0.25f;
    private final static float castlingFactor = 0.25f;
    private final static float openingFactor = 0.1f;
    private final static float doublePawnFactor = -0.1f;

    private GameStatus game;
    private int turn; // 0 = white, 1 = black
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private final int[] chessCount = new int[2];
    private final float[] piecesWeight = new float[2];
    private final float[] mobilityWeight = new float[2];
    private final float[] threadWeight = new float[2];
    private final float[] fieldDominanceWeight = new float[2];
    private boolean containsIllegalMove;
    private final int[] castlingState = new int[2];
    private final int[] openingState = new int[2];
    private final int[] doublePawnCount = new int[2];

    public static float calculateMaterialWeight(Board theBoard) {
        final byte[] board = theBoard.getRawBoard();
        final float[] piecesWeight = new float[2];

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

    public static float getMaterialWeightOfMove(int move, int depth) {
        final float capturedWeight = WeightingFunction.weightOfPiece[Move.getCapturedPiece(move)];
        //final float capturedWeight = pw != 0 ? pw - depth * 0.0001f : 0;
        final byte moveType = Move.getMoveType(move);
        if (moveType == Move.typeNormal)
            return capturedWeight;

        final float pawnWeight = weightOfPiece[Board.whitePawn];
        if (moveType == Move.typePawnPromotionQueen)
            return weightOfPiece[Board.whiteQueen] - pawnWeight + capturedWeight;
        else if (moveType == Move.typePawnPromotionKnight)
            return weightOfPiece[Board.whiteKnight] - pawnWeight + capturedWeight;
        else if (moveType == Move.typePawnPromotionRook)
            return weightOfPiece[Board.whiteRook] - pawnWeight + capturedWeight;
        else if (moveType == Move.typePawnPromotionBishop)
            return weightOfPiece[Board.whiteBishop] - pawnWeight + capturedWeight;
        else
            return 0;
    }

    public float calculate(GameStatus game, Board theBoard) {
        this.game = game;
        this.turn = game.getTurn() == GameStatus.TURN_WHITE ? 0 : 1;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.chessCount[0] = 0;
        this.chessCount[1] = 0;
        this.piecesWeight[0] = 0;
        this.piecesWeight[1] = 0;
        this.mobilityWeight[0] = 0;
        this.mobilityWeight[1] = 0;
        this.threadWeight[0] = 0;
        this.threadWeight[1] = 0;
        this.fieldDominanceWeight[0] = 0;
        this.fieldDominanceWeight[1] = 0;
        this.containsIllegalMove = false;
        this.castlingState[0] = 0;
        this.castlingState[1] = 0;
        this.openingState[0] = 0;
        this.openingState[1] = 0;
        this.doublePawnCount[0] = 0;
        this.doublePawnCount[1] = 0;

        final int stopField = Board.h8 + 1;

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
                fieldDominanceWeight[color] += getWeightOfField(piece, field, color);

                calculationFunctions[piece].calculate(this, field, color);
            }
        }

        calculateCastlingState();
        calculateOpeningState();

        return calculatePositionWeight();
    }

    private float calculatePositionWeight() {
        if (containsIllegalMove)
            return ILLEGAL_WEIGHT;

        final int plyCount = game.getPlyCount();
        final float openingFactorCorrection = plyCount > 20 ? (plyCount > 40 ? 0f : 0.5f) : 1.0f;

        return Math.round((
                  (piecesWeight[0] - piecesWeight[1])
                + (mobilityWeight[0] - mobilityWeight[1]) * mobilityFactor
                + (threadWeight[0] - threadWeight[1]) * threadWeightFactor
                + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor
                + (castlingState[0] - castlingState[1]) * castlingFactor
                + (openingState[0] - openingState[1]) * openingFactor * openingFactorCorrection
                + (chessCount[0] - chessCount[1]) * chessFactor
                + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor) * 100) / 100f;
    }

    void print() {
        System.out.println(toString());
    }

    @Override
    public String toString() {
        return "piecesWeight:       w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + ", delta=" + (piecesWeight[0] - piecesWeight[1]) + ", weight=" + (piecesWeight[0] - piecesWeight[1]) + '\n' +
               "mobilityWeight:     w=" + mobilityWeight[0] + ", b=" + mobilityWeight[1] + ", delta=" + (mobilityWeight[0] - mobilityWeight[1]) + ", weight=" + (mobilityWeight[0] - mobilityWeight[1]) * mobilityFactor + '\n' +
               "threadWeight:       w=" + threadWeight[0] + ", b=" + threadWeight[1] + ", delta=" + (threadWeight[0] - threadWeight[1]) + ", weight=" + (threadWeight[0] - threadWeight[1]) * threadWeightFactor + '\n' +
               "fieldDominance:     w=" + fieldDominanceWeight[0] + ", b=" + fieldDominanceWeight[1] + ", delta=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) + ", weight=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor + '\n' +
               "castlingState:      w=" + castlingState[0] + ", b=" + castlingState[1] + ", delta=" + (castlingState[0] - castlingState[1]) + ", weight=" + (castlingState[0] - castlingState[1]) * castlingFactor + '\n' +
               "openingState:       w=" + openingState[0] + ", b=" + openingState[1] + ", delta=" + (openingState[0] - openingState[1]) + ", weight=" + (openingState[0] - openingState[1]) * openingFactor + '\n' +
               "doublePawnCount:    w=" + doublePawnCount[0] + ", b=" + doublePawnCount[1] + ", delta=" + (doublePawnCount[0] - doublePawnCount[1]) + ", weight=" + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor + '\n' +
               "chessCount:         w=" + chessCount[0] + ", b=" + chessCount[1] + ", delta=" + (chessCount[0] - chessCount[1]) + ", weight=" + (chessCount[0] - chessCount[1]) * chessFactor + '\n' +
               "weight: " + calculatePositionWeight();
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    private static float getWeightOfField(byte piece, int field, int color) {
        final byte[] weightOfField = color == 0 ? weightOfFieldForWhite : weightOfFieldForBlack;
        return weightOfField[field] * mobilityWeightOfPiece[piece];
    }

    private void calculateForWhitePawn(int field, int color) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
            fieldDominanceWeight[color] += getWeightOfField(Board.whitePawn, to, color);
        } else if (board[to] == Board.whitePawn) {
            doublePawnCount[color]++; // double pawn
        }

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
                fieldDominanceWeight[color] += getWeightOfField(Board.whitePawn, to, color);
            }
        }

        // capture right
        to = field + Board.LENGTH + 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            capture(Board.whitePawn, field, to, color, board[to]);
        } else if (board[to] != Board.illegal) { // own color
            fieldDominanceWeight[color] += getWeightOfField(Board.whitePawn, to, color);
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            capture(Board.whitePawn, field, to, color, board[to]);
        } else if (board[to] != Board.illegal) { // own color
            fieldDominanceWeight[color] += getWeightOfField(Board.whitePawn, to, color);
        }

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.blackPawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH)
                    capture(Board.whitePawn, field, field - 1, color, Board.blackPawn);
                else if (board[field + 1] == Board.blackPawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH)
                    capture(Board.whitePawn, field, field + 1, color, Board.blackPawn);
            }
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
            fieldDominanceWeight[color] += getWeightOfField(Board.blackPawn, to, color);
        } else if (board[to] == Board.blackPawn) {
            doublePawnCount[color]++; // double pawn
        }

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field - Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
                fieldDominanceWeight[color] += getWeightOfField(Board.blackPawn, to, color);
            }
        }

        // capture right
        to = field - Board.LENGTH + 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            capture(Board.blackPawn, field, to, color, board[to]);
        } else if (board[to] != Board.illegal) { // own color
            fieldDominanceWeight[color] += getWeightOfField(Board.blackPawn, to, color);
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            capture(Board.blackPawn, field, to, color, board[to]);
        } else if (board[to] != Board.illegal) { // own color
            fieldDominanceWeight[color] += getWeightOfField(Board.blackPawn, to, color);
        }

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if (board[field - 1] == Board.whitePawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH)
                capture(Board.blackPawn, field, field - 1, color, Board.whitePawn);
            else if (board[field + 1] == Board.whitePawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH)
                capture(Board.blackPawn, field, field + 1, color, Board.whitePawn);
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

        // move up
        for (int to = field + Board.LENGTH; move(myPiece, field, to, color); to += Board.LENGTH);
        // move down
        for (int to = field - Board.LENGTH; move(myPiece, field, to, color); to -= Board.LENGTH);
        // move left
        for (int to = field - 1; move(myPiece, field, to, color); to--);
        // move right
        for (int to = field + 1; move(myPiece, field, to, color); to++);
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
        final byte piece = board[to];
        final int oppositeColor = WeightingFunction.oppositeColor[color];

        if (piece == Board.illegal)
            return false;

        if (piece == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[movingPiece];
            fieldDominanceWeight[color] += getWeightOfField(movingPiece, to, color);
            return true;
        } else if ((piece & oppositeColor) == oppositeColor) {
            capture(movingPiece, from, to, color, piece);
            return false;
        } else { // own color
            fieldDominanceWeight[color] += getWeightOfField(movingPiece, to, color);
            return false;
        }
    }

    private void capture(final byte movingPiece, final int from, final int to, final int color, final byte piece) {
        if (piece == oppositeKing[color]) {
            if (turn == color) {
                containsIllegalMove = true;
            } else {
                chessCount[color]++;
                threadWeight[color] += 4; // ok, give some weight to the attacked king as well (since weightOfPiece(king) is 0)
            }
        }

        mobilityWeight[color] += mobilityWeightOfPiece[movingPiece];
        fieldDominanceWeight[color] += getWeightOfField(movingPiece, to, color);
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

    private void calculateOpeningState() {
        // white
        int state = 0;
        if (!game.hasWhiteCastled()) state--;
        if (board[Board.b1] == Board.whiteKnight) state--;
        if (board[Board.c1] == Board.whiteBishop) state--;
        if (board[Board.f1] == Board.whiteBishop) state--;
        if (board[Board.g1] == Board.whiteKnight) state--;
        int movedPawnCount = 0;
        if (board[Board.b2] != Board.whitePawn) movedPawnCount++;
        if (board[Board.c2] != Board.whitePawn) movedPawnCount++;
        if (board[Board.d2] != Board.whitePawn) movedPawnCount++;
        if (board[Board.e2] != Board.whitePawn) movedPawnCount++;
        if (board[Board.g2] != Board.whitePawn) movedPawnCount++;
        if (movedPawnCount == 0) state -= 2;
        else if (movedPawnCount == 1) state--;
        openingState[0] = state;

        // white
        state = 0;
        if (!game.hasBlackCastled()) state--;
        if (board[Board.b8] == Board.blackKnight) state--;
        if (board[Board.c8] == Board.blackBishop) state--;
        if (board[Board.f8] == Board.blackBishop) state--;
        if (board[Board.g8] == Board.blackKnight) state--;
        movedPawnCount = 0;
        if (board[Board.b7] != Board.blackPawn) movedPawnCount++;
        if (board[Board.c7] != Board.blackPawn) movedPawnCount++;
        if (board[Board.d7] != Board.blackPawn) movedPawnCount++;
        if (board[Board.e7] != Board.blackPawn) movedPawnCount++;
        if (board[Board.g7] != Board.blackPawn) movedPawnCount++;
        if (movedPawnCount == 0) state -= 2;
        else if (movedPawnCount == 1) state--;
        openingState[1] = state;
    }
}
