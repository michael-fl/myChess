package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

/**
 * @author Michael Fleischhauer
 */
public final class MyChessEnv {

    private final OpeningDB openingDB;

    public MyChessEnv() {
        this.openingDB = null;
    }

    public MyChessEnv(OpeningDB openingDB) {
        this.openingDB = openingDB;
    }

    public OpeningDB getOpeningDB() {
        return openingDB;
    }
}
