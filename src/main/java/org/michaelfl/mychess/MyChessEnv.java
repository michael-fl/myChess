package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

/**
 * Ambient environment passed to engine calls — currently just the optional
 * {@link OpeningDB}. The no-arg constructor yields an empty environment for
 * tests and headless usage.
 *
 * @author Michael Fleischhauer
 */
public record MyChessEnv(OpeningDB openingDB) {

    public MyChessEnv() {
        this(null);
    }

}
