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

import java.util.Random;

@SuppressWarnings({"StatementWithEmptyBody", "Duplicates", "PointlessArithmeticExpression"})
public final class MoveGenerator {

    @FunctionalInterface
    private interface CalculateMoves {
        void calculateMoves(MoveGenerator generator, int field);
    }

    private final static CalculateMoves[] calculationFunctions = new CalculateMoves[22];
    static {
        calculationFunctions[Board.whitePawn]   = MoveGenerator::_calculateWhitePawnMoves;
        calculationFunctions[Board.whiteKnight] = MoveGenerator::_calculateKnightMoves;
        calculationFunctions[Board.whiteBishop] = MoveGenerator::_calculateBishopMoves;
        calculationFunctions[Board.whiteRook]   = MoveGenerator::_calculateRookMoves;
        calculationFunctions[Board.whiteQueen]  = MoveGenerator::_calculateQueenMoves;
        calculationFunctions[Board.whiteKing]   = MoveGenerator::_calculateKingMoves;
        calculationFunctions[Board.blackPawn]   = MoveGenerator::_calculateBlackPawnMoves;
        calculationFunctions[Board.blackKnight] = MoveGenerator::_calculateKnightMoves;
        calculationFunctions[Board.blackBishop] = MoveGenerator::_calculateBishopMoves;
        calculationFunctions[Board.blackRook]   = MoveGenerator::_calculateRookMoves;
        calculationFunctions[Board.blackQueen]  = MoveGenerator::_calculateQueenMoves;
        calculationFunctions[Board.blackKing]   = MoveGenerator::_calculateKingMoves;
    }

    private final Random rand;
    private final MoveSorter moveSorter;

    private GameStatus game;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private int oppositeColor;
    private int oppositeKing;
    private boolean containsIllegalMove;

    public MoveGenerator(Random rand) {
        this(rand, new KillerMoves());
    }

    public MoveGenerator(Random rand, KillerMoves killerMoves) {
        this.rand = rand;
        this.moveSorter = new MoveSorter(rand, killerMoves);
    }

    public Moves calculateMoves(GameStatus game, Board theBoard) {
        return calculateMoves(game, theBoard, 0);
    }

    public Moves calculateMoves(GameStatus game, Board theBoard, int depth) {
        final int turn = game.getTurn();
        this.game = game;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.oppositeColor = game.getOppositeColor();
        this.oppositeKing = turn == GameStatus.TURN_WHITE ? Board.blackKing : Board.whiteKing;
        this.containsIllegalMove = false;
        this.moveSorter.reset(game, theBoard, depth);

        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if ((piece & turn) == turn)
                calculationFunctions[piece].calculateMoves(this, field);
        }

        if (containsIllegalMove)
            return Moves.ILLEGAL;

        return moveSorter.getSortedMoves();
    }

    private static void _calculateWhitePawnMoves(MoveGenerator generator, int field) {
        generator.calculateWhitePawnMoves(field);
    }

    private void calculateWhitePawnMoves(int field) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == 0)
            addWhitePawnMove(field, to);

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == 0 && board[field + Board.LENGTH] == 0)
                addWhitePawnMove(field, to);
        }

        // capture right
        to = field + Board.LENGTH + 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            addWhitePawnMove(field, to);
        }

        // capture left
        to = field + Board.LENGTH - 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            addWhitePawnMove(field, to);
        }

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (board[field - 1] == Board.blackPawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH)
                addWhitePawnMove(field, field - 1 + Board.LENGTH);
            else if (board[field + 1] == Board.blackPawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH)
                addWhitePawnMove(field, field + 1 + Board.LENGTH);
        }
    }

    private void addWhitePawnMove(int from, int to) {
        if (to >= Board.a8) {
            // Pawn promotion
            addMove(from, to, Board.whitePawn, board[to], Move.typePawnPromotionQueen);
            addMove(from, to, Board.whitePawn, board[to], Move.typePawnPromotionKnight);
        } else {
            addMove(from, to, Board.whitePawn, board[to], Move.typeNormal);
        }
    }

    private void addBlackPawnMove(int from, int to) {
        if (to <= Board.h1) {
            // Pawn promotion
            addMove(from, to, Board.blackPawn, board[to], Move.typePawnPromotionQueen);
            addMove(from, to, Board.blackPawn, board[to], Move.typePawnPromotionKnight);
        } else {
            addMove(from, to, Board.blackPawn, board[to], Move.typeNormal);
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
            addBlackPawnMove(field, to);

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == 0 && board[field - Board.LENGTH] == 0)
                addBlackPawnMove(field, to);
        }

        // capture right
        to = field - Board.LENGTH + 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            addBlackPawnMove(field, to);
        }

        // capture left
        to = field - Board.LENGTH - 1;
        if ((board[to] & oppositeColor) == oppositeColor) {
            if (board[to] == oppositeKing)
                containsIllegalMove = true;
            addBlackPawnMove(field, to);
        }

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if (board[field - 1] == Board.whitePawn && Move.getToField(lastMove) == field - 1 && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH)
                addBlackPawnMove(field, field - 1 - Board.LENGTH);
            else if (board[field + 1] == Board.whitePawn && Move.getToField(lastMove) == field + 1 && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH)
                addBlackPawnMove(field, field + 1 - Board.LENGTH);
        }
    }

    private static void _calculateKnightMoves(MoveGenerator generator, int field) {
        generator.calculateKnightMoves(field);
    }

    private void calculateKnightMoves(int field) {
        final byte piece = board[field];

        move(piece, field, field + 2 * Board.LENGTH + 1);
        move(piece, field, field + 1 * Board.LENGTH + 2);
        move(piece, field, field - 1 * Board.LENGTH + 2);
        move(piece, field, field - 2 * Board.LENGTH + 1);
        move(piece, field, field - 2 * Board.LENGTH - 1);
        move(piece, field, field - 1 * Board.LENGTH - 2);
        move(piece, field, field + 1 * Board.LENGTH - 2);
        move(piece, field, field + 2 * Board.LENGTH - 1);
    }

    private static void _calculateBishopMoves(MoveGenerator generator, int field) {
        generator.calculateBishopMoves(field);
    }

    private void calculateBishopMoves(int field) {
        final byte piece = board[field];

        // move up-right
        for (int to = field + Board.LENGTH + 1; move(piece, field, to); to += Board.LENGTH + 1);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(piece, field, to); to = to - Board.LENGTH + 1);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(piece, field, to); to = to - Board.LENGTH - 1);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(piece, field, to); to += Board.LENGTH - 1);
    }

    private static void _calculateRookMoves(MoveGenerator generator, int field) {
        generator.calculateRookMoves(field);
    }

    private void calculateRookMoves(int field) {
        final byte piece = board[field];

        // move up
        for (int to = field + Board.LENGTH; move(piece, field, to); to += Board.LENGTH);
        // move down
        for (int to = field - Board.LENGTH; move(piece, field, to); to -= Board.LENGTH);
        // move left
        for (int to = field - 1; move(piece, field, to); to--);
        // move right
        for (int to = field + 1; move(piece, field, to); to++);
    }

    private static void _calculateQueenMoves(MoveGenerator generator, int field) {
        generator.calculateQueenMoves(field);
    }

    private void calculateQueenMoves(int field) {
        final byte piece = board[field];

        // move up
        for (int to = field + Board.LENGTH; move(piece, field, to); to += Board.LENGTH);
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(piece, field, to); to += Board.LENGTH + 1);
        // move right
        for (int to = field + 1; move(piece, field, to); to++);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(piece, field, to); to = to - Board.LENGTH + 1);
        // move down
        for (int to = field - Board.LENGTH; move(piece, field, to); to -= Board.LENGTH);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(piece, field, to); to = to - Board.LENGTH - 1);
        // move left
        for (int to = field - 1; move(piece, field, to); to--);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(piece, field, to); to += Board.LENGTH - 1);
    }

    private static void _calculateKingMoves(MoveGenerator generator, int field) {
        generator.calculateKingMoves(field);
    }

    private void calculateKingMoves(int field) {
        final byte piece = board[field];

        // move up
        move(piece, field, field + Board.LENGTH);
        // move up-right
        move(piece, field, field + Board.LENGTH + 1);
        // move right
        move(piece, field, field + 1);
        // move down-right
        move(piece, field, field - Board.LENGTH + 1);
        // move down
        move(piece, field, field - Board.LENGTH);
        // move down-left
        move(piece, field, field - Board.LENGTH - 1);
        // move left
        move(piece, field, field - 1);
        // move up-left
        move(piece, field, field + Board.LENGTH - 1);

        // castling
        if (game.isCastlingPossible())
            calculateCastlingMoves();
    }

    private boolean move(final byte piece, final int from, final int to) {
        final byte capturedPiece = board[to];
        if (capturedPiece == 0 || (capturedPiece & oppositeColor) == oppositeColor) {
            addMove(from, to, piece, capturedPiece, Move.typeNormal);
            if (capturedPiece == oppositeKing)
                containsIllegalMove = true;
        }
        return capturedPiece == 0;
    }

    private void calculateCastlingMoves() {
        if (game.getTurn() == GameStatus.TURN_WHITE) {
            if (game.isWhiteCastlingKingSidePossible() && canDoWhiteCastlingKingSide()) {
                addMove(Board.e1, Board.g1, Board.whiteKing, (byte) 0, Move.typeCastlingKingSide);
            } else if (game.isWhiteCastlingQueenSidePossible() && canDoWhiteCastlingQueenSide()) {
                addMove(Board.e1, Board.c1, Board.whiteKing, (byte) 0, Move.typeCastlingQueenSide);
            }
        } else {
            if (game.isBlackCastlingKingSidePossible() && canDoBlackCastlingKingSide()) {
                addMove(Board.e8, Board.g8, Board.blackKing, (byte) 0, Move.typeCastlingKingSide);
            } else if (game.isBlackCastlingQueenSidePossible() && canDoBlackCastlingQueenSide()) {
                addMove(Board.e8, Board.c8, Board.blackKing, (byte) 0, Move.typeCastlingQueenSide);
            }
        }
    }

    private boolean canDoWhiteCastlingKingSide() {
        // The fields between king and rook must be empty
        if (board[Board.f1] != Board.empty || board[Board.g1] != Board.empty)
            return false;

        // Neither of king's start field, crossed field and target field must be under attack (chess)
        return !(isWhiteCastlingFieldUnderAttack(Board.e1)
                || isWhiteCastlingFieldUnderAttack(Board.f1)
                || isWhiteCastlingFieldUnderAttack(Board.g1));

    }

    private boolean canDoWhiteCastlingQueenSide() {
        // The fields between king and rook must be empty
        if (board[Board.d1] != Board.empty || board[Board.c1] != Board.empty || board[Board.b1] != Board.empty)
            return false;

        // Neither of king's start field, crossed field and target field must be under attack (chess)
        return !(isWhiteCastlingFieldUnderAttack(Board.e1)
                || isWhiteCastlingFieldUnderAttack(Board.d1)
                || isWhiteCastlingFieldUnderAttack(Board.c1));
    }

    private boolean canDoBlackCastlingKingSide() {
        // The fields between king and rook must be empty
        if (board[Board.f8] != Board.empty || board[Board.g8] != Board.empty)
            return false;

        // Neither of king's start field, crossed field and target field must be under attack (chess)
        return !(isBlackCastlingFieldUnderAttack(Board.e8)
                || isBlackCastlingFieldUnderAttack(Board.f8)
                || isBlackCastlingFieldUnderAttack(Board.g8));

    }

    private boolean canDoBlackCastlingQueenSide() {
        // The fields between king and rook must be empty
        if (board[Board.d8] != Board.empty || board[Board.c8] != Board.empty || board[Board.b8] != Board.empty)
            return false;

        // Neither of king's start field, crossed field and target field must be under attack (chess)
        return !(isBlackCastlingFieldUnderAttack(Board.e8)
                || isBlackCastlingFieldUnderAttack(Board.d8)
                || isBlackCastlingFieldUnderAttack(Board.c8));
    }

    private boolean isWhiteCastlingFieldUnderAttack(int field) {
        // check left
        int f = field - 1;
        if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
            for (; board[f] == Board.empty; f--) ;
            byte piece = board[f];
            if (piece == Board.blackQueen || piece == Board.blackRook)
                return true;
        }

        // check up
        f = field + Board.LENGTH;
        if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
            for (; board[f] == Board.empty; f += Board.LENGTH) ;
            byte piece = board[f];
            if (piece == Board.blackQueen || piece == Board.blackRook)
                return true;
        }

        // check right
        f = field + 1;
        if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
            for (; board[f] == Board.empty; f++) ;
            byte piece = board[f];
            if (piece == Board.blackQueen || piece == Board.blackRook)
                return true;
        }

        // check up-left
        f = field + Board.LENGTH - 1;
        if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
            for (; board[f] == Board.empty; f += Board.LENGTH - 1) ;
            byte piece = board[f];
            if (piece == Board.blackQueen || piece == Board.blackBishop)
                return true;
        }

        // check up-right
        f = field + Board.LENGTH + 1;
        if ((board[f] & GameStatus.TURN_WHITE) != GameStatus.TURN_WHITE) {
            for (; board[f] == Board.empty; f += Board.LENGTH + 1) ;
            byte piece = board[f];
            if (piece == Board.blackQueen || piece == Board.blackBishop)
                return true;
        }

        // check knights
        if (board[field + Board.LENGTH - 2] == Board.blackKnight
                || board[field + 2 * Board.LENGTH - 1] == Board.blackKnight
                || board[field + 2 * Board.LENGTH + 1] == Board.blackKnight
                || board[field + Board.LENGTH + 2] == Board.blackKnight)
            return true;

        // check pawns
        if (board[field + Board.LENGTH - 1] == Board.blackPawn
                || board[field + Board.LENGTH + 1] == Board.blackPawn)
            return true;

        // check king
        //noinspection RedundantIfStatement
        if (board[field + Board.LENGTH - 1] == Board.blackKing
                || board[field + Board.LENGTH] == Board.blackKing
                || board[field + Board.LENGTH + 1] == Board.blackKing)
            return true;

        return false;
    }

    private boolean isBlackCastlingFieldUnderAttack(int field) {
        // check left
        int f = field - 1;
        if ((board[f] & GameStatus.TURN_BLACK) != GameStatus.TURN_BLACK) {
            for (; board[f] == Board.empty; f--) ;
            byte piece = board[f];
            if (piece == Board.whiteQueen || piece == Board.whiteRook)
                return true;
        }

        // check down
        f = field - Board.LENGTH;
        if ((board[f] & GameStatus.TURN_BLACK) != GameStatus.TURN_BLACK) {
            for (; board[f] == Board.empty; f -= Board.LENGTH) ;
            byte piece = board[f];
            if (piece == Board.whiteQueen || piece == Board.whiteRook)
                return true;
        }

        // check right
        f = field + 1;
        if ((board[f] & GameStatus.TURN_BLACK) != GameStatus.TURN_BLACK) {
            for (; board[f] == Board.empty; f++) ;
            byte piece = board[f];
            if (piece == Board.whiteQueen || piece == Board.whiteRook)
                return true;
        }

        // check down-left
        f = field - Board.LENGTH - 1;
        if ((board[f] & GameStatus.TURN_BLACK) != GameStatus.TURN_BLACK) {
            for (; board[f] == Board.empty; f = f - Board.LENGTH - 1) ;
            byte piece = board[f];
            if (piece == Board.whiteQueen || piece == Board.whiteBishop)
                return true;
        }

        // check down-right
        f = field - Board.LENGTH + 1;
        if ((board[f] & GameStatus.TURN_BLACK) != GameStatus.TURN_BLACK) {
            for (; board[f] == Board.empty; f = f - Board.LENGTH + 1) ;
            byte piece = board[f];
            if (piece == Board.whiteQueen || piece == Board.whiteBishop)
                return true;
        }

        // check knights
        if (board[field - Board.LENGTH - 2] == Board.whiteKnight
                || board[field - 2 * Board.LENGTH - 1] == Board.whiteKnight
                || board[field - 2 * Board.LENGTH + 1] == Board.whiteKnight
                || board[field - Board.LENGTH + 2] == Board.whiteKnight)
            return true;

        // check pawns
        if (board[field - Board.LENGTH - 1] == Board.whitePawn
                || board[field - Board.LENGTH + 1] == Board.whitePawn)
            return true;

        // check king
        //noinspection RedundantIfStatement
        if (board[field - Board.LENGTH - 1] == Board.whiteKing
                || board[field - Board.LENGTH] == Board.whiteKing
                || board[field - Board.LENGTH + 1] == Board.whiteKing)
            return true;

        return false;
    }

    private void addMove(final int fromField, final int toField, final byte movingPiece, final byte capturedPiece, final byte moveType) {
        int move = Move.create((byte) fromField, (byte) toField, capturedPiece, moveType);

        moveSorter.addMove(move, fromField, toField, movingPiece, capturedPiece);
    }

}
