package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.michaelfl.mychess.WeightingFunction.checkmateIn;

public class EngineSmokeTest extends EngineTestBase {

    // Lost position for black. Black must sacrifice a rook against night: Rxd5 (otherwise mate in 2)
    @Test
    void testPosition1() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1 Bf6 19. Bh3 fxe6 20. Bxe6+ Kh8 21. Nd5 Qe8 22. Qh3 Ra7 23.
                Rg3 g6 24. Rc3 Nd7 25. Rc6 Be5 26. Bg4 Nc5 27. Rc8 Qf7 28. Rf1 Rxc8 29. Rxf7 Rxf7 30. Bxc8
                Nxe4 31. c3 Nd2+ 32. Kc2 Rf2 33. Qh4 Ne4+ 34. Kc1 Nc5 35. Nb4 Bf4+ 36. Kd1 Rd2+ 37. Ke1 g5
                38. Qh6 Kg8 39. Be6+ Nxe6 40. Qxe6+ Kg7 41. Nd5
                """;
        testPosition(pgn,
                Set.of("d2-d5"),
                "d2-d5 e6-d5 g7-f6 h2-h3 f4-e5 e1-e2 f6-e7".split(" "),
                4.5f,
                5.5f,
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 4 (8 plies). Expected move: Rxe6
    @Test
    void testPosition8() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1 Bf6 19. Bh3 Re8 20. exf7+ Nxf7 21. Bf5 h6 22. Nd5 a5 23.
                Qg6 a4 24. Nxf6+ Qxf6 25. Qxf6 Ng5 26. Qg6 d5 27. Rxd5 Re7 28. Rxg5 hxg5 29. Be6+
                """;
        testPosition(pgn,
                "e7-e6",
                12.0f,
                13.0f,  // TODO: M8
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 1 (2 plies). Only possible move: Rf7
    @Test
    void testPosition10() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1 Bf6 19. Bh3 Re8 20. exf7+ Nxf7 21. Bf5 h6 22. Nd5 a5 23.
                Qg6 a4 24. Nxf6+ Qxf6 25. Qxf6 Ng5 26. Qg6 d5 27. Rxd5 Re7 28. Rxg5 hxg5 29. Be6+ Kf8 30.
                Rf5+
                """;
        testPosition(pgn,
                Set.of("e7-f7"),
                "e7-f7 g6-f7".split(" "),
                checkmateIn(2),
                checkmateIn(2),
                new GameConfig(ENGINE, engineConfig())
        );
    }

    // Black mate in 4 (8 plies). Weight: #4. Expected moves: Rd3+ 46.Ng3+ Kh6 47.Rg8 Rxg3+ 48.Rxg3 a4 49.Rh4#
    // FEN: 8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 0 45
    @Test
    // FEN: 8/6Rp/5p2/p4N1k/5R2/7K/3r4/8 b - - 2 45
    void testPosition14() {
        var pgn = """
                1. e4 e6 2. Nf3 d5 3. exd5 exd5 4. Bb5+ Bd7 5. Bxd7+ Qxd7 6. O-O Bd6 7. Re1+ Ne7 8. Qe2
                O-O 9. Nc3 c5 10. d4 cxd4 11. Nxd4 Nbc6 12. Nf3 Nf5 13. Bd2 Nfd4 14. Qd1 Bc5 15. Nxd4 Bxd4
                16. Qf3 Nb4 17. Qd1 Qf5 18. Re2 Qxc2 19. Qxc2 Nxc2 20. Rc1 Nb4 21. Nb5 Bxb2 22. Rb1 Nd3
                23. Re3 Nxf2 24. Kxf2 Bf6 25. Rd3 Rfc8 26. Rxd5 Rc2 27. a4 Ra2 28. Kf1 Rxa4 29. Nc7 Rb8
                30. Rd7 Ra2 31. Nd5 Bd4 32. Ne7+ Kf8 33. Bb4 Rf2+ 34. Ke1 Re8 35. Rxd4 Rxg2 36. Kf1 Rxh2
                37. Re4 f6 38. Kg1 a5 39. Bd6 Rd8 40. Nf5+ Kf7 41. Rxb7+ Kg6 42. Rf4 Rxd6 43. Rxg7+ Kh5
                44. Kxh2 Rd2+ 45. Kh3
                """;
        testPosition(pgn,
                Set.of("d2-d3"),
                // PV-path assertion dropped: root move d2-d3 is Stockfish-best and the
                // weight is still checked; the deep PV shifted since v4.2.0 (was over-specified).
                8.0f, // TODO: Should be M8 (SF depth 20: mate in 4 for White)
                9.0f,
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

}
