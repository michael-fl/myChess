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
        List<MoveDescription> moves = new ArrayList<>();
        String[] moveNotations = gameNotation.split(" ");

        for (String moveNotation : moveNotations) {
            moves.add(parseMove(moveNotation));
        }

        return new Game(moves);
    }

    private MoveDescription parseMove(String moveNotation) {
        if (moveNotation.length() < 5 || moveNotation.length() > 6 || moveNotation.charAt(2) != '-')
            throw new IllegalArgumentException("Illegal move notation: " + moveNotation);
        String fromFieldNotation = moveNotation.substring(0, 2);
        String toFieldNotation = moveNotation.substring(3, 5);
        char pawnPromotionSymbol = 0;
        if (moveNotation.length() >= 6)
            pawnPromotionSymbol = moveNotation.charAt(5);

        int[] from = ChessUtil.getColAndRowFromString(fromFieldNotation);
        int[] to   = ChessUtil.getColAndRowFromString(toFieldNotation);

        return new MoveDescription(from[0], from[1], to[0], to[1], pawnPromotionSymbol);
    }

    public static void main(String[] args) {
        // Stalemate: "[[g1-h3 g7-g5 h3-f4 g5-g4 b2-b3 f8-h6 h2-h4 h6-g5 a2-a4 g5-h4 d2-d4 h4-f2 e1-f2 b8-c6 f4-h5 e7-e5 e2-e4 c6-a5 h5-f6 d8-f6 c1-f4 f6-e7 c2-c4 e7-f8 f4-c1 f7-f5 h1-h3 f5-f4 b1-a3 f8-c5 a1-a2 e8-f8 a3-c2 d7-d6 c1-e3 c8-d7 d1-d3 c7-c6 h3-h5 e5-d4 h5-c5 d7-f5 e3-d2 f5-e6 d2-e3 d4-e3 c2-e3 d6-d5 a2-e2 h7-h5 e2-e1 h8-h7 c5-a5 h7-e7 e3-f5 e7-d7 a5-a6 g8-h6 e4-d5 f8-f7 a6-c6 d7-e7 e1-e2 b7-c6 b3-b4 f7-g6 d3-h3 e7-g7 f2-e1 a8-d8 b4-b5 d8-b8 f5-e3 f4-e3 g2-g3 b8-b5 e2-b2 g7-c7 e1-e2 e6-d7 h3-h2 b5-d5 f1-g2 d5-e5 b2-b1 d7-f5 b1-b3 f5-d3 e2-e1 d3-e4 h2-h3 e4-f3 b3-c3 g6-h7 c3-c1 f3-e4 h3-h2 c7-d7 h2-h4 e5-b5 c4-c5 h7-g7 h4-h1 h5-h4 c1-c2 d7-d6 g2-e4 e3-e2 h1-h4 d6-d2 e1-f2 d2-d8 f2-e3 h6-f5 e3-f4 d8-e8 c2-c3 e8-e4 f4-f5 e4-a4 c3-c4 e2-e1Q h4-h3 b5-b3 h3-h4 b3-b8 h4-d8 e1-e2 d8-e8 e2-a2 c4-c1 a2-a1 c1-c2 b8-b7 c2-d2 a7-a6 d2-f2 a1-c1 f2-g2 c1-g5 f5-e6 g5-g6 e6-e5 g6-c2 g2-g1 a4-a5 g1-g2 c2-d1 e5-e6 d1-h1 e8-f8 g7-h7 g2-d2 b7-a7 d2-f2 a7-f7 f8-f7 h7-h8 f2-f4 h1-h7 f7-d7 h7-h3 d7-f7 h3-h4 f7-f5 a5-a3 f4-a4 a3-b3 a4-e4 h4-h3 e4-e5 b3-b6 f5-f2 b6-b2 f2-f4 b2-a2 f4-f7 a2-h2 e5-h5 h3-h5 f7-c7 h2-d2 c7-d6 h5-h1 d6-d7 h1-h2 d7-f7 d2-d1 f7-d7 d1-d2 e6-e7 h2-g3 e7-e6 g3-e5 e6-f7 e5-e1 d7-d8 e1-e8 f7-f6 d2-d1 d8-d5 d1-b1 d5-g2 b1-d1 g2-g3 e8-b8 f6-f7 b8-d6 g3-g2 d6-h2 g2-d5 h2-g1 f7-e8 g1-g2 d5-e4 g2-h2 e4-b4 d1-d2 b4-c3 d2-d4 e8-e7 a6-a5 c3-b2 h2-h3 b2-b4 d4-d7 e7-e8 h3-g3 b4-g4 g3-e3 g4-e4 e3-e4 e8-f8 e4-e2]]";
        // Checkmate: "[[f2-f4 h7-h5 b2-b4 e7-e5 a2-a3 c7-c6 c2-c4 e8-e7 g2-g4 d8-c7 f1-g2 c6-c5 e1-f1 e7-e6 f1-e1 d7-d6 b1-c3 a7-a6 g1-h3 b8-c6 c3-b1 g7-g5 d2-d3 g8-e7 g2-f3 c6-d8 f4-g5 d6-d5 e2-e3 b7-b5 c1-d2 c7-a7 c4-b5 f7-f6 d1-b3 c8-b7 b1-c3 d8-f7 f3-e2 h8-h7 c3-d1 a7-b6 e1-f1 h7-h8 b3-a4 h5-h4 a4-b3 a6-b5 d1-b2 f8-g7 a1-c1 a8-c8 b2-d1 e6-d6 g5-g6 c8-a8 b3-d5 d6-d5 a3-a4 h8-b8 f1-e1 b8-c8 h3-g1 h4-h3 d1-b2 d5-e6 b2-d1 a8-a5 e2-f3 c8-f8 f3-c6 f7-d6 c6-g2 d6-e8 b4-a5 b6-d6 g1-h3 b7-d5 g2-d5 d6-d5 h3-f2 f8-g8 c1-c3 e6-d6 d1-b2 g7-f8 f2-d1 g8-h8 c3-a3 d5-b3 d1-c3 e7-g8 a3-a1 h8-h2 a5-a6 e8-c7 a1-d1 h2-h7 d3-d4 b3-c2 c3-e4 c2-e4 d2-c1 b5-a4 a6-a7 e4-f5 d4-c5 d6-e7 d1-d7 f5-d7 c1-d2 h7-f7 d2-a5 c7-b5 h1-g1 d7-g4 c5-c6 g4-g1 e1-e2 g1-e1 e2-f3 e7-e8 b2-c4 b5-a3 a5-d2 f8-d6 g6-f7 e8-f8 c4-b6 e1-g1 e3-e4 a3-b1 d2-e3 f6-f5 e3-c5 f5-f4 f7-g8Q g1-g8 a7-a8Q f8-g7 f3-g4 d6-b8 g4-h3 g7-g6 b6-a4 b1-d2 c5-e7 d2-e4 a8-a7 g8-b3 h3-h4 b3-g8 a7-b8 g8-h8 h4-g4 h8-c8 g4-h4 c8-e6 b8-d8 e4-g5 d8-g8 e6-g8 e7-a3 g5-h3 a4-b2 g8-a2 c6-c7 a2-b2 c7-c8Q g6-f6 a3-b4 b2-b3 b4-c5 f6-g7 c8-h8 g7-g6 h8-g7 g6-g7 c5-d4 b3-a2 h4-h3 a2-b3 h3-h4 b3-e3 h4-g5 e3-e4 d4-e5 e4-e5 g5-h4 g7-h8 h4-h3 e5-c5 h3-g4 f4-f3 g4-h4 f3-f2 h4-h3 c5-c6 h3-h4 c6-g2 h4-h5 h8-g8 h5-h6 g2-g1 h6-h5 g1-c1 h5-h4 g8-h8 h4-g3 c1-a1 g3-g4 f2-f1Q g4-g3 a1-a5 g3-g4 f1-e2 g4-g3 a5-b6 g3-h4 h8-g7 h4-g5 b6-d4 g5-f5 d4-g4]]";
        // Checkmate: "[[b2-b4 e7-e5 b1-a3 g7-g6 c2-c4 f7-f6 a3-b1 d8-e7 b1-c3 e7-d6 e2-e4 d6-e7 d1-b3 h7-h6 d2-d3 a7-a5 c1-a3 a5-b4 f2-f4 a8-a3 c3-d1 a3-a4 f1-e2 b8-a6 b3-c3 b4-c3 a1-b1 a6-b4 d1-c3 a4-a5 b1-a1 g6-g5 g1-h3 f8-g7 c3-a4 a5-a7 h3-g1 d7-d5 g1-f3 e8-f7 a1-b1 a7-a5 f3-d2 b4-c6 e2-d1 h6-h5 d1-g4 f7-f8 b1-b5 e7-e8 a4-c3 e8-f7 b5-b3 g7-h6 e1-f2 c8-f5 h1-c1 c6-b4 c1-d1 f7-d7 g4-h3 c7-c6 g2-g3 d7-c7 d1-h1 c7-d6 d2-f1 a5-b5 h1-g1 b5-a5 f2-f3 f5-g4 f3-g2 a5-a7 h3-g4 d5-d4 g4-e6 a7-a6 e6-h3 b4-a2 c3-b5 d6-c5 b3-c3 a6-a8 b5-c7 f8-g7 c7-b5 a8-a3 c3-b3 c5-c4 b3-a3 g7-g6 a3-a7 c6-c5 b5-d6 c4-b4 f1-e3 b4-c4 a7-a2 h8-h7 g1-h1 f6-f5 f4-g5 c4-e6 a2-a7 e6-b3 h1-g1 b3-b4 g1-a1 b4-d2 g2-h1 d2-g2 h1-g2 g6-g5 d6-e8 h7-h8 e8-c7 d4-e3 a7-a5 c5-c4 a5-a7 f5-f4 a1-a5 e3-e2 a5-a4 e2-e1Q h3-g4 e1-e2 g2-h3 e2-f1]]";
        // En passant: "[[e2-e4 c2-c5 f2-f3 c5-c4 d2-d4]]";
        // Castling king's side: "[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 g8-f6 e1-g1]]";
        // Castling queen's side: "[[e2-e4 e7-e5 d2-d4 e5-d4 d1-d4 b8-c6 d4-e3 g8-f6 b1-c3 f8-b4 c1-d2 e8-g8 e1-c1]]";
        String notation = "[[e2-e3 c7-c6 b1-c3 b8-a6 f1-d3 a8-b8 f2-f4 d8-a5 d3-h7 g8-h6 e1-f1 a5-g5 c3-d5 g5-g2 f1-g2 e7-e5 h7-g8 e5-f4 d2-d3 f4-e3 a2-a3 a6-c5 c2-c3 g7-g6 b2-b3 h6-f5 d5-b4 a7-a5 c1-d2 h8-h6 d1-e2 h6-h7 g2-f1 h7-h3 f1-g2 c5-a4 g1-f3 h3-h5 e2-e3 e8-d8 f3-d4 b8-a8 c3-c4 h5-g5 g2-h3 c6-c5 e3-e8 d8-e8]]";

        Game game = new SimpleNotationImporter(notation).importGame();
        game.print();
        MoveGenerator moveGenerator = new MoveGenerator();
        Moves possibleMoves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
        System.out.println("Possible moves: " + possibleMoves);
        Game.playAutoGame(game);
    }
}
