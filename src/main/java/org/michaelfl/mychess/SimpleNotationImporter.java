package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.List;

final class SimpleNotationImporter {

    private final String gameNotation;

    SimpleNotationImporter(String gameNotation) {
        gameNotation = gameNotation.trim();
        if (gameNotation.startsWith("[["))
            gameNotation = gameNotation.substring(2);
        if (gameNotation.endsWith("]]"))
            gameNotation = gameNotation.substring(0, gameNotation.length() - 2);

        this.gameNotation = gameNotation;
    }

    Game importGame() {
        List<Move> moves = new ArrayList<>();
        String[] moveNotations = gameNotation.split(" ");

        for (String moveNotation : moveNotations) {
            moves.add(parseMove(moveNotation));
        }

        return new Game(moves);
    }

    private Move parseMove(String moveNotation) {
        if (moveNotation.length() != 5 || moveNotation.charAt(2) != '-')
            throw new IllegalArgumentException("Illegal move notation: " + moveNotation);
        String fromFieldNotation = moveNotation.substring(0, 2);
        String toFieldNotation = moveNotation.substring(3);

        int from = ChessUtil.getFieldFromString(fromFieldNotation);
        int to   = ChessUtil.getFieldFromString(toFieldNotation);

        return new Move(from, to);
    }

    public static void main(String[] args) {
        // Stalemate: "[[c2-c4 b8-a6 a2-a3 e7-e6 c4-c5 f8-c5 g1-h3 d7-d5 d1-b3 d8-d7 b3-c4 d7-b5 h3-f4 c5-a3 d2-d4 b5-c6 f4-h3 b7-b5 c1-e3 e8-f8 f2-f3 b5-b4 e3-c1 c8-b7 h3-g5 d5-c4 e1-d1 g8-f6 h2-h4 a6-b8 e2-e3 f6-d5 f1-c4 f7-f5 b2-b3 a3-b2 c4-d5 b2-c3 c1-b2 e6-d5 b2-c1 c6-f6 g5-f7 g7-g5 f7-h8 f6-d6 a1-a6 d6-c5 b1-c3 f8-g7 g2-g4 b7-c6 h1-h2 c6-b7 h4-h5 f5-g4 a6-a3 g4-f3 h2-e2 a7-a6 c3-a2 g7-f8 h8-g6 h7-g6 c1-d2 f8-f7 a2-b4 f7-f6 h5-h6 c5-b4 a3-a4 b8-d7 a4-a6 b4-b6 b3-b4 a8-d8 a6-a2 b7-c6 a2-a7 f6-f7 a7-a2 d8-f8 a2-a5 b6-a7 d2-c3 f8-d8 a5-d5 a7-b7 c3-a1 d8-g8 e2-c2 g8-e8 h6-h7 d7-c5 d5-c5 b7-b6 c2-h2 c6-e4 h2-c2 b6-b7 c2-d2 e4-c6 d2-d3 e8-e6 c5-c6 f7-e8 c6-c7 b7-c6 d1-e1 e6-e7 e1-f1 c6-c7 d3-d1 c7-d7 e3-e4 e8-f7 a1-b2 d7-e8 d1-a1 e8-d8 f1-g1 f7-f6 b2-c3 d8-f8 a1-a5 f6-f7 b4-b5 e7-e6 a5-a2 f8-c8 a2-h2 f7-e7 h2-b2 c8-f8 b2-b1 f8-h6 b1-b2 h6-g7 g1-h1 g7-h8 b2-a2 h8-g8 a2-e2 e7-f6 h1-g1 g8-f8 e2-g2 f8-a8 b5-b6 f3-f2 g1-h2 e6-c6 g2-g5 c6-d6 g5-g4 a8-d8 h2-h3 d8-e8 h3-h4 e8-c8 c3-a5 d6-b6 a5-d2 b6-b3 g4-g2 c8-h8 d2-f4 b3-b4 g2-g1 b4-b7 h4-g4 b7-a7 d4-d5 h8-g7 f4-g5 f6-f7 g5-h4 f2-f1 g4-g3 f1-g1 g3-h3 a7-d7 h4-g5 g1-g4 h3-h2 g4-h5 h2-g1 h5-f3 g5-d8 g7-h8 d8-e7 h8-h7 e7-d8 h7-h6 d8-a5 h6-f8 g1-h2 f8-d6 h2-g1 f3-g2 g1-g2 f7-g7 g2-h1 d7-d8 h1-g1 d8-f8 a5-c7 d6-b6 g1-g2 g6-g5 c7-b6 f8-d8 b6-d8 g7-f8 g2-f3 f8-g7 f3-f2 g7-f7 d8-f6 f7-g6 f6-b2 g5-g4 e4-e5 g6-f7 f2-e3 f7-g8 e3-e2 g8-h8 b2-c3 h8-h7 c3-e1 h7-g8 e1-a5 g8-h7 a5-d8 h7-h6 d8-c7 h6-h5 e2-f1 h5-g6 f1-g2 g6-g7 g2-h1 g7-g8 c7-d6 g4-g3 h1-g1 g3-g2 d6-b4 g8-f7 g1-g2 f7-g8 b4-f8 g8-f8 g2-f2 f8-g8 e5-e6 g8-h8 f2-g1 h8-g8 g1-h1 g8-h8 d5-d6 h8-g8 h1-g1 g8-h8 e6-e7 h8-g8 e7-e8 g8-h7 g1-h2 h7-h6 h2-h3 h6-h7 e8-e3 h7-h8 e3-f3 h8-g8 f3-e2 g8-h7 e2-b2 h7-h6 b2-a1 h6-g6 h3-g4 g6-h7 a1-a2 h7-g6 a2-a3 g6-g7 g4-h3 g7-h6 a3-g3 h6-h5 g3-g4 h5-h6 g4-g8 h6-h5 g8-d5 h5-g6 d5-h5 g6-g7 h5-f7 g7-h8 d6-d7]]";
        // Checkmate: "[[c2-c4 b8-a6 a2-a3 e7-e6 c4-c5 f8-c5 g1-h3 d7-d5 d1-b3 d8-d7 b3-c4 d7-b5 h3-f4 c5-a3 d2-d4 b5-c6 f4-h3 b7-b5 c1-e3 e8-f8 f2-f3 b5-b4 e3-c1 c8-b7 h3-g5 d5-c4 e1-d1 g8-f6 h2-h4 a6-b8 e2-e3 f6-d5 f1-c4 f7-f5 b2-b3 a3-b2 c4-d5 b2-c3 c1-b2 e6-d5 b2-c1 c6-f6 g5-f7 g7-g5 f7-h8 f6-d6 a1-a6 d6-c5 b1-c3 f8-g7 g2-g4 b7-c6 h1-h2 c6-b7 h4-h5 f5-g4 a6-a3 g4-f3 h2-e2 a7-a6 c3-a2 g7-f8 h8-g6 h7-g6 c1-d2 f8-f7 a2-b4 f7-f6 h5-h6 c5-b4 a3-a4 b8-d7 a4-a6 b4-b6 b3-b4 a8-d8 a6-a2 b7-c6 a2-a7 f6-f7 a7-a2 d8-f8 a2-a5 b6-a7 d2-c3 f8-d8 a5-d5 a7-b7 c3-a1 d8-g8 e2-c2 g8-e8 h6-h7 d7-c5 d5-c5 b7-b6 c2-h2 c6-e4 h2-c2 b6-b7 c2-d2 e4-c6 d2-d3 e8-e6 c5-c6 f7-e8 c6-c7 b7-c6 d1-e1 e6-e7 e1-f1 c6-c7 d3-d1 c7-d7 e3-e4 e8-f7 a1-b2 d7-e8 d1-a1 e8-d8 f1-g1 f7-f6 b2-c3 d8-f8 a1-a5 f6-f7 b4-b5 e7-e6 a5-a2 f8-c8 a2-h2 f7-e7 h2-b2 c8-f8 b2-b1 f8-h6 b1-b2 h6-g7 g1-h1 g7-h8 b2-a2 h8-g8 a2-e2 e7-f6 h1-g1 g8-f8 e2-g2 f8-a8 b5-b6 f3-f2 g1-h2 e6-c6 g2-g5 c6-d6 g5-g4 a8-d8 h2-h3 d8-e8 h3-h4 e8-c8 c3-a5 d6-b6 a5-d2 b6-b3 g4-g2 c8-h8 d2-f4 b3-b4 g2-g1 b4-b7 h4-g4 b7-a7 d4-d5 h8-g7 f4-g5 f6-f7 g5-h4 f2-f1 g4-g3 f1-g1 g3-h3 a7-d7 h4-g5 g1-g4 h3-h2 g4-h5 h2-g1 h5-f3 g5-d8 g7-h8 d8-e7 h8-h7 e7-d8 h7-h6 d8-a5 h6-f8 g1-h2 f8-d6 h2-g1 f3-g2 g1-g2 f7-g7 g2-h1 d7-d8 h1-g1 d8-f8 a5-c7 d6-b6 g1-g2 g6-g5 c7-b6 f8-d8 b6-d8 g7-f8 g2-f3 f8-g7 f3-f2 g7-f7 d8-f6 f7-g6 f6-b2 g5-g4 e4-e5 g6-f7 f2-e3 f7-g8 e3-e2 g8-h8 b2-c3 h8-h7 c3-e1 h7-g8 e1-a5 g8-h7 a5-d8 h7-h6 d8-c7 h6-h5 e2-f1 h5-g6 f1-g2 g6-g7 g2-h1 g7-g8 c7-d6 g4-g3 h1-g1 g3-g2 d6-b4 g8-f7 g1-g2 f7-g8 b4-f8 g8-f8 g2-f2 f8-g8 e5-e6 g8-h8 f2-g1 h8-g8 g1-h1 g8-h8 d5-d6 h8-g8 h1-g1 g8-h8 e6-e7 h8-g8 e7-e8 g8-h7 g1-h2 h7-h6 h2-h3 h6-h7 e8-e3 h7-h8 e3-f3 h8-g8 f3-e2 g8-h7 e2-b2 h7-h6 b2-a1 h6-g6 h3-g4 g6-h7 a1-a2 h7-g6 a2-a3 g6-g7 g4-h3 g7-h6 a3-g3 h6-h5 g3-g4 h5-h6 g4-g8 h6-h5 g8-d5 h5-g6 d5-h5 g6-g7 h5-f7 g7-h8 f7-f8]]";
        // Stalemate: "[[g1-h3 c7-c6 d2-d3 b8-a6 f2-f4 f7-f6 e1-d2 h7-h5 d2-c3 a6-b8 f4-f5 c6-c5 e2-e4 a7-a5 d1-e2 g7-g6 c3-b3 d7-d6 e2-e1 a8-a6 c1-f4 g6-g5 b1-a3 a6-a7 e1-d2 b8-c6 g2-g4 h5-h4 f1-e2 b7-b5 f4-e3 a7-a8 e3-g1 c8-a6 c2-c3 c6-e5 d2-e3 a8-b8 a1-e1 b8-b6 e3-f3 e8-d7 f3-g3 e5-g4 b3-c2 f8-h6 h3-g5 d7-c8 g5-f3 b5-b4 d3-d4 c8-b7 g3-e5 b7-a7 f3-d2 d6-d5 e2-d3 a6-d3 c2-d3 b4-a3 d3-e2 d5-e4 e1-a1 d8-b8 a1-f1 b6-b3 f1-d1 h6-d2 d4-d5 b8-d6 d1-b1 a7-b6 b1-a1 d6-b8 a1-b1 d2-e3 b1-c1 e3-d4 c1-c2 b6-b7 e2-d2 b8-c8 g1-f2 a3-b2 d2-d1 b7-b6 c2-d2 c5-c4 h1-g1 e4-e3 a2-a4 h4-h3 d2-b2 g4-f2 d1-e2 b3-b4 g1-g2 d4-c3 g2-g1 c8-d8 b2-d2 d8-a8 g1-a1 b4-b2 a1-a2 c3-d2 a2-a3 c4-c3 e5-d6 b6-b7 e2-f3 b2-b3 d6-d8 h8-h4 d8-d6 a8-e8 d6-b8 b7-b8 a3-a2 f2-h1 d5-d6 e8-d8 a2-a3 h4-h7 a3-a2 h7-g7 a2-a3 d8-b6 a3-a1 g7-g5 a1-a2 b6-c7 a2-d2 c3-d2 f3-f4 b3-b1 f4-f3 b1-c1 f3-f4 c7-c8 f4-f3 c1-c3 f3-f4 c8-e6 f4-f3 b8-c8 f3-f4 c3-b3 f5-e6 g8-h6 f4-e4 h1-f2 e4-f4 e3-e2 d6-d7 c8-d8]]";
        // Checkmate: "[[b1-a3 b8-c6 c2-c3 g8-h6 g1-h3 f7-f6 g2-g3 c6-e5 a3-b5 h6-f7 b5-d4 f7-h6 h3-g5 e5-c4 g5-e4 c4-e3 d4-b3 a8-b8 f2-f4 d7-d6 g3-g4 e3-c4 a1-b1 d6-d5 e4-d6 c4-d6 b3-a5 d6-f7 h1-g1 e8-d7 d2-d4 e7-e6 c1-d2 e6-e5 b2-b4 c7-c6 d2-e3 c6-c5 g1-g3 d8-a5 d1-d2 f7-d8 b4-b5 e5-f4 e1-f2 f4-g3 f2-f3 g3-g2 d2-d3 d8-e6 e3-c1 a5-a3 c1-g5 f8-d6 b1-d1 b7-b6 c3-c4 b8-b7 d1-e1 a3-c3 e1-d1 c3-d2 d3-g6 a7-a5 d1-d2 f6-g5 g6-f5 h6-g4 f5-f4 d6-e7 e2-e4 e7-d6 h2-h4 e6-f8 f4-e3 d6-e7 d2-c2 d7-d6 e3-f2 h7-h6 f1-e2 g4-f6 h4-g5 c8-h3 f3-f4 a5-a4 f2-g3 f8-d7 g5-g6 h8-a8 c2-b2 d6-e6 e2-g4 h3-g4 b2-c2 e7-d8 g3-a3 h6-h5 a3-b4 a8-a7 a2-a3 d7-f8 c2-b2 g4-d1 b2-a2 f6-d7 b4-b1 d8-e7 c4-d5 e6-d6 a2-e2 e7-h4 b1-c2 f8-g6 f4-f5 g6-f4 c2-a2 d1-e2 d4-c5 b6-c5 a2-b3 e2-f3 b3-b4 f4-d5 b4-b1 h4-f6 b1-d1 g2-g1 d1-b1 d7-f8 e4-e5 d6-d7 b1-c1 b7-c7 c1-c5 g1-b1 c5-c2 b1-c2]]";
        // En passant: "[[e2-e4 c2-c5 f2-f3 c5-c4 d2-d4]]";
        // Castling king's side: "[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 g8-f6 e1-g1]]";
        // Castling queen's side: "[[e2-e4 e7-e5 d2-d4 e5-e4 d1-d4 b8-c6 d4-e3 g8-f6 b1-c3 f8-b4 c1-d2 e8-g8 e1-c1]]";
        String notation = "[[e2-e4 e7-e5 d2-d4 e5-e4 d1-d4 b8-c6 d4-e3 g8-f6 b1-c3 f8-b4 c1-d2 e8-g8 e1-c1]]";

        Game game = new SimpleNotationImporter(notation).importGame();
        game.print();
        //Game.playAutoGame(game);
    }
}
