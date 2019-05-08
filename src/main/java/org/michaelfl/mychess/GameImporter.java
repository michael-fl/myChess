package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.InputStreamReader;

final class GameImporter {

    static Game importGame(String gameNotation) {
        if (gameNotation.startsWith("[["))
            return new SimpleNotationImporter(gameNotation).importGame();

        throw new IllegalArgumentException("Unknown game notation");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Enter game notation (end with / or ]]):");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder buf = new StringBuilder();
        String line;

        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.endsWith("/")) {
                line = line.substring(0, line.length() - 1);
                buf.append(line);
                break;
            }

            buf.append(line).append(' ');
            if (line.endsWith("]]"))
                break;
        }

        String gameNotation = buf.toString().trim();
        if (gameNotation.isEmpty())
            return;

        Game game = importGame(gameNotation);
        game.print();
    }
}
