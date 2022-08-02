package org.michaelfl.mychess;

import java.util.Collection;

public final class ChessUtil {

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
            (int) colChar - (int) 'a',
            (int) rowChar - (int) '1'
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
        return moveToString(move.getMove(), board);
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

    public static String weightToString(float weight) {
        return weightToString(weight, 1f);
    }

    public static String weightToString(float weight, float factor) {
        if (WeightingFunction.isIllegalWeight(weight))
            return "illegal";
        weight *= factor;
        if (weight >= WeightingFunction.CHECKMATE_WEIGHT_LOW)
            return "M" + (Math.round(WeightingFunction.CHECKMATE_WEIGHT_HIGH - weight));
        if (weight <= -WeightingFunction.CHECKMATE_WEIGHT_LOW)
            return "-M" + (Math.round(WeightingFunction.CHECKMATE_WEIGHT_HIGH + weight));

        return String.valueOf(weight);
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
    public static int getFieldNumber64(int field) {
        int row = field / Board.LENGTH - 2;
        int col = field % Board.LENGTH - 2;

        return row * 8 + col;
    }

    public static int setBit(int bitSet, int bit, boolean set) {
        return set ? bitSet | bit : bitSet & ~bit;
    }

    public static int setBit(int bitSet, int bit) {
        return bitSet | bit;
    }

    @SuppressWarnings("unused")
    public static int clearBit(int bitSet, int bit) {
        return bitSet & ~bit;
    }

    public static byte symbolToPiece(char symbol, int turn) {
        final boolean isWhite = turn == GameStatus.TURN_WHITE;

        switch (symbol) {
            case 'P': return isWhite ? Board.whitePawn : Board.blackPawn;
            case 'N': return isWhite ? Board.whiteKnight : Board.blackKnight;
            case 'B': return isWhite ? Board.whiteBishop : Board.blackBishop;
            case 'R': return isWhite ? Board.whiteRook : Board.blackRook;
            case 'Q': return isWhite ? Board.whiteQueen : Board.blackQueen;
            case 'K': return isWhite ? Board.whiteKing : Board.blackKing;
            default:
                throw new IllegalArgumentException("Unknown symbol: " + symbol);
        }
    }

    public static String pieceToString(byte piece) {
        switch (piece) {
            case Board.empty: return "";
            case Board.whitePawn:
            case Board.blackPawn: return "P";
            case Board.whiteKnight:
            case Board.blackKnight: return "N";
            case Board.whiteBishop:
            case Board.blackBishop: return "B";
            case Board.whiteRook:
            case Board.blackRook: return "R";
            case Board.whiteQueen:
            case Board.blackQueen: return "Q";
            case Board.whiteKing:
            case Board.blackKing: return "K";
            default:
                throw new IllegalArgumentException("Unknown piece: " + piece);
        }
    }
}
