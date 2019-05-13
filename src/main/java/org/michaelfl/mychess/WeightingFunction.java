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
final class WeightingFunction {

    final static float ILLEGAL_WEIGHT = Float.MIN_VALUE;

    private final static float[] weightOfPiece = new float[Board.blackKing + 1];
    static {
        weightOfPiece[Board.whitePawn]   = 1.0f;
        weightOfPiece[Board.whiteKnight] = 3.0f;
        weightOfPiece[Board.whiteBishop] = 3.0f;
        weightOfPiece[Board.whiteRook]   = 4.5f;
        weightOfPiece[Board.whiteQueen]  = 9.0f;
        weightOfPiece[Board.whiteKing]   = 0.0f;
        weightOfPiece[Board.blackPawn]   = 1.0f;
        weightOfPiece[Board.blackKnight] = 3.0f;
        weightOfPiece[Board.blackBishop] = 3.0f;
        weightOfPiece[Board.blackRook]   = 4.5f;
        weightOfPiece[Board.blackQueen]  = 9.0f;
        weightOfPiece[Board.blackKing]   = 0.0f;
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

    private final static float mobilityFactor = 0.1f;
    private final static float threadCountFactor = 0.01f;
    private final static float threadWeightFactor = 0.5f;
    private final static float fieldDominanceWeightFactor = 0.1f;

    private GameStatus game;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private float[] piecesWeight = new float[2];
    private int[] movesCount = new int[2];
    private int[] threadCount = new int[2];
    private float[] threadWeight = new float[2];
    private int[] fieldDominanceWeight = new int[2];
    private boolean containsIllegalMove;

    float calculate(GameStatus game, Board theBoard) {
        this.game = game;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.piecesWeight[0] = 0;
        this.piecesWeight[1] = 0;
        this.movesCount[0] = 0;
        this.movesCount[1] = 0;
        this.threadCount[0] = 0;
        this.threadCount[1] = 0;
        this.threadWeight[0] = 0;
        this.threadWeight[1] = 0;
        this.fieldDominanceWeight[0] = 0;
        this.fieldDominanceWeight[1] = 0;
        this.containsIllegalMove = false;

        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
                fieldDominanceWeight[color] += getWeightOfField(field, color);

                calculationFunctions[piece].calculate(this, field, color);

                // TODO: Consider castling states: ++has castled; -has not yet castled; --has not yet & cannot castle
            }
        }

        return calculatePositionWeight();
    }

    private float calculatePositionWeight() {
        if (containsIllegalMove)
            return ILLEGAL_WEIGHT;

        return piecesWeight[0] - piecesWeight[1]
                + (movesCount[0] - movesCount[1]) * mobilityFactor
                + (threadCount[0] - threadCount[1]) * threadCountFactor
                + (threadWeight[0] - threadWeight[1]) * threadWeightFactor
                + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor;
    }

    void print() {
        System.out.println(toString());
    }

    @Override
    public String toString() {
        return "piecesWeight:   w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + ", delta=" + (piecesWeight[0] - piecesWeight[1]) + ", weight=" + (piecesWeight[0] - piecesWeight[1]) + '\n' +
               "movesCount:     w=" + movesCount[0] + ", b=" + movesCount[1] + ", delta=" + (movesCount[0] - movesCount[1]) + ", weight=" + (movesCount[0] - movesCount[1]) * mobilityFactor + '\n' +
               "threadCount:    w=" + threadCount[0] + ", b=" + threadCount[1] + ", delta=" + (threadCount[0] - threadCount[1]) + ", weight=" + (threadCount[0] - threadCount[1]) * threadCountFactor + '\n' +
               "threadWeight:   w=" + threadWeight[0] + ", b=" + threadWeight[1] + ", delta=" + (threadWeight[0] - threadWeight[1]) + ", weight=" + (threadWeight[0] - threadWeight[1]) * threadWeightFactor + '\n' +
               "fieldDominance: w=" + fieldDominanceWeight[0] + ", b=" + fieldDominanceWeight[1] + ", delta=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) + ", weight=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor + '\n' +
               "weight: " + calculatePositionWeight();
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    private static int getWeightOfField(int field, int color) {
        final byte[] weightOfField = color == 0 ? weightOfFieldForWhite : weightOfFieldForBlack;
        return weightOfField[field];
    }

    private void calculateForWhitePawn(int field, int color) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == Board.empty)
            movesCount[color]++;

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty)
                movesCount[color]++;
        }

        // capture right
        to = field + Board.LENGTH + 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
        }

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if ((board[field - 1] == Board.blackPawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH)
                        || (board[field + 1] == Board.blackPawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH)) {
                    countThreat(field, to, color, board[to]);
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

    private void calculateForBlackPawn(int field, int color) {
        // single step
        int to = field - Board.LENGTH;
        if (board[to] == Board.empty)
            movesCount[color]++;

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field - Board.LENGTH] == Board.empty)
                movesCount[color]++;
        }

        // capture right
        to = field - Board.LENGTH + 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
        }

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if ((board[field - 1] == Board.whitePawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH)
                    || (board[field + 1] == Board.whitePawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH))
                countThreat(field, to, color, board[to]);
        }
    }

    private static void _calculateForKnight(WeightingFunction generator, int field, int color) {
        generator.calculateForKnight(field, color);
    }

    private void calculateForKnight(int field, int color) {
        move(field, field + 2 * Board.LENGTH + 1, color);
        move(field, field + 1 * Board.LENGTH + 2, color);
        move(field, field - 1 * Board.LENGTH + 2, color);
        move(field, field - 2 * Board.LENGTH + 1, color);
        move(field, field - 2 * Board.LENGTH - 1, color);
        move(field, field - 1 * Board.LENGTH - 2, color);
        move(field, field + 1 * Board.LENGTH - 2, color);
        move(field, field + 2 * Board.LENGTH - 1, color);
    }

    private static void _calculateForBishop(WeightingFunction generator, int field, int color) {
        generator.calculateForBishop(field, color);
    }

    private void calculateForBishop(int field, int color) {
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(field, to, color); to += Board.LENGTH + 1);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(field, to, color); to = to - Board.LENGTH + 1);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(field, to, color); to = to - Board.LENGTH - 1);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForRook(WeightingFunction generator, int field, int color) {
        generator.calculateForRook(field, color);
    }

    private void calculateForRook(int field, int color) {
        // move up
        for (int to = field + Board.LENGTH; move(field, to, color); to += Board.LENGTH);
        // move down
        for (int to = field - Board.LENGTH; move(field, to, color); to -= Board.LENGTH);
        // move left
        for (int to = field - 1; move(field, to, color); to--);
        // move right
        for (int to = field + 1; move(field, to, color); to++);
    }

    private static void _calculateForQueen(WeightingFunction generator, int field, int color) {
        generator.calculateForQueen(field, color);
    }

    private void calculateForQueen(int field, int color) {
        // move up
        for (int to = field + Board.LENGTH; move(field, to, color); to += Board.LENGTH);
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(field, to, color); to += Board.LENGTH + 1);
        // move right
        for (int to = field + 1; move(field, to, color); to++);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(field, to, color); to = to - Board.LENGTH + 1);
        // move down
        for (int to = field - Board.LENGTH; move(field, to, color); to -= Board.LENGTH);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(field, to, color); to = to - Board.LENGTH - 1);
        // move left
        for (int to = field - 1; move(field, to, color); to--);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForKing(WeightingFunction generator, int field, int color) {
        generator.calculateForKing(field, color);
    }

    private void calculateForKing(int field, int color) {
        // move up
        move(field, field + Board.LENGTH, color);
        // move up-right
        move(field, field + Board.LENGTH + 1, color);
        // move right
        move(field, field + 1, color);
        // move down-right
        move(field, field - Board.LENGTH + 1, color);
        // move down
        move(field, field - Board.LENGTH, color);
        // move down-left
        move(field, field - Board.LENGTH - 1, color);
        // move left
        move(field, field - 1, color);
        // move up-left
        move(field, field + Board.LENGTH - 1, color);
    }

    private boolean move(final int from, final int to, int color) {
        final byte piece = board[to];
        final int oppositeColor = WeightingFunction.oppositeColor[color];

        if (piece == Board.empty) {
            countMove(to, color);
            return true;
        } else if ((piece & oppositeColor) == oppositeColor) {
            countThreat(from, to, color, piece);
            return false;
        } else {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
            return false;
        }
    }

    private void countMove(final int field, final int color) {
        movesCount[color]++;
        fieldDominanceWeight[color] += getWeightOfField(field, color);
    }

    private void countThreat(final int from, final int to, final int color, final byte piece) {
        if (piece == oppositeKing[color]) {
            containsIllegalMove = true;
            return;
        }

        final byte[] weightOfField = color == 0 ? weightOfFieldForWhite : weightOfFieldForBlack;
        movesCount[color]++;
        fieldDominanceWeight[color] += getWeightOfField(to, color);
        threadCount[color]++;

        final float myWeight = weightOfPiece[board[from]];
        final float opponentWeight = weightOfPiece[piece];
        if (myWeight < opponentWeight)
            threadWeight[color] += (opponentWeight - myWeight) * 2;
        else if (myWeight == opponentWeight)
            threadWeight[color] += 1;
    }

}
