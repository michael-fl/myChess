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

    /**
     * Attack-unit weight per piece kind: how strongly a piece bearing on the
     * enemy king zone contributes to the king-attack pressure. Heavier pieces
     * weigh more; the king contributes nothing (and is thus never counted as an
     * attacker). These weights are summed into {@link #attackUnit} and used to
     * index {@link #KING_ATTACK_PENALTY}.
     *
     * <p><b>They cannot be changed on their own.</b> The penalty table is a lookup
     * <em>indexed by</em> these weights, not merely scaled by them: raise the queen to 9 and
     * every position with a queen in the zone reads two entries further along a table nobody
     * refitted. Package-private because {@code KingAttackUnits} — the standalone implementation
     * the curve was fitted over, in test sources — reads them from here rather than repeating
     * them, so the two cannot drift apart. Changing one means refitting the curve.
     */
    static final int ATTACK_UNIT_KING = 0;
    static final int ATTACK_UNIT_PAWN = 1;
    static final int ATTACK_UNIT_KNIGHT = 2;
    static final int ATTACK_UNIT_BISHOP = 2;
    static final int ATTACK_UNIT_ROOK = 3;
    static final int ATTACK_UNIT_QUEEN = 5;

    /** Attack-unit weight indexed by piece constant; see {@link #ATTACK_UNIT_KING}. */
    private static final int[] ATTACK_UNIT_OF_PIECE = new int[Board.blackKing + 1];
    static {
        ATTACK_UNIT_OF_PIECE[Board.whitePawn]   = ATTACK_UNIT_PAWN;
        ATTACK_UNIT_OF_PIECE[Board.whiteKnight] = ATTACK_UNIT_KNIGHT;
        ATTACK_UNIT_OF_PIECE[Board.whiteBishop] = ATTACK_UNIT_BISHOP;
        ATTACK_UNIT_OF_PIECE[Board.whiteRook]   = ATTACK_UNIT_ROOK;
        ATTACK_UNIT_OF_PIECE[Board.whiteQueen]  = ATTACK_UNIT_QUEEN;
        ATTACK_UNIT_OF_PIECE[Board.whiteKing]   = ATTACK_UNIT_KING;
        ATTACK_UNIT_OF_PIECE[Board.blackPawn]   = ATTACK_UNIT_PAWN;
        ATTACK_UNIT_OF_PIECE[Board.blackKnight] = ATTACK_UNIT_KNIGHT;
        ATTACK_UNIT_OF_PIECE[Board.blackBishop] = ATTACK_UNIT_BISHOP;
        ATTACK_UNIT_OF_PIECE[Board.blackRook]   = ATTACK_UNIT_ROOK;
        ATTACK_UNIT_OF_PIECE[Board.blackQueen]  = ATTACK_UNIT_QUEEN;
        ATTACK_UNIT_OF_PIECE[Board.blackKing]   = ATTACK_UNIT_KING;
    }

    /**
     * King-attack penalty at full midgame material, in centipawns, indexed by the attacking
     * side's accumulated {@link #attackUnit} and scaled down by the game phase in
     * {@link #calcKingAttackPenalty}.
     *
     * <p><b>Fitted, not shaped by hand</b> — see {@code docs/king-safety.md} § 4.6. The target
     * was Stockfish's <em>static</em> evaluation minus this one, over a 39 619-position corpus,
     * with monotonicity as a constraint of the fit rather than a property hoped for afterward.
     * An earlier hand-built table ran to index 20 and rose to 400; the entries above 8 covered
     * positions real play reaches in 0.3 % of samples, and its lower half carried a quarter to a
     * half of what the fit puts there.
     *
     * <p><b>The flat runs are the measurement, not rounding.</b> Indices 5 to 7 share a value
     * because the data place all three well above zero without distinguishing between them;
     * where the unconstrained fit fell — which would score more attackers as less danger — the
     * isotonic projection merges the offending indices into one level. Constraining the fit that
     * way costs 0.077 % of residual, so the descent it removed was noise.
     *
     * <p>Indices 1 and 2 are zero because a single minor piece bearing on the king zone is the
     * normal case rather than a danger, and their intervals do not separate from zero. Index 0
     * is pinned: only the difference between the two sides reaches the score, so a constant on
     * every entry would cancel.
     *
     * <p>Applied only once at least two distinct pieces attack, and the index is clamped to the
     * last entry — both in {@link #calcKingAttackPenalty}, both deliberate, both explained
     * there.
     */
    static final int[] KING_ATTACK_PENALTY = {
            0,    //  0
            0,    //  1
            0,    //  2
            13,   //  3
            16,   //  4
            47,   //  5
            47,   //  6
            47,   //  7
            80    //  8
    };

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

    /**
     * Phase of the full starting material (4·1 knights + 4·1 bishops + 4·2 rooks + 2·4 queens); the phase is clamped to this.
     *
     * <p>Package-private rather than private so {@code WeightingFunctionAttackUnitTest} can ask
     * for the king-attack penalty at full midgame. Its fixtures build a {@link WeightingFunction}
     * without calling {@link #calculate(Board)}, so their phase field is 0 and every phase-scaled
     * term would come back as 0 — the assertions would pass while testing nothing.
     */
    static final int MAX_PHASE = 24;

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
     * Scales the king-attack penalty delta (white − black, from
     * {@link #KING_ATTACK_PENALTY}) into the final position weight. With the
     * penalty table already in centipawns and the final-weight sum in pawn
     * units, {@code 0.01} carries the table's centipawn values through
     * unchanged. Not (yet) a tunable factor — a first fixed value to be
     * confirmed by self-play before Texel tuning.
     */
    private static final float kingAttackFactor = 0.01f;

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
    /** Accumulated attack units bearing on the enemy king zone, per attacking color. */
    private final int[] attackUnit = new int[2];
    /** Number of distinct pieces bearing on the enemy king zone, per attacking color. */
    private final int[] kingAttackerCount = new int[2];
    /** Per-color sum of midgame piece-square values for the current position (index 0 = white, 1 = black). */
    private final int[] pstMidGameWeight = new int[2];
    /** Per-color sum of endgame piece-square values for the current position (index 0 = white, 1 = black). */
    private final int[] pstEndGameWeight = new int[2];
    private final int[] kingField = new int[2];
    /** Game phase of the most recently evaluated position, {@code 0..}{@link #MAX_PHASE}; see {@link #phaseWeightOfPiece}. */
    private int phase;
    private boolean isCurrentAttackerCounted;

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
        this.attackUnit[0] = 0;
        this.attackUnit[1] = 0;
        this.kingAttackerCount[0] = 0;
        this.kingAttackerCount[1] = 0;
        this.pstMidGameWeight[0] = 0;
        this.pstMidGameWeight[1] = 0;
        this.pstEndGameWeight[0] = 0;
        this.pstEndGameWeight[1] = 0;

        System.arraycopy(board, 0, this.tempBoard, 0, Board.LENGTH * Board.LENGTH);

        final int stopField = Board.h8 + 1;
        int phase = 0;

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece == Board.whiteKing) {
                kingField[0] = field;
            } else if (piece == Board.blackKing) {
                kingField[1] = field;
            }
        }

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];

                // Accumulate every piece's PST (both phases; blended by phase
                // afterward). The king is no longer excepted here: the crude
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

        return calculatePositionWeight(phase);
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
            "bishopPairFactor"
    };

    /** Current values of {@link #TUNABLE_FACTOR_NAMES}, in the same order. */
    public static double[] tunableFactorValues() {
        return new double[] {
                positionFactor, mobilityFactor, threadWeightFactor,
                castlingFactor, chessFactor, doublePawnFactor, undefendedPiecesFactor,
                bishopPairFactor
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
                ((bishopCount[0] >= 2 ? 1 : 0) - (bishopCount[1] >= 2 ? 1 : 0)) * 100.0
        };

        return new FactorBreakdown(eval, features);
    }

    private int calculatePositionWeight(final int phase) {
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
                + (calcKingAttackPenalty(0, phase) - calcKingAttackPenalty(1, phase)) * kingAttackFactor) * 100);
    }

    /**
     * Records that the piece on {@code fromField} bears on {@code toField}. When
     * {@code toField} lies in the enemy king zone, the piece's
     * {@link #ATTACK_UNIT_OF_PIECE attack-unit weight} is added to
     * {@link #attackUnit} and {@link #kingAttackerCount} is incremented.
     *
     * <p>Each piece is counted at most once per evaluation, regardless of how
     * many king-zone squares it attacks: {@link #isKingAttackerCounted}, keyed by
     * the origin square, absorbs the repeated calls a sliding piece makes along
     * its rays. Two like pieces on different squares still count separately. The
     * king itself has zero weight and is therefore never counted as an attacker.
     *
     * @param color     attacking color (0 = white, 1 = black)
     * @param toField   attacked square
     * @param piece     the attacking piece
     */
    private void increaseAttackUnit(final int color, final int toField, final byte piece) {
        if (!isCurrentAttackerCounted && isKingZoneField(toField, color ^ 1)) {
            final int score = ATTACK_UNIT_OF_PIECE[piece];

            if (score > 0) {
                isCurrentAttackerCounted = true;
                kingAttackerCount[color]++;
                attackUnit[color] += score;
            }
        }
    }

    private boolean isKingZoneField(int field, int color) {
        if (board[field] == Board.illegal) {
            return false;
        }

        final int delta = field - kingField[color];

        return (delta >= - 1 && delta <= 1)
                || (delta >= - Board.LENGTH - 1 && delta <= - Board.LENGTH + 1)
                || (delta >= Board.LENGTH - 1 && delta <= Board.LENGTH + 1);
    }

    /**
     * King-attack penalty for the given attacking color, looked up in
     * {@link #KING_ATTACK_PENALTY} by that side's accumulated {@link #attackUnit} and scaled by
     * the game phase.
     *
     * <p><b>The phase scaling is the whole reason this term was worth porting.</b> Branch
     * {@code attack-units} carried it for weeks without any reference to the phase, so it ran at
     * full strength in the endgame — where the measured effect is not merely smaller but of the
     * <em>opposite</em> sign: roughly −34 cp per attacker in the midgame against +12 in the
     * endgame ({@code docs/king-safety.md} § 4.2). An unscaled term therefore charges a penalty
     * where the data show a small bonus. {@link #blend} against a zero endgame value applies it.
     *
     * <p><b>Gated on at least two distinct attackers</b> ({@link #kingAttackerCount}), and the
     * gate is load-bearing for the calibration rather than a plausible-sounding filter. It is
     * what makes an index mean one thing: without it, five units mixes a lone queen with a rook
     * and a knight, and refitting under the gate moves that entry from 20 cp to 47 — the two
     * differ by more than a factor of two. The table shipped here was fitted <em>with</em> the
     * gate applied; removing the gate requires refitting, because the gate suppresses 41.4 % of
     * the term's total mass and 33.5 % of all king samples carry exactly one attacker.
     *
     * <p>The attack-unit index is clamped to the last table entry. Units above 8 occur in 0.72 %
     * of samples and cannot be fitted from the data; measured across the legal moves of a
     * position the term still varies by a median of 18.3 cp, so the clamp does not flatten it
     * where it has to discriminate.
     *
     * @param color attacking color (0 = white, 1 = black)
     * @param phase game phase in {@code [0, }{@link #MAX_PHASE}{@code ]}; 0 switches the term off
     * @return the penalty the enemy king incurs, as a positive centipawn value
     */
    int calcKingAttackPenalty(final int color, final int phase) {
        return kingAttackerCount[color] < 2 ?
                0 :
                blend(KING_ATTACK_PENALTY[Math.min(attackUnit[color], KING_ATTACK_PENALTY.length - 1)], 0, phase);
    }

    // --- Package-private accessors for attack-unit unit tests. The arrays are
    // populated by calculate(Board); index 0 = white, 1 = black. ---

    int[] getAttackUnit() {
        return attackUnit;
    }

    int[] getKingAttackerCount() {
        return kingAttackerCount;
    }

    boolean isInKingZone(int color, int field) {
        return isKingZoneField(field, color);
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
               "attackUnit:            w=" + attackUnit[0] + ", b=" + attackUnit[1] + DELTA_STR + (attackUnit[0] - attackUnit[1]) + WEIGHT_STR + round((calcKingAttackPenalty(0, phase) - calcKingAttackPenalty(1, phase)) * kingAttackFactor) + '\n' +
               "weight: " + calculatePositionWeight(phase) / 100f;
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

        isCurrentAttackerCounted = false;

        // capture right
        captureOrDefendWithPawn(field, field + Board.LENGTH + 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

        // capture left
        captureOrDefendWithPawn(field, field + Board.LENGTH - 1, GameStatus.TURN_WHITE, GameStatus.TURN_BLACK, Board.whitePawn, color);

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

    private void captureOrDefendWithPawn(final int from, final int to, final int myTurn, final int oppositeTurn, final byte movingPawn, final int color) {
        increaseAttackUnit(color, to, movingPawn);

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

        isCurrentAttackerCounted = false;

        // capture right
        to = field - Board.LENGTH + 1;
        captureOrDefendWithPawn(field, to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

        // capture left
        to = field - Board.LENGTH - 1;
        captureOrDefendWithPawn(field, to, GameStatus.TURN_BLACK, GameStatus.TURN_WHITE, Board.blackPawn, color);

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

        isCurrentAttackerCounted = false;

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

        isCurrentAttackerCounted = false;

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

        isCurrentAttackerCounted = false;

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

        isCurrentAttackerCounted = false;

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

        isCurrentAttackerCounted = false;

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

        increaseAttackUnit(color, to, movingPiece);

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
