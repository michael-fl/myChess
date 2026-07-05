package org.michaelfl.mychess;

/**
 * Builds a {@link Game} from a notation string. {@link #importerFor(String)}
 * dispatches to {@link SimpleNotationImporter} for the {@code [[...]]} form
 * or {@link PGNImporter} for standard PGN.
 *
 * @author Michael Fleischhauer
 */
public interface GameImporter {
    Game importGame();
    Game importGame(GameConfig config);

    static GameImporter importerFor(String gameNotation) {
        if (gameNotation.startsWith("[[")) {
            return new SimpleNotationImporter(gameNotation);
        } else {
            var pgn = Pgn.parse(gameNotation).findFirst();
            return new PGNImporter(pgn.orElseThrow(() -> new IllegalArgumentException("No PGN given")));
        }
    }

    /**
     * Build a {@link PGNImporter} that plays {@code gameNotation} from an
     * explicitly given {@code startFen}, overriding any {@code [FEN "..."]}
     * tag pair carried in the PGN header. Chess960 vs. standard castling
     * rules are auto-detected from {@code startFen}: back ranks matching
     * the classical {@code RNBQKBNR} layout with {@code KQkq} castling
     * shorthand are treated as standard chess, anything else (non-standard
     * back rank or Shredder / X-FEN file-letter castling notation) as
     * Chess960. Only supports PGN input — the {@code [[...]]}
     * simple-notation form does not carry a starting position separate
     * from its notation.
     *
     * <p>{@code startFen} is validated via
     * {@link Fen#requireStartFen(String)}: only genuine game-starting
     * positions are accepted (standard chess or one of the 960 Chess960
     * setups, in either classical {@code KQkq} or Shredder spelling).
     * Mid-game FENs must go through the PGN {@code [FEN "..."]} header
     * instead — that path accepts any legal FEN.
     *
     * @param gameNotation the PGN move text, optionally with a header
     * @param startFen     the starting position as a FEN string
     * @throws IllegalArgumentException when {@code gameNotation} is
     *         {@code null}, empty, or parses to zero PGNs; or when
     *         {@code startFen} is not a valid game starting position
     */
    static GameImporter importerFor(String gameNotation, String startFen) {
        var pgn = Pgn.parse(gameNotation).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No PGN given"));

        return new PGNImporter(pgn, startFen);
    }
}
