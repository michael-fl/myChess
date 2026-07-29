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
 * Static position evaluation in centipawn units: material plus
 * {@link PieceSquareTables} bonus plus per-piece capture/threat heuristics
 * (pawn structure including en-passant target, king safety, rook-file
 * activity, ...). Returns a
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

    /** Piece weight in centi pawns. */
    public static final int[] weightOfPiece = new int[Board.blackKing + 1];
    static {
        weightOfPiece[Board.whitePawn]   = 100;
        weightOfPiece[Board.whiteKnight] = 300;
        weightOfPiece[Board.whiteBishop] = 300;
        weightOfPiece[Board.whiteRook]   = 500;
        weightOfPiece[Board.whiteQueen]  = 900;
        weightOfPiece[Board.whiteKing]   = 0;
        weightOfPiece[Board.blackPawn]   = 100;
        weightOfPiece[Board.blackKnight] = 300;
        weightOfPiece[Board.blackBishop] = 300;
        weightOfPiece[Board.blackRook]   = 500;
        weightOfPiece[Board.blackQueen]  = 900;
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
    /** Own pawn constant per color (0 = white, 1 = black); {@code ownPawn[color ^ 1]} is the opponent's pawn. Used by the rook-file evaluation. */
    private static final byte[] ownPawn = new byte[] { Board.whitePawn, Board.blackPawn };
    /** Own rook constant per color (0 = white, 1 = black). Used by the rook-file / battery evaluation. */
    private static final byte[] ownRook = new byte[] { Board.whiteRook, Board.blackRook };

    private static final float mobilityFactor = 0.1f;
    private static final float positionFactor = 0.5f;
    private static final float threadWeightFactor = 0.02f;
    private static final float chessFactor = 0.25f;
    private static final float castlingFactor = 0.25f;
    /**
     * Scales the rook-file / battery bonus in the final weight. The per-file
     * bonuses in {@link #calculateRookFileWeight(int, int)} are already in
     * centipawns, so this is a {@code 1.0} pass-through; the offline tuner can
     * adjust it as the last entry of {@link #TUNABLE_FACTOR_NAMES}.
     */
    private static final float rookFileFactor = 1.0f;

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
    /**
     * The (up to) two distinct files occupied by each color's rooks, as file
     * indices 0-7 with {@code -1} marking an unused slot; indexed
     * {@code [color][slot]} and filled by {@link #storeRookFile(int, int)}. At
     * most two distinct files per color are tracked (a third promoted rook on a
     * new file is dropped).
     */
    private final int[][] rookFiles = new int[2][2];
    /** Per-color rook-file / battery bonus in centipawns (index 0 = white, 1 = black); see {@link #calculateRookFileWeight(int, int)}. */
    private final int[] rookFilesWeight = new int[2];

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
        this.rookFiles[0][0] = -1;
        this.rookFiles[0][1] = -1;
        this.rookFiles[1][0] = -1;
        this.rookFiles[1][1] = -1;
        this.rookFilesWeight[0] = 0;
        this.rookFilesWeight[1] = 0;

        System.arraycopy(board, 0, this.tempBoard, 0, Board.LENGTH * Board.LENGTH);

        final int stopField = Board.h8 + 1;
        final boolean isEndGame = game.isEndGame();

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;

                piecesWeight[color] += weightOfPiece[piece];
                if (!(isEndGame && Board.isKing(piece))) {
                    positionWeight[color] += PieceSquareTables.getPieceSquareWeight(piece, field);
                }

                calculationFunctions[piece].calculate(this, field, color);
            }
        }

        calculateRookFileWeights();

        calculateCastlingState();

        calculateUndefendedPiecesCount();

        return calculatePositionWeight();
    }

    /** The evaluation factors the offline tuner can adjust, in a fixed order. */
    public static final String[] TUNABLE_FACTOR_NAMES = {
            "positionFactor", "mobilityFactor", "threadWeightFactor",
            "castlingFactor", "chessFactor", "doublePawnFactor", "undefendedPiecesFactor", "rookFileFactor"
    };

    /** Current values of {@link #TUNABLE_FACTOR_NAMES}, in the same order. */
    public static double[] tunableFactorValues() {
        return new double[] {
                positionFactor, mobilityFactor, threadWeightFactor,
                castlingFactor, chessFactor, doublePawnFactor, undefendedPiecesFactor, rookFileFactor
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
                rookFilesWeight[0] - rookFilesWeight[1]
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
                + (rookFilesWeight[0] - rookFilesWeight[1]) / 100f * rookFileFactor) * 100);
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
               "rookFilesWeight:       w=" + rookFilesWeight[0] + ", b=" + rookFilesWeight[1] + DELTA_STR + (rookFilesWeight[0] - rookFilesWeight[1]) + WEIGHT_STR + round((rookFilesWeight[0] - rookFilesWeight[1]) / 100f * rookFileFactor) + '\n' +
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

        storeRookFile(field, color);
    }

    /**
     * Record the file of a rook at {@code field} for {@code color} in
     * {@link #rookFiles}. Stores at most two <em>distinct</em> files: the first
     * rook's file goes to slot 0, a rook on a different file to slot 1, and any
     * further rook is ignored (a second rook on an already-stored file, or a
     * third promoted rook on a new file). Two rooks on the same file are thus
     * recorded once; their battery is detected later when the file is scanned by
     * {@link #calculateRookFileWeight(int, int)}.
     */
    private void storeRookFile(final int field, final int color) {
        final int file = field % Board.LENGTH - 2;
        final int[] rf = rookFiles[color];

        if (rf[0] == -1) {
            rf[0] = file;
        } else if (rf[0] != file && rf[1] == -1) {
            rf[1] = file;
        }
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

    /** A copy of the (up to) two tracked rook files for {@code color}; {@code -1} marks an unused slot. Exposed for tests. */
    int[] getRookFiles(int color) {
        return Arrays.copyOf(rookFiles[color], 2);
    }

    /** A copy of the per-color rook-file / battery bonus in centipawns (index 0 = white, 1 = black). Exposed for tests. */
    int[] getRookFilesWeight() {
        return Arrays.copyOf(rookFilesWeight, 2);
    }

    /** Fill {@link #rookFilesWeight} for both colors from the files recorded in {@link #rookFiles}. */
    private void calculateRookFileWeights() {
        rookFilesWeight[0] = calculateRookFileWeightsForColor(0, rookFiles[0]);
        rookFilesWeight[1] = calculateRookFileWeightsForColor(1, rookFiles[1]);
    }

    /**
     * Sum the rook-file / battery bonus over the files stored for one color.
     * Slot 0 is the sentinel: when it is {@code -1} (no rook recorded) the result
     * is 0 and slot 1 is not consulted.
     *
     * @param color     0 = white, 1 = black
     * @param rookFiles the color's two file slots (see {@link #rookFiles})
     * @return the summed bonus in centipawns
     */
    int calculateRookFileWeightsForColor(final int color, final int[] rookFiles) {
        int weight = 0;

        if (rookFiles[0] >= 0) {
            weight += calculateRookFileWeight(color, rookFiles[0]);
            if (rookFiles[1] >= 0) {
                weight += calculateRookFileWeight(color, rookFiles[1]);
            }
        }

        return weight;
    }

    /**
     * Bonus in centipawns for {@code color}'s rook(s) on a single {@code file}
     * (0 = a-file .. 7 = h-file), scanning rank 1 to rank 8:
     * <ul>
     *   <li><b>Own pawn on the file</b> &rarr; {@code 0} — the file is closed;
     *       this overrides everything else, including a battery.</li>
     *   <li><b>Open file</b> (no pawn of either color) &rarr; {@code 20}.</li>
     *   <li><b>Half-open file</b> (an opponent pawn, no own pawn) &rarr; {@code 10}.</li>
     *   <li><b>Rook battery</b> — two of the color's rooks on this file with no
     *       piece between them — adds a further {@code 30}. Only rook-rook
     *       batteries count; any non-empty square between the rooks (a queen
     *       included) breaks it.</li>
     * </ul>
     *
     * @param color 0 = white, 1 = black
     * @param file  file index 0-7
     * @return the bonus in centipawns
     */
    int calculateRookFileWeight(final int color, final int file) {
        final int startField = Board.a1 + file;
        final byte myPawn = ownPawn[color];
        final byte opponentPawn = ownPawn[color^1];
        final byte myRook = ownRook[color];

        boolean haveOpponentPawn = false;
        boolean sawMyRook = false;
        boolean isBattery = false;
        boolean sawNonEmptyField = false;

        for (int field = startField; field <= Board.h8; field += Board.LENGTH) {
            final byte piece = board[field];
            if (piece == myPawn) {
                // File is not half-open for this color
                return 0;
            }
            if (piece == myRook) {
                if (sawMyRook && !sawNonEmptyField) {
                    isBattery = true; // Count as battery if both rooks can see each other
                }
                sawMyRook = true;
                sawNonEmptyField = false;
            } else if (piece != Board.empty) {
                sawNonEmptyField = true;
            }

            haveOpponentPawn |= piece == opponentPawn;
        }

        // Rook on open file gets 20cp bonus, on half-open file 10cp
        int weight = haveOpponentPawn ? 10 : 20;

        // Rook batteries get an extra bonus of 30cp
        if (isBattery) {
            weight += 30;
        }

        return weight;
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
