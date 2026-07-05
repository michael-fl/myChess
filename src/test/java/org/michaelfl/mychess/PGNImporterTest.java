package org.michaelfl.mychess;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;

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
class PGNImporterTest {

    @Test
    void testPGNImport1() {
        testPGN(PgnTest.PGN_1, GameResult.ONGOING, GameStatus.TURN_BLACK, 51, Board.whiteQueen, Board.c4, Board.blackPawn, Board.a5);
    }

    @Test
    void testPGNImport2() {
        testPGN(PgnTest.PGN_2, GameResult.ONGOING, GameStatus.TURN_WHITE, 62, Board.blackKing, Board.f5, Board.whiteKing, Board.g7);
    }

    @Test
    void testPGNImport3() {
        testPGN(PgnTest.PGN_3, GameResult.ONGOING, GameStatus.TURN_BLACK, 46, Board.whiteKing, Board.e4, Board.blackPawn, Board.c4);
    }

    @Test
    void testPGNImport4() {
        testPGN(PgnTest.PGN_4, GameResult.CHECKMATE, GameStatus.TURN_BLACK, 44, Board.whiteKnight, Board.g6, Board.blackKnight, Board.h7);
    }

    @Test
    void testPGNImport5() {
        testPGN(PgnTest.PGN_5, GameResult.CHECKMATE, GameStatus.TURN_WHITE, 76, Board.blackQueen, Board.h1, Board.whiteKing, Board.h3);
    }

    @Test
    void testPGNImport6() {
        testPGN(PgnTest.PGN_6, GameResult.CHECKMATE, GameStatus.TURN_BLACK, 96, Board.whiteKnight, Board.g6, Board.blackKing, Board.h8);
    }

    @Test
    void testPGNImport7() {
        var pgn = """
                1.e4 c5 2.Be2 Nc6 3.f4 e6 4.Nf3 b6 5.O-O Bb7 6.d3 Qc7 7.c3 Nf6 8.a4 d5 9.
                e5 Nd7 10.Na3 a6 11.Qe1 Ne7 12.Nc2 Nf5 13.g4 Ne7 14.Qg3 h5 15.h3 d4 16.c4
                Nc6 17.Bd2 g6 18.Ng5 Be7 19.Ne4 O-O-O 20.a5 Nxa5 21.Rxa5 bxa5 22.Bf3 Nb8
                23.Qg2 Nc6 24.g5 Nb4 25.Bxb4 axb4 26.Na1 a5 27.Bd1 Bc6 28.Re1 a4 29.b3 a3
                30.Qa2 Kb7 31.Bf3 Kb6 32.Nc2 h4 33.Re2 Rdf8 34.Ne1 Bb7 35.Rg2 Bc6 36.Re2
                Ra8 37.Rg2 Rhg8 38.Rg4 Raf8 39.Rg2 Rh8 40.Rg4 Bb7 41.Rg2 Bc6 42.Rg4 Qd8
                43.Rg2 Rh7 44.Nd2 Bxf3 45.Nexf3 Rhh8 46.Ne4 Qc7 47.Rg4 Qc6 1/2-1/2
                """;
        testPGN(pgn, GameResult.ONGOING, GameStatus.TURN_WHITE, 47, Board.blackQueen, Board.c6, Board.whiteRook, Board.g4);
    }

    @Test
    @Disabled("Manual benchmark: requires a non-versioned 'large.pgn' test resource.")
    void testImportLargePGNFile() throws IOException {
        var classLoader = getClass().getClassLoader();
        var resource = classLoader.getResource("large.pgn");
        assert resource != null;
        var path = Path.of(resource.getFile());

        int count = testImportLargePGNFile(path);
        assertEquals(276670, count, "wrong number of PGNs");

    }

    @Test
    @Disabled("Manual benchmark: hard-coded path to a local KingBase PGN archive outside the repo.")
    void testImportMultipleLargePGNFiles() throws IOException {
        var dir = Path.of("/Users/mf/_PRIVAT_/Schach/KingBase2019-pgn/");
        try (var paths = Files.list(dir)) {
            paths.forEach(pgnFile -> {
                try {
                    System.out.println("Importing PGN file " + pgnFile);
                    testImportLargePGNFile(pgnFile);
                } catch (IOException e) {
                    fail(e);
                }
            });
        }
    }

    private int testImportLargePGNFile(Path path) throws IOException {
        var counter = new AtomicInteger();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
            Pgn.parse(reader, true).forEach(pgn -> {
                if (counter.incrementAndGet() > 0 && pgn.moves.getFirst().turn() == GameStatus.TURN_WHITE) { // skip PGNs starting with black
                    var importer = new PGNImporter(pgn);
                    try {
                        importer.importGame();
                    } catch (RuntimeException e) {
                        System.err.println("Error when importing file " + path);
                        System.err.println(pgn);
                        throw e;
                    }
                }
                if (counter.get() % 1000 == 0) {
                    System.out.println(counter);
                }
            });
        }

        assertTrue(counter.get() > 10000, "Not enough PGNs");
        return counter.get();
    }

    private void testPGN(String pgn, GameResult result, int turn, int moveCount, byte piece1, int field1, byte piece2, int field2) {
        var pgns = new ArrayList<Pgn>(1);
        Pgn.parse(pgn).forEach(pgns::add);
        var importer = new PGNImporter(pgns.getFirst());
        var game = importer.importGame();
        game.print();

        assertEquals(result, game.getResult(), "unexpected game result");
        assertEquals(turn, game.getTurn(), "unexpected turn");
        assertEquals(moveCount, game.getMoveCount(), "unexpected move count");
        assertEquals(piece1, game.getBoard().get(field1), "unexpected piece");
        assertEquals(piece2, game.getBoard().get(field2), "unexpected piece");
    }

    @Test
    void testPGNImport8() {
        var pgn = """
                [Event "World Blitz 2018"]
                [Site "St Petersburg RUS"]
                [Date "2018.12.30"]
                [Round "20.84"]
                [White "Guimaraes, Diogo Duarte"]
                [Black "Teske, Henrik"]
                [Result "1/2-1/2"]
                [WhiteElo "2278"]
                [BlackElo "2483"]
                [ECO "B32"]
                [EventDate "2018.12.29"]

                1.e4 c5 2.Nf3 Nc6 3.d4 cxd4 4.Nxd4 d6 5.Bb5 Bd7 6.O-O g6 7.b3 Bg7 8.Bb2
                Nf6 9.Nc3 O-O 10.Re1 Rc8 11.Nxc6 bxc6 12.Ba6 Rb8 13.e5 Ng4 14.exd6 exd6
                15.Be2 Qa5 16.Na4 Bxb2 17.Nxb2 Qe5 18.Bxg4 Bxg4 19.Qxg4 Qxb2 20.Qc4 c5 21.
                Rad1 Rb4 22.Qd5 Qxa2 23.Qxd6 Qxc2 24.Ra1 Rxb3 25.Rxa7 Rb1 26.Re7 Rxe1+ 27.
                Rxe1 c4 28.h4 h5 29.Qc5 Rd8 30.Qc7 Rd1 31.Rxd1 Qxd1+ 32.Kh2 Qd4 33.Kg1
                Qxh4 34.Qb8+ Kh7 35.Qa7 Qf4 36.Qc5 Kg8 37.Qc8+ Kh7 38.Qb7 Qc1+ 39.Kh2 Qf4+
                40.Kg1 c3 41.Qc6 Qc1+ 42.Kh2 Qf4+ 43.Kg1 Qe5 44.Qc4 Kg8 45.Qc8+ Kh7 46.Qc4
                Qe1+ 47.Kh2 Qe5+ 48.Kg1 Qe1+ 49.Kh2 Qxf2 50.Qxc3 Qf4+ 51.Kg1 h4 52.Qb3 Kh6
                53.Qc3 Kg5 54.Qc5+ Kh6 55.Qf8+ Kh7 56.Qc5 Qg4 57.Qc7 Qf5 58.Qc4 Qf6 59.Kh2
                Qe5+ 60.Kg1 Qf6 61.Kh2 g5 62.Qe4+ Kh6 63.Qa8 Qe5+ 64.Kg1 Kg6 65.Qg8+ Qg7
                66.Qb8 Qd4+ 67.Kh1 Qd1+ 68.Kh2 Qd5 69.Qg8+ Kf6 70.Qh8+ Ke7 71.Qh5 Qe5+ 72.
                Kh1 Qe1+ 73.Kh2 Qg3+ 74.Kh1 Qe1+ 75.Kh2 Qe5+ 76.Kh1 Ke6 77.Qh6+ f6 78.Qh5
                Qe1+ 79.Kh2 Qe5+ 80.Kh1 Qe4 81.Qe8+ Kf5 82.Qd7+ Kf4 83.Qd6+ Kf5 84.Qd7+
                Kg6 85.Qc8 Qd5 86.Qe8+ Qf7 87.Qe4+ Kh6 88.Qa8 Kg6 89.Qe4+ f5 90.Qc6+ Kh5
                91.Qa8 Qf6 92.Qe8+ Kh6 93.Kh2 Qd6+ 94.Kh1 Qf6 95.Qe3 Kg6 96.Qe8+ Kg7 97.
                Qd7+ Kh6 98.Qd2 Qc6 99.Qd8 Kg6 100.Qg8+ Kf6 101.Qf8+ Kg6 102.Qg8+ Kh5 103.
                Qb3 Qd7 104.Qf3+ g4 105.Qc3 Kg5 106.Qc1+ Kg6 107.Kh2 Qd6+ 108.Kh1 Qd5 109.
                Kh2 Qe5+ 110.Kh1 Qd6 111.Qe1 g3 112.Qe8+ Kg5 113.Qg8+ Kf4 114.Qb3 Qc6 115.
                Qb4+ Qe4 116.Qd6+ Ke3 117.Qc5+ Qd4 118.Qc1+ Qd2 119.Qc5+ Ke4 120.Qe7+ Kd3
                121.Qa3+ Qc3 122.Qd6+ Ke4 123.Qe7+ Kd3 124.Qd6+ Ke2 125.Qe6+ Qe3 126.Qa2+
                Kd3 127.Qb3+ Ke4 128.Qe6+ Kd4 129.Qd5+ Kc3 130.Qc4+ Kb2 131.Qc2+ Ka3 132.
                Qa4+ Kb2 133.Qc2+ 1/2-1/2
                """;
        testPGN(pgn, GameResult.ONGOING, GameStatus.TURN_BLACK, 133, Board.whiteQueen, Board.c2, Board.blackKing, Board.b2);
    }

    @Test
    void testPGNImport9() {
        var pgn = """
                1.e4 c5 2.Nf3 Nc6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 e5 6.Ndb5 d6 7.Bg5 a6 8.Na3
                b5 9.Nd5 Be7 10.Bxf6 Bxf6 11.c4 b4 12.Nc2 O-O 13.Qf3 Be6 14.Nxf6+ Qxf6 15.
                Qxf6 gxf6 16.Bd3 a5 17.b3 a4 18.Kd2 Ra7 19.g3 Rfa8 20.f4 Kf8 21.f5 Bd7 22.
                g4 Ke7 23.h4 h6 24.Ke3 Kd8 25.Rag1 Ke7 26.g5 hxg5 27.hxg5 fxg5 28.Rxg5
                axb3 29.axb3 Ra2 30.Rh6 Rf8 31.f6+ Kd8 32.Rgh5 Be6 33.Rh8 Rxh8 34.Rxh8+
                Kc7 35.Re8 Kd7 36.Rh8 Rb2 37.Ra8 Rxb3 38.Kd2 Nd4 39.Ne3 Ra3 40.Rxa3 bxa3
                41.Bb1 Kc6 42.Kc3 Kc5 43.Ba2 Ne2+ 44.Kd2 Ng3 45.Kd3 Nh5 46.Nc2 Nxf6 47.
                Nxa3 Nd7 48.Nc2 Nb6 49.Ne3 Bd7 50.Bb3 Bc8 51.Bd1 Ba6 52.Bh5 f6 53.Bf7 Na4
                54.Bg8 Bc8 55.Bf7 Bd7 56.Nd5 Nb2+ 57.Kc3 Na4+ 58.Kd3 Bc6 59.Nxf6 Kb4 60.
                Be8 Nc5+ 61.Ke3 Bxe8 62.Nxe8 Nb7 63.Nc7 Kxc4 64.Nd5 Kc5 65.Kd3 Nd8 66.Nf6
                Ne6 67.Kc3 Nd4 68.Nd7+ Kc6 69.Nf6 Ne6 70.Kc4 Nc5 71.Kc3 Kb5 72.Kd2 Kc4 73.
                Ke3 Ne6 74.Ne8 Kc5 75.Nf6 Nc7 76.Nd7+ Kc6 77.Nf6 Ne6 78.Nd5 Kd7 79.Nf6+
                Ke7 80.Nd5+ Ke8 81.Nf6+ Kf7 82.Nd5 Nc5 83.Nc7 Nd7 84.Nb5 Ke7 85.Nc7 Nf6
                86.Nb5 Kd7 87.Na3 Kc6 88.Nc4 Kc5 89.Nd2 Kb4 90.Kd3 Nd7 91.Nf3 Nc5+ 92.Ke3
                Ne6 93.Nh4 Kc4 94.Nf5 Kc5 95.Kd3 Nd4 96.Ne7 Nc6 97.Nf5 Na5 98.Ne7 Nb3 99.
                Nf5 Nd4 100.Ne7 Ne6 101.Nf5 Kc6 102.Ne7+ Kd7 103.Nf5 Nc5+ 104.Ke3 Ke6 105.
                Ng3 Kf6 106.Ne2 Kg5 107.Nc3 Kf6 108.Ne2 Ne6 109.Nc3 Nf4 110.Nb5 Ke7 111.
                Nc3 Ke6 112.Nb5 Kd7 113.Na3 Kc6 114.Nc4 Ne6 115.Kd3 Kc5 116.Ne3 Nf4+ 117.
                Kc3 Ne2+ 118.Kd3 Nc1+ 119.Kc3 Na2+ 120.Kd3 Nb4+ 121.Kc3 Nc6 122.Nf5 Nd4
                123.Ne3 Ne2+ 124.Kd3 Nf4+ 125.Kc3 Kc6 126.Kc4 Ne6 127.Ng4 Nc5 128.Nf6 Nb7
                129.Kd3 Nd8 130.Nd5 Kc5 131.Ne7 Nc6 132.Nf5 Nd4 133.Ne7 Nc6 134.Nf5 Nb4+
                135.Kc3 Na6 136.Kd3 Nc7 137.Kc3 Ne8 138.Ne7 Nf6 139.Kd3 Kb4 140.Nc8 Ne8
                141.Ne7 Kc5 142.Nf5 Nc7 143.Ne7 Ne6 144.Nf5 Kc6 145.Ne7+ Kd7 146.Nf5 Nc5+
                147.Ke3 Ke6 148.Ng3 Nd7 149.Ne2 Nf6 150.Nc3 Kd7 151.Kd3 Kc6 152.Nd1 Ng4
                153.Nc3 Kc5 154.Nd5 Nf2+ 155.Ke3 Nd1+ 156.Kd3 Nb2+ 157.Ke3 Na4 158.Ne7 Nb6
                159.Kd3 Nd7 160.Nd5 Kb5 161.Ne7 Nf6 162.Nf5 Kc5 163.Ne7 Nh5 164.Nf5 Nf4+
                165.Ke3 Ne6 166.Kd3 Kc6 167.Ne7+ Kd7 168.Nf5 Nc5+ 169.Ke3 Ke6 170.Ng3 Kf6
                171.Ne2 Kg5 172.Nc3 Kg4 173.Nb5 Nb7 174.Nc7 Kg3 175.Ne8 Kg2 176.Nc7 Kf1
                177.Ne8 Ke1 178.Nc7 Kd1 179.Kd3 Nc5+ 180.Kc4 Nxe4 181.Kd5 Nc3+ 182.Kxd6 e4
                183.Ne6 e3 184.Nf4 Kd2 185.Ke5 Nb5 186.Ke4 Nd6+ 187.Kf3 Nc4 188.Ke4 Nd6+
                189.Kf3 Nf5 190.Ke4 Nh4 191.Kd4 Kd1 192.Kxe3 1/2-1/2
                """;
        testPGN(pgn, GameResult.ONGOING, GameStatus.TURN_BLACK, 192, Board.whiteKing, Board.e3, Board.blackKing, Board.d1);
    }

    // -------------------------------------------------------------------
    // Non-standard starting position — covered via two entry points:
    //   (a) [FEN "..."] tag pair in the PGN header (PGN_9 already exercises
    //       this shape end-to-end here; extra tests below cover Chess960
    //       and the standard-vs-Chess960 branch)
    //   (b) explicit startFen parameter on PGNImporter / GameImporter that
    //       overrides any tag pair.
    // -------------------------------------------------------------------

    private static final String STANDARD_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final String CHESS960_START_FEN =
            "bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w KQkq - 0 1";

    /** Same starting position as PGN_9 — Black to move after 1. e4. */
    private static final String KINGS_PAWN_BLACK_TO_MOVE_FEN =
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

    @Test
    void importGame_withFenTag_startsFromThatPosition() {
        // PGN_9 header has [SetUp "1"] and [FEN "...4P3..."] (Black to move
        // after 1. e4). Importer must honor the tag and start there; the
        // first move in the notation is "1...c5" (Black), which would be
        // illegal from the standard start with White to move.
        testPGN(PgnTest.PGN_9, GameResult.ONGOING, GameStatus.TURN_WHITE, 36,
                Board.whiteKnight, Board.b5, Board.blackKing, Board.b8);
    }

    @Test
    void importGame_chess960_withVariantAndFenTag_appliesChess960Castling() {
        // Chess960 setup with corner bishops and c/f-file rooks. The
        // canonical short-castle for Black on this back rank keeps the
        // rook on its starting file (f8) — a move that would be illegal
        // under standard castling rules. Also exercises Ng8→f6 clearing
        // the king's path.
        var pgnString = """
                [Event "Chess960 castle smoke test"]
                [Variant "Chess960"]
                [SetUp "1"]
                [FEN "%s"]

                1. g3 Nf6 2. Nf3 O-O *
                """.formatted(CHESS960_START_FEN);

        var game = new PGNImporter(parseSingle(pgnString)).importGame();

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "White to move after Black's O-O");
        assertEquals(Board.blackKing, game.getBoard().get(Board.g8), "Black king on g8 after O-O");
        assertEquals(Board.blackRook, game.getBoard().get(Board.f8),
                "Chess960 O-O leaves kingside rook on its original file");
    }

    @Test
    void importGame_noFenTag_startsFromStandardPosition() {
        // Regression guard: an unmodified headerless PGN (no [FEN] tag)
        // must still start from the classical initial position.
        var pgnString = "1. e4 e5 2. Nf3 Nc6 *";

        var game = new PGNImporter(parseSingle(pgnString)).importGame();

        assertEquals(Board.whiteKing, game.getBoard().get(Board.e1), "White king on e1 (standard start)");
        assertEquals(Board.blackKnight, game.getBoard().get(Board.c6), "Black knight on c6 after 2...Nc6");
    }

    @Test
    void importGame_explicitChess960StartFen_autoDetectsChess960Castling() {
        // Chess960 setup passed as startFen — auto-detection based on
        // non-standard back-rank layout must pick Chess960 castling rules,
        // otherwise the O-O parser would map KQkq to a/h files (empty)
        // and reject the castle.
        var moveText = "1. g3 Nf6 2. Nf3 O-O *";

        var importer = GameImporter.importerFor(moveText, CHESS960_START_FEN);
        var game = importer.importGame();

        assertEquals(Board.blackKing, game.getBoard().get(Board.g8),
                "Black king on g8 after Chess960 O-O with auto-detected Chess960 rules");
        assertEquals(Board.blackRook, game.getBoard().get(Board.f8),
                "Chess960 kingside rook stays on f8 (its starting file)");
    }

    @Test
    void importGame_explicitStartFen_overridesFenTagInHeader() {
        // Header carries a [FEN "..."] tag for one position, but the
        // constructor receives a different startFen — the explicit
        // parameter must win. To make the assertion unambiguous, choose
        // an override FEN that leads to a different piece layout than
        // the header would.
        //
        // Header FEN: Black to move after 1. e4 (KINGS_PAWN_BLACK_TO_MOVE_FEN).
        // Override FEN: Chess960 corner-bishop setup, White to move.
        // Move list: only 1. g3 (works from Chess960 setup but 1. g3 as
        // WHITE's first move is illegal from the header FEN, since it's
        // Black's turn there — an inversion strong enough to expose any
        // silent fall-through to the tag).
        var pgnString = """
                [Event "Override test"]
                [SetUp "1"]
                [FEN "%s"]

                1. g3 Nf6 *
                """.formatted(KINGS_PAWN_BLACK_TO_MOVE_FEN);

        var pgn = parseSingle(pgnString);
        var importer = new PGNImporter(pgn, CHESS960_START_FEN);

        var game = importer.importGame();

        // If the override worked, we're on the Chess960 setup with a black
        // knight now on f6 (came from g8). If the header's FEN had won,
        // constructing the game would have thrown (1. g3 is illegal when
        // Black is to move) — a passing test here means the override took.
        assertEquals(Board.blackKnight, game.getBoard().get(Board.f6),
                "Chess960 knight on f6 after 1...Nf6 — override FEN was used");
        assertEquals(Board.whiteBishop, game.getBoard().get(Board.a1),
                "corner bishop preserved from override Chess960 start");
    }

    @Test
    void importGame_explicitStartFen_nullFallsBackToHeaderTag() {
        // startFen == null means "defer to the header tag". Equivalent to
        // the single-arg PGNImporter(Pgn) constructor.
        var pgnString = """
                [Event "Fallback test"]
                [SetUp "1"]
                [FEN "%s"]

                1... c5 *
                """.formatted(KINGS_PAWN_BLACK_TO_MOVE_FEN);

        var pgn = parseSingle(pgnString);
        var importer = new PGNImporter(pgn, /* startFen = */ null);

        var game = importer.importGame();

        assertEquals(Board.blackPawn, game.getBoard().get(Board.c5),
                "Black c-pawn advanced to c5 from header FEN");
    }

    @Test
    void gameImporterFactory_pgnPathAcceptsExplicitStartFen() {
        // Public entry via GameImporter.importerFor — the same override
        // behavior is available to external callers, not just to the
        // package-private PGNImporter constructor. Uses a Chess960 setup
        // so the effect of the explicit startFen is observable (the
        // corner-bishop layout differs from the standard start).
        var moveText = "1. g3 Nf6 *";

        var importer = GameImporter.importerFor(moveText, CHESS960_START_FEN);

        assertInstanceOf(PGNImporter.class, importer, "PGN move text dispatches to PGNImporter (not SimpleNotationImporter)");
        var game = importer.importGame();
        assertEquals(Board.whiteBishop, game.getBoard().get(Board.a1),
                "Chess960 corner bishop preserved — startFen honored through the factory");
        assertEquals(Board.blackKnight, game.getBoard().get(Board.f6),
                "1...Nf6 played from the Chess960 starting position");
    }

    @Test
    void chess960AutoDetection_standardStartFenIsNotChess960() {
        // Verify that passing the canonical standard-chess starting FEN
        // explicitly still parses under standard-chess rules — the auto-
        // detector must recognize the RNBQKBNR back rank and defer to
        // Fen.importFEN rather than Fen.importChess960FEN.
        var moveText = "1. e4 e5 *";

        var importer = GameImporter.importerFor(moveText, STANDARD_START_FEN);
        var game = importer.importGame();

        assertEquals(Board.whiteRook, game.getBoard().get(Board.a1),
                "standard-chess back rank preserved from start FEN");
        assertEquals(Board.blackPawn, game.getBoard().get(Board.e5),
                "1...e5 played from the standard starting position");
    }

    @Test
    void importGame_explicitStartFen_acceptsShredderCastlingSpelling() {
        // Same corner-bishop Chess960 setup as CHESS960_START_FEN but the
        // castling-rights field uses the Shredder / X-FEN file-letter
        // spelling (Cc = rooks on c-file, Ff = rooks on f-file) instead
        // of the classical KQkq shorthand. Both spellings must lead to
        // the same board — a Chess960 castling parser sees the file
        // letters, and myChess's auto-detector routes Shredder-style
        // castling through the Chess960 code path even when the back
        // rank alone would look standard.
        var shredderStartFen = "bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w FCfc - 0 1";
        var moveText = "1. g3 Nf6 2. Nf3 O-O *";

        var importer = GameImporter.importerFor(moveText, shredderStartFen);
        var game = importer.importGame();

        assertEquals(Board.blackKing, game.getBoard().get(Board.g8),
                "Black king on g8 after O-O — Shredder-castling FEN parsed via Chess960 path");
        assertEquals(Board.blackRook, game.getBoard().get(Board.f8),
                "Chess960 kingside rook stays on f8");
    }

    @Test
    void importGame_explicitStartFen_rejectsMidGameFen() {
        // Strict-validation contract: the explicit startFen parameter
        // demands a genuine game-starting position, not an arbitrary FEN.
        // Mid-game snapshots must go through the PGN [FEN] header instead
        // (which has no such restriction — see importGame_withFenTag_...).
        var pgnString = "1... c5 *";

        var ex = assertThrows(IllegalArgumentException.class,
                () -> GameImporter.importerFor(pgnString, KINGS_PAWN_BLACK_TO_MOVE_FEN));

        assertTrue(ex.getMessage().contains("not a valid game starting position"),
                "error message should identify the strict-startFen contract, got: " + ex.getMessage());
    }

    private static Pgn parseSingle(String pgnString) {
        return Pgn.parse(pgnString).findFirst()
                .orElseThrow(() -> new IllegalStateException("no PGN parsed"));
    }

}
