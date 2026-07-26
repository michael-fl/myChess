package org.michaelfl.mychess;

import org.michaelfl.mychess.Pgn.IOExceptionWrapper;
import org.michaelfl.mychess.Pgn.IllegalPGNException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Parsed PGN game: the seven-tag-roster headers plus a list of
 * {@link MoveDescription}s. {@link #parse(String)} streams one or more games
 * out of a string or {@link java.io.Reader}; consume the moves via
 * {@link PGNImporter} to drive an actual {@link Game}.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("unused")
public final class Pgn {

    private static final String DRAW_TOKEN = "1/2-1/2";

    /** PGN tag name for the starting position FEN. */
    public static final String TAG_FEN = "FEN";

    private static final String EN_PASSANT = "e.p.";

    /** PGN tag name for the chess variant (e.g. "Chess960", "fischerandom"). */
    public static final String TAG_VARIANT = "Variant";

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
    public final Map<String, String> tags;

    private Pgn(String notation, Result result, List<MoveDescription> moves, Map<String, String> tags) {
        this.notation = notation;
        this.result = result;
        this.moves = Collections.unmodifiableList(moves);
        this.tags = Collections.unmodifiableMap(tags);
    }

    @Override
    public String toString() {
        return notation;
    }

    /**
     * Value of the given PGN tag pair, or {@code null} if the header did not
     * contain that tag.
     */
    public String getTag(String key) {
        return tags.get(key);
    }

    /**
     * Starting-position FEN from the {@code [FEN "..."]} tag pair, or
     * {@code null} if the PGN uses the standard initial position.
     */
    public String getStartFen() {
        return tags.get(TAG_FEN);
    }

    /**
     * {@code true} if the {@code [Variant "..."]} tag pair indicates a
     * Chess960 / Fischer-random game (the check is case-insensitive and
     * matches on "960" or "fischer" substrings, covering the common
     * cutechess and Lichess spellings).
     */
    public boolean isChess960() {
        var variant = tags.get(TAG_VARIANT);
        if (variant == null) {
            return false;
        }

        var v = variant.toLowerCase();
        return v.contains("960") || v.contains("fischer");
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
        private final Map<String, String> tags = new LinkedHashMap<>();
        private int startOfMoveTextSection = -1;

        void addTagPair(String line) {
            buf.append(line).append('\n');
            parseTagPair(line);
        }

        /**
         * Parse {@code [Key "Value"]} into the tags map. The line is
         * already known to start with {@code [} and end with {@code ]}
         * (validated by the caller). Malformed tag pairs are silently
         * skipped: the raw text remains in {@link #buf}, so callers
         * that need the original notation are unaffected.
         */
        private void parseTagPair(String line) {
            var contents = line.substring(1, line.length() - 1);
            int quoteStart = contents.indexOf('"');
            if (quoteStart < 0) {
                return;
            }

            var key = contents.substring(0, quoteStart).trim();
            if (key.isEmpty()) {
                return;
            }

            int quoteEnd = contents.lastIndexOf('"');
            if (quoteEnd <= quoteStart) {
                return;
            }

            var value = contents.substring(quoteStart + 1, quoteEnd);
            tags.put(key, value);
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
                if (isComment(token) || isVariation(token) || isEnPassant(token) || isNAG(token)) {
                    continue; // Ignore comments
                }
                if (i == 0 && token.endsWith("...")) {
                    // PGN starts with black continuation
                    int moveNo = parseMoveNoFromBlackContinuation(token);
                    if (moveNo != expectedMoveNo) {
                        System.err.println("Wrong move no " + moveNo + ". Expected " + expectedMoveNo + ": " + this);
                    }
                    i++;
                } else if (isGameTerminationMarker(token)) {
                    if (i % 3 == 1) {
                        throw new IllegalPGNException("Wrong position for game termination marker: " + this);
                    }
                    result = parseGameTerminationMarker(token);
                    break;
                } else if (i % 3 == 0) { // move no
                    int moveNo = parseMoveNo(token);
                    if (moveNo != expectedMoveNo) {
                        System.err.println("Wrong move no " + moveNo + ". Expected " + expectedMoveNo + ": " + this);
                    }
                } else if (i % 3 == 1) { // white move
                    moves.add(MoveDescription.fromString(token, GameStatus.TURN_WHITE));
                } else if (i % 3 == 2) { // black move
                    if (Character.isDigit(token.charAt(0))) {
                        int moveNo = parseMoveNoFromBlackContinuation(token);
                        if (moveNo != expectedMoveNo) {
                            System.err.println("Wrong move no " + moveNo + ". Expected " + expectedMoveNo + ": " + this);
                        }
                        continue;
                    }
                    moves.add(MoveDescription.fromString(token, GameStatus.TURN_BLACK));
                    expectedMoveNo++;
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

            return new Pgn(buf.toString(), result, moves, tags);
        }

        private boolean isComment(String token) {
            return token.startsWith("{") || token.startsWith(";");
        }

        private boolean isVariation(String token) {
            return token.startsWith("(");
        }

        private boolean isEnPassant(String token) {
            return token.equals(EN_PASSANT);
        }

        private boolean isNAG(String token) {
            return token.startsWith("$");
        }

        private int parseMoveNo(String token) {
            int i = token.indexOf('.');
            if (i != token.length() - 1) {
                throw new IllegalPGNException("Move no expected: " + this);
            }
            return Integer.parseInt(token.substring(0, i));
        }

        private int parseMoveNoFromBlackContinuation(String token) {
            if (!token.endsWith("...")) {
                throw new IllegalPGNException("Continuation expected: " + this);
            }
            return Integer.parseInt(token.substring(0, token.length() - 3));
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
                    if (!ignoreErrors || !(e instanceof IllegalPGNException || e instanceof IllegalArgumentException)) {
                        lineIter.close();
                        Log.error("PGN parse failed:\n" + pgnBuilder, e);
                        throw e;
                    } else {
                        // ignore error and continue with next PGN
                        Log.error("PGN parse error (ignored, continuing):\n" + pgnBuilder, e);
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
                if (!line.isEmpty() && !line.startsWith("%")) { // skip empty lines
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
                if (c == 'e' && lookAhead(i, EN_PASSANT)) {
                    pos = skipWhitespace(i + EN_PASSANT.length());
                    return EN_PASSANT;
                }
                if (c == '.' && (i == length - 1 || buf.charAt(i + 1) != '.')) {
                    var token = buf.substring(pos, i + 1);
                    pos = skipWhitespace(i + 1);
                    return token;
                }
                if (c == '$') {
                    if (i > pos) {
                        return extractCurrentToken(i);
                    }

                    return extractGlyph();
                }

                if (c == '{') { // Start of comment
                    if (i > pos) {
                        return extractCurrentToken(i);
                    }

                    return extractComment();
                }

                if (c == ';') { // Rest of line comment
                    if (i > pos) {
                        return extractCurrentToken(i);
                    }

                    return extractLineComment();
                }

                if (c == '(') { // Start of variant
                    if (i > pos) {
                        return extractCurrentToken(i);
                    }

                    return extractVariation();
                }
            }

            var token = buf.substring(pos);
            pos = length;
            return token;
        }

        @SuppressWarnings("SameParameterValue")
        private boolean lookAhead(int index, String term) {
            return buf.indexOf(term, index) == index;
        }

        private String extractCurrentToken(int index) {
            var token = buf.substring(pos, index);
            pos = index;
            return token;
        }

        private String extractComment() {
            int i2 = buf.indexOf("}", pos + 1);
            if (i2 < 0) {
                throw new IllegalPGNException("Comment not closed");
            }
            var token = buf.substring(pos, i2 + 1);
            pos = skipWhitespace(i2 + 1);
            return token;
        }

        private String extractLineComment() {
            int i2 = findEOL();
            var token = i2 != -1 ? buf.substring(pos, i2) : buf.substring(pos);
            pos = skipWhitespace(i2);
            return token;
        }

        private String extractVariation() {
            int bracesCount = 0;

            for (int i = pos; i < length; i++) {
                var c = buf.charAt(i);
                if (c == '(') {
                    bracesCount++;
                } else if (c == ')') {
                    bracesCount = Math.max(0, bracesCount - 1);
                }
                if (bracesCount == 0) {
                    var token = buf.substring(pos, i + 1);
                    pos = skipWhitespace(i + 1);
                    return token;
                }
            }

            throw new IllegalPGNException("Variant not closed: " + this);
        }

        private String extractGlyph() {
            for (int i = pos + 1; i < length; i++) {
                var c = buf.charAt(i);
                if (!Character.isDigit(c)) {
                    var token = buf.substring(pos, i);
                    pos = skipWhitespace(i);
                    return token;
                }
            }

            var token = buf.substring(pos);
            pos = length;

            return token;
        }

        private int findEOL() {
            for (int i = pos; i < length; i++) {
                var c = buf.charAt(i);
                if (c == '\n' || c == '\r') {
                    return i;
                }
            }

            return -1;
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
