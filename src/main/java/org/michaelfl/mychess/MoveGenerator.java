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
final class MoveGenerator {

    @FunctionalInterface
    private interface CalculateMoves {
        void calculateMoves(MoveGenerator generator, int field);
    }

    private final static CalculateMoves[] calculationFunctions = new CalculateMoves[22];
    static {
        calculationFunctions[Board.whitePawn] = MoveGenerator::_calculateWhitePawnMoves;
        calculationFunctions[Board.whiteKnight] = MoveGenerator::_calculateKnightMoves;
        calculationFunctions[Board.whiteBishop] = MoveGenerator::_calculateBishopMoves;
        calculationFunctions[Board.whiteRook] = MoveGenerator::_calculateRookMoves;
        calculationFunctions[Board.whiteQueen] = MoveGenerator::_calculateQueenMoves;
        calculationFunctions[Board.whiteKing] = MoveGenerator::_calculateKingMoves;
        calculationFunctions[Board.blackPawn] = MoveGenerator::_calculateBlackPawnMoves;
        calculationFunctions[Board.blackKnight] = MoveGenerator::_calculateKnightMoves;
        calculationFunctions[Board.blackBishop] = MoveGenerator::_calculateBishopMoves;
        calculationFunctions[Board.blackRook] = MoveGenerator::_calculateRookMoves;
        calculationFunctions[Board.blackQueen] = MoveGenerator::_calculateQueenMoves;
        calculationFunctions[Board.blackKing] = MoveGenerator::_calculateKingMoves;
    }

    private GameStatus game;
    private Board theBoard;
    private byte[] board;
    private int oppositeColor;
    private int oppositeKing;
    private Moves moves;
    private boolean containsIllegalMove;

    Moves calculateMoves(GameStatus game, Board theBoard) {
        final int turn = game.getTurn();
        this.game = game;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.oppositeColor = game.getOppositeColor();
        this.oppositeKing = turn == Game.TURN_WHITE ? Board.blackKing : Board.whiteKing;
        this.moves = new Moves();
        this.containsIllegalMove = false;

        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if ((piece & turn) == turn)
                calculationFunctions[piece].calculateMoves(this, field);
        }

        if (containsIllegalMove)
            return Moves.ILLEGAL;

        return moves;
    }

    private static void _calculateWhitePawnMoves(MoveGenerator generator, int field) {
        generator.calculateWhitePawnMoves(field);
    }

    private void calculateWhitePawnMoves(int field) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == 0)
            moves.addMove(field, to);

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == 0 && board[field + Board.LENGTH] == 0)
                moves.addMove(field, to);
        }

        // capture right
        to = field + Board.LENGTH + 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            moves.addMove(field, to);
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            moves.addMove(field, to);
        }

        // en passant
        if (fieldToRow(field) == 4) {
            if (board[field - 1] == Board.blackPawn && game.getLastMoveTo() == field - 1 && game.getLastMoveFrom() == field - 1 + 2 * Board.LENGTH)
                moves.addMove(field, field - 1 + Board.LENGTH);
            else if (board[field + 1] == Board.blackPawn && game.getLastMoveTo() == field + 1 && game.getLastMoveFrom() == field + 1 + 2 * Board.LENGTH)
                moves.addMove(field, field + 1 + Board.LENGTH);
        }
    }

    private static int fieldToRow(int field) {
        return field / Board.LENGTH - 2;
    }

    private static void _calculateBlackPawnMoves(MoveGenerator generator, int field) {
        generator.calculateBlackPawnMoves(field);
    }

    private void calculateBlackPawnMoves(int field) {
        // single step
        int to = field - Board.LENGTH;
        if (board[to] == 0)
            moves.addMove(field, to);

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == 0 && board[field - Board.LENGTH] == 0)
                moves.addMove(field, to);
        }

        // capture right
        to = field - Board.LENGTH + 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            moves.addMove(field, to);
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            moves.addMove(field, to);
        }

        // en passant
        if (fieldToRow(field) == 3) {
            if (board[field - 1] == Board.whitePawn && game.getLastMoveTo() == field - 1 && game.getLastMoveFrom() == field - 1 - 2 * Board.LENGTH)
                moves.addMove(field, field - 1 - Board.LENGTH);
            else if (board[field + 1] == Board.whitePawn && game.getLastMoveTo() == field + 1 && game.getLastMoveFrom() == field + 1 - 2 * Board.LENGTH)
                moves.addMove(field, field + 1 - Board.LENGTH);
        }
    }

    private static void _calculateKnightMoves(MoveGenerator generator, int field) {
        generator.calculateKnightMoves(field);
    }

    private void calculateKnightMoves(int field) {
        move(field, field + 2 * Board.LENGTH + 1);
        move(field, field + 1 * Board.LENGTH + 2);
        move(field, field - 1 * Board.LENGTH + 2);
        move(field, field - 2 * Board.LENGTH + 1);
        move(field, field - 2 * Board.LENGTH - 1);
        move(field, field - 1 * Board.LENGTH - 2);
        move(field, field + 1 * Board.LENGTH - 2);
        move(field, field + 2 * Board.LENGTH - 1);
    }

    private static void _calculateBishopMoves(MoveGenerator generator, int field) {
        generator.calculateBishopMoves(field);
    }

    private void calculateBishopMoves(int field) {
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(field, to); to += Board.LENGTH + 1);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(field, to); to = to - Board.LENGTH + 1);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(field, to); to = to - Board.LENGTH - 1);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(field, to); to += Board.LENGTH - 1);
    }

    private static void _calculateRookMoves(MoveGenerator generator, int field) {
        generator.calculateRookMoves(field);
    }

    private void calculateRookMoves(int field) {
        // move up
        for (int to = field + Board.LENGTH; move(field, to); to += Board.LENGTH);
        // move down
        for (int to = field - Board.LENGTH; move(field, to); to -= Board.LENGTH);
        // move left
        for (int to = field - 1; move(field, to); to--);
        // move right
        for (int to = field + 1; move(field, to); to++);
    }

    private static void _calculateQueenMoves(MoveGenerator generator, int field) {
        generator.calculateQueenMoves(field);
    }

    private void calculateQueenMoves(int field) {
        // move up
        for (int to = field + Board.LENGTH; move(field, to); to += Board.LENGTH);
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(field, to); to += Board.LENGTH + 1);
        // move right
        for (int to = field + 1; move(field, to); to++);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(field, to); to = to - Board.LENGTH + 1);
        // move down
        for (int to = field - Board.LENGTH; move(field, to); to -= Board.LENGTH);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(field, to); to = to - Board.LENGTH - 1);
        // move left
        for (int to = field - 1; move(field, to); to--);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(field, to); to += Board.LENGTH - 1);
    }

    private static void _calculateKingMoves(MoveGenerator generator, int field) {
        generator.calculateKingMoves(field);
    }

    private void calculateKingMoves(int field) {
        // move up
        move(field, field + Board.LENGTH);
        // move up-right
        move(field, field + Board.LENGTH + 1);
        // move right
        move(field, field + 1);
        // move down-right
        move(field, field - Board.LENGTH + 1);
        // move down
        move(field, field - Board.LENGTH);
        // move down-left
        move(field, field - Board.LENGTH - 1);
        // move left
        move(field, field - 1);
        // move up-left
        move(field, field + Board.LENGTH - 1);

        // TODO: castling (Must check if king and crossed fields are not under attack!!!)
    }

    private boolean move(final int from, final int to) {
        final byte piece = board[to];
        if (piece == 0 || (piece & oppositeColor) == oppositeColor) {
            moves.addMove(from, to);
            if (piece == oppositeKing)
                containsIllegalMove = true;
        }
        return piece == 0;
    }

}
