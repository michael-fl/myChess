package org.michaelfl.mychess;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft for Chess960: the move generator, {@code makeMove} and {@code revertMove} must produce
 * exactly as many legal leaf positions as an independent reference generator.
 *
 * <p>The positions come from myChess's own Chess960 games on lichess: every start position at
 * depth 4, and for every castling move in those games the position one ply before it at depth 3,
 * so the castling move and its undo lie inside the counted tree. The positions are reached the
 * way the UCI handler reaches them, from the start FEN via {@link Fen#importChess960FEN} and the
 * UCI moves replayed through {@link Game}. The reference counts in {@code perft960.csv} were
 * computed with python-chess ({@code myChess-Lab/scripts/perft960-reference.py}).
 *
 * <p>Besides the count, every case checks that the board is restored exactly after the
 * enumeration: a castling undo that leaves a piece behind would show up there even when the
 * count happens to match.
 *
 * @author Michael Fleischhauer
 */
class Chess960PerftTest {

    private static final String RESOURCE = "/perft960.csv";

    static Stream<Arguments> cases() {
        var stream = Objects.requireNonNull(Chess960PerftTest.class.getResourceAsStream(RESOURCE), RESOURCE);
        List<Arguments> cases = new ArrayList<>();

        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] fields = line.split(";", -1);
                cases.add(Arguments.of(fields[0], fields[1], fields[2], Integer.parseInt(fields[3]),
                        Long.parseLong(fields[4])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void perft_matchesTheReferenceAndRestoresTheBoard(String label, String startFen, String uciMoves,
                                                      int depth, long expected) {
        Board board = positionAfter(startFen, uciMoves);
        String fenBefore = board.exportFEN();
        String keyBefore = board.calculatePositionKey();

        long nodes = PerftTest.perft(board, depth);

        assertEquals(expected, nodes, label + ": leaf count at depth " + depth + " must match python-chess");
        assertEquals(fenBefore, board.exportFEN(), label + ": the board must be restored after the enumeration");
        assertEquals(keyBefore, board.calculatePositionKey(), label + ": the position key must be restored too");
    }

    /** Replays {@code uciMoves} from {@code startFen} the way {@link UciHandler} does. */
    private static Board positionAfter(String startFen, String uciMoves) {
        Board start = Fen.importChess960FEN(startFen);

        if (uciMoves.isBlank()) {
            return start;
        }

        var game = new Game(Game.standardConfig(), start);

        try {
            for (String uci : uciMoves.trim().split("\\s+")) {
                game.makeMove(UciMoveParser.parse(uci, game.getBoard()));
            }

            return game.getBoard();
        } finally {
            game.shutdown();
        }
    }
}
