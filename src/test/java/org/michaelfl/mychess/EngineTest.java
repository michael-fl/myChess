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
                4.5f,
                5.5f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Lost position for black. Black must sacrifice a rook against night: Rxd5 (otherwise mate in 2)
    @Test
    void testPosition1v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1 f8-c8 f1-f7 a7-f7 g4-c8 c5-e4 c2-c3 e4-d2 b1-c2 f7-f2 h3-h4 d2-e4 c2-c1 e4-c5 d5-b4 e5-f4 c1-d1 f2-d2 d1-e1 g6-g5 h4-h6 h8-g8 c8-e6 c5-e6 h6-e6 g8-g7 b4-d5]]",
                "d2-d5",
                4.5f,
                5.5f,
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
                2.4f,
                4.2f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // White wins (back) a pawn and gives chess: Bxe6+
    @Test
    void testPosition3() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6]]",
                "h3-e6",
                0.2f,
                0.4f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // White wins (back) a pawn and gives chess: Bxe6+
    @Test
    void testPosition3v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6]]",
                "h3-e6",
                0.4f,
                0.6f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Bad position for black (last move was a mistake). Expected move: exf7+, Weight: 7.34
    @Test
    void testPosition4() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8]]",
                "c3-d5", // TODO
                0.4f,
                1.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Bad position for black (last move was a mistake). Expected move: exf7+, Weight: 7.34
    @Test
    void testPosition4v1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8]]",
                "e6-f7",
                0.7f,
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
                13.0f,
                14.0f,
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
                "g1-g2",
                14f,
                checkmateIn(12),
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

    // Expected move Bxf7+. Expected weight: 1.7
    @Test
    void testPosition11() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4]]",
                "b3-f7",
                0.7f,
                2.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Expected move Bxf7+. Expected weight: 1.7
    @Test
    void testPosition11v1() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4]]",
                "d4-e5",
                1.5f,
                2.5f,
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
                3.0f,
                4.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 7 (13 plies). Weight: M7. Expected moves:  43.Rxg7+ Kh5 44.Kxh2 Rd2+ 45.Kh3 Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/1R4pp/3r1pk1/p4N2/5R2/8/7r/6K1 w - - 0 43
    @Test
    void testPosition13() {
        testPosition("[[e2-e4 e7-e6 g1-f3 d7-d5 e4-d5 e6-d5 f1-b5 c8-d7 b5-d7 d8-d7 e1-g1 f8-d6 f1-e1 g8-e7 d1-e2 e8-g8 b1-c3 c7-c5 d2-d4 c5-d4 f3-d4 b8-c6 d4-f3 e7-f5 c1-d2 f5-d4 e2-d1 d6-c5 f3-d4 c5-d4 d1-f3 c6-b4 f3-d1 d7-f5 e1-e2 f5-c2 d1-c2 b4-c2 a1-c1 c2-b4 c3-b5 d4-b2 c1-b1 b4-d3 e2-e3 d3-f2 g1-f2 b2-f6 e3-d3 f8-c8 d3-d5 c8-c2 a2-a4 c2-a2 f2-f1 a2-a4 b5-c7 a8-b8 d5-d7 a4-a2 c7-d5 f6-d4 d5-e7 g8-f8 d2-b4 a2-f2 f1-e1 b8-e8 d7-d4 f2-g2 e1-f1 g2-h2 d4-e4 f7-f6 f1-g1 a7-a5 b4-d6 e8-d8 e7-f5 f8-f7 b1-b7 f7-g6 e4-f4 d8-d6]]",
                "b7-g7",
                6.0f, // OPT: Should be M13
                7.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 7 (13 plies). Weight: #7. Expected moves:  43.Rxg7+ Kh5 44.Kxh2 Rd2+ 45.Kh3 Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/1R4pp/3r1pk1/p4N2/5R2/8/7r/6K1 w - - 0 43
    @Test
    void testPosition13v1() {
        testPosition("[[e2-e4 e7-e6 g1-f3 d7-d5 e4-d5 e6-d5 f1-b5 c8-d7 b5-d7 d8-d7 e1-g1 f8-d6 f1-e1 g8-e7 d1-e2 e8-g8 b1-c3 c7-c5 d2-d4 c5-d4 f3-d4 b8-c6 d4-f3 e7-f5 c1-d2 f5-d4 e2-d1 d6-c5 f3-d4 c5-d4 d1-f3 c6-b4 f3-d1 d7-f5 e1-e2 f5-c2 d1-c2 b4-c2 a1-c1 c2-b4 c3-b5 d4-b2 c1-b1 b4-d3 e2-e3 d3-f2 g1-f2 b2-f6 e3-d3 f8-c8 d3-d5 c8-c2 a2-a4 c2-a2 f2-f1 a2-a4 b5-c7 a8-b8 d5-d7 a4-a2 c7-d5 f6-d4 d5-e7 g8-f8 d2-b4 a2-f2 f1-e1 b8-e8 d7-d4 f2-g2 e1-f1 g2-h2 d4-e4 f7-f6 f1-g1 a7-a5 b4-d6 e8-d8 e7-f5 f8-f7 b1-b7 f7-g6 e4-f4 d8-d6]]",
                "g1-h2",
                10.0f, // OPT: Should be M13
                11.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 4 (8 plies). Weight: #4. Expected moves: Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 0 45
    @Test
    void testPosition14() {
        testPosition("[[e2-e4 e7-e6 g1-f3 d7-d5 e4-d5 e6-d5 f1-b5 c8-d7 b5-d7 d8-d7 e1-g1 f8-d6 f1-e1 g8-e7 d1-e2 e8-g8 b1-c3 c7-c5 d2-d4 c5-d4 f3-d4 b8-c6 d4-f3 e7-f5 c1-d2 f5-d4 e2-d1 d6-c5 f3-d4 c5-d4 d1-f3 c6-b4 f3-d1 d7-f5 e1-e2 f5-c2 d1-c2 b4-c2 a1-c1 c2-b4 c3-b5 d4-b2 c1-b1 b4-d3 e2-e3 d3-f2 g1-f2 b2-f6 e3-d3 f8-c8 d3-d5 c8-c2 a2-a4 c2-a2 f2-f1 a2-a4 b5-c7 a8-b8 d5-d7 a4-a2 c7-d5 f6-d4 d5-e7 g8-f8 d2-b4 a2-f2 f1-e1 b8-e8 d7-d4 f2-g2 e1-f1 g2-h2 d4-e4 f7-f6 f1-g1 a7-a5 b4-d6 e8-d8 e7-f5 f8-f7 b1-b7 f7-g6 e4-f4 d8-d6 b7-g7 g6-h5 g1-h2 d6-d2 h2-h3]]",
                "d2-d3",
                8.0f, // TODO: Should be M8
                9.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 4 (8 plies). Weight: #4. Expected moves: Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 0 45
    @Test
    void testPosition14v1() {
        testPosition("[[e2-e4 e7-e6 g1-f3 d7-d5 e4-d5 e6-d5 f1-b5 c8-d7 b5-d7 d8-d7 e1-g1 f8-d6 f1-e1 g8-e7 d1-e2 e8-g8 b1-c3 c7-c5 d2-d4 c5-d4 f3-d4 b8-c6 d4-f3 e7-f5 c1-d2 f5-d4 e2-d1 d6-c5 f3-d4 c5-d4 d1-f3 c6-b4 f3-d1 d7-f5 e1-e2 f5-c2 d1-c2 b4-c2 a1-c1 c2-b4 c3-b5 d4-b2 c1-b1 b4-d3 e2-e3 d3-f2 g1-f2 b2-f6 e3-d3 f8-c8 d3-d5 c8-c2 a2-a4 c2-a2 f2-f1 a2-a4 b5-c7 a8-b8 d5-d7 a4-a2 c7-d5 f6-d4 d5-e7 g8-f8 d2-b4 a2-f2 f1-e1 b8-e8 d7-d4 f2-g2 e1-f1 g2-h2 d4-e4 f7-f6 f1-g1 a7-a5 b4-d6 e8-d8 e7-f5 f8-f7 b1-b7 f7-g6 e4-f4 d8-d6 b7-g7 g6-h5 g1-h2 d6-d2 h2-h3]]",
                "d2-d3",
                checkmateIn(8),
                checkmateIn(8),
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
        );
    }

    // Black mate in 8 (15 plies). Expected moves:  47.Be6+ Rd7 48.Rxd7 Bg3 49.hxg3 h5 50.Rg7+ Kb8 51.c7+ Kb7 52.c8=Q+ Kb6 53.Rb7+ Ka5 54.Qa8#
    // [[b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8]]
    // FEN: 2kr4/1R5p/2P3p1/3B4/8/P3p3/5b1P/7K w - - 0 47
    @Test
    void testPosition15() {
        testPosition("[[b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8]]",
                "d5-e6",
                6.0f, // OPT: Should be M15
                8.0f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    // Black mate in 8 (15 plies). Expected moves:  47.Be6+ Rd7 48.Rxd7 Bg3 49.hxg3 h5 50.Rg7+ Kb8 51.c7+ Kb7 52.c8=Q+ Kb6 53.Rb7+ Ka5 54.Qa8#
    // [[b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8]]
    // FEN: 2kr4/1R5p/2P3p1/3B4/8/P3p3/5b1P/7K w - - 0 47
    @Test
    void testPosition15v1() {
        testPosition("[[b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8]]",
                "d5-e6",
                9.0f, // OPT: Should be M15
                11.0f,
                new GameConfig(MyChessEngine1.class, engineV1Config(false))
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
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected maximum of " + ChessUtil.weightToString(expectedMaxWeight));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static float checkmateIn(int depth) {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth;
    }

}
