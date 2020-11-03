package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.v1.MyChessEngine1;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class EngineTest {

    // Lost position for black. Black must sacrifice a rook against night: Rxd5 (otherwise mate in 2)
    @Test
    void testPosition1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1 f8-c8 f1-f7 a7-f7 g4-c8 c5-e4 c2-c3 e4-d2 b1-c2 f7-f2 h3-h4 d2-e4 c2-c1 e4-c5 d5-b4 e5-f4 c1-d1 f2-d2 d1-e1 g6-g5 h4-h6 h8-g8 c8-e6 c5-e6 h6-e6 g8-g7 b4-d5]]",
                "d2-d5",
                5.0f,
                6.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Lost position for black. Black must sacrifice a rook against night: Rxd5 (otherwise mate in 2)
    @Test
    void testPosition1v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1 f8-c8 f1-f7 a7-f7 g4-c8 c5-e4 c2-c3 e4-d2 b1-c2 f7-f2 h3-h4 d2-e4 c2-c1 e4-c5 d5-b4 e5-f4 c1-d1 f2-d2 d1-e1 g6-g5 h4-h6 h8-g8 c8-e6 c5-e6 h6-e6 g8-g7 b4-d5]]",
                "d2-d5",
                5.0f,
                6.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Only way: Black must play Rxc8 and white will win a rook in the end
    @Test
    void testPosition2() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1]]",
                "f7-f1", // TODO
                3.2f,
                4.2f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Only way: Black must play Rxc8 and white will win a rook in the end
    @Test
    void testPosition2v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1]]",
                "f8-c8",
                3.2f,
                4.2f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // White wins (back) a pawn and gives chess: Bxe6+
    @Test
    void testPosition3() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6]]",
                "h3-e6",
                0.0f,
                0.1f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // White wins (back) a pawn and gives chess: Bxe6+
    @Test
    void testPosition3v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6]]",
                "g1-g2",
                1.0f,
                1.5f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Bad position for black (last move was a mistake). Expected move: exf7+, Weight: 7.34
    @Test
    void testPosition4() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8]]",
                "c3-d5", // TODO
                0.1f,
                0.2f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Bad position for black (last move was a mistake). Expected move: exf7+, Weight: 7.34
    @Test
    void testPosition4v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8]]",
                "h3-f5", // TODO
                2.0f,
                3.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Lost position for black. Expected move: Nxf6+, Weight: > 20, mate in 12
    @Test
    void testPosition5() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4]]",
                "d5-f6",
                10.0f,
                11.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Lost position for black. Expected move: Nxf6+, Weight: > 20, mate in 12
    @Test
    void testPosition5v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4]]",
                "d5-f6",
                11.0f,
                12.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Mate in 2 (3 plies). Expected move: g6-g7
    @Test
    void testPosition6() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 g8-f8]]",
                "g6-g7",
                checkmateIn(3),
                checkmateIn(3),
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Mate in 2 (3 plies). Expected move: g6-g7
    @Test
    void testPosition6v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 g8-f8]]",
                "g6-g7",
                checkmateIn(3),
                checkmateIn(3),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 6 (12 plies). Expected move: Rxg5
    @Test
    void testPosition7() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7]]",
                "g1-g5",
                11.0f,
                12.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 6 (12 plies). Expected move: Rxg5
    @Test
    void testPosition7v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7]]",
                "g1-g2", // TODO
                checkmateIn(9),
                checkmateIn(9),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 4 (8 plies). Expected move: Rxe6
    @Test
    void testPosition8() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6]]",
                "e7-e6",
                12.0f,
                13.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 4 (8 plies). Expected move: Rxe6
    @Test
    void testPosition8v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6]]",
                "e7-e6",
                checkmateIn(8),
                checkmateIn(8),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 3 (6 plies). Expected moves: Kh8 13.Rxg5 g6 14.Qxg6 Rd8 15.Rh5#
    @Test
    void testPosition9() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 e7-e6 g6-e6]]",
                "g8-h8",
                checkmateIn(6),
                checkmateIn(6),
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 3 (6 plies). Expected moves: Kh8 13.Rxg5 g6 14.Qxg6 Rd8 15.Rh5#
    @Test
    void testPosition9v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 e7-e6 g6-e6]]",
                "g8-h8",
                checkmateIn(6),
                checkmateIn(6),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 1 (2 plies). Only possible move: Rf7
    @Test
    void testPosition10() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 g8-f8 d5-f5]]",
                "e7-f7",
                checkmateIn(2),
                checkmateIn(2),
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 1 (2 plies). Only possible move: Rf7
    @Test
    void testPosition10v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 g8-f8 d5-f5]]",
                "e7-f7",
                checkmateIn(2),
                checkmateIn(2),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Expected move Bxf7+.
    @Test
    void testPosition11() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4]]",
                "b3-f7",
                0.7f,
                2.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Expected move Bxf7+.
    @Test
    void testPosition11v1() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4]]",
                "b3-d5",
                0.7f,
                2.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Great position for white. Weight: 6.12, expected move e6.
    @Test
    void testPosition12() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4 b3-f7 e8-f7 d4-e5 f6-g8 f3-g5 f7-e8 b1-c3 b5-b4 d1-d5 g8-h6 c3-b5 a4-a3 f1-d1 c8-b7]]",
                "g5-e6", // TODO: should be e5-e6
                2.0f,
                3.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Great position for white. Weight: 6.12, expected move e6.
    @Test
    void testPosition12v1() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4 b3-f7 e8-f7 d4-e5 f6-g8 f3-g5 f7-e8 b1-c3 b5-b4 d1-d5 g8-h6 c3-b5 a4-a3 f1-d1 c8-b7]]",
                "g5-e6",
                2.0f,
                3.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @SuppressWarnings("SameParameterValue")
    static EngineConfig engineConfig(boolean doCheckmateCheck) {
        return new EngineConfig.Builder()
                .maxDepth(8)
                .checkmateCheck(doCheckmateCheck)
                .build();
    }

    @SuppressWarnings("SameParameterValue")
    static EngineConfig engineV1Config(boolean doCheckmateCheck) {
        return new EngineConfig.Builder()
                .maxDepth(14)
                .iterationDepth(6)
                .variants(4)
                .checkmateCheck(doCheckmateCheck)
                .build();
    }

    private void testPosition(String gameNotation, String expectedMove, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            SimpleNotationImporter importer = new SimpleNotationImporter(gameNotation);
            var game = importer.importGame(config);

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            if (!expectedMove.equals(ChessUtil.moveToString(move.move))) {
                game.print();
                System.out.println(game.exportFEN());
                fail("Wrong move: " + ChessUtil.moveToString(move.move) + ". Expected " + expectedMove);
            }

            var weight = move.weight;
            if (weight < expectedMinWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected minimum of " + ChessUtil.weightToString(expectedMinWeight));
            }
            if (weight > expectedMaxWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected maximum of " + ChessUtil.weightToString(expectedMinWeight));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static float checkmateIn(int depth) {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth;
    }

}
