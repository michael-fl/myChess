package org.michaelfl.mychess;

final class ChessUtil {

    private ChessUtil() {
        // class cannot be instantiated
    }

    static int getFieldFromColAndRow(int col, int row) {
        return (row + 2) * 12 + col + 2;
    }

    static int getFieldFromString(String fieldString) {
        if (fieldString.length() != 2)
            throw new IllegalArgumentException("Wrong field notation: " + fieldString);
        char colChar = fieldString.charAt(0);
        char rowChar = fieldString.charAt(1);
        if (colChar < 'a' || colChar > 'h' || rowChar < '1' || rowChar > '8')
            throw new IllegalArgumentException("Wrong field notation: " + fieldString);

        int col = (int) colChar - (int) 'a';
        int row = (int) rowChar - (int) '1';

        return getFieldFromColAndRow(col, row);
    }

    static int getRowOfField(int field) {
        int row = field / Board.LENGTH;
        if (row < 2 || row > 9)
            return -1;
        return row - 2;
    }

    static int getColOfField(int field) {
        int row = field / Board.LENGTH;
        if (row < 2 || row > 9)
            return -1;
        int col = field % Board.LENGTH;
        if (col < 2 || col > 9)
            return -1;
        return col - 2;
    }

    static String fieldToString(int field) {
        int row = getRowOfField(field);
        int col = getColOfField(field);
        return String.valueOf((char) ('a' + col)) + (row + 1);
    }

    static String moveToString(int fromField, int toField) {
        return fieldToString(fromField) + "-" + fieldToString(toField);
    }

}
