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

import java.util.Arrays;

@SuppressWarnings({"StatementWithEmptyBody", "Duplicates", "PointlessArithmeticExpression"})
final class WeightingFunction {

    final static float ILLEGAL_WEIGHT = Float.NEGATIVE_INFINITY;

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

    private final static float mobilityFactor = 0.02f;
    private final static float threadCountFactorLow = 0.05f;
    private final static float threadCountFactorHigh = 0.2f;
    private final static float threadWeightFactor = 0.01f;
    private final static float unguardedWeightFactor = 0.2f;
    private final static float underGuardedWeightFactor = 0.05f;
    private final static float fieldDominanceWeightFactor = 0.025f;
    private final static float chessFactor = 0.5f;

    private GameStatus game;
    private int turn; // 0 = white, 1 = black
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private int[] chessCount = new int[2];
    private float[] piecesWeight = new float[2];
    private int[] movesCount = new int[2];
    private int[] threadCount = new int[2];
    private float[] threadWeight = new float[2];
    private int[] fieldDominanceWeight = new int[2];
    private float[] unguardedWeight = new float[2];
    private float[] underGuardedWeight = new float[2];
    private boolean containsIllegalMove;
    private final byte[] fieldAttackCountWhite = Board.createEmptyBoard().getRawBoard();
    private final byte[] fieldAttackCountBlack = Board.createEmptyBoard().getRawBoard();

    float calculate(GameStatus game, Board theBoard) {
        this.game = game;
        this.turn = game.getTurn() == GameStatus.TURN_WHITE ? 0 : 1;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.chessCount[0] = 0;
        this.chessCount[1] = 0;
        this.piecesWeight[0] = 0;
        this.piecesWeight[1] = 0;
        this.movesCount[0] = 0;
        this.movesCount[1] = 0;
        this.threadCount[0] = 0;
        this.threadCount[1] = 0;
        this.threadWeight[0] = 0;
        this.threadWeight[1] = 0;
        this.unguardedWeight[0] = 0;
        this.unguardedWeight[1] = 0;
        this.underGuardedWeight[0] = 0;
        this.underGuardedWeight[1] = 0;
        this.fieldDominanceWeight[0] = 0;
        this.fieldDominanceWeight[1] = 0;
        this.containsIllegalMove = false;

        Arrays.fill(fieldAttackCountWhite, (byte) 0);
        Arrays.fill(fieldAttackCountBlack, (byte) 0);

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

        calculateAttackersAndGuards();

        return calculatePositionWeight();
    }

    private float calculatePositionWeight() {
        if (containsIllegalMove)
            return ILLEGAL_WEIGHT;

        final int threadCountDelta = threadCount[0] - threadCount[1];

        return piecesWeight[0] - piecesWeight[1]
                + (movesCount[0] - movesCount[1]) * mobilityFactor
                + (threadCountDelta * ((Math.abs(threadCountDelta) > 2) ? threadCountFactorHigh : threadCountFactorLow))
                + (threadWeight[0] - threadWeight[1]) * threadWeightFactor
                + (unguardedWeight[0] - unguardedWeight[1]) * unguardedWeightFactor
                + (underGuardedWeight[0] - underGuardedWeight[1]) * underGuardedWeightFactor
                + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor
                + (chessCount[0] - chessCount[1]) * chessFactor;
    }

    void print() {
        System.out.println(toString());
    }

    @Override
    public String toString() {
        final int threadCountDelta = threadCount[0] - threadCount[1];

        return "piecesWeight:       w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + ", delta=" + (piecesWeight[0] - piecesWeight[1]) + ", weight=" + (piecesWeight[0] - piecesWeight[1]) + '\n' +
               "movesCount:         w=" + movesCount[0] + ", b=" + movesCount[1] + ", delta=" + (movesCount[0] - movesCount[1]) + ", weight=" + (movesCount[0] - movesCount[1]) * mobilityFactor + '\n' +
               "chessCount:         w=" + chessCount[0] + ", b=" + chessCount[1] + ", delta=" + (chessCount[0] - chessCount[1]) + ", weight=" + (chessCount[0] - chessCount[1]) * chessFactor + '\n' +
               "threadCount:        w=" + threadCount[0] + ", b=" + threadCount[1] + ", delta=" + threadCountDelta + ", weight=" + (threadCountDelta * ((Math.abs(threadCountDelta) > 2) ? threadCountFactorHigh : threadCountFactorLow)) + '\n' +
               "threadWeight:       w=" + threadWeight[0] + ", b=" + threadWeight[1] + ", delta=" + (threadWeight[0] - threadWeight[1]) + ", weight=" + (threadWeight[0] - threadWeight[1]) * threadWeightFactor + '\n' +
               "unguardedWeight:    w=" + unguardedWeight[0] + ", b=" + unguardedWeight[1] + ", delta=" + (unguardedWeight[0] - unguardedWeight[1]) + ", weight=" + (unguardedWeight[0] - unguardedWeight[1]) * unguardedWeightFactor + '\n' +
               "underGuardedWeight: w=" + underGuardedWeight[0] + ", b=" + underGuardedWeight[1] + ", delta=" + (underGuardedWeight[0] - underGuardedWeight[1]) + ", weight=" + (underGuardedWeight[0] - underGuardedWeight[1]) * underGuardedWeightFactor + '\n' +
               "fieldDominance:     w=" + fieldDominanceWeight[0] + ", b=" + fieldDominanceWeight[1] + ", delta=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) + ", weight=" + (fieldDominanceWeight[0] - fieldDominanceWeight[1]) * fieldDominanceWeightFactor + '\n' +
               "weight: " + calculatePositionWeight();
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    private static int getWeightOfField(int field, int color) {
        final byte[] weightOfField = color == 0 ? weightOfFieldForWhite : weightOfFieldForBlack;
        return weightOfField[field];
    }

    private byte[] getFieldAttackCount(int color) {
        return color == 0 ? fieldAttackCountWhite : fieldAttackCountBlack;
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
            if (board[to] != Board.empty) // own color
                getFieldAttackCount(color)[to]++;
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
            if (board[to] != Board.empty) // own color
                getFieldAttackCount(color)[to]++;
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
            if (board[to] != Board.empty) // own color
                getFieldAttackCount(color)[to]++;
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) {
            countThreat(field, to, color, board[to]);
        } else if (board[to] != Board.illegal) {
            fieldDominanceWeight[color] += getWeightOfField(to, color);
            if (board[to] != Board.empty) // own color
                getFieldAttackCount(color)[to]++;
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
            movesCount[color]++;
            fieldDominanceWeight[color] += getWeightOfField(to, color);
            return true;
        } else if ((piece & oppositeColor) == oppositeColor) {
            countThreat(from, to, color, piece);
            return false;
        } else if (piece != Board.illegal) { // own color
            fieldDominanceWeight[color] += getWeightOfField(to, color);
            getFieldAttackCount(color)[to]++;
            return false;
        } else {
            return false;
        }
    }

    private void countThreat(final int from, final int to, final int color, final byte piece) {
        if (piece == oppositeKing[color]) {
            if (turn == color)
                containsIllegalMove = true;
            else
                chessCount[color]++;
            return;
        }

        final byte[] weightOfField = color == 0 ? weightOfFieldForWhite : weightOfFieldForBlack;
        movesCount[color]++;
        fieldDominanceWeight[color] += getWeightOfField(to, color);
        threadCount[color]++;

        final byte myPiece = board[from];
        if (!Board.isKing(myPiece)) {
            final float myWeight = weightOfPiece[myPiece];
            final float opponentWeight = weightOfPiece[piece];
            if (myWeight < opponentWeight)
                threadWeight[color] += opponentWeight - myWeight;
            else if (myWeight == opponentWeight)
                threadWeight[color] += 1;
            else
                threadWeight[color] += 0.2;
        }

        getFieldAttackCount(color)[to]++;
    }

    private void calculateAttackersAndGuards() {
        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;
                final byte countGuards = getFieldAttackCount(color)[field];
                final byte countAttackers = getFieldAttackCount(color == 0 ? 1 : 0)[field];

                if (countGuards == 0) {
                    // unguarded piece
                    unguardedWeight[color] -= weightOfPiece[piece];
                }

                if (countAttackers > countGuards) {
                    // potential loss
                    underGuardedWeight[color] -= weightOfPiece[piece];
                }
            }
        }

    }
}
