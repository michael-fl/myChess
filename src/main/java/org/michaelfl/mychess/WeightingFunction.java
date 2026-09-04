package org.michaelfl.mychess;

import java.util.Arrays;

import static org.michaelfl.mychess.Assert.__assert;

//    132           ...             143
//    120           ...             131
//    108   110(a8) ... 117(h8) 118 119
//           98(a7) ... 105(h7)
//           86(a6) ...  93(h6)
//           74(a5) ...  81(h5)
//           62(a4) ...  69(h4)
//           50(a3) ...  57(h3)
//           38(a2) ...  45(h2)  46  47
//    24 25  26(a1) ...  33(h1)  34  35
//    12 13         ...          22  23
//    00 01         ...          10  11

/**
 * Static position evaluation in centipawn units: material plus a
 * game-phase-<em>tapered</em> {@link PieceSquareTables} bonus (each piece's
 * midgame and endgame table interpolated by the remaining material via
 * {@link #blend}) plus per-piece capture/threat heuristics
 * (pawn structure including en-passant target, king safety, ...). Returns a
 * white-positive score that the engine negates at the boundary for black.
 * The {@code CHECKMATE_*} and {@code ILLEGAL_*} constants are sentinel
 * ranges above any normal material delta.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings({"StatementWithEmptyBody", "Duplicates", "PointlessArithmeticExpression",
                   "java:S115", "java:S2386", "java:S3358"})
public final class WeightingFunction {

    public static final int MIN_ALPHA = -Integer.MAX_VALUE;
    public static final int MAX_BETA = Integer.MAX_VALUE;
    public static final int ILLEGAL_WEIGHT_NEG = -1_000_000;
    public static final int ILLEGAL_WEIGHT_POS = 1_000_000;
    public static final int CHECKMATE_WEIGHT_LOW = 100_000;
    public static final int CHECKMATE_WEIGHT_HIGH = 200_000;

    private static final String DELTA_STR = ", delta=";
    private static final String WEIGHT_STR = ", weight=";
    private static final byte ATTACK_MARK_BIT = 32;
    private static final byte WHITE_KING_ATTACKED = Board.whiteKing | ATTACK_MARK_BIT;
    private static final byte BLACK_KING_ATTACKED = Board.blackKing | ATTACK_MARK_BIT;

    /**
     * Piece weight in centipawns. The queen is 1000 (not the classical 900) as a
     * v4.3.2 candidate: "two rooks = one queen", and it lifts myChess's
     * queen/rook ratio from 1.8 toward PeSTO's ~2.15. Used everywhere material is
     * counted — the evaluation, SEE, MVV-LVA move ordering, the null-move
     * zugzwang guard and the material-only search shortcut.
     */
    public static final int[] weightOfPiece = new int[Board.blackKing + 1];
    static {
        weightOfPiece[Board.whitePawn]   = 100;
        weightOfPiece[Board.whiteKnight] = 300;
        weightOfPiece[Board.whiteBishop] = 300;
        weightOfPiece[Board.whiteRook]   = 500;
        weightOfPiece[Board.whiteQueen]  = 1000;
        weightOfPiece[Board.whiteKing]   = 0;
        weightOfPiece[Board.blackPawn]   = 100;
        weightOfPiece[Board.blackKnight] = 300;
        weightOfPiece[Board.blackBishop] = 300;
        weightOfPiece[Board.blackRook]   = 500;
        weightOfPiece[Board.blackQueen]  = 1000;
        weightOfPiece[Board.blackKing]   = 0;
    }

    private static final int[] mobilityWeightOfPiece = new int[Board.blackKing + 1];
    static {
        mobilityWeightOfPiece[Board.whitePawn]   = 5;
        mobilityWeightOfPiece[Board.whiteKnight] = 40;
        mobilityWeightOfPiece[Board.whiteBishop] = 30;
        mobilityWeightOfPiece[Board.whiteRook]   = 20;
        mobilityWeightOfPiece[Board.whiteQueen]  = 3;
        mobilityWeightOfPiece[Board.whiteKing]   = 0;
        mobilityWeightOfPiece[Board.blackPawn]   = 5;
        mobilityWeightOfPiece[Board.blackKnight] = 40;
        mobilityWeightOfPiece[Board.blackBishop] = 30;
        mobilityWeightOfPiece[Board.blackRook]   = 20;
        mobilityWeightOfPiece[Board.blackQueen]  = 3;
        mobilityWeightOfPiece[Board.blackKing]   = 0;
    }

    /**
     * Game-phase weight per piece constant, summed over all pieces on the board
     * to derive the tapered-evaluation phase: knight = bishop = 1, rook = 2,
     * queen = 4, pawn = king = 0. Full starting material sums to
     * {@link #MAX_PHASE}; as pieces are traded the phase falls toward 0, sliding
     * the evaluation from the midgame tables toward the endgame tables. These
     * weights are fixed (not derived from the tunable {@link #weightOfPiece}) so
     * the phase stays a constant per position and the tapered evaluation remains
     * linear in its tunable parameters.
     */
    private static final int[] phaseWeightOfPiece = new int[Board.blackKing + 1];
    static {
        phaseWeightOfPiece[Board.whitePawn]   = 0;
        phaseWeightOfPiece[Board.whiteKnight] = 1;
        phaseWeightOfPiece[Board.whiteBishop] = 1;
        phaseWeightOfPiece[Board.whiteRook]   = 2;
        phaseWeightOfPiece[Board.whiteQueen]  = 4;
        phaseWeightOfPiece[Board.whiteKing]   = 0;
        phaseWeightOfPiece[Board.blackPawn]   = 0;
        phaseWeightOfPiece[Board.blackKnight] = 1;
        phaseWeightOfPiece[Board.blackBishop] = 1;
        phaseWeightOfPiece[Board.blackRook]   = 2;
        phaseWeightOfPiece[Board.blackQueen]  = 4;
        phaseWeightOfPiece[Board.blackKing]   = 0;
    }

    @FunctionalInterface
    private interface CalculateWeight {
        void calculate(WeightingFunction generator, int field, int color);
    }

    private static final CalculateWeight[] calculationFunctions = new CalculateWeight[Board.blackKing + 1];
    static {
        calculationFunctions[Board.whitePawn]   = WeightingFunction::_calculateForWhitePawn;
        calculationFunctions[Board.whiteKnight] = WeightingFunction::_calculateForKnight;
        calculationFunctions[Board.whiteBishop] = WeightingFunction::_calculateForBishop;
        calculationFunctions[Board.whiteRook]   = WeightingFunction::_calculateForRook;
        calculationFunctions[Board.whiteQueen]  = WeightingFunction::_calculateForQueen;
        calculationFunctions[Board.whiteKing]   = WeightingFunction::_calculateForKing;
        calculationFunctions[Board.blackPawn]   = WeightingFunction::_calculateForBlackPawn;
        calculationFunctions[Board.blackKnight] = WeightingFunction::_calculateForKnight;
        calculationFunctions[Board.blackBishop] = WeightingFunction::_calculateForBishop;
        calculationFunctions[Board.blackRook]   = WeightingFunction::_calculateForRook;
        calculationFunctions[Board.blackQueen]  = WeightingFunction::_calculateForQueen;
        calculationFunctions[Board.blackKing]   = WeightingFunction::_calculateForKing;
    }

    private static final int[] oppositeColor = new int[] { GameStatus.TURN_BLACK, GameStatus.TURN_WHITE };
    private static final int[] oppositeKing = new int[] { Board.blackKing, Board.whiteKing };
    /** Enemy rook by defending color, for the king-line walk. Index 0 = white defends. */
    private static final int[] oppositeRook = new int[] { Board.blackRook, Board.whiteRook };
    /** Enemy queen by defending color, same indexing as {@link #oppositeRook}. */
    private static final int[] oppositeQueen = new int[] { Board.blackQueen, Board.whiteQueen };
    /** Pawn of the given color; {@code ownPawn[color ^ 1]} is the enemy's. */
    private static final int[] ownPawn = new int[] { Board.whitePawn, Board.blackPawn };

    /** Phase of the full starting material (4·1 knights + 4·1 bishops + 4·2 rooks + 2·4 queens); the phase is clamped to this. */
    private static final int MAX_PHASE = 24;

    /**
     * The ordered danger scale for one file at or beside the king, from safest to worst. Level 0
     * has no constant: it is "an own pawn shelters this file" and is returned as a literal zero.
     *
     * <p>Ordinal, not additive — the distance between levels is not meaningful, only their order
     * is. The centipawn values live in {@link #KING_LINE_PENALTY}, indexed by the sum over the
     * three files, and that sum is where the non-linearity sits: three half-open files are worse
     * than three times one.
     */
    final static int KING_DANGER_HALF_OPEN = 1;
    /** Half-open and the enemy pawn has crossed onto the defending king's half. */
    final static int KING_DANGER_HALF_OPEN_ADVANCED_OPPONENT_PAWN = 2;
    /** No pawn of either color on the file. */
    final static int KING_DANGER_OPEN = 3;
    /** Open, and an enemy rook or queen stands on it. */
    final static int KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE = 4;

    private final static int[] KING_LINE_OFFSETS = { 0, -1, -1, -1, -1, -1, -1, -2 };

    /*
     * The three mirroring lookups the king-line walk needs, all indexed by defending color
     * (0 = white). They exist instead of `color == 0 ? … : …` at each use: this term is computed
     * per color and the walk runs in opposite directions, which is the combination that hides a
     * defect best — a mirror error leaves one color correct and makes the other silently
     * constant. Every such branch removed is one place that can no longer go wrong.
     */

    /** Step from one rank to the next, away from the defending king's own back rank. */
    private static final int[] ROW_OFFSET = { Board.LENGTH, -Board.LENGTH };
    /** The rank the walk ends on: the defender's eighth. */
    private static final int[] LAST_RANK = { 7, 0 };
    /** Last rank still counted as the defender's own half, for the "advanced pawn" level. */
    private static final int[] MIDDLE_RANK = { 3, 4 };

    /**
     * Penalty in centipawns for the summed king-line danger of the three files at and beside the
     * king, indexed {@code 0..12} — three files of at most
     * {@link #KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE}.
     *
     * <p><b>Fitted against game results.</b> Texel-style coordinate descent on ~1.34 M labeled
     * positions of the Zurichess + self-play hybrid, with the evaluation's linearity in the table
     * entries giving the design matrix in one pass ({@code KingLineTableTuner},
     * {@code test-results/king-line-table-texel-tune.log}).
     *
     * <p><b>This replaces a table fitted against the wrong objective.</b> The first version came
     * from isotonic least squares against Stockfish's <em>static</em> NNUE evaluation — agreement
     * with an evaluator, which is not the same thing as winning games — and it measured
     * <b>−28.9 ± 28.6 Elo</b> (SPRT H0 at 437 games). Its values were
     * {@code 0 21 42 42 77 91 95 134 138 223 223 223 223}. The re-fit halves the top of the
     * table and raises its middle: what predicts Stockfish's judgement of an open file and what
     * predicts the game's outcome are measurably different things.
     *
     * <p><b>Not a scaling problem, which was checked first.</b> A one-dimensional sweep of
     * {@link #kingLinePenaltyFactor} over two corpora put the optimum at −0.008 against the
     * shipped −0.010, a 0.07 % difference in mean squared error on a flat basin
     * ({@code test-results/king-line-factor-sweep.log}). The old table was near-optimally scaled
     * and still lost 29 Elo, so the error was in the shape.
     *
     * <p><b>Indices 6, 7 and 8 are pooled at 91.</b> The free re-fit dips at 7 then 8 — 91, 97,
     * 73.5 — and the same dip appears under both opponent-material scalings, which would mean
     * more open files scoring as less dangerous. Pool-adjacent-violators over the occupancy
     * weights merges all three, not just the two that look wrong: pooling 7 and 8 alone lands at
     * 81 and puts a fresh fall behind index 6's 91. That mistake was made here first and caught by
     * {@code WeightingFunctionKingLineTest.theTableRisesWithDanger}, which is what that test is
     * for.
     *
     * <p>Whether the dip is real is open — indices 7 and 8 carry 2.9 % of samples between them.
     * It is recorded rather than smoothed away silently, because the first fit <em>imposed</em>
     * monotonicity as a constraint of the optimisation and so could never have raised the
     * question.
     *
     * <p><b>Indices 11 and 12 carry index 10's value.</b> They hold 0.02 % and 0.00 % of samples,
     * so the tuner moved them essentially unconstrained — 231.5 and 231.5 under one scaling,
     * 222.5 and 247.5 under another. A coefficient without occupancy behind it means nothing; the
     * first pawn-storm encoding read 141.5 cp on 0.5 % of the data and fell to 28.5 cp once the
     * mass was spread.
     *
     * <p><b>What the fit does not license.</b> The re-fit lowers mean squared error by 0.00053
     * against having no term at all, where the old table managed 0.00036 — half again as much, on
     * the objective that matters. It is still a proxy on a labeled corpus. The old table also had
     * a positive proxy value and lost 29 Elo; what is new here is where the numbers come from,
     * not a promise about strength. Only a match settles it.
     */
    static final int[] KING_LINE_PENALTY = {
            0,    // 0
            26,   // 1
            32,   // 2
            66,   // 3
            68,   // 4
            73,   // 5
            91,   // 6   6, 7 and 8 pooled: the free fit dips at 7 (97) then 8 (73.5)
            91,   // 7
            91,   // 8
            125,  // 9
            153,  // 10
            153,  // 11  index 10's value; occupancy 0.02 %
            153   // 12  index 10's value; occupancy 0.00 %
    };

    private static final float mobilityFactor = 0.1f;
    private static final float positionFactor = 0.5f;
    private static final float threadWeightFactor = 0.02f;
    private static final float chessFactor = 0.25f;
    private static final float castlingFactor = 0.25f;
    /**
     * Per-doubled-pair penalty in pawn units, applied directly in the
     * final-weight formula.
     *
     * <p>The standard chess-theory value is around -0.15 pawns per doubled
     * pair; we use that value here.
     */
    private static final float doublePawnFactor = -0.15f;
    /**
     * Per-hanging-piece penalty in pawn units, applied directly in the
     * final-weight formula. A piece counts as "hanging" when it is
     * simultaneously attacked by an opposing piece AND has no own-color
     * defender (kings excluded). Tracked via the {@link #ATTACK_MARK_BIT}
     * marker on {@link #tempBoard} during the per-piece scan: every
     * {@code capture} call sets the marker on the attacked square; every
     * {@link #defend(int)} call wipes the entire square (clearing both
     * piece bits and any marker), so a defended piece never satisfies the
     * "marker bit set AND piece bits set" predicate counted in
     * {@link #calculateUndefendedPiecesCount()}.
     *
     * <p>The sign convention mirrors {@link #doublePawnFactor}: the
     * factor is negative and applied to {@code white_count - black_count},
     * so more own-side hanging pieces decrease this side's score.
     */
    private static final float undefendedPiecesFactor = -0.1f;

    /**
     * Bishop-pair bonus in pawn units, awarded once to a side holding both
     * bishops ({@code count >= 2}), applied directly in the final-weight formula.
     * Two bishops cover both color complexes and complement each other, most of
     * all in open positions — the best-established material-combination bonus in
     * computer chess (Kaufman ~0.5; engines typically 0.3-0.5). myChess had no
     * such term (knight = bishop = 300). A first fixed value, to be confirmed /
     * tuned. Note: {@code count >= 2} ignores the vanishingly rare same-color
     * double-promotion case.
     */
    private static final float bishopPairFactor = 0.4f;

    /**
     * Scales {@link #KING_LINE_PENALTY} into the evaluation. <b>Negative on purpose</b>: the
     * penalty itself is a positive "how bad is it" quantity, and that more is worse is expressed
     * by the factor's sign — the same convention as {@link #doublePawnFactor} and
     * {@link #undefendedPiecesFactor}. Read the two together or the sign in
     * {@link #calculatePositionWeight()} looks inverted.
     *
     * <p>At {@code -0.01f} the fitted table applies at exactly 1:1 in centipawns, because
     * everything inside the sum in {@link #calculatePositionWeight()} is in pawns and the
     * {@code * 100} sits outside. So this is not a cautious starting guess — it is the value at
     * which the table means what it was fitted to mean. The ninth entry of
     * {@link #TUNABLE_FACTOR_NAMES}, so the scale can be tuned against game results later rather
     * than guessed one SPRT at a time.
     */
    private static final float kingLinePenaltyFactor = -0.01f;

    private GameStatus game;
    private int turn; // 0 = white, 1 = black
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board theBoard; // For debugger only
    private byte[] board;
    private final byte[] tempBoard = new byte[Board.LENGTH * Board.LENGTH];
    private final int[] chessCount = new int[2];
    private final float[] piecesWeight = new float[2];
    private final int[] mobilityWeight = new int[2];
    private final int[] positionWeight = new int[2];
    private final int[] threadWeight = new int[2];
    private boolean containsIllegalMove;
    private final int[] castlingState = new int[2];
    private final int[] doublePawnCount = new int[2];
    private final int[] undefendedPiecesCount = new int[2];
    private final int[] bishopCount = new int[2];
    /** Per-color sum of midgame piece-square values for the current position (index 0 = white, 1 = black). */
    private final int[] pstMidGameWeight = new int[2];
    /** Per-color sum of endgame piece-square values for the current position (index 0 = white, 1 = black). */
    private final int[] pstEndGameWeight = new int[2];
    /**
     * Per-color summed king-line danger from the current evaluation, {@code 0..12}
     * (index 0 = white). Written once per king by {@code _calculateForKing} during the ordinary
     * piece walk, read by {@link #calculateKingLinePenalty(int)}.
     */
    private final int[] kingLineDanger = new int[2];
    /** Game phase of the most recently evaluated position, {@code 0..}{@link #MAX_PHASE}; see {@link #phaseWeightOfPiece}. */
    private int phase;

    /** Material weight (delta white - black) in centi pawns. */
    public static int calculateMaterialWeight(Board theBoard) {
        final byte[] board = theBoard.getRawBoard();
        final int[] piecesWeight = new int[2];

        final int stopField = 9 * Board.LENGTH + 10;

        for (int field = 2 * Board.LENGTH + 2; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
            }
        }

        return piecesWeight[0] - piecesWeight[1];
    }

    /** Get material weight of move in centi pawns. The returned value is always a positive number. */
    public static int getMaterialWeightOfMove(int move) {
        if (move == 0) {
            return 0;
        }

        final int capturedWeight = WeightingFunction.weightOfPiece[Move.getCapturedPiece(move)];
        final byte moveType = Move.getMoveType(move);
        if (moveType == Move.typeNormal || moveType == Move.typeEnPassant) {
            return capturedWeight;
        }

        final int pawnWeight = weightOfPiece[Board.whitePawn];
        return switch (moveType) {
            case Move.typePawnPromotionQueen -> weightOfPiece[Board.whiteQueen] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionKnight -> weightOfPiece[Board.whiteKnight] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionRook -> weightOfPiece[Board.whiteRook] - pawnWeight + capturedWeight;
            case Move.typePawnPromotionBishop -> weightOfPiece[Board.whiteBishop] - pawnWeight + capturedWeight;
            default -> 0;
        };
    }

    /** Calculate weight of position in centi pawns. */
    public int calculate(Board theBoard) {
        this.game = theBoard.getGameStatus();
        this.turn = game.getTurn() == GameStatus.TURN_WHITE ? 0 : 1;
        this.theBoard = theBoard;
        this.board = theBoard.getRawBoard();
        this.chessCount[0] = 0;
        this.chessCount[1] = 0;
        this.piecesWeight[0] = 0;
        this.piecesWeight[1] = 0;
        this.mobilityWeight[0] = 0;
        this.mobilityWeight[1] = 0;
        this.positionWeight[0] = 0;
        this.positionWeight[1] = 0;
        this.threadWeight[0] = 0;
        this.threadWeight[1] = 0;
        this.containsIllegalMove = false;
        this.castlingState[0] = 0;
        this.castlingState[1] = 0;
        this.doublePawnCount[0] = 0;
        this.doublePawnCount[1] = 0;
        this.undefendedPiecesCount[0] = 0;
        this.undefendedPiecesCount[1] = 0;
        this.bishopCount[0] = 0;
        this.bishopCount[1] = 0;
        this.pstMidGameWeight[0] = 0;
        this.pstMidGameWeight[1] = 0;
        this.pstEndGameWeight[0] = 0;
        this.pstEndGameWeight[1] = 0;
        this.kingLineDanger[0] = 0;
        this.kingLineDanger[1] = 0;

        System.arraycopy(board, 0, this.tempBoard, 0, Board.LENGTH * Board.LENGTH);

        final int stopField = Board.h8 + 1;
        int phase = 0;

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];

                // Accumulate every piece's PST (both phases; blended by phase
                // afterwards). The king is no longer excepted here: the crude
                // endgame king-PST skip (isEndGame / plyCount > 60) has been
                // removed, so the tapered king endgame table handles centralization.
                int packed = PieceSquareTables.getCombinedWeight(piece, field);
                pstMidGameWeight[color] += (short) packed;
                pstEndGameWeight[color] += (short) ((packed + 0x8000) >> 16);

                phase += phaseWeightOfPiece[piece];

                calculationFunctions[piece].calculate(this, field, color);
            }
        }

        phase = Math.min(phase, MAX_PHASE);

        positionWeight[0] = blend(pstMidGameWeight[0], pstEndGameWeight[0], phase);
        positionWeight[1] = blend(pstMidGameWeight[1], pstEndGameWeight[1], phase);
        this.phase = phase; // store in local field to allow tests to read the phase

        calculateCastlingState();

        calculateUndefendedPiecesCount();

        return calculatePositionWeight();
    }

    /**
     * Interpolate a midgame and an endgame weight by game phase (tapered
     * evaluation): {@code (mg·phase + eg·(MAX_PHASE − phase)) / MAX_PHASE}. At
     * {@code phase == MAX_PHASE} the result is the pure midgame weight, at
     * {@code phase == 0} the pure endgame weight. The caller passes a phase
     * already clamped to {@code [0, MAX_PHASE]}. Rounding is done with
     * {@code roundSymmetric} (round half away from zero) so it is odd
     * ({@code round(-x) == -round(x)}), which preserves the evaluation's color
     * antisymmetry once the midgame and endgame tables differ.
     *
     * @param mgWeight midgame weight
     * @param egWeight endgame weight
     * @param phase    game phase in {@code [0, MAX_PHASE]}
     * @return the phase-interpolated weight in centipawns
     */
    static int blend(int mgWeight, int egWeight, int phase) {
        final int weight = mgWeight * phase + egWeight * (MAX_PHASE - phase);

        return weight > 0 ?
                (weight + MAX_PHASE / 2) / MAX_PHASE :
                -((-weight + MAX_PHASE / 2) / MAX_PHASE);
    }

    /** The game phase of the most recently evaluated position, {@code 0..}{@link #MAX_PHASE}. */
    int getPhase() {
        return phase;
    }

    /**
     * A copy of the per-color king-line danger sums from the last evaluation (index 0 = white,
     * 1 = black), each the sum of the three files at and beside that king, {@code 0..12}.
     *
     * <p>Exists for {@code WeightingFunctionKingLineTest}: without it the term is only observable
     * through the finished evaluation, where material, piece-square tables and mobility move too,
     * so a phase-scaling defect could not be separated from any other change.
     *
     * @return a copy, so a caller cannot disturb the next evaluation
     */
    int[] getKingLineDanger() {
        return Arrays.copyOf(kingLineDanger, kingLineDanger.length);
    }

    /** A copy of the per-color midgame position-weight sums from the last evaluation (index 0 = white, 1 = black). */
    int[] getPstMidGameWeight() {
        return Arrays.copyOf(pstMidGameWeight, pstMidGameWeight.length);
    }

    /** A copy of the per-color endgame position-weight sums from the last evaluation (index 0 = white, 1 = black). */
    int[] getPstEndGameWeight() {
        return Arrays.copyOf(pstEndGameWeight, pstEndGameWeight.length);
    }

    /** The evaluation factors the offline tuner can adjust, in a fixed order. */
    public static final String[] TUNABLE_FACTOR_NAMES = {
            "positionFactor", "mobilityFactor", "threadWeightFactor",
            "castlingFactor", "chessFactor", "doublePawnFactor", "undefendedPiecesFactor",
            "bishopPairFactor", "kingLinePenaltyFactor"
    };

    /** Current values of {@link #TUNABLE_FACTOR_NAMES}, in the same order. */
    public static double[] tunableFactorValues() {
        return new double[] {
                positionFactor, mobilityFactor, threadWeightFactor,
                castlingFactor, chessFactor, doublePawnFactor, undefendedPiecesFactor,
                bishopPairFactor, kingLinePenaltyFactor
        };
    }

    /**
     * A position's evaluation together with the per-factor coefficients that
     * feed it. The evaluation is linear in the factors, so the White-POV eval is
     * a factor-independent material part plus {@code sum(features[i] * factor[i])}
     * where {@code features} are in the {@link #TUNABLE_FACTOR_NAMES} order and
     * in centipawns per unit factor. Used by the offline Texel factor tuner.
     *
     * @param eval     the White-POV evaluation in centipawns
     * @param features the per-factor coefficients for this position
     */
    public record FactorBreakdown(int eval, double[] features) {}

    /** Evaluate {@code board} and return its {@link FactorBreakdown} for tuning. */
    public FactorBreakdown analyzeFactors(Board board) {
        int eval = calculate(board);

        double[] features = {
                positionWeight[0] - positionWeight[1],
                mobilityWeight[0] - mobilityWeight[1],
                threadWeight[0] - threadWeight[1],
                (castlingState[0] - castlingState[1]) * 100.0,
                (chessCount[0] - chessCount[1]) * 100.0,
                (doublePawnCount[0] - doublePawnCount[1]) * 100.0,
                (undefendedPiecesCount[0] - undefendedPiecesCount[1]) * 100.0,
                ((bishopCount[0] >= 2 ? 1 : 0) - (bishopCount[1] >= 2 ? 1 : 0)) * 100.0,
                (calculateKingLinePenalty(0) - calculateKingLinePenalty(1)) * 100.0
        };

        return new FactorBreakdown(eval, features);
    }

    private int calculatePositionWeight() {
        if (containsIllegalMove)
            return turn == 0 ? ILLEGAL_WEIGHT_POS : ILLEGAL_WEIGHT_NEG;

        return roundSymmetric((
                  (piecesWeight[0] - piecesWeight[1]) / 100f
                + (positionWeight[0] - positionWeight[1]) / 100f * positionFactor
                + (mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor
                + (threadWeight[0] - threadWeight[1]) / 100f * threadWeightFactor
                + (castlingState[0] - castlingState[1]) * castlingFactor
                + (chessCount[0] - chessCount[1]) * chessFactor
                + (doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor
                + (undefendedPiecesCount[0] - undefendedPiecesCount[1]) * undefendedPiecesFactor
                + ((bishopCount[0] >= 2 ? 1 : 0) - (bishopCount[1] >= 2 ? 1 : 0)) * bishopPairFactor
                + (calculateKingLinePenalty(0) - calculateKingLinePenalty(1)) * kingLinePenaltyFactor) * 100);
    }

    /**
     * The king-line penalty for one color: the fitted table entry for that king's danger, blended
     * toward zero by the game phase. <b>Positive</b> — that more is worse is carried by
     * {@link #kingLinePenaltyFactor} being negative.
     *
     * <p>The blend fades the term to nothing in the endgame, which is the term's central design
     * decision: an exposed king in an endgame is often an <em>active</em> king, so the sign of
     * king exposure reverses there. An implementation that gets this backwards has no visible
     * symptom — the evaluation stays plausible and only the played moves get worse. Hence
     * package-private rather than private, so {@code WeightingFunctionKingLineTest} can assert
     * the identity directly against {@link #blend(int, int, int)}.
     *
     * @param color the defending side, 0 = white
     * @return the phase-scaled penalty, positive, {@code 0} once the phase reaches the endgame
     */
    int calculateKingLinePenalty(int color) {
        return blend(KING_LINE_PENALTY[kingLineDanger[color]], 0, phase);
    }

    /**
     * Round half-values away from zero in both directions, so the rounding
     * is antisymmetric: {@code roundSymmetric(-x) == -roundSymmetric(x)}.
     * Java's {@link Math#round(float)} rounds halves toward positive
     * infinity, which would turn an inner-eval pair of +45.5 / -45.5 into
     * +46 / -45 and silently break the white-vs-black antisymmetry of the
     * evaluation. See {@code MirrorEvalTest} for the invariant this
     * preserves.
     */
    private static int roundSymmetric(float value) {
        return value >= 0f ? Math.round(value) : -Math.round(-value);
    }

    void print() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "piecesWeight:          w=" + piecesWeight[0] + ", b=" + piecesWeight[1] + DELTA_STR + (piecesWeight[0] - piecesWeight[1]) + WEIGHT_STR + round((piecesWeight[0] - piecesWeight[1]) / 100f) + '\n' +
               "positionWeight:        w=" + positionWeight[0] + ", b=" + positionWeight[1] + DELTA_STR + (positionWeight[0] - positionWeight[1]) + WEIGHT_STR + round((positionWeight[0] - positionWeight[1]) / 100f * positionFactor) + '\n' +
               "mobilityWeight:        w=" + mobilityWeight[0] + ", b=" + mobilityWeight[1] + DELTA_STR + (mobilityWeight[0] - mobilityWeight[1]) + WEIGHT_STR + round((mobilityWeight[0] - mobilityWeight[1]) / 100f * mobilityFactor) + '\n' +
               "threadWeight:          w=" + threadWeight[0] + ", b=" + threadWeight[1] + DELTA_STR + (threadWeight[0] - threadWeight[1]) + WEIGHT_STR + round((threadWeight[0] - threadWeight[1])  / 100f * threadWeightFactor) + '\n' +
               "castlingState:         w=" + castlingState[0] + ", b=" + castlingState[1] + DELTA_STR + (castlingState[0] - castlingState[1]) + WEIGHT_STR + round((castlingState[0] - castlingState[1]) * castlingFactor) + '\n' +
               "doublePawnCount:       w=" + doublePawnCount[0] + ", b=" + doublePawnCount[1] + DELTA_STR + (doublePawnCount[0] - doublePawnCount[1]) + WEIGHT_STR + round((doublePawnCount[0] - doublePawnCount[1]) * doublePawnFactor) + '\n' +
               "chessCount:            w=" + chessCount[0] + ", b=" + chessCount[1] + DELTA_STR + (chessCount[0] - chessCount[1]) + WEIGHT_STR + round((chessCount[0] - chessCount[1]) * chessFactor) + '\n' +
               "undefendedPiecesCount: w=" + undefendedPiecesCount[0] + ", b=" + undefendedPiecesCount[1] + DELTA_STR + (undefendedPiecesCount[0] - undefendedPiecesCount[1]) + WEIGHT_STR + round((undefendedPiecesCount[0] - undefendedPiecesCount[1]) * undefendedPiecesFactor) + '\n' +
               "kingLinePenalty:       w=" + calculateKingLinePenalty(0) + ", b=" + calculateKingLinePenalty(1) + DELTA_STR + (calculateKingLinePenalty(0) - calculateKingLinePenalty(1)) + WEIGHT_STR + round((calculateKingLinePenalty(0) - calculateKingLinePenalty(1)) * kingLinePenaltyFactor) + '\n' +
               "weight: " + calculatePositionWeight() / 100f;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static void _calculateForWhitePawn(WeightingFunction generator, int field, int color) {
        generator.calculateForWhitePawn(field, color);
    }

    private void calculateForWhitePawn(int field, int color) {
        // single step
        int to = field + Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
        }

        // double step
        if (fieldToRow(field) == 1) {
            to = field + 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field + Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.whitePawn];
            }
        }

        // capture right
        captureOrDefendWithPawn(field + Board.LENGTH + 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

        // capture left
        captureOrDefendWithPawn(field + Board.LENGTH - 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

        // en passant
        if (fieldToRow(field) == 4) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 + 2 * Board.LENGTH) {
                    capture(field - 1, Board.whitePawn, color, Board.blackPawn);
                } else if (board[field + 1] == Board.blackPawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 + 2 * Board.LENGTH) {
                    capture(field + 1, Board.whitePawn, color, Board.blackPawn);
                }
            }
        }

        // Is a doubled pawn? Searching ahead (toward 8th rank for white)
        // means only the LOWER pawn in a pair finds its partner, so each
        // pair is counted exactly once.
        for (var f = field + Board.LENGTH; board[f] != Board.illegal; f += Board.LENGTH) {
            if (board[f] == Board.whitePawn) {
                doublePawnCount[color]++;
                break;
            }
        }
    }

    private void captureOrDefendWithPawn(final int to, final int myTurn, final int oppositeTurn, final byte movingPawn, final int color) {
        if ((board[to] & oppositeTurn) == oppositeTurn) {
            capture(to, movingPawn, color, board[to]);
        } else if ((board[to] & myTurn) == myTurn) {
            defend(to);
        }
    }

    private static int fieldToRow(int field) {
        return field / Board.LENGTH - 2;
    }

    private static void _calculateForBlackPawn(WeightingFunction generator, int field, int color) {
        generator.calculateForBlackPawn(field, color);
    }

    private void calculateForBlackPawn(int field, int color) {
        // single step
        int to = field - Board.LENGTH;
        if (board[to] == Board.empty) {
            mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
        }

        // double step
        if (fieldToRow(field) == 6) {
            to = field - 2 * Board.LENGTH;
            if (board[to] == Board.empty && board[field - Board.LENGTH] == Board.empty) {
                mobilityWeight[color] += mobilityWeightOfPiece[Board.blackPawn];
            }
        }

        // capture right
        to = field - Board.LENGTH + 1;
        captureOrDefendWithPawn(to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

        // capture left
        to = field - Board.LENGTH - 1;
        captureOrDefendWithPawn(to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

        // en passant
        if (fieldToRow(field) == 3) {
            int lastMove = game.getLastMove();
            if (lastMove != 0) {
                if (board[field - 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field - 1
                        && Move.getFromField(lastMove) == field - 1 - 2 * Board.LENGTH) {
                    capture(field - 1, Board.blackPawn, color, Board.whitePawn);
                } else if (board[field + 1] == Board.whitePawn
                        && Move.getToField(lastMove) == field + 1
                        && Move.getFromField(lastMove) == field + 1 - 2 * Board.LENGTH) {
                    capture(field + 1, Board.blackPawn, color, Board.whitePawn);
                }
            }
        }

        // Is a doubled pawn? Searching ahead (toward 1st rank for black)
        // means only the UPPER pawn in a pair finds its partner, so each
        // pair is counted exactly once.
        for (var f = field - Board.LENGTH; board[f] != Board.illegal; f -= Board.LENGTH) {
            if (board[f] == Board.blackPawn) {
                doublePawnCount[color]++;
                break;
            }
        }
    }

    private static void _calculateForKnight(WeightingFunction generator, int field, int color) {
        generator.calculateForKnight(field, color);
    }

    private void calculateForKnight(int field, int color) {
        final byte myPiece = board[field];

        move(myPiece, field, field + 2 * Board.LENGTH + 1, color);
        move(myPiece, field, field + 1 * Board.LENGTH + 2, color);
        move(myPiece, field, field - 1 * Board.LENGTH + 2, color);
        move(myPiece, field, field - 2 * Board.LENGTH + 1, color);
        move(myPiece, field, field - 2 * Board.LENGTH - 1, color);
        move(myPiece, field, field - 1 * Board.LENGTH - 2, color);
        move(myPiece, field, field + 1 * Board.LENGTH - 2, color);
        move(myPiece, field, field + 2 * Board.LENGTH - 1, color);
    }

    private static void _calculateForBishop(WeightingFunction generator, int field, int color) {
        generator.calculateForBishop(field, color);
    }

    private void calculateForBishop(int field, int color) {
        final byte myPiece = board[field];

        // count this bishop toward the side's bishop-pair bonus (awarded once in calculatePositionWeight)
        bishopCount[color]++;

        // move up-right
        for (int to = field + Board.LENGTH + 1; move(myPiece, field, to, color); to += Board.LENGTH + 1);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(myPiece, field, to, color); to = to - Board.LENGTH + 1);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(myPiece, field, to, color); to = to - Board.LENGTH - 1);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(myPiece, field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForRook(WeightingFunction generator, int field, int color) {
        generator.calculateForRook(field, color);
    }

    private void calculateForRook(int field, int color) {
        final byte myPiece = board[field];
        final int rankWeight = mobilityWeightOfPiece[myPiece] / 2;

        // move up — file mobility (full weight)
        for (int to = field + Board.LENGTH; move(myPiece, field, to, color); to += Board.LENGTH);
        // move down — file mobility (full weight)
        for (int to = field - Board.LENGTH; move(myPiece, field, to, color); to -= Board.LENGTH);
        // move left — rank mobility (half weight)
        for (int to = field - 1; move(myPiece, field, to, color, rankWeight); to--);
        // move right — rank mobility (half weight)
        for (int to = field + 1; move(myPiece, field, to, color, rankWeight); to++);
    }

    private static void _calculateForQueen(WeightingFunction generator, int field, int color) {
        generator.calculateForQueen(field, color);
    }

    private void calculateForQueen(int field, int color) {
        final byte myPiece = board[field];

        // move up
        for (int to = field + Board.LENGTH; move(myPiece, field, to, color); to += Board.LENGTH);
        // move up-right
        for (int to = field + Board.LENGTH + 1; move(myPiece, field, to, color); to += Board.LENGTH + 1);
        // move right
        for (int to = field + 1; move(myPiece, field, to, color); to++);
        // move down-right
        for (int to = field - Board.LENGTH + 1; move(myPiece, field, to, color); to = to - Board.LENGTH + 1);
        // move down
        for (int to = field - Board.LENGTH; move(myPiece, field, to, color); to -= Board.LENGTH);
        // move down-left
        for (int to = field - Board.LENGTH - 1; move(myPiece, field, to, color); to = to - Board.LENGTH - 1);
        // move left
        for (int to = field - 1; move(myPiece, field, to, color); to--);
        // move up-left
        for (int to = field + Board.LENGTH - 1; move(myPiece, field, to, color); to += Board.LENGTH - 1);
    }

    private static void _calculateForKing(WeightingFunction generator, int field, int color) {
        generator.calculateForKing(field, color);
    }

    private void calculateForKing(int field, int color) {
        final byte myPiece = board[field];

        // move up
        move(myPiece, field, field + Board.LENGTH, color);
        // move up-right
        move(myPiece, field, field + Board.LENGTH + 1, color);
        // move right
        move(myPiece, field, field + 1, color);
        // move down-right
        move(myPiece, field, field - Board.LENGTH + 1, color);
        // move down
        move(myPiece, field, field - Board.LENGTH, color);
        // move down-left
        move(myPiece, field, field - Board.LENGTH - 1, color);
        // move left
        move(myPiece, field, field - 1, color);
        // move up-left
        move(myPiece, field, field + Board.LENGTH - 1, color);

        // The king's own file and its two neighbors. Computed here, inside the walk the evaluation
        // performs anyway, rather than as a separate pass: a standalone scan for the shelved
        // attack-unit term cost more than the entire evaluation.
        final int col = field % Board.LENGTH - 2;
        final int startField = field + KING_LINE_OFFSETS[col];
        kingLineDanger[color] = calculateKingLineDanger(color, startField)
                + calculateKingLineDanger(color, startField + 1)
                + calculateKingLineDanger(color, startField + 2);
    }

    /**
     * Classifies one file on the {@code KING_DANGER_*} scale by walking away from the king.
     *
     * <p>The walk starts one square in front of {@code startField} and runs to the far rank, so
     * everything behind the king is ignored. That is deliberate: pawn shelter is directional, and
     * a pawn behind the king covers nothing. It also means an enemy rook <em>behind</em> an
     * advanced king is not seen — a real blind spot, accepted because such a rook is a concrete
     * threat one ply deep that the search resolves better than a static level could.
     *
     * <p>The first pawn met decides the shelter question, which is why the walk is anchored on the
     * king and not on the back rank: an own pawn the enemy has already passed is not cover.
     *
     * <p>One case diverges from the fitted definition: an enemy rook or queen met <em>before</em>
     * the own shield pawn scores {@link #KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE}, whereas the fit
     * stopped at the nearest own pawn and never looked for majors, scoring it as sheltered. The
     * reading here is the better one, and the divergence is not measurable: it occurs on 0.0697 %
     * of king files in the calibration corpus (163 of 233,799, in 161 of 39,619 positions).
     *
     * @param color      the defending side, 0 = white
     * @param startField the king's square, or a neighboring file's square on the king's rank
     * @return the danger level, or 0 if {@code startField} is off the board
     */
    int calculateKingLineDanger(final int color, final int startField) {
        final int col = startField % Board.LENGTH - 2;
        final int offset = ROW_OFFSET[color];
        final int endField = ChessUtil.getFieldFromColAndRow(col, LAST_RANK[color]) + offset;
        final int middleField = ChessUtil.getFieldFromColAndRow(col, MIDDLE_RANK[color]);
        final int myPawn = ownPawn[color];
        final int opponentPawn = ownPawn[color ^ 1];
        final int opponentRook = oppositeRook[color];
        final int opponentQueen = oppositeQueen[color];
        boolean sawOpponentMajorPiece = false;

        for (int field = startField + offset; field != endField; field += offset) {
            final int piece = board[field];

            if (piece == opponentPawn) {
                // half open line
                if (color == 0) {
                    return field <= middleField ? KING_DANGER_HALF_OPEN_ADVANCED_OPPONENT_PAWN : KING_DANGER_HALF_OPEN;
                } else {
                    return field >= middleField ? KING_DANGER_HALF_OPEN_ADVANCED_OPPONENT_PAWN : KING_DANGER_HALF_OPEN;
                }
            }

            if (piece == myPawn) {
                return sawOpponentMajorPiece ? KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE : 0;
            }

            if (piece == opponentRook || piece == opponentQueen) {
                sawOpponentMajorPiece = true;
            }
        }

        // open line
        return sawOpponentMajorPiece ? KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE : KING_DANGER_OPEN;
    }

    private boolean move(final byte movingPiece, final int from, final int to, int color) {
        return move(movingPiece, from, to, color, mobilityWeightOfPiece[movingPiece]);
    }

    @SuppressWarnings({"unused", "java:S1117"})
    private boolean move(final byte movingPiece, final int from, final int to, final int color, final int weight) {
        final byte piece = board[to];
        final int oppositeColor = WeightingFunction.oppositeColor[color];

        if (piece == Board.illegal)
            return false;

        if (piece == Board.empty) {
            mobilityWeight[color] += weight;
            return true;
        } else if ((piece & oppositeColor) == oppositeColor) {
            capture(to, color, piece, weight);
            return false;
        } else { // own color
            defend(to);
            return false;
        }
    }

    private void capture(final int to, final byte movingPiece, final int color, final byte piece) {
        capture(to, color, piece, mobilityWeightOfPiece[movingPiece]);
    }

    private void capture(final int field, final int color, final byte piece, final int weight) {
        if (piece == oppositeKing[color]) {
            if (turn == color) {
                containsIllegalMove = true;
            } else {
                chessCount[color]++;
                threadWeight[color] += 4; // ok, give some weight to the attacked king as well (since weightOfPiece(king) is 0)
            }
        }

        mobilityWeight[color] += weight;
        threadWeight[color] += weightOfPiece[piece];
        tempBoard[field] |= ATTACK_MARK_BIT;
    }

    private void defend(final int field) {
        this.tempBoard[field] = Board.empty;
    }

    private void calculateCastlingState() {
        if (game.hasWhiteCastled())
            castlingState[0] = 0;
        else if (game.isWhiteCastlingQueenSidePossible() && game.isWhiteCastlingKingSidePossible())
            castlingState[0] = -1;
        else if (game.isWhiteCastlingQueenSidePossible() || game.isWhiteCastlingKingSidePossible())
            castlingState[0] = -2;
        else
            castlingState[0] = -4;

        if (game.hasBlackCastled())
            castlingState[1] = 0;
        else if (game.isBlackCastlingQueenSidePossible() && game.isBlackCastlingKingSidePossible())
            castlingState[1] = -1;
        else if (game.isBlackCastlingQueenSidePossible() || game.isBlackCastlingKingSidePossible())
            castlingState[1] = -2;
        else
            castlingState[1] = -4;
    }

    /**
     * Number of hanging pieces (attacked AND undefended, kings excluded)
     * found for the given color during the most recent {@link #calculate}
     * call. Package-private test hook — production callers should consume
     * this contribution via the final weight returned by {@code calculate}.
     *
     * @param color {@code 0} for white, {@code 1} for black
     */
    int getHangingPiecesCount(int color) {
        return undefendedPiecesCount[color];
    }

    private void calculateUndefendedPiecesCount() {
        for (int field = Board.a1; field <= Board.h8; field++) {
            final byte piece = tempBoard[field];
            if ((piece & ATTACK_MARK_BIT) == ATTACK_MARK_BIT) {
                if ((piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE && piece != WHITE_KING_ATTACKED) {
                    undefendedPiecesCount[0]++;
                } else if ((piece & GameStatus.TURN_BLACK) == GameStatus.TURN_BLACK && piece != BLACK_KING_ATTACKED) {
                    undefendedPiecesCount[1]++;
                }
            }
        }
    }

    public static boolean isIllegalWeight(int weightCenti) {
        return weightCenti == ILLEGAL_WEIGHT_NEG || weightCenti == ILLEGAL_WEIGHT_POS;
    }

    public static float checkmateIn(int depth) {
        return (WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth * 100) / 100f;
    }

    public static int checkmateInCenti(int depth) {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth * 100;
    }

    public static int checkmateInCenti() {
        return WeightingFunction.CHECKMATE_WEIGHT_HIGH;
    }

    /** Returns true if given weight is a checkmate score (positive or negative). */
    public static boolean isCheckmateWeight(float weight) {
        final int w = (int) (Math.abs(weight) * 100);
        return w >= CHECKMATE_WEIGHT_LOW && w <= CHECKMATE_WEIGHT_HIGH;
    }

    /** Returns true if given weight (in centi pawns) is a checkmate score (positive or negative). */
    public static boolean isCheckmateWeight(int weightCenti) {
        final int w = Math.abs(weightCenti);
        return w >= CHECKMATE_WEIGHT_LOW && w <= CHECKMATE_WEIGHT_HIGH;
    }

    /**
     * Returns the number of plies (depth) that corresponds to given checkmate score,
     * i.e. mate in X half moves.
     * Precondition: Given weight must be a checkmate score.
     */
    public static int checkmateWeightToPlies(float weight) {
        final int w = (int) (Math.abs(weight) * 100);
        return (CHECKMATE_WEIGHT_HIGH - w) / 100;
    }

    /**
     * Returns the number of plies (depth) that corresponds to given checkmate score (in centi pawns),
     * i.e. mate in X half moves.
     * Precondition: Given weight must be a checkmate score.
     */
    public static int checkmateWeightToPlies(int weightCenti) {
        final int w = Math.abs(weightCenti);
        return (CHECKMATE_WEIGHT_HIGH - w) / 100;
    }

    /**
     * Translates a mate score from "mate-at-depth-d-from-root" form into
     * the position-relative "mate-in-K-plies-from-here" form used by the
     * transposition table. Subtracts {@code depth} from the encoded plies
     * and preserves the sign of the score; non-mate scores pass through
     * unchanged.
     *
     * <p>The {@code plies >= depth} assertion catches inconsistent
     * states early — if a positive mate score's encoded ply count is
     * smaller than the current depth, the resulting stored score would
     * underflow the mate sentinel range and start looking like an
     * ordinary positional score on lookup.
     *
     * <p>Despite the "TT" in the name, this is a pure score-coordinate
     * transformation with no transposition-table dependency. It lives
     * here next to the other mate-score helpers because that is the
     * coordinate system it operates on.
     */
    public static int scoreToTT(int score, int depth) {
        if (isCheckmateWeight(score)) {
            int plies = checkmateWeightToPlies(score);
            __assert(() -> plies >= depth, () -> String.format("checkmate plies=%s, depth=%s", plies, depth));
            int checkmateCenti = checkmateInCenti(plies - depth);
            return score >= 0 ? checkmateCenti : -checkmateCenti;
        }

        return score;
    }

    /**
     * Inverse of {@link #scoreToTT(int, int)}: translates a TT-stored
     * mate score back to the current search depth. The TT stores mate
     * scores relative to the cached position ("mate-in-K plies from
     * here"), independent of how deep in the tree that position was
     * when the score was computed. On lookup at depth {@code d} from
     * the root, we add {@code d} so the returned value reads as "mate
     * at depth {@code d + K} of the current tree", which is what the
     * negamax caller compares against alpha/beta and what an
     * iteration's PV propagates up.
     *
     * <p>Sign preservation matters: a stored negative mate stays a
     * negative mate. The earlier private implementation in
     * {@code PositionSearch} dropped the sign, and the regression that
     * surfaced this was {@code GameStatusTest.testWhiteCheckmate}
     * flipping its expected mate move because a "we are mated" entry
     * came back from the TT as "we are mating".
     *
     * <p>Non-mate scores are positional and depth-independent in
     * storage, so they pass through unchanged.
     */
    public static int scoreFromTT(int score, int depth) {
        if (isCheckmateWeight(score)) {
            int plies = checkmateWeightToPlies(score);
            int checkmateCenti = checkmateInCenti(depth + plies);
            return score >= 0 ? checkmateCenti : -checkmateCenti;
        }

        return score;
    }
}
