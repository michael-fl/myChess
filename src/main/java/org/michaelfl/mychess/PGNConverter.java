package org.michaelfl.mychess;

/**
 * Converts a game written in the project's long-algebraic {@code [[...]]} format
 * (see {@link SimpleNotationImporter}) into a standard PGN move-text string
 * (the format consumed by {@link Pgn#parse(String)}).
 *
 * <p>Each ply is replayed on a fresh {@link Board}; for every move the
 * corresponding short algebraic notation is produced via
 * {@link Board#moveToShortNotation(Move)} and emitted with PGN-style move
 * numbers. Castling is written as {@code O-O} / {@code O-O-O}. The result is
 * wrapped at roughly 90 characters per line so it pastes cleanly into source.
 *
 * <p>Example:
 * <pre>{@code
 * PGNConverter.toPGN("[[e2-e4 e7-e5 g1-f3 b8-c6]]")
 *   // "1. e4 e5 2. Nf3 Nc6"
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class PGNConverter {

    private static final int LINE_WIDTH = 90;

    private PGNConverter() {
        // utility class
    }

    /**
     * Convert a {@code [[...]]} long-algebraic game string to PGN move text.
     *
     * @param legacyInput a non-null {@code [[move move ...]]} game as accepted by
     *                    {@link SimpleNotationImporter}
     * @return PGN move-text (no tag-pair section, no game-termination marker)
     */
    public static String toPGN(String legacyInput) {
        var sourceGame = new SimpleNotationImporter(legacyInput).importGame();
        var statusStack = sourceGame.getBoard().getGameStatusStackCopy();

        var board = Board.createNewGame();
        var tokens = new StringBuilder();
        int plyCount = 0;

        for (int i = 1; i < statusStack.size(); i++) {
            int packedMove = statusStack.get(i).getLastMove();
            var san = board.moveToShortNotation(new Move(packedMove)).toString();
            san = san.replace("0-0-0", "O-O-O").replace("0-0", "O-O");

            if (plyCount % 2 == 0) {
                if (!tokens.isEmpty()) {
                    tokens.append(' ');
                }
                tokens.append(plyCount / 2 + 1).append(". ").append(san);
            } else {
                tokens.append(' ').append(san);
            }

            board.makeMove(packedMove);
            plyCount++;
        }

        return wrap(tokens.toString());
    }

    private static String wrap(String text) {
        var out = new StringBuilder();
        var line = new StringBuilder();
        for (var token : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + token.length() > LINE_WIDTH) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(line);
                line.setLength(0);
            }

            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(token);
        }

        if (!line.isEmpty()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(line);
        }

        return out.toString();
    }

}
