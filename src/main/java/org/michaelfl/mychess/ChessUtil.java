package org.michaelfl.mychess;

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
        int row = field / Board.LENGTH;
        if (row < 2 || row > 9)
            return -1;
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

        String s = moveToString(Move.getFromField(move), Move.getToField(move));

        byte moveType = Move.getMoveType(move);
        if (moveType == Move.typePawnPromotionKnight)
            s += "N";
        else if (moveType == Move.typePawnPromotionQueen)
            s += "Q";
        else if (moveType == Move.typePawnPromotionRook)
            s += "R";
        else if (moveType == Move.typePawnPromotionBishop)
            s += "B";

        return s;
    }

    public static String movesToString(int[] moves, int length) {
        StringBuilder buf = new StringBuilder();
        buf.append(length).append('#');

        for (int i = 0; i < length; i ++) {
            if (i > 0)
                buf.append(" ");
            buf.append(ChessUtil.moveToString(moves[i]));
        }

        return buf.toString();
    }

    public static String weightToString(float weight) {
        if (weight == WeightingFunction.ILLEGAL_WEIGHT)
            return "illegal";
        if (weight >= WeightingFunction.CHECKMATE_WEIGHT)
            return "M" + (Math.round(weight - WeightingFunction.CHECKMATE_WEIGHT));
        if (weight <= -WeightingFunction.CHECKMATE_WEIGHT)
            return "-M" + (Math.round(-weight - WeightingFunction.CHECKMATE_WEIGHT));

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
}
