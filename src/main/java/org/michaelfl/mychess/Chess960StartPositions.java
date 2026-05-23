package org.michaelfl.mychess;

import org.jspecify.annotations.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Static lookup table for all 960 Chess960 / Fischer Random starting
 * positions in Scharnagl's canonical numbering (IDs {@code 0..959}).
 *
 * <p>The position data is loaded once from {@code /chess960_fens.csv} on the
 * classpath. The CSV format is {@code id,backrank,fen} per line, no header
 * row. The list is derived from Mark Weeks's reference table
 * (mark-weeks.com / chess960.net), which is the most widely-cited source for
 * the Scharnagl numbering in the chess-engine community. The standard chess
 * starting position has Scharnagl ID {@value #STANDARD_CHESS_ID}.
 *
 * <p>The CSV resource on disk carries classical {@code KQkq} castling-rights
 * notation, inherited from Mark Weeks. At load time {@link #toShredderStartFen}
 * rewrites every FEN to Shredder notation (rook-file letters, e.g.
 * {@code HAha} for standard chess, {@code HFhf} for position 0). The
 * Shredder form is what cutechess and other 960-aware GUIs actually send
 * via {@code position fen ...}, so emitting it here keeps the data
 * downstream-ready without per-call conversion.
 *
 * @author Michael Fleischhauer
 */
public final class Chess960StartPositions {

    /** Total number of distinct Chess960 starting positions. */
    public static final int COUNT = 960;

    /** Scharnagl ID for the standard chess starting position (RNBQKBNR). */
    public static final int STANDARD_CHESS_ID = 518;

    private static final String RESOURCE_PATH = "/chess960_fens.csv";

    private static final String[] FENS = loadFens();

    private static final Random DEFAULT_RANDOM = new Random();

    private Chess960StartPositions() {
        // class cannot be instantiated
    }

    /**
     * Returns the starting FEN for the given Scharnagl ID.
     *
     * @throws IllegalArgumentException if {@code id} is not in {@code [0, COUNT)}
     */
    public static String fenById(int id) {
        if (id < 0 || id >= COUNT) {
            throw new IllegalArgumentException(
                    "Chess960 ID must be in [0, " + COUNT + "), got " + id);
        }

        return FENS[id];
    }

    /**
     * Returns a random Chess960 starting FEN using a process-wide default
     * {@link Random}. The standard chess starting position (Scharnagl ID
     * {@value #STANDARD_CHESS_ID}) is excluded — drawing it would defeat the
     * purpose of starting a 960 game. The remaining 959 positions are
     * uniformly distributed.
     *
     * <p>Convenient but non-deterministic; for reproducible behavior in tests
     * use {@link #randomFen(Random)}.
     */
    @SuppressWarnings("unused")
    public static String randomFen() {
        return randomFen(DEFAULT_RANDOM);
    }

    /**
     * Returns a random Chess960 starting FEN using the supplied {@link Random},
     * with the standard chess starting position excluded. See {@link #randomFen()}
     * for the rationale.
     */
    public static String randomFen(Random rng) {
        int id;
        do {
            id = rng.nextInt(COUNT);
        } while (id == STANDARD_CHESS_ID);

        return FENS[id];
    }

    private static String[] loadFens() {
        try (InputStream in = Chess960StartPositions.class.getResourceAsStream(RESOURCE_PATH);
             var reader = openReader(in)) {

            String[] fens = reader.lines()
                    .map(Chess960StartPositions::extractFen)
                    .toArray(String[]::new);

            if (fens.length != COUNT) {
                throw new IllegalStateException(
                        "Expected " + COUNT + " entries in " + RESOURCE_PATH + ", got " + fens.length);
            }
            return fens;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    private static BufferedReader openReader(InputStream in) {
        if (in == null) {
            throw new IllegalStateException("Resource not found on classpath: " + RESOURCE_PATH);
        }

        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * Returns the Shredder-FEN extracted from one {@code id,backrank,fen}
     * CSV line. The {@code id} and {@code backrank} columns are not
     * interpreted — they exist in the file as self-describing metadata for
     * humans reading the CSV; the FEN is the authoritative content. The
     * caller relies on the CSV being valid (the integrity test in
     * {@code Chess960StartPositionsTest} catches violations).
     */
    private static String extractFen(String line) {
        String[] parts = line.split(",");
        if (parts.length != 3) {
            throw new IllegalStateException(
                    "Malformed CSV line in " + RESOURCE_PATH + ": " + line);
        }

        return toShredderStartFen(parts[2]);
    }

    /**
     * Rewrites a Chess960 starting FEN from classical {@code KQkq} castling
     * notation to Shredder notation by inspecting which files the rooks
     * stand on. Output order is kingside-rook-file then queenside-rook-file,
     * uppercase for white, lowercase for black — exactly what cutechess and
     * other 960-aware GUIs emit on {@code position fen}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code rnbqkbnr/.../RNBQKBNR w KQkq - 0 1} → {@code ... w HAha - 0 1}</li>
     *   <li>{@code bbqnnrkr/.../BBQNNRKR w KQkq - 0 1} → {@code ... w HFhf - 0 1}</li>
     *   <li>{@code rkrnnqbb/.../RKRNNQBB w KQkq - 0 1} → {@code ... w CAca - 0 1}</li>
     * </ul>
     *
     * <p>Strict on input: the back rank must be an uncompressed 8-character
     * string with one king between two rooks, and the castling field must
     * literally be {@code KQkq}. Intended for the canonical 960 starting
     * positions, not for arbitrary mid-game FENs.
     */
    static String toShredderStartFen(String classicFen) {
        String[] fields = classicFen.split(" ");
        String whiteBackRank = extractWhiteBackRankFromFen(classicFen, fields);

        int kingFile = whiteBackRank.indexOf('K');
        int queensideRookFile = whiteBackRank.indexOf('R');
        int kingsideRookFile = whiteBackRank.lastIndexOf('R');
        if (kingFile < 0 || queensideRookFile < 0 || queensideRookFile == kingsideRookFile) {
            throw new IllegalArgumentException(
                    "Back rank must contain one K and two Rs, got '" + whiteBackRank
                            + "': " + classicFen);
        }
        if (queensideRookFile > kingFile || kingFile > kingsideRookFile) {
            throw new IllegalArgumentException(
                    "King must sit between two rooks, got '" + whiteBackRank
                            + "': " + classicFen);
        }

        char kingsideUpper = (char) ('A' + kingsideRookFile);
        char queensideUpper = (char) ('A' + queensideRookFile);
        char kingsideLower = Character.toLowerCase(kingsideUpper);
        char queensideLower = Character.toLowerCase(queensideUpper);
        String castlingRights = "" + kingsideUpper + queensideUpper + kingsideLower + queensideLower;

        return String.join(" ",
                fields[0], fields[1], castlingRights,
                fields[3], fields[4], fields[5]);
    }

    private static @NonNull String extractWhiteBackRankFromFen(String fen, String[] fields) {
        if (fields.length != 6) {
            throw new IllegalArgumentException(
                    "Expected 6 FEN fields, got " + fields.length + ": " + fen);
        }
        if (!"KQkq".equals(fields[2])) {
            throw new IllegalArgumentException(
                    "Expected 'KQkq' castling field, got '" + fields[2] + "': " + fen);
        }

        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException(
                    "Expected 8 ranks, got " + ranks.length + ": " + fen);
        }

        String whiteBackRank = ranks[7];
        if (whiteBackRank.length() != 8) {
            throw new IllegalArgumentException(
                    "Expected an uncompressed 8-character back rank, got '"
                            + whiteBackRank + "': " + fen);
        }

        return whiteBackRank;
    }
}
