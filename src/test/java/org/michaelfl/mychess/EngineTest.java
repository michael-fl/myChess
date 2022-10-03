package org.michaelfl.mychess;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.v1.MyChessEngine1;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.michaelfl.mychess.WeightingFunction.checkmateIn;

/**
 * @author Michael Fleischhauer
 */
class EngineTest {

    private static final Class<? extends ChessEngine> ENGINE = MyChessEngine.class;

    // Lost position for black. Black must sacrifice a rook against night: Rxd5 (otherwise mate in 2)
    @Test
    void testPosition1() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1 f8-c8 f1-f7 a7-f7 g4-c8 c5-e4 c2-c3 e4-d2 b1-c2 f7-f2 h3-h4 d2-e4 c2-c1 e4-c5 d5-b4 e5-f4 c1-d1 f2-d2 d1-e1 g6-g5 h4-h6 h8-g8 c8-e6 c5-e6 h6-e6 g8-g7 b4-d5]]",
                Set.of("d2-d5"),
                "d2-d5 e6-d5 g7-f6 d5-f3 h7-h6 f3-h3 f6-g7".split(" "), // + "h3-d7"
                4.5f,
                5.5f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Only way: Black must play Rxc8 and white will win a rook in the end
    @Test
    void testPosition2() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6 h3-e6 g8-h8 c3-d5 d8-e8 h5-h3 a8-a7 g1-g3 g7-g6 g3-c3 e5-d7 c3-c6 f6-e5 e6-g4 d7-c5 c6-c8 e8-f7 d1-f1]]",
                "f7-f1", // TODO
                3.2f,
                4.2f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // White wins (back) a pawn and gives chess: Bxe6+
    @Test
    void testPosition3() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f7-e6]]",
                "h3-e6",
                0.2f,
                0.4f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Bad position for black (last move was a mistake). Expected move: exf7+, Weight: 7.34
    @Test
    void testPosition4() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8]]",
                "c3-d5", // TODO
                0.4f,
                1.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Lost position for black. Expected move: Nxf6+, Weight: > 20, mate in 12
    @Test
    void testPosition5() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4]]",
                "d5-f6",
                10.0f,
                11.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Mate in 2 (3 plies). Expected move: g6-g7
    @Test
    void testPosition6() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 g8-f8]]",
                Set.of("g6-g7"),
                "g6-g7 f8-e7 f6-d5".split(" "),
                checkmateIn(3),
                checkmateIn(3),
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 6 (12 plies). Expected move: Rxg5
    @Test
    void testPosition7() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7]]",
                Set.of("g1-g5"),
                "g1-g5 h6-g5 f5-e6 e7-e6 g6-e6 g8-h7 d5-g5".split(" "), // + "a8-e8"
                11.0f, // TODO M12
                12.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 4 (8 plies). Expected move: Rxe6
    @Test
    void testPosition8() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6]]",
                "e7-e6",
                12.0f,
                13.0f,  // TODO: M8
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 3 (6 plies). Expected moves: Kh8 13.Rxg5 g6 14.Qxg6 Rd8 15.Rh5#
    @Test
    void testPosition9() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 e7-e6 g6-e6]]",
                Set.of("g8-h8"),
                "Kh8 Rxg5 g6 Qxg6 Rb8 Qg7#".split(" "),
                checkmateIn(6),
                checkmateIn(6),
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 1 (2 plies). Only possible move: Rf7
    @Test
    void testPosition10() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 g8-f8 d5-f5]]",
                Set.of("e7-f7"),
                "e7-f7 g6-f7".split(" "),
                checkmateIn(2),
                checkmateIn(2),
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Expected move Bxf7+. Expected weight: 1.7
    @Test
    void testPosition11() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4]]",
                "b3-f7",
                0.7f,
                2.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Great position for white. Weight: 6.12, expected move e6.
    @Test
    void testPosition12() {
        testPosition("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4 b3-f7 e8-f7 d4-e5 f6-g8 f3-g5 f7-e8 b1-c3 b5-b4 d1-d5 g8-h6 c3-b5 a4-a3 f1-d1 c8-b7]]",
                Set.of("g5-e6"), // TODO: should be e5-e6
                "g5-e6 d7-e6 d5-e6 f8-e7 d1-d8 c6-d8 e6-c4".split(" "), // + "h6-g4"
                2.0f,
                3.0f, // TODO should be 6
                new GameConfig(ENGINE, engineConfig())
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
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 4 (8 plies). Weight: #4. Expected moves: Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 0 45
    @Test
    void testPosition14() {
        testPosition("[[e2-e4 e7-e6 g1-f3 d7-d5 e4-d5 e6-d5 f1-b5 c8-d7 b5-d7 d8-d7 e1-g1 f8-d6 f1-e1 g8-e7 d1-e2 e8-g8 b1-c3 c7-c5 d2-d4 c5-d4 f3-d4 b8-c6 d4-f3 e7-f5 c1-d2 f5-d4 e2-d1 d6-c5 f3-d4 c5-d4 d1-f3 c6-b4 f3-d1 d7-f5 e1-e2 f5-c2 d1-c2 b4-c2 a1-c1 c2-b4 c3-b5 d4-b2 c1-b1 b4-d3 e2-e3 d3-f2 g1-f2 b2-f6 e3-d3 f8-c8 d3-d5 c8-c2 a2-a4 c2-a2 f2-f1 a2-a4 b5-c7 a8-b8 d5-d7 a4-a2 c7-d5 f6-d4 d5-e7 g8-f8 d2-b4 a2-f2 f1-e1 b8-e8 d7-d4 f2-g2 e1-f1 g2-h2 d4-e4 f7-f6 f1-g1 a7-a5 b4-d6 e8-d8 e7-f5 f8-f7 b1-b7 f7-g6 e4-f4 d8-d6 b7-g7 g6-h5 g1-h2 d6-d2 h2-h3]]",
                Set.of("d2-d3"),
                "d2-d3 f5-g3 h5-h6 g7-g8 d3-g3 g8-g3 f6-f5".split(" "), // + "f4-f5"
                8.0f, // TODO: Should be M8
                9.0f,
                new GameConfig(ENGINE, engineConfig())
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
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Bad position for black. Black must move Be6 - all other moves are even worse.
    // Weight: +2.86. Expected moves: 17....Be6 18.Bxb8 Rxb8 19.Nd5 Bd6 20.Nxf6+ gxf6 21.Rad1 Be5 22.Bd5 Bg4 23.f3 Bh5 24.c3 Kg7 25.g3 a5
    // FEN: 1rbr2k1/4bppp/p4n2/1pp1B3/8/2N2B2/PPP2PPP/R3R1K1 b - - 0 17
    @Test
    void testPosition16() {
        testPosition("[[b1-c3 e7-e5 g1-f3 b8-c6 d2-d4 e5-d4 f3-d4 c6-d4 d1-d4 g8-f6 e2-e4 d7-d6 c1-g5 f8-e7 f1-c4 e8-g8 e1-g1 f8-e8 f1-e1 c7-c6 g5-f4 b7-b5 c4-e2 a7-a6 e2-f3 c6-c5 d4-d3 a8-b8 e4-e5 d6-e5 d3-d8 e8-d8 f4-e5]]",
                "e7-d6", // TODO: Should be "c8-e6", e7-d6 has weight > 5.0 (add a test for that one as well)
                1.7f,
                3.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Lost position for black. White wins material.
    // Weight: > 7.0.
    // Expected moves: 24.Rxd8+ Bxd8 25.Bxf6 gxf6 26.Re8+ Kg7 27.Rxd8 Be6 28.Be2 Bf5 29.Bd1 Rb7 30.f3 h5 31.c4 a5 32.Kf2
    // Or:             24.Bxf6 Rxd1 25.Bxd1 Bf8 26.Bc3 Rb7 27.Re8 Bf5 28.Nc5 Rc7 29.b4 f6 30.Ra8 a5 31.Rxa5 Bxc5 32.Rxc5
    // FEN: 2br2k1/4bppp/p4n2/4B3/Nr6/1P3B2/2P2PPP/3RR1K1 w - - 0 24
    @Test
    void testPosition17() {
        testPosition("[[b1-c3 e7-e5 g1-f3 b8-c6 d2-d4 e5-d4 f3-d4 c6-d4 d1-d4 g8-f6 e2-e4 d7-d6 c1-g5 f8-e7 f1-c4 e8-g8 e1-g1 f8-e8 f1-e1 c7-c6 g5-f4 b7-b5 c4-e2 a7-a6 e2-f3 c6-c5 d4-d3 a8-b8 e4-e5 d6-e5 d3-d8 e8-d8 f4-e5 e7-d6 a1-d1 b8-b6 a2-a4 b5-a4 c3-a4 b6-b4 a4-c5 b4-b6 c5-a4 b6-b4 b2-b3 d6-e7]]",
                Set.of("d1-d8", "e5-f6"),
                4.0f, // OPT: Should be > 7
                5.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black checkmate in 5.
    // FEN: 1r5r/k4ppp/2B1p3/pQ2Nq2/Pb1P4/6B1/5PPP/5RK1 w - - 3 26
    @Test
    void testPosition18() {
        testPosition("[[e2-e4 d7-d5 e4-d5 d8-d5 b1-c3 d5-a5 d2-d4 g8-f6 g1-f3 c8-f5 c1-d2 b8-c6 f1-c4 c6-b4 a1-c1 e7-e6 a2-a3 b4-c2 c1-c2 f5-c2 d1-c2 a5-h5 c2-a4 c7-c6 d2-f4 h5-f5 f4-g3 f6-e4 e1-g1 e4-c3 b2-c3 a7-a6 a4-b3 b7-b5 c4-e2 e8-c8 f3-e5 c8-b7 e2-f3 d8-c8 a3-a4 b5-b4 b3-c4 a6-a5 c3-b4 f8-b4 f3-c6 b7-a7 c4-b5 c8-b8]]",
                Set.of("c6-b7"),
                6.0f, // OPT: Should be checkmate in 5
                8.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Assure that the white knight on f6 is not captured with the king pawn,
    // since this would weaken blacks kind position a lot
    // TODO Test currently disabled
    @Test @Disabled
    void dontCaptureWithKingPawn() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 e7-e5 d4-b3 b8-c6 b1-c3 g8-f6 f1-e2 c8-e6 e1-g1 f8-e7 c1-e3 e8-g8 e2-f3 a7-a5 c3-d5 a5-a4 b3-d2 e6-d5 e4-d5 c6-b4 c2-c4 d8-d7 a2-a3 b4-d3 a1-b1 f8-d8 d2-e4 d3-c5 e4-f6]]\n",
                Set.of("e7-f6"),
                -0.5f,
                0.5f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // White has made a mistake with the rook move (correct move was Rc7)
    // and now looses its advantage. The only expected black answer is Rg5.
    // All other alternatives are catastrophic for black.
    // FEN: 2R5/1p2bqBk/p2p4/3Ppr2/3p2Q1/P2P3P/1P3PP1/6K1 b - - 2 23
    @Test
    void testPosition19() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8
                """;
        testPosition(pgn,
                Set.of("Rg5"),
                0.0f,
                0.2f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Follow-on position of test 19. Black has made a huge mistake with the queen move and now has a lost position.
    // Correct move was Rg5 (see test 19).
    // FEN: 2R5/1p2b1Bk/p2p2q1/3Ppr2/3p2Q1/P2P3P/1P3PP1/6K1 w - - 3 24
    @Test
    void testPosition20() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8 Qg6
                """;
        testPosition(pgn,
                Set.of("Rh8+"),
                8.0f,
                15.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Follow-on position of test 19 (2 moves further). Position is already lost for black.
    // In this situation there exists only one strong move for white.
    // All other possibilities are really weak.
    @Test
    void testPosition21() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8 Qg6 24.Rh8+ Kxg7
                """;
        testPosition(pgn,
                Set.of("Rg8+"),
                8.0f,
                19.0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Lost position for black. White should play Qf2, h5 or Qg3.
    // Otherwise, the pawn on h4 will be lost and white looses the big advantage.
    // Weight: 4.5 - 5
    @Test
    void testPosition22() {
        var pgn = """
                1. e4 c5 2. Nf3 e6 3. d4 cxd4 4. Nxd4 Bc5 5. Be3 Nf6 6. Nc3 Bb4 7. Bd2 Bxc3 8.
                Bxc3 O-O 9. Bd3 e5 10. Nf5 d6 11. Qf3 Nc6 12. O-O-O g6 13. Nh6+ Kg7 14. Qe3 Ng4
                15. Nxg4 Bxg4 16. f3 Be6 17. Kb1 Qc7 18. h4 h5 19. Rdg1 a5 20. g4 Rh8 21. gxh5
                Rxh5 22. f4 f6 23. f5 Bf7 24. fxg6 Be6 25. Be2 Rh6 26. Bd2 Rah8
                """;
        testPosition(pgn,
                Set.of("Qf2", "h5", "Qg3"),
                "e3-g3 c6-b4 d2-h6 h8-h6 c2-c4 c7-c6 g3-e3".split(" "), // + "h6-g6"
                1.7f, // TODO > 4.5
                2.0f, // TODO 5.0
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // White (myself) has made a big mistake with the last move c3.
    // The big advantage is lost, since black can ow capture the pawn on h4.
    // Note: Correct moves for white are tested in testPosition22.
    // Expected move: Rxh4, weight: 1.7
    @Test
    void testPosition23() {
        var pgn = """
                1. e4 c5 2. Nf3 e6 3. d4 cxd4 4. Nxd4 Bc5 5. Be3 Nf6 6. Nc3 Bb4 7. Bd2 Bxc3 8.
                Bxc3 O-O 9. Bd3 e5 10. Nf5 d6 11. Qf3 Nc6 12. O-O-O g6 13. Nh6+ Kg7 14. Qe3 Ng4
                15. Nxg4 Bxg4 16. f3 Be6 17. Kb1 Qc7 18. h4 h5 19. Rdg1 a5 20. g4 Rh8 21. gxh5
                Rxh5 22. f4 f6 23. f5 Bf7 24. fxg6 Be6 25. Be2 Rh6 26. Bd2 Rah8 27. c3
                """;
        testPosition(pgn,
                Set.of("Rxh4"),
                -0.2f, // TODO 1.7
                0f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // One of my own chess.com games (playing black).
    // Black has missed a winning opportunity: e5
    // This will win material. Expected weight: -5.3
    @Test
    void testPosition24() {
        var pgn = """
                1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. d4 exd4 5. O-O Nxe4 6. Re1 d5 7. Bxd5 Qxd5
                8. Nc3 Qh5 9. Nxe4 Be6 10. Ng3 Qd5 11. Ne2 O-O-O 12. Nf4 Qd7 13. Nxe6 fxe6
                14. Bf4 h6 15. Qe2 Bb4 16. Red1 Rhe8 17. Rd3
                """;
        testPosition(pgn,
                Set.of("e5"),
                -2.1f, // TODO -5.3
                -1.8f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // One of my own chess.com games (same as above) (playing black).
    // White should play Rd2, the best move, although others are not that bad.
    // Expected weight: -0.9
    @Test
    void testPosition25() {
        var pgn = """
                1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. d4 exd4 5. O-O Nxe4 6. Re1 d5 7. Bxd5 Qxd5 8.
                Nc3 Qh5 9. Nxe4 Be6 10. Ng3 Qd5 11. Ne2 O-O-O 12. Nf4 Qd7 13. Nxe6 fxe6 14. Bf4
                h6 15. Qe2 Bb4 16. Red1 Rhe8 17. Rd3 g5 18. Ne5 Nxe5 19. Bxe5 Qb5 20. c3 Bd6 21.
                Bxd6 Rxd6 22. Qc2 e5 23. Rad1 Rc6 24. Qe2 Rce6 25. f3 e4 26. fxe4 Rxe4 27. Qc2
                dxc3 28. Qxc3 Qb6+ 29. Kf1 Rf4+ 30. Rf3 Qb5+ 31. Qd3 Qxd3+ 32. Rxd3 Rxf3+ 33.
                gxf3 Rd8 34. Re3 Rf8 35. Kg2 b6 36. Kg3 Rd8 37. Re2 h5 38. h4 gxh4+ 39. Kxh4 Rd5
                40. Re7 Rf5 41. Re3 Kb7 42. Kg3 a5 43. f4 a4 44. Kf3 Rb5 45. Re2 Kc6
                """;
        testPosition(pgn,
                Set.of("Kg3"), // TODO Rd2
                -1.1f,
                -0.8f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    @SuppressWarnings("SameParameterValue")
    static EngineConfig engineConfig() {
        return new EngineConfig.Builder()
                .maxDepth(8)
                .build();
    }

    static void testPosition(String gameNotation, String expectedMove, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        testPosition(gameNotation, Set.of(expectedMove), expectedMinWeight, expectedMaxWeight, config);
    }

    static void testPosition(String gameNotation, Set<String> expectedMoves, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        testPosition(gameNotation, expectedMoves, null, expectedMinWeight, expectedMaxWeight, config);
    }

    static void testPosition(String gameNotation, Set<String> expectedMoves, String[] expectedPathOpt, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            GameImporter importer = GameImporter.importerFor(gameNotation);
            var game = importer.importGame(config);
            boolean isEngineV1 = game.getEngine() instanceof MyChessEngine1;

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

            var expectedPathDepth = config.getEngineWhiteConfig().getMaxDepth() - 1;
            if (WeightingFunction.isCheckmateWeight(expectedMinWeight)) {
                expectedPathDepth = Math.min(expectedPathDepth, WeightingFunction.checkmateWeightToPlies(expectedMinWeight));
            }
            if (!isEngineV1) {
                assertEquals(expectedPathDepth, pathLength(move.path), "Unexpected path length: " + ChessUtil.pathToString(move.path));
            }
            if (expectedPathOpt != null) {
                assertEquals(expectedPathDepth, expectedPathOpt.length, "Test setup error: Wrong length of expected path");
            }

            if (notContainsMove(game, expectedMoves, move.move)) {
                game.print();
                System.out.println(game.exportFEN());
                fail("Wrong move: " + ChessUtil.moveToString(move.move) + ". Expected one of " + expectedMoves);
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

            if (!isEngineV1) {
                for (int i = 0; i < expectedPathDepth; i++) {
                    if (expectedPathOpt != null) {
                        if (notContainsMove(game, Set.of(expectedPathOpt[i]), move.path[i])) {
                            game.print();
                            fail("Unexpected move at path depth " + i + ": " + game.getBoard().moveToShortNotation(new Move(move.path[i])) + ", expected " + expectedPathOpt[i] + ", expected path=" + Arrays.toString(expectedPathOpt) + ", actual path=" + ChessUtil.pathToString(move.path));
                        }
                    }
                    try {
                        game.makeMove(new Move(move.path[i]));
                    } catch (Exception e) {
                        System.out.println("Failed to execute move " + ChessUtil.moveToString(move.path[i]));
                        game.getBoard().print();
                        throw e;
                    }
                }

                if (WeightingFunction.isCheckmateWeight(expectedMinWeight)) {
                    assertEquals(GameResult.CHECKMATE, game.getResult(), "Game result should be checkmate");
                }
                assertEquals(move.result, game.getResult(), "Unexpected game result");
            }

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean notContainsMove(Game game, Collection<String> moveStrings, int move) {
        return !moveStrings.contains(ChessUtil.moveToString(move)) && !moveStrings.contains(game.getBoard().moveToShortNotation(new Move(move)).toString());
    }

    private static Object pathLength(int[] path) {
        int len = 0;
        //noinspection StatementWithEmptyBody
        for (int i = 0; i < path.length && path[i] != 0; i++, len++) {
        }
        return len;
    }

}
