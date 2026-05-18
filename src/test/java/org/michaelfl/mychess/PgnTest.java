package org.michaelfl.mychess;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Pgn.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
    void testPGN1() {
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
    void testPGN2() {
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
    void testPGN3() {
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
    void testPGN4() {
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
    void testPGN5() {
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
    void testPGN6() {
        testPGN(PGN_6, Result.WHITE_WINS, 191);
    }

    static final String PGN_7 = "[Event \"Hastings Masters 2018-19\"]\n" +
            "[Site \"Hastings ENG\"]\n" +
            "[Date \"2018.12.31\"]\n" +
            "[Round \"4.12\"]\n" +
            "[White \"White, Stuart A\"]\n" +
            "[Black \"Bates, Richard A\"]\n" +
            "[Result \"1/2-1/2\"]\n" +
            "[WhiteElo \"2096\"]\n" +
            "[BlackElo \"2372\"]\n" +
            "[ECO \"A13\"]\n" +
            "[EventDate \"2018.12.28\"]\n" +
            "\n" +
            "1.c4 e6 2.Nc3 Bb4 3.Qb3 Nc6 4.Nf3 Nf6 5.a3 Bxc3 6.Qxc3 d6 7.b3 e5 8.Bb2\n" +
            "O-O 9.e3 Re8 10.Be2 Bg4 11.h3 Bh5 12.d3 d5 13.cxd5 Nxd5 14.Qc5 f6 15.O-O\n" +
            "Bf7 16.Qc2 Qd7 17.Nd2 Nd8 18.Rac1 Ne6 19.Ne4 b6 20.b4 a5 21.b5 a4 22.Qc6\n" +
            "Qxc6 23.bxc6 f5 24.Nc3 Nc5 25.Nb5 Ra5 26.d4 exd4 27.Nxd4 f4 28.Nf5 Ne6 29.\n" +
            "e4 Ne7 30.Nxe7+ Rxe7 31.Rfd1 Nc5 32.f3 Ra8 33.Bc3 Nb3 34.Rb1 Be6 35.Bb5\n" +
            "Kf7 36.Kf2 g5 37.Rd3 h5 38.Rbd1 Nc5 39.Rd8 Re8 40.Rxa8 Rxa8 41.Rd4 Ke8 42.\n" +
            "Rb4 Bb3 43.Rd4 Be6 44.Rd1 Kf7 1/2-1/2" +
            "\n" +
            "[Event \"4. IIFL Wealth Mumbai Op\"]\n" +
            "[Site \"Mumbai IND\"]\n" +
            "[Date \"2018.12.31\"]\n" +
            "[Round \"2.30\"]\n" +
            "[White \"Senthil, Maran K\"]\n" +
            "[Black \"Deviatkin, Andrei\"]\n" +
            "[Result \"1/2-1/2\"]\n" +
            "[WhiteElo \"2197\"]\n" +
            "[BlackElo \"2464\"]\n" +
            "[ECO \"A04\"]\n" +
            "[EventDate \"2018.12.30\"]\n" +
            "\n" +
            "1.Nf3 b6 2.e4 Bb7 3.Bc4 e6 4.Qe2 Bxe4 5.Qxe4 d5 6.Qe2 dxc4 7.O-O Nf6 8.\n" +
            "Qxc4 c6 9.d4 Qc7 10.Re1 Bd6 11.Bg5 Nbd7 12.Nbd2 b5 13.Qe2 O-O 14.Ne4 Nxe4\n" +
            "15.Qxe4 Nb6 16.Qg4 Nd5 17.h4 Rae8 18.Re2 a5 19.h5 a4 20.a3 Kh8 21.Rae1 h6\n" +
            "22.Bd2 c5 23.c3 cxd4 24.Nxd4 Qc4 25.Qf3 Rb8 26.g4 Bc5 27.g5 hxg5 28.Bxg5\n" +
            "Bxd4 29.cxd4 b4 30.h6 Rg8 31.Qxf7 Qc7 32.Qxe6 Nf4 33.Bxf4 Qxf4 34.Qe5 Qg4+\n" +
            "35.Kh2 Rb6 36.Qg3 Rxh6+ 37.Kg2 Qxd4 38.Re4 Qxb2 39.Rxb4 Qc2 40.Ree4 Rf8\n" +
            "41.Rh4 Qc6+ 42.Kg1 Rf6 43.Rxh6+ Rxh6 44.Rh4 1/2-1/2" +
            "\n" +
            "[Event \"Australian Open 2019\"]\n" +
            "[Site \"Melbourne AUS\"]\n" +
            "[Date \"2018.12.31\"]\n" +
            "[Round \"9.5\"]\n" +
            "[White \"Ikeda, Junta\"]\n" +
            "[Black \"Ryjanova, Julia\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"2421\"]\n" +
            "[BlackElo \"2308\"]\n" +
            "[ECO \"A30\"]\n" +
            "[EventDate \"2018.12.27\"]\n" +
            "\n" +
            "1.Nf3 Nf6 2.g3 b6 3.Bg2 Bb7 4.c4 c5 5.Nc3 g6 6.d4 cxd4 7.Qxd4 Nc6 8.Qf4\n" +
            "Bg7 9.O-O Rc8 10.Rb1 d6 11.b3 O-O 12.Qh4 Nb8 13.Be3 a6 14.Rbc1 Nbd7 15.Bh3\n" +
            "Rc7 16.g4 h5 17.gxh5 gxh5 18.Nd4 e6 19.f3 Ne5 20.Qg3 Kh7 21.Bg2 Rg8 22.Bg5\n" +
            "Bh6 23.h4 Rc5 24.f4 Bxg2 25.Kxg2 Neg4 26.Qd3+ Kh8 27.Nf3 Qc7 28.Kh3 Ne8\n" +
            "29.Ne4 Rf5 30.Ng3 Rxf4 31.Nxh5 Rf5 32.Kxg4 f6 33.Ng3 fxg5 34.hxg5 Rfxg5+\n" +
            "35.Nxg5 Rxg5+ 36.Kh3 Qg7 37.Rf3 Rg6 38.Rcf1 Nf6 39.Qxd6 Bg5 40.Rh1 Bh4 41.\n" +
            "Qb8+ Kh7 42.Kg2 Rh6 43.Qf4 Bg5 44.Rxh6+ Kxh6 45.Qe5 Qe7 46.Nf5+ 1-0";

    @Test
    void testPGN7() {
        var pgnString = PGN_7;
        var pgnList = new ArrayList<Pgn>();
        Pgn.parse(pgnString).forEach(pgnList::add);
        assertEquals(3, pgnList.size(), "3 PGNs expected");

        var pgnStr1 = pgnString.substring(0, pgnString.indexOf("[Event \"4. IIFL Wealth Mumbai Op\"]"));
        testPGN(pgnStr1, pgnList.get(0), Result.DRAW, 88);
        var pgnStr2 = pgnString.substring(pgnString.indexOf("[Event \"4. IIFL Wealth Mumbai Op\"]"), pgnString.indexOf("[Event \"Australian Open 2019\"]"));
        testPGN(pgnStr2, pgnList.get(1), Result.DRAW, 87);
        var pgnStr3 = pgnString.substring(pgnString.indexOf("[Event \"Australian Open 2019\"]"));
        testPGN(pgnStr3, pgnList.get(2), Result.WHITE_WINS, 91);
    }

    static final String PGN_8 = "[Event \"Varennes Open 2017\"]\n" +
            "[Site \"Montreal CAN\"]\n" +
            "[Date \"2017.10.07\"]\n" +
            "[Round \"2.5\"]\n" +
            "[White \"Hambleton, Aman\"]\n" +
            "[Black \"Morella Cabrera, Julio Antonio\"]\n" +
            "[Result \"*\"]\n" +
            "[WhiteElo \"2479\"]\n" +
            "[BlackElo \"2248\"]\n" +
            "[ECO \"B37\"]\n" +
            "[EventDate \"2017.10.06\"]\n" +
            "\n" +
            "1.Nf3 c5 2.c4 Nc6 3.Nc3 g6 4.d4 cxd4 5.Nxd4 Bg7 6.Nc2 Nf6 7.e4 d6 8.Be2\n" +
            "O-O 9.O-O a5 10.Be3 a4 11.f3 Qa5 12.Rb1 Be6 13.Qd2 Nd7 14.Rfd1 Rfc8 15.Na3\n" +
            "Qh5 16.Nd5 Bxd5 17.cxd5 Nd8 18.Bg5 f6 19.f4 Qxe2 20.Qxe2 fxg5 21.fxg5 Nf7\n" +
            "22.Rbc1 Nc5 23.Nc4 Rd8 24.e5 b5 25.exd6 bxc4 26.dxe7 Re8 27.d6 Nd3 28.Rxc4\n" +
            "Nxd6 29.Qxd3 Nxc4 30.Qxc4+ Kh8 31.Qf7 Bxb2 32.Qxe8+ *";

    @Test
    void testPGN8() {
        testPGN(PGN_8, Result.UNKNOWN, 63);
    }

    private void testPGN(String pgnString, Result expectedResult, int expectedPlyCount) {
        var pgnList = new ArrayList<Pgn>();
        Pgn.parse(pgnString).forEach(pgnList::add);

        assertEquals(1, pgnList.size(), "should be exactly one PGN");

        var pgn = pgnList.get(0);

        pgnString = pgnString.substring(pgnString.indexOf("1."));
        testPGN(pgnString, pgn, expectedResult, expectedPlyCount);
    }

    private void testPGN(String pgnString, Pgn pgn, Result expectedResult, int expectedPlyCount) {
        assertEquals(expectedPlyCount, pgn.moves.size(), "wrong number of moves (plies)");
        assertEquals(expectedResult, pgn.result, "wrong game result");

        pgnString = pgnString.substring(pgnString.indexOf("1."));
        var tokens = pgnString.split("\\s");
        var moves = new ArrayList<MoveDescription>();

        var plyCount = 0;
        for (int i = 0; i < tokens.length - 1; i++) {
            var token = tokens[i];
            int i1 = token.indexOf('.');
            if (i1 > 0) {
                token = token.substring(i1 + 1);
            }
            if (!token.isEmpty()) {
                if (token.startsWith("..")) {
                    plyCount++;
                    token = token.substring(2);
                }
                moves.add(MoveDescription.fromString(token, plyCount % 2 == 0 ? GameStatus.TURN_WHITE : GameStatus.TURN_BLACK));
                plyCount++;
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

    @Test
    @Disabled("Manual benchmark: requires a non-versioned 'large.pgn' test resource.")
    void testReadLargePGNFile() throws IOException {
        var classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("large.pgn");
        assert resource != null;
        var path = Path.of(resource.getFile());
        var counter = new AtomicInteger();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
            Pgn.parse(reader, false).forEach(pgn -> {
                if (counter.incrementAndGet() % 1000 == 0) {
                    System.out.println(counter);
                }
            });
        }

        assertEquals(276670, counter.get(), "wrong number of PGNs");
    }

    static final String PGN_9 = "[Event \"2. Bundesliga Mitte 2005/06 rounds 3-5\"]\n" +
            "[Site \"AUT\"]\n" +
            "[Date \"2006.??.??\"]\n" +
            "[Round \"6.3\"]\n" +
            "[White \"Jeric, Simon\"]\n" +
            "[Black \"Jurkovic, Hrvoje\"]\n" +
            "[Result \"0-1\"]\n" +
            "[WhiteElo \"2328\"]\n" +
            "[BlackElo \"2426\"]\n" +
            "[ECO \"B22\"]\n" +
            "[EventDate \"2006.02.03\"]\n" +
            "[SetUp \"1\"]\n" +
            "[FEN \"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1\"]\n" +
            "\n" +
            "1...c5 2.c3 Nf6 3.e5 Nd5 4.d4 cxd4 5.Nf3 Nc6 6.cxd4 d6 7.Bc4 Nb6 8.Bb3\n" +
            "dxe5 9.d5 Na5 10.Nc3 Bg4 11.Be3 Nxb3 12.Qxb3 Bxf3 13.gxf3 g6 14.a4 Qd7 15.\n" +
            "Bxb6 axb6 16.Qxb6 Bh6 17.Qb5 Qxb5 18.Nxb5 Kd7 19.Ke2 Rhc8 20.Kd3 Rc5 21.\n" +
            "Nc3 Rac8 22.Rhe1 f5 23.Ra3 Bg7 24.Rb3 R8c7 25.Re3 Rc4 26.Ke2 Rh4 27.Rb6\n" +
            "Kc8 28.Nb5 Rc2+ 29.Kd1 Rhc4 30.d6 Rc1+ 31.Kd2 R4c2+ 32.Kd3 exd6 33.Nxd6+\n" +
            "Kc7 34.a5 Bh6 35.Re2 Rc5 36.Nb5+ Kb8 0-1";

    @Test
    void testPgnStartsWithBlack() {
        testPGN(PGN_9, Result.BLACK_WINS, 71);
    }
}
