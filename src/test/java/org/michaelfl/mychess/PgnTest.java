package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Pgn.Result;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class PgnTest {

    static final String PGN_1 = "[Event \"48. Rilton Cup 2018-19\"]\n" +
            "[Site \"Stockholm SWE\"]\n" +
            "[Date \"2018.12.31\"]\n" +
            "[Round \"5.8\"]\n" +
            "[White \"Donchenko, Alexander\"]\n" +
            "[Black \"Eggleston, David J\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"2604\"]\n" +
            "[BlackElo \"2397\"]\n" +
            "[ECO \"D37\"]\n" +
            "[EventDate \"2018.12.27\"]\n" +
            "\n" +
            "1.d4 Nf6 2.c4 e6 3.Nf3 d5 4.Nc3 Be7 5.Bf4 O-O 6.Rc1 c5 7.dxc5 Bxc5 8.e3\n" +
            "Nc6 9.a3 a6 10.cxd5 exd5 11.Bd3 h6 12.O-O Ba7 13.h3 d4 14.exd4 Nxd4 15.\n" +
            "Nxd4 Bxd4 16.Qf3 Be6 17.Rfd1 Qb6 18.Ne2 Bc5 19.b4 Be7 20.Be3 Qd6 21.Bh7+\n" +
            "Kxh7 22.Rxd6 Bxd6 23.Bf4 Bxf4 24.Nxf4 Bc8 25.Rc7 Kg8 26.Qd3 Rb8 27.Re7 Ra8\n" +
            "28.Ng6 fxg6 29.Qxg6 Ne8 30.Rxe8 Bf5 31.Rxf8+ Rxf8 32.Qd6 Rf7 33.Qd5 Bc2\n" +
            "34.g4 Ba4 35.f4 Bc6 36.Qe6 Bd7 37.Qe5 Bc6 38.f5 Kf8 39.Qb8+ Ke7 40.Kh2 h5\n" +
            "41.Qe5+ Kf8 42.g5 Rd7 43.Kg3 Kf7 44.Kh4 Re7 45.g6+ Kf8 46.Qb8+ Re8 47.Qd6+\n" +
            "Kg8 48.Kxh5 Rf8 49.Qe6+ Kh8 50.Kg5 a5 51.Qc4 1-0";

    @Test
    void testPGN1() throws IOException {
        testPGN(PGN_1, Result.WHITE_WINS, 101);
    }

    static final String PGN_2 = "[Event \"World Blitz Women 2018\"]\n" +
            "[Site \"St Petersburg RUS\"]\n" +
            "[Date \"2018.12.30\"]\n" +
            "[White \"Assaubayeva, Bibisara\"]\n" +
            "[Black \"Murashova, Ekaterina\"]\n" +
            "[Result \"0-1\"]\n" +
            "[WhiteElo \"2372\"]\n" +
            "[BlackElo \"2259\"]\n" +
            "[ECO \"D53\"]\n" +
            "[EventDate \"2018.12.29\"]\n" +
            "\n" +
            "1.d4 d5 2.c4 e6 3.Nf3 Nf6 4.Nc3 Be7 5.Bg5 Nbd7 6.e3 h6 7.Bh4 O-O 8.Bd3 c5\n" +
            "9.cxd5 Nxd5 10.Bxe7 Qxe7 11.Nxd5 exd5 12.dxc5 Nxc5 13.O-O Bg4 14.Be2 Rfd8\n" +
            "15.Nd4 Bxe2 16.Qxe2 Rac8 17.Rfd1 Ne6 18.Nxe6 fxe6 19.Rac1 a6 20.h3 Rc5 21.\n" +
            "Qg4 Rdc8 22.Rxc5 Rxc5 23.Qb4 Qc7 24.a3 Rc2 25.g3 b5 26.Qd4 Rc4 27.Qd3 Qe5\n" +
            "28.Rd2 Rc1+ 29.Kg2 Qc7 30.Rd1 Rc4 31.Qg6 Qc6 32.Qd3 Rc2 33.Rd2 Rc1 34.Rd1\n" +
            "d4+ 35.Kg1 dxe3 36.fxe3 Rxd1+ 37.Qxd1 Qe4 38.Qd8+ Kh7 39.Qd2 h5 40.h4 Kh6\n" +
            "41.Kf2 Qf5+ 42.Kg2 Qe4+ 43.Kf2 Kg6 44.Qc3 Qf5+ 45.Kg2 Qd5+ 46.Kf2 e5 47.\n" +
            "Qc2+ e4 48.Ke2 Kh7 49.Qd2 Qf5 50.Qd4 Qg6 51.Kd2 Qc6 52.Qe5 Qd7+ 53.Ke1 Qg4\n" +
            "54.Qf4 Qxf4 55.exf4 Kg6 56.Kf2 Kf5 57.Ke3 Kg4 58.Kxe4 Kxg3 59.Kf5 Kxh4 60.\n" +
            "Kg6 Kg4 61.f5 h4 62.Kxg7 Kxf5 0-1";

    @Test
    void testPGN2() throws IOException {
        testPGN(PGN_2, Result.BLACK_WINS, 124);
    }

    static final String PGN_3 = "[Event \"4. IIFL Wealth Mumbai Op\"]\n" +
            "[Site \"Mumbai IND\"]\n" +
            "[Date \"2018.12.31\"]\n" +
            "[Round \"2.9\"]\n" +
            "[White \"Sundararajan, Kidambi\"]\n" +
            "[Black \"Ziatdinov, Raset\"]\n" +
            "[Result \"1/2-1/2\"]\n" +
            "[WhiteElo \"2458\"]\n" +
            "[BlackElo \"2252\"]\n" +
            "[ECO \"A25\"]\n" +
            "[EventDate \"2018.12.30\"]\n" +
            "\n" +
            "1.c4 e5 2.Nc3 Nc6 3.e3 Nf6 4.a3 Be7 5.Nf3 O-O 6.Be2 d6 7.d4 exd4 8.Nxd4\n" +
            "Nxd4 9.Qxd4 Be6 10.Nd5 c5 11.Nxe7+ Qxe7 12.Qh4 d5 13.cxd5 Bxd5 14.f3 Qe6\n" +
            "15.O-O Nd7 16.Bd2 f5 17.Rac1 Rac8 18.Rfe1 Ne5 19.Bc3 Ng6 20.Qf2 Bb3 21.Bf1\n" +
            "a6 22.Qg3 Qe7 23.Bd3 Rc6 24.Qf2 Re6 25.g3 h5 26.h4 b5 27.f4 Bd5 28.Be2 Kf7\n" +
            "29.Bxh5 Rh8 30.Rcd1 Bb3 31.Bf3 Bxd1 32.Rxd1 Rd8 33.Rd5 Kg8 34.Rxf5 Rxe3\n" +
            "35.Bd5+ Rxd5 36.Rxd5 Qe4 37.Rd1 Re2 38.Re1 Nxf4 39.Rxe2 Nxe2+ 40.Kh2 Nd4\n" +
            "41.Qf4 Qxf4 42.gxf4 Nc6 43.Kg3 b4 44.Bd2 a5 45.Kf3 c4 46.Ke4 1/2-1/2";

    @Test
    void testPGN3() throws IOException {
        testPGN(PGN_3, Result.DRAW, 91);
    }

    static final String PGN_4 = "[Event \"World Blitz 2018\"]\n" +
            "[Site \"St Petersburg RUS\"]\n" +
            "[Date \"2018.12.30\"]\n" +
            "[Round \"14.30\"]\n" +
            "[White \"Ghaem Maghami, Ehsan\"]\n" +
            "[Black \"Vokhidov, Shamsiddin\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"2537\"]\n" +
            "[BlackElo \"2480\"]\n" +
            "[ECO \"A28\"]\n" +
            "[EventDate \"2018.12.29\"]\n" +
            "\n" +
            "1.c4 e5 2.Nc3 Nf6 3.Nf3 Nc6 4.e4 Bb4 5.d3 d6 6.a3 Bxc3+ 7.bxc3 O-O 8.g3\n" +
            "Nd7 9.Bg2 Nc5 10.Nh4 Ne7 11.Be3 b6 12.O-O Bb7 13.Qc2 Qd7 14.a4 f5 15.Bh3\n" +
            "Qc6 16.f3 fxe4 17.fxe4 a5 18.Bg5 Ng6 19.Nf5 Bc8 20.Bg2 Qe8 21.Rf2 Bd7 22.\n" +
            "Raf1 Bxa4 23.Qe2 Kh8 24.h4 Ne6 25.Qg4 Nxg5 26.Qxg5 h6 27.Qg4 Rg8 28.h5 Nf8\n" +
            "29.Qh4 Ne6 30.Ne7 Ng5 31.Ng6+ Kh7 32.Bh3 Bd7 33.Rf5 a4 34.Bg4 Be6 35.Nf8+\n" +
            "Kh8 36.Ng6+ Kh7 37.Rf8 Qd7 38.Bxe6 Nxe6 39.R8f7 Qd8 40.Qg4 Ng5 41.Qf5 Qc8\n" +
            "42.Nf8+ Kh8 43.Qh7+ Nxh7 44.Ng6# 1-0";

    @Test
    void testPGN4() throws IOException {
        testPGN(PGN_4, Result.WHITE_WINS, 87);
    }

    static final String PGN_5 = "[Event \"World Blitz Women 2018\"]\n" +
            "[Site \"St Petersburg RUS\"]\n" +
            "[Date \"2018.12.30\"]\n" +
            "[Round \"17.31\"]\n" +
            "[White \"Savina, Anastasia\"]\n" +
            "[Black \"Vo, Thi Kim Phung\"]\n" +
            "[Result \"0-1\"]\n" +
            "[WhiteElo \"2334\"]\n" +
            "[BlackElo \"2352\"]\n" +
            "[ECO \"A12\"]\n" +
            "[EventDate \"2018.12.29\"]\n" +
            "\n" +
            "1.Nf3 d5 2.c4 c6 3.b3 Nf6 4.Bb2 Bf5 5.g3 e6 6.Bg2 h6 7.d3 Nbd7 8.Nbd2 Be7\n" +
            "9.O-O O-O 10.Qe1 Bh7 11.Ne5 Nxe5 12.Bxe5 Nd7 13.Bb2 Bf6 14.Bxf6 Qxf6 15.\n" +
            "Qc1 Rac8 16.Qa3 a6 17.Rac1 Rfd8 18.Qb4 Rb8 19.Qa5 Bg6 20.Rc2 Bh5 21.Nb1\n" +
            "Ne5 22.Nc3 Bg6 23.Rcc1 Bh5 24.Rc2 Bg6 25.Rcc1 h5 26.Na4 h4 27.cxd5 exd5\n" +
            "28.Qd2 hxg3 29.hxg3 Nd7 30.Nc5 Nxc5 31.Rxc5 Re8 32.Rfc1 Re7 33.Bf3 Rbe8\n" +
            "34.Kg2 Bf5 35.Rh1 Qg6 36.b4 Bg4 37.Qf4 Bxf3+ 38.Qxf3 Rxe2 39.Rcc1 Rd2 40.\n" +
            "Rcd1 Rxa2 41.Rh4 Ree2 42.Rdh1 Rxf2+ 43.Qxf2 Rxf2+ 44.Kxf2 f6 45.Rh8+ Kf7\n" +
            "46.Rb8 Qxd3 47.Rxb7+ Kg6 48.Rh4 f5 49.Rc7 Qc2+ 50.Kf3 Kf6 51.Rh8 g6 52.\n" +
            "Rf8+ Ke5 53.Rfc8 Qe4+ 54.Kf2 Qxb4 55.Rxc6 a5 56.Rxg6 Qd2+ 57.Kf3 Qd3+ 58.\n" +
            "Kf2 Qd4+ 59.Kf3 Qe4+ 60.Kf2 Qd4+ 61.Kf3 Qd1+ 62.Kf2 Kd4 63.Rd6 Qd2+ 64.Kf3\n" +
            "Qe3+ 65.Kg2 a4 66.Rcd8 Qe4+ 67.Kf2 a3 68.Ra8 Qc2+ 69.Kf3 Qb3+ 70.Kg2 Qb2+\n" +
            "71.Kh3 Kc5 72.Rda6 Qb1 73.Kg2 Qb2+ 74.Kh3 Qb1 75.Kh4 Qd1 76.Kh3 Qh1# 0-1";

    @Test
    void testPGN5() throws IOException {
        testPGN(PGN_5, Result.BLACK_WINS, 152);
    }

    static final String PGN_6 = "[Event \"2. Sharjah Masters 2018\"]\n" +
            "[Site \"Sharjah UAE\"]\n" +
            "[Date \"2018.04.16\"]\n" +
            "[Round \"6.17\"]\n" +
            "[White \"Rakhmanov, Aleksandr\"]\n" +
            "[Black \"Pranav, Vijay\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"2652\"]\n" +
            "[BlackElo \"2366\"]\n" +
            "[ECO \"A30\"]\n" +
            "[EventDate \"2018.04.12\"]\n" +
            "\n" +
            "1.Nf3 Nf6 2.c4 b6 3.g3 Bb7 4.Bg2 c5 5.O-O g6 6.b3 Bg7 7.Bb2 O-O 8.Nc3 d6\n" +
            "9.d4 cxd4 10.Qxd4 Ne4 11.Qe3 Nxc3 12.Bxc3 Bxc3 13.Qxc3 Nd7 14.Rfd1 Qc7 15.\n" +
            "Qe3 Nf6 16.Rd4 Kg7 17.Rad1 Rfe8 18.Nh4 Bxg2 19.Nxg2 Qc5 20.Qd3 b5 21.Ne3\n" +
            "bxc4 22.Rxc4 Qa5 23.Qc2 Qe5 24.h3 Qe6 25.Kg2 Rac8 26.Rc1 Rxc4 27.Qxc4 Qe5\n" +
            "28.Ng4 Nxg4 29.hxg4 d5 30.Qd3 Rd8 31.Rc5 f6 32.b4 Kf7 33.a4 a6 34.b5 axb5\n" +
            "35.axb5 Qe6 36.f3 Rd6 37.Qc2 Qe3 38.Rc6 Rd7 39.Rc3 Qd4 40.Rc8 Qe3 41.Rc3\n" +
            "Qd4 42.Rb3 Rb7 43.Qb1 Rb6 44.Qh1 Kg7 45.Qc1 Qc4 46.Qe3 Rb7 47.b6 Kf7 48.\n" +
            "Rb1 Qc2 49.Rc1 Qb2 50.Rc6 Qe5 51.Qd2 Kg7 52.Kf2 d4 53.Qd3 Qd5 54.Qe4 Qxe4\n" +
            "55.fxe4 Kf7 56.g5 Ke8 57.gxf6 exf6 58.Rxf6 Re7 59.Rd6 Kf7 60.Kf3 Re5 61.\n" +
            "Rd5 Re7 62.e5 Ke6 63.Ke4 Rb7 64.Rd6+ Ke7 65.Kxd4 Rb8 66.Kd5 Rc8 67.b7 Rb8\n" +
            "68.Kc6 Rg8 69.Kc7 Kf7 70.b8=Q Rxb8 71.Kxb8 Ke7 72.Kc7 Ke8 73.Re6+ Kf7 74.\n" +
            "Kd7 Kg7 75.Rf6 Kh6 76.e6 Kg7 77.Rf1 Kh8 78.e7 Kg8 79.e4 Kg7 80.e5 Kg8 81.\n" +
            "e6 Kg7 82.e8=N+ Kh8 83.Rf7 Kg8 84.Nf6+ Kh8 85.Nxh7 Kg8 86.Nf8 Kh8 87.Nxg6+\n" +
            "Kg8 88.Ne5 Kh8 89.g4 Kg8 90.g5 Kh8 91.g6 Kg8 92.g7 Kh7 93.e7 Kh6 94.e8=Q\n" +
            "Kh7 95.g8=N+ Kh8 96.Ng6# 1-0";

    @Test
    void testPGN6() throws IOException {
        testPGN(PGN_6, Result.WHITE_WINS, 191);
    }

    private void testPGN(String pgnString, Result expectedResult, int expectedPlyCount) throws IOException {
        var pgnList = new ArrayList<Pgn>();
        Pgn.parse(pgnString).forEach(pgnList::add);

        assertEquals(1, pgnList.size(), "should be exactly one PGN");

        var pgn = pgnList.get(0);
        assertEquals(expectedPlyCount, pgn.moves.size(), "wrong number of moves (plies)");
        assertEquals(expectedResult, pgn.result, "wrong game result");

        pgnString = pgnString.substring(pgnString.indexOf("1."));
        var tokens = pgnString.split("\\s");
        var moves = new ArrayList<MoveDescription>();

        for (int i = 0; i < tokens.length - 1; i++) {
            var token = tokens[i];
            int i1 = token.indexOf('.');
            if (i1 > 0) {
                token = token.substring(i1 + 1);
            }
            if (!token.isEmpty()) {
                moves.add(MoveDescription.fromString(token, moves.size() % 2 == 0 ? GameStatus.TURN_WHITE : GameStatus.TURN_BLACK));
            }
        }

        assertEquals(moves.size(), pgn.moves.size(), "wrong number of moves");

        for (int i = 0; i < moves.size(); i++) {
            var m1 = moves.get(i);
            var m2 = pgn.moves.get(i);
            if (!m1.equals(m2)) {
                fail("Wrong move " + i + ": expected " + m1 + ", actual " + m2);
            }
        }
    }
}
