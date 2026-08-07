package org.michaelfl.mychess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds the Texel tuning hybrid dataset: the Zurichess {@code quiet-labeled}
 * base plus myChess self-play EPD lines (Chess960 and standard), combined and
 * <b>seeded-shuffled</b> so that any prefix read via a {@code load(..., limit)}
 * call is a representative mix of both sources.
 *
 * <p>Usage: {@code HybridDatasetBuilder <output> <seed> <zurichess.epd> <selfplay.epd>...}
 *
 * @author Michael Fleischhauer
 */
public final class HybridDatasetBuilder {

    private HybridDatasetBuilder() {
        // entry point
    }

    /**
     * Combine {@code base} and {@code selfplay} lines and shuffle deterministically
     * with {@code seed}. Every input line appears exactly once in the result.
     */
    static List<String> combineAndShuffle(List<String> base, List<String> selfplay, long seed) {
        var combined = new ArrayList<String>(base.size() + selfplay.size());

        combined.addAll(base);
        combined.addAll(selfplay);
        Collections.shuffle(combined, new Random(seed));

        return combined;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "usage: HybridDatasetBuilder <output> <seed> <zurichess.epd> <selfplay.epd>...");
        }

        Path output = Path.of(args[0]);
        long seed = Long.parseLong(args[1]);
        Path base = Path.of(args[2]);

        List<String> baseLines = readLines(base);
        var selfplayLines = new ArrayList<String>();

        for (int i = 3; i < args.length; i++) {
            List<String> lines = readLines(Path.of(args[i]));
            selfplayLines.addAll(lines);
            System.out.printf("  self-play %s: %,d lines%n", args[i], lines.size());
        }

        List<String> combined = combineAndShuffle(baseLines, selfplayLines, seed);
        writeLines(output, combined);

        long total = combined.size();
        double selfplayFraction = total == 0 ? 0.0 : 100.0 * selfplayLines.size() / total;
        System.out.printf("done: %,d base + %,d self-play = %,d lines -> %s (self-play %.1f%%)%n",
                baseLines.size(), selfplayLines.size(), total, output, selfplayFraction);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }

    private static void writeLines(Path output, List<String> lines) {
        try {
            Path parent = output.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.write(output, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + output, e);
        }
    }
}
