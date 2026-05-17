package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

/**
 * @author Michael Fleischhauer
 */
public record MyChessEnv(OpeningDB openingDB) {

    public MyChessEnv() {
        this(null);
    }

}
