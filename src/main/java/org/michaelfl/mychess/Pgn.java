package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("unused")
public final class Pgn {

    private static final String DRAW_TOKEN = "1/2-1/2";

    public static final class IllegalPGNException extends RuntimeException {
        IllegalPGNException(String message) {
            super(message);
        }
    }

    public static final class IOExceptionWrapper extends RuntimeException {
        public IOExceptionWrapper(IOException cause) {
            super(cause);
        }
    }

    public enum Result {
        WHITE_WINS,
        BLACK_WINS,
        DRAW,
        ONGOING,
        UNKNOWN
    }

    private final String notation;
    public final Result result;
    public final List<MoveDescription> moves;

    private Pgn(String notation, Result result, List<MoveDescription> moves) {
        this.notation = notation;
        this.result = result;
        this.moves = Collections.unmodifiableList(moves);
    }

    @Override
    public String toString() {
        return notation;
    }

    public static Stream<Pgn> parse(String pgn) throws IllegalPGNException {
        return parse(pgn, false);
    }

    public static Stream<Pgn> parse(String pgn, boolean ignoreErrors) throws IllegalPGNException {
        return parse(new BufferedReader(new StringReader(pgn)), ignoreErrors);
    }

    public static Stream<Pgn> parse(File pgnFile, boolean ignoreErrors) throws IllegalPGNException {
        try {
            return parse(new BufferedReader(new FileReader(pgnFile, StandardCharsets.UTF_8)), ignoreErrors);
        } catch (IOException e) {
            throw new IOExceptionWrapper(e);
        }
    }

    public static Stream<Pgn> parse(BufferedReader pgnReader, boolean ignoreErrors) throws IllegalPGNException {
        try {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new PGNIterator(pgnReader, ignoreErrors), 0), false);
        } catch (IOException e) {
            throw new IOExceptionWrapper(e);
        }
    }

    private static class Builder {
        private final StringBuilder buf = new StringBuilder();
        private int startOfMoveTextSection = -1;

        void addTagPair(String line) {
            buf.append(line).append('\n');
        }

        void addMovesLine(String line) {
            if (startOfMoveTextSection == -1) {
                if (!buf.isEmpty()) {
                    buf.append('\n');
                }
                startOfMoveTextSection = buf.length();
            }
            buf.append(line).append('\n');
        }

        Pgn build() {
            if (startOfMoveTextSection == -1) {
                throw new IllegalPGNException("No moves specified: " + this);
            }

            var moves = new ArrayList<MoveDescription>();
            var tokenizer = new Tokenizer(buf, startOfMoveTextSection);
            String token;
            Result result = null;
            int expectedMoveNo = 1;
            int i = 0;

            // Tokens must come in triples: Move no, white move, black move
            while ((token = tokenizer.nextToken()) != null) {
                if (isGameTerminationMarker(token)) {
                    if (i % 3 == 1) {
                        throw new IllegalPGNException("Wrong position for game termination marker: " + this);
                    }
                    result = parseGameTerminationMarker(token);
                    break;
                } else if (i % 3 == 0) { // move no
                    int moveNo = parseMoveNo(token);
                    if (moveNo != expectedMoveNo) {
                        throw new IllegalPGNException("Wrong move no " + moveNo + ". Expected " + expectedMoveNo + ": " + this);
                    }
                    expectedMoveNo++;
                } else if (i % 3 == 1) { // white move
                    if (!"..".equals(token)) {
                        moves.add(MoveDescription.fromString(token, GameStatus.TURN_WHITE));
                    }
                } else if (i % 3 == 2) { // black move
                    if ("..".equals(token)) {
                        throw new IllegalPGNException("Wrong move notation: " + this);
                    }
                    moves.add(MoveDescription.fromString(token, GameStatus.TURN_BLACK));
                }

                i++;
            }

            if (result == null) {
                // Game termination marker missing
                result = Result.ONGOING;
            }
            if (moves.isEmpty()) {
                throw new IllegalPGNException("No moves defined: " + this);
            }

            return new Pgn(buf.toString(), result, moves);
        }

        private int parseMoveNo(String token) {
            int i = token.indexOf('.');
            if (i != token.length() - 1) {
                throw new IllegalPGNException("Move no expected: " + this);
            }
            return Integer.parseInt(token.substring(0, i));
        }

        private static boolean isGameTerminationMarker(String token) {
            return "1-0".equals(token) || "0-1".equals(token) || DRAW_TOKEN.equals(token) || "*".equals(token);
        }

        private Result parseGameTerminationMarker(String token) {
            return switch (token) {
                case "1-0" -> Result.WHITE_WINS;
                case "0-1" -> Result.BLACK_WINS;
                case DRAW_TOKEN -> Result.DRAW;
                case "*" -> Result.UNKNOWN;
                default -> throw new IllegalPGNException("Illegal game termination marker: " + this);
            };
        }

        @Override
        public String toString() {
            return buf.toString();
        }
    }

    private static class PGNIterator implements Iterator<Pgn> {

        private final LineReaderIterator lineIter;
        private final boolean ignoreErrors;
        private Pgn nextPgn;

        private PGNIterator(BufferedReader reader, boolean ignoreErrors) throws IOException {
            this.lineIter = new LineReaderIterator(reader);
            this.ignoreErrors = ignoreErrors;
            this.nextPgn = readNextPgn();
        }

        @Override
        public boolean hasNext() {
            return nextPgn != null;
        }

        @Override
        public Pgn next() {
            if (nextPgn == null) {
                throw new NoSuchElementException();
            }
            var pgn = nextPgn;
            nextPgn = readNextPgn();
            return pgn;
        }

        private Pgn readNextPgn() {
            while (true) {
                var pgnBuilder = new Builder();
                try {
                    return readOnePgn(pgnBuilder);
                } catch (RuntimeException e) {
                    System.err.println(pgnBuilder);
                    if (!ignoreErrors || !(e instanceof IllegalPGNException || e instanceof IllegalArgumentException)) {
                        lineIter.close();
                        throw e;
                    } else {
                        // ignore error and continue with next PGN
                        e.printStackTrace();
                    }
                }
            }
        }

        private Pgn readOnePgn(Builder pgnBuilder) {
            var haveReadMoves = false;

            while (lineIter.hasNext()) {
                var line = lineIter.next();

                if (line.startsWith("[")) {
                    if (!line.endsWith("]")) {
                        throw new IllegalPGNException("Tag pair not terminated with an ]: " + pgnBuilder);
                    }
                    if (haveReadMoves) {
                        throw new IllegalPGNException("Game termination marker missing: " + pgnBuilder);
                    }
                    pgnBuilder.addTagPair(line);
                } else {
                    haveReadMoves = true;
                    pgnBuilder.addMovesLine(line);
                    if (line.endsWith("1-0") || line.endsWith("0-1") || line.endsWith(DRAW_TOKEN) || line.endsWith("*")) {
                        // Game termination marker found
                        return pgnBuilder.build();
                    }
                }
            }

            if (!haveReadMoves) {
                return null;
            }

            // EOF reached without termination marker
            return pgnBuilder.build();
        }
    }

    private static class LineReaderIterator implements Iterator<String> {

        private final BufferedReader reader;
        private String nextLine;

        private LineReaderIterator(BufferedReader reader) throws IOException {
            this.reader = reader;
            this.nextLine = readNextLine();
        }

        void close() {
            try {
                reader.close();
            } catch (IOException _) {
                // ignore
            }
        }

        @Override
        public boolean hasNext() {
            return nextLine != null;
        }

        @Override
        public String next() {
            try {
                if (nextLine == null) {
                    throw new NoSuchElementException();
                }
                var line = nextLine;
                nextLine = readNextLine();

                return line;
            } catch (IOException e) {
                close();
                throw new IOExceptionWrapper(e);
            }
        }

        private String readNextLine() throws IOException {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) { // skip empty lines
                    return line;
                }
            }

            close();
            return null; // EOF reached
        }
    }

    private static final class Tokenizer {
        final StringBuilder buf;
        final int length;
        int pos;

        Tokenizer(StringBuilder buf, int startPos) {
            this.buf = buf;
            this.length = buf.length();
            this.pos = startPos;
        }

        String nextToken() {
            if (pos == length) {
                return null;
            }

            for (int i = pos; i < length; i++) {
                var c = buf.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r') {
                    var token = buf.substring(pos, i);
                    pos = skipWhitespace(i + 1);
                    return token;
                }
                if (c == '.' && (buf.charAt(i - 1) != '.' || buf.charAt(i + 1) != '.')) {
                    var token = buf.substring(pos, i + 1);
                    pos = skipWhitespace(i + 1);
                    return token;
                }
            }

            var token = buf.substring(pos);
            pos = length;
            return token;
        }

        private int skipWhitespace(int position) {
            for (int i = position; i < length; i++) {
                var c = buf.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r') {
                    return i;
                }
            }

            return length;
        }
    }

}
