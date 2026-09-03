package org.michaelfl.mychess;

/**
 * King-safety quantities other than attack units, for pre-screening against Stockfish's static
 * evaluation before any of them is built into {@link WeightingFunction}.
 *
 * <p><b>Why screen at all.</b> The attack-unit term cost four attempts, a fitted curve, a 22 %
 * NPS regression and a −42.9 Elo match to establish that the quantity carries about 80 cp of
 * missing evaluation at its loudest and 1.3 % of the residual variance overall. That number was
 * obtainable in an hour with `tools/king-attack-vs-stockfish.py` and the cached Stockfish
 * evaluations, without writing a line of production code. Every further candidate gets measured
 * that way first.
 *
 * <p><b>What the screen can and cannot say.</b> It measures how much *evaluation* myChess is
 * missing as a function of the feature, against a far stronger static evaluator. A **flat**
 * result is a reliable stop signal: a feature that explains nothing of the gap cannot help. A
 * **strong** result is not a promise — attack units screened at 1.3 % and still lost 42.9 Elo,
 * because a static term also has to survive the search, the clock and its own cost. Use it to
 * reject, not to predict.
 *
 * <p>Both quantities here are deliberately cheap to compute in production, which is the second
 * lesson of the attack-unit attempt: the term itself was nearly free, and the bookkeeping around
 * it cost a fifth of the node rate.
 *
 * @author Michael Fleischhauer
 */
final class KingSafetyFeatures {

    /** Files either side of the king that a storm is read from, plus the king's own. */
    private static final int STORM_FILE_SPREAD = 1;

    /** Highest storm index the screen distinguishes; above it the isotonic fit clamps. */
    static final int MAX_STORM = 8;

    /** Rank distances from the king at which an enemy pawn still scores, closest first. */
    private static final int[] STORM_VALUE_BY_DISTANCE = {0, 3, 2, 1};

    private static final int FILES = 8;

    /** Files the control square sits from the king; 4 keeps it clear of the king's own lines. */
    private static final int PLACEBO_FILE_SHIFT = 4;

    private KingSafetyFeatures() {
        // measurement helper
    }

    /**
     * How hard the opponent is storming {@code kingColor}'s king with pawns.
     *
     * <p><b>This is not the shelved pawn shield.</b> That term scored how far the king's *own*
     * shelter pawns had advanced and measured −57.5 Elo standalone in 2026. This one scores how
     * far the *enemy's* pawns have advanced toward the king. Strong classical engines carry both,
     * as separate per-file tables; myChess has tried neither successfully and has never tried
     * this one at all.
     *
     * <p>Per file — the king's and its two neighbors — the most advanced enemy pawn scores by
     * how close it has come to the king's rank: adjacent 3, two ranks away 2, three away 1,
     * nothing beyond. Summed over the three files, so the range is 0…9, and clamped to
     * {@link #MAX_STORM} for the fit.
     *
     * <p>The choice of three files and a 3/2/1 ramp is a first cut, not a calibration. If the
     * screen shows signal, the shape is what a real fit would place — a screen only has to tell
     * a live quantity from a dead one.
     *
     * @param board     the position
     * @param kingColor {@link GameStatus#TURN_WHITE} or {@link GameStatus#TURN_BLACK} — the side
     *                  whose king is being stormed
     * @return the storm index, 0 if that king is not on the board
     */
    static int stormAgainst(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final boolean defenderIsWhite = kingColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, defenderIsWhite ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final byte enemyPawn = defenderIsWhite ? Board.blackPawn : Board.whitePawn;
        final int relative = kingField - Board.a1;
        final int kingRank = relative / Board.LENGTH;
        final int kingFile = relative % Board.LENGTH;
        int storm = 0;

        for (int offset = -STORM_FILE_SPREAD; offset <= STORM_FILE_SPREAD; offset++) {
            final int file = kingFile + offset;

            if (file < 0 || file >= FILES) {
                continue;
            }

            storm += stormOnFile(squares, file, kingRank, enemyPawn);
        }

        return Math.min(storm, MAX_STORM);
    }

    /** The nearest enemy pawn on one file, scored by its rank distance from the king. */
    private static int stormOnFile(byte[] squares, int file, int kingRank, byte enemyPawn) {
        int best = 0;

        for (int rank = 0; rank < FILES; rank++) {
            if (squares[Board.a1 + rank * Board.LENGTH + file] != enemyPawn) {
                continue;
            }

            final int distance = Math.abs(rank - kingRank);

            if (distance < STORM_VALUE_BY_DISTANCE.length) {
                best = Math.max(best, STORM_VALUE_BY_DISTANCE[distance]);
            }
        }

        return best;
    }

    /** Rank distance at which a storming pawn stops counting; beyond it there is no file. */
    private static final int STORM_REACH = 7;

    /** Raw dense-storm points per index level; 18 possible points over 9 levels. */
    private static final int DENSE_POINTS_PER_LEVEL = 2;

    /**
     * The same idea as {@link #stormAgainst}, encoded densely instead of with a cutoff.
     *
     * <p><b>Why a second encoding.</b> The first one scored an enemy pawn only within three ranks
     * and reached its upper indices in 0.5 % of positions — self-play and human games alike, so
     * the sparsity was the encoding rather than the corpus. A coefficient resting on 0.5 % of the
     * data measures nothing. Classical engines score <em>every</em> enemy pawn by file and rank,
     * which is non-zero almost always and spreads across the range; this mirrors that.
     *
     * <p>Per file — the king's and its two neighbors — the most advanced enemy pawn contributes
     * {@code STORM_REACH − rankDistance}, so an adjacent pawn scores 6 and one seven ranks away
     * scores 0. Summed over three files the raw range is 0…18, bucketed in twos so the isotonic
     * fit sees indices 0…8. A starting position lands near 1, not 0 — the index measures relative
     * advancement, and the fit places what that is worth.
     *
     * @param board     the position
     * @param kingColor the side whose king is being stormed
     * @return the bucketed storm index, 0 if that king is not on the board
     */
    static int denseStormAgainst(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final boolean defenderIsWhite = kingColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, defenderIsWhite ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final byte enemyPawn = defenderIsWhite ? Board.blackPawn : Board.whitePawn;
        final int relative = kingField - Board.a1;
        final int kingRank = relative / Board.LENGTH;
        final int kingFile = relative % Board.LENGTH;
        int points = 0;

        for (int offset = -STORM_FILE_SPREAD; offset <= STORM_FILE_SPREAD; offset++) {
            final int file = kingFile + offset;

            if (file < 0 || file >= FILES) {
                continue;
            }

            points += densePointsOnFile(squares, file, kingRank, enemyPawn);
        }

        return Math.min(points / DENSE_POINTS_PER_LEVEL, MAX_STORM);
    }

    /** The nearest enemy pawn on one file, scored on the full rank range. */
    private static int densePointsOnFile(byte[] squares, int file, int kingRank, byte enemyPawn) {
        int best = 0;

        for (int rank = 0; rank < FILES; rank++) {
            if (squares[Board.a1 + rank * Board.LENGTH + file] != enemyPawn) {
                continue;
            }

            best = Math.max(best, Math.max(0, STORM_REACH - Math.abs(rank - kingRank)));
        }

        return best;
    }

    /** Danger level of one file in front of the king, 0 (sheltered) to 4 (open with a heavy piece). */
    private static final int FILE_SHELTERED = 0;
    private static final int FILE_HALF_OPEN = 1;
    private static final int FILE_HALF_OPEN_STORMED = 2;
    private static final int FILE_OPEN = 3;
    private static final int FILE_OPEN_WITH_HEAVY = 4;

    /**
     * The state of the files around the king, as a summed danger index.
     *
     * <p>The classical "open and half-open lines toward the king" term, which strong hand-crafted
     * engines carry alongside the shelter and storm tables. Each of the king's file and its two
     * neighbors is classified on an ordered scale, and the three are summed:
     *
     * <table>
     *   <caption>Per-file danger</caption>
     *   <tr><th>level</th><th>state</th></tr>
     *   <tr><td>0</td><td>an own pawn stands on the file — sheltered</td></tr>
     *   <tr><td>1</td><td>half-open: the own pawn is gone, an enemy pawn remains</td></tr>
     *   <tr><td>2</td><td>…and that enemy pawn has crossed the middle onto the king's half</td></tr>
     *   <tr><td>3</td><td>open: neither pawn</td></tr>
     *   <tr><td>4</td><td>…and an enemy rook or queen stands on it</td></tr>
     * </table>
     *
     * <p><b>One simplification worth naming.</b> Level 0 covers both "own and enemy pawn present"
     * — a genuinely closed file — and "own pawn present, enemy pawn gone". The second is
     * half-open from the enemy's side, but from the king's point of view it is still sheltered,
     * which is what this measures. Splitting them would need a sixth level and the screen can say
     * whether the five already carry anything.
     *
     * <p><b>And it overlaps with {@link #virtualQueenMobility} by construction.</b> Both ask how
     * open the lines around the king are; this one asks in a structured way about three files,
     * the other in one number about eight directions. The screen's job is to say whether the
     * structure buys anything over the single number — not to establish that open lines matter,
     * which mobility already showed.
     *
     * <p><b>Returns the raw sum, 0…12, deliberately unbucketed.</b> A first version clamped it at
     * 8 and destroyed the top of its own scale: three open files score 9 and three open files
     * with heavy pieces on them score 12, so both collapsed onto the same index — and a pair of
     * bare kings in an endgame sat at the ceiling from the start. The caller buckets, after
     * looking at the distribution, and reports occupancy alongside every coefficient.
     *
     * @param board     the position
     * @param kingColor the side whose king's files are examined
     * @return the summed danger index, 0 if that king is not on the board
     */
    static int fileDangerAround(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final boolean defenderIsWhite = kingColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, defenderIsWhite ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final int kingFile = (kingField - Board.a1) % Board.LENGTH;
        int danger = 0;

        for (int offset = -STORM_FILE_SPREAD; offset <= STORM_FILE_SPREAD; offset++) {
            final int file = kingFile + offset;

            if (file >= 0 && file < FILES) {
                danger += fileDanger(squares, file, defenderIsWhite);
            }
        }

        return danger;
    }

    /**
     * A six-level variant that splits level 0, for screening against the five-level scale.
     *
     * <p>The five-level scale treats "own and enemy pawn present" and "own pawn present, enemy
     * pawn gone" alike, because from the king's point of view both are shelter. The second is
     * half-open from the enemy's side, though, and a rook behind it has a target the moment the
     * pawn moves. Whether that distinction carries anything is a question for the screen, not for
     * an argument: the two encodings differ in one level and the fit says which explains more.
     *
     * <p>Levels: 0 both pawns (truly closed), 1 own pawn only, then 2/3/4/5 as the five-level
     * scale's 1/2/3/4. Raw sum 0…15.
     *
     * @param board     the position
     * @param kingColor the side whose king's files are examined
     * @return the summed danger index, 0 if that king is not on the board
     */
    static int fileDangerSplitAround(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final boolean defenderIsWhite = kingColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, defenderIsWhite ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final int kingFile = (kingField - Board.a1) % Board.LENGTH;
        int danger = 0;

        for (int offset = -STORM_FILE_SPREAD; offset <= STORM_FILE_SPREAD; offset++) {
            final int file = kingFile + offset;

            if (file >= 0 && file < FILES) {
                danger += splitFileDanger(squares, file, defenderIsWhite);
            }
        }

        return danger;
    }

    /** Classifies one file on the six-level scale of {@link #fileDangerSplitAround}. */
    private static int splitFileDanger(byte[] squares, int file, boolean defenderIsWhite) {
        final byte ownPawn = defenderIsWhite ? Board.whitePawn : Board.blackPawn;
        final byte enemyPawn = defenderIsWhite ? Board.blackPawn : Board.whitePawn;
        boolean ownSeen = false;
        boolean enemySeen = false;

        for (int rank = 0; rank < FILES; rank++) {
            final byte piece = squares[Board.a1 + rank * Board.LENGTH + file];

            if (piece == ownPawn) {
                ownSeen = true;
            } else if (piece == enemyPawn) {
                enemySeen = true;
            }
        }

        if (ownSeen) {
            return enemySeen ? 0 : 1;
        }

        return 1 + fileDanger(squares, file, defenderIsWhite);
    }

    /**
     * The control for {@link #fileDangerAround}: the identical classification, four files away.
     *
     * <p>Open files near a king also mean the position is open, and a regression cannot tell
     * "this king is exposed" from "there is little left on the board". Reading the same three-file
     * window centered on a square that is not a king, at the same rank, isolates the part that is
     * about the king. Whatever the control earns is openness rather than exposure.
     *
     * <p>Written because the mobility control's result was about to be carried over to file danger
     * by assertion. The two features look alike, but a control that transfers by resemblance is
     * not a control.
     *
     * @param board     the position
     * @param kingColor the side whose king supplies the rank and the offset
     * @return the summed danger index of the control window, 0 if that king is not on the board
     */
    static int placeboFileDanger(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final boolean defenderIsWhite = kingColor == GameStatus.TURN_WHITE;
        final int kingField = findKing(squares, defenderIsWhite ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final int centerFile = ((kingField - Board.a1) % Board.LENGTH + PLACEBO_FILE_SHIFT) % FILES;
        int danger = 0;

        for (int offset = -STORM_FILE_SPREAD; offset <= STORM_FILE_SPREAD; offset++) {
            final int file = centerFile + offset;

            if (file >= 0 && file < FILES) {
                danger += fileDanger(squares, file, defenderIsWhite);
            }
        }

        return danger;
    }

    /** Classifies one file on the scale documented at {@link #fileDangerAround}. */
    private static int fileDanger(byte[] squares, int file, boolean defenderIsWhite) {
        final byte ownPawn = defenderIsWhite ? Board.whitePawn : Board.blackPawn;
        final byte enemyPawn = defenderIsWhite ? Board.blackPawn : Board.whitePawn;
        final byte enemyRook = defenderIsWhite ? Board.blackRook : Board.whiteRook;
        final byte enemyQueen = defenderIsWhite ? Board.blackQueen : Board.whiteQueen;
        boolean enemyPawnSeen = false;
        boolean enemyPawnPastMiddle = false;
        boolean enemyHeavySeen = false;

        for (int rank = 0; rank < FILES; rank++) {
            final byte piece = squares[Board.a1 + rank * Board.LENGTH + file];

            if (piece == ownPawn) {
                return FILE_SHELTERED;
            }

            if (piece == enemyPawn) {
                enemyPawnSeen = true;

                // "Past the middle" means standing on the defending king's half of the board.
                if (defenderIsWhite ? rank <= 3 : rank >= 4) {
                    enemyPawnPastMiddle = true;
                }
            } else if (piece == enemyRook || piece == enemyQueen) {
                enemyHeavySeen = true;
            }
        }

        if (enemyPawnSeen) {
            return enemyPawnPastMiddle ? FILE_HALF_OPEN_STORMED : FILE_HALF_OPEN;
        }

        return enemyHeavySeen ? FILE_OPEN_WITH_HEAVY : FILE_OPEN;
    }

    /**
     * Squares a queen standing on {@code kingColor}'s king square could move to.
     *
     * <p>The cheapest exposure metric there is: open files, ranks and diagonals radiating from
     * the king all show up in one number, without enumerating attackers and without an attack
     * map. Several strong classical engines carry it for exactly that reason.
     *
     * <p>Counts empty squares along all eight rays and stops at the first occupied one, which is
     * not counted — the question is how far the lines are open, not what stands at their end.
     * Own pawns in front of the king therefore lower it, which is the intent.
     *
     * <p><b>Known limitation, stated because it bears on one of the cases this is meant to
     * address.</b> In `BlunderTest.castling960_atMove5` the king walks toward a pawn storm while
     * its own shelter is still intact, so exposure at the moment of the decision is *low*. This
     * quantity will not catch that position; {@link #stormAgainst} is the one that can.
     *
     * @param board     the position
     * @param kingColor the side whose king's exposure is measured
     * @return the count, 0 if that king is not on the board
     */
    static int virtualQueenMobility(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final int kingField = findKing(squares,
                kingColor == GameStatus.TURN_WHITE ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        int reachable = 0;

        for (int direction : Board.KING_ADJACENCY_OFFSETS) {
            int to = kingField + direction;

            while (squares[to] == Board.empty) {
                reachable++;
                to += direction;
            }
        }

        return reachable;
    }

    /**
     * Virtual-queen mobility from a square four files from the king, on the same rank.
     *
     * <p>The control the raw metric needs. Open lines radiating from a king also mean the
     * position is open, and a regression cannot tell "this king is exposed" from "there is
     * little left on the board" — the same confound the placebo zone was built for in
     * {@link KingAttackUnits#placeboCenter}. Measuring the identical quantity from a square that
     * is not a king, at the same rank and the same depth, isolates the part that is about the
     * king. Whatever the control also earns is openness rather than exposure.
     *
     * @param board     the position
     * @param kingColor the side whose king supplies the rank and the offset
     * @return the count, 0 if that king is not on the board
     */
    static int placeboQueenMobility(Board board, int kingColor) {
        final byte[] squares = board.getRawBoard();
        final int kingField = findKing(squares,
                kingColor == GameStatus.TURN_WHITE ? Board.whiteKing : Board.blackKing);

        if (kingField < 0) {
            return 0;
        }

        final int relative = kingField - Board.a1;
        final int center = Board.a1 + relative / Board.LENGTH * Board.LENGTH
                + (relative % Board.LENGTH + PLACEBO_FILE_SHIFT) % FILES;
        int reachable = 0;

        for (int direction : Board.KING_ADJACENCY_OFFSETS) {
            int to = center + direction;

            while (squares[to] == Board.empty) {
                reachable++;
                to += direction;
            }
        }

        return reachable;
    }


    private static int findKing(byte[] squares, byte king) {
        for (int field = Board.a1; field <= Board.h8; field++) {
            if (squares[field] == king) {
                return field;
            }
        }

        return -1;
    }
}
