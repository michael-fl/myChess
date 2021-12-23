package org.michaelfl.mychess.openingdb;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.MoveSorter;
import org.michaelfl.mychess.Pgn;
import org.michaelfl.mychess.Pgn.IOExceptionWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Fleischhauer
 */
final class OpeningDBImporter {

    final MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
    final int maxMoveDepth = 16;
    int pgnCounter = 0;
    int totalMovesCounter = 0;
    final Map<String, byte[]> positionMap = new HashMap<>(100000);

    void importPGNs() throws IOException {
        var dir = Path.of("/Users/mf/_PRIVAT_/Schach/KingBase2019-pgn/");
        Files.list(dir).forEach(pgnFile -> {
            try {
                System.out.println("Importing PGN file " + pgnFile);
                importPGNFile(pgnFile);
            } catch (IOException e) {
                throw new IOExceptionWrapper(e);
            }
        });

        cleanupPositionMap();

        System.out.println("\n#" + pgnCounter + ", #moves " + totalMovesCounter + ", #positions " + positionMap.size());

        buildDatabase();
        verifyDatabase();
    }

    private void cleanupPositionMap() {
        var iter = positionMap.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var dbValue = new DBValue(entry.getValue());
            if (dbValue.getPositionCount() < 20) {
                iter.remove();
            }
        }
    }

    private void buildDatabase() {
        System.out.println("Building opening database...");

        var db = OpeningDB.open();
        try (db) {
            int count = 0;
            for (var entry : positionMap.entrySet()) {
                db.put(entry.getKey(), entry.getValue());
                if (++count % 1000 == 0) {
                    db.commit();
                    System.out.println("Inserted #" + count);
                }
            }

            db.commit();
            System.out.println("Inserted #" + count);

        } catch (Error | RuntimeException e) {
            db.rollback();
            throw e;
        }
    }

    private void verifyDatabase() {
        System.out.println("Verifying opening database...");

        var db = OpeningDB.open();
        try (db) {
            int count = 0;
            for (var entry : positionMap.entrySet()) {
                var value = db.get(entry.getKey());
                if (value == null) {
                    throw new IllegalStateException("Position not found in database: " + entry.getKey());
                }
                if (!Arrays.equals(entry.getValue(), value.getBuffer())) {
                    throw new IllegalStateException("Wrong value stored for position: " + entry.getKey());
                }
                if (++count % 1000 == 0) {
                    System.out.println("Read #" + count);
                }
            }

            db.commit();
            System.out.println("Read #" + count);

        } catch (Error | RuntimeException e) {
            db.rollback();
            throw e;
        }
    }

    private void importPGNFile(Path pgnFile) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(pgnFile, StandardCharsets.ISO_8859_1)) {
            Pgn.parse(reader, true).forEach(this::importPgn);
        }
    }

    private void importPgn(Pgn pgn) {
        if (pgn.moves.get(0).turn == GameStatus.TURN_BLACK) {
            return; // skip PGNs starting with a black move
        }

        try {
            final var board = Board.createNewGame();
            final int maxDepth = maxMoveDepth * 2;
            int depth = 0;

            for (var moveDescr : pgn.moves) {
                moveDescr = Game.resolveMoveDescription(moveDescr, board, moveGenerator);
                var move = Game.moveDescriptionToMove(moveDescr, board);

                var key = board.calculatePositionKey();
                var dbValue = new DBValue(positionMap.get(key));
                dbValue.addMove(move.getMove(), moveDescr.turn, pgn.result);
                positionMap.put(key, dbValue.getBuffer());

                board.makeMove(move.getMove());
                totalMovesCounter++;

                if (++depth == maxDepth) {
                    break;
                }
            }

            if (++pgnCounter % 100 == 0) {
                System.out.println("#" + pgnCounter + ", #moves " + totalMovesCounter + ", #positions " + positionMap.size());
            }
        } catch (RuntimeException e) {
            System.err.println(pgn);
            throw e;
        }
    }

    public static void main(String[] args) throws IOException {
        var importer = new OpeningDBImporter();

        importer.importPGNs();
    }
}
