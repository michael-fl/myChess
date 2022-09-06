package org.michaelfl.mychess;

import java.util.BitSet;

import static org.michaelfl.mychess.ChessUtil.setBit;

/**
 * Encoding of a chess position, similar to a FEN, but much more compact.
 * It includes en passant file and castling rights.
 *
 * Idea/algorithm taken from https://codegolf.stackexchange.com/questions/19397/smallest-chess-board-compression
 *
 * Space: max. 192 bits (3 longs), depending on the number of pieces left on the board.
 *
 * Layout:
 *
 * 64 bits board layout       pieces, 4 bits per piece
 * 0=empty, 1=piece           a1, ... a8, b1 ... h8
 * 0 ...                63 64                     191
 * |                      |0000|0000| ... |0000|0000|
 *
 * Piece types:
 *     | 0  | White Pawn (normal)
 *     | 1  | White Rook (has moved)
 *     | 2  | White Knight
 *     | 3  | White Bishop
 *     | 4  | White Queen
 *     | 5  | White King; White to move next
 *     | 6  | White King; Black to move next
 *     | 7  | White Rook (pre castle) / Pawn (en Passant)
 *     | 8  | Black Pawn (normal)
 *     | 9  | Black Rook (has moved)
 *     | 10 | Black Knight
 *     | 11 | Black Bishop
 *     | 12 | Black Queen
 *     | 13 | Black King
 *     | 14 | Black Rook (pre castle) / Pawn (en Passant)
 *
 * @author Michael Fleischhauer
 */
final class PositionEncoding {

    static final int SIZE_IN_LONGS = 3; // 192 / 64

    private final static byte WHITE_KING_BLACKS_TURN = 6;
    private final static byte WHITE_PAWN_EN_PASSANT = 7;
    private final static byte BLACK_PAWN_EN_PASSANT = 14;
    private final static byte WHITE_PAWN = 0;
    private final static byte WHITE_ROOK = 1;
    private final static byte WHITE_KING = 5;
    private final static byte BLACK_PAWN = 8;
    private final static byte BLACK_ROOK = 9;
    private final static byte BLACK_KING = 13;

    private final static byte WHITE_KING_BLACKS_TURN_INDEX = 1;
    private final static byte WHITE_PAWN_EN_PASSANT_INDEX = 2;
    private final static byte BLACK_PAWN_EN_PASSANT_INDEX = 3;
    private final static byte WHITE_ROOK_PRE_CASTLE_INDEX = WHITE_PAWN_EN_PASSANT_INDEX; // Use same piece code. When decoding, row will be used to distinguish between pawn and rook.
    private final static byte BLACK_ROOK_PRE_CASTLE_INDEX = BLACK_PAWN_EN_PASSANT_INDEX;

    private final static boolean[][] PIECE_BITMAP = new boolean[22][4];
    static {
        PIECE_BITMAP[WHITE_KING_BLACKS_TURN_INDEX] = new boolean[] {false, true, true, false}; // 0b0110 (6)
        PIECE_BITMAP[WHITE_PAWN_EN_PASSANT_INDEX] = new boolean[] {false, true, true, true}; // 0b0111 (7)
        PIECE_BITMAP[BLACK_PAWN_EN_PASSANT_INDEX] = new boolean[] {true, true, true, false}; // 0b1110 (14)
        PIECE_BITMAP[Board.whitePawn]   = new boolean[] {false, false, false, false}; // 0b0000 (0)
        PIECE_BITMAP[Board.whiteRook]   = new boolean[] {false, false, false, true};  // 0b0001 (1)
        PIECE_BITMAP[Board.whiteKnight] = new boolean[] {false, false, true, false};  // 0b0010 (2)
        PIECE_BITMAP[Board.whiteBishop] = new boolean[] {false, false, true, true};   // 0b0011 (3)
        PIECE_BITMAP[Board.whiteQueen]  = new boolean[] {false, true, false, false};  // 0n0100 (4)
        PIECE_BITMAP[Board.whiteKing]   = new boolean[] {false, true, false, true};   // 0b0101 (5)
        PIECE_BITMAP[Board.blackPawn]   = new boolean[] {true, false, false, false};  // 0b1000 (8)
        PIECE_BITMAP[Board.blackRook]   = new boolean[] {true, false, false, true};   // 0b1001 (9)
        PIECE_BITMAP[Board.blackKnight] = new boolean[] {true, false, true, false};   // 0b1010 (10)
        PIECE_BITMAP[Board.blackBishop] = new boolean[] {true, false, true, true};    // 0b1011 (11)
        PIECE_BITMAP[Board.blackQueen]  = new boolean[] {true, true, false, false};   // 0b1100 (12)
        PIECE_BITMAP[Board.blackKing]   = new boolean[] {true, true, false, true};    // 0b1101 (13)
    }

    private final static byte[] pieceMap = new byte[] {
            Board.whitePawn, // 0
            Board.whiteRook, // 1
            Board.whiteKnight, // 2
            Board.whiteBishop, // 3
            Board.whiteQueen, // 4
            Board.whiteKing, // 5
            -1,
            -1,
            Board.blackPawn, // 8
            Board.blackRook, // 9
            Board.blackKnight, // 10
            Board.blackBishop, // 11
            Board.blackQueen, // 12
            Board.blackKing // 13
    };

    static long[] encode(Board board) {
        final var b = board.getRawBoard();
        final var gameStatus = board.getGameStatus();
        final var isBlacksTurn = !gameStatus.isWhiteTurn();
        final var enPassantField = gameStatus.getEnPassantField();
        final var enPassantRow = enPassantField != 0 ? ChessUtil.getRowOfField(enPassantField) : -1;
        final int enPassantPawnField = enPassantRow >= 0 ? (enPassantRow == 2 ? enPassantField + Board.LENGTH : enPassantField - Board.LENGTH) : 0;
        final var bitSet = new BitSet(192);
        int fieldBitIndex = 0;
        int pieceBitIndex = 64;

        for (int field = Board.a1; field <= Board.h8; field++) {
            byte piece = b[field];

            if (piece != Board.illegal) {
                if (piece != Board.empty) {
                    bitSet.set(fieldBitIndex); // Mark field as used in board matrix

                    if (isBlacksTurn && piece == Board.whiteKing) {
                        // Encode whose turn it is using two different white king pieces
                        piece = WHITE_KING_BLACKS_TURN_INDEX;
                    } else if (field == enPassantPawnField) {
                        // Encode en passant field by using a special pawn piece
                        // TODO remove
                        if (!((field >= Board.a4 && field <= Board.h4 && piece == Board.whitePawn) || (field >= Board.a5 && field <= Board.h5 && piece == Board.blackPawn))) {
                            board.print();
                            throw new IllegalStateException("field=" + ChessUtil.fieldToString(field) + ", piece=" + ChessUtil.pieceToString(piece));
                        }
                        piece = piece == Board.whitePawn ? WHITE_PAWN_EN_PASSANT_INDEX : BLACK_PAWN_EN_PASSANT_INDEX;
                    } else if (piece == Board.whiteRook &&
                            ((field == Board.a1 && gameStatus.isWhiteCastlingQueenSidePossible())
                                || (field == Board.h1 && gameStatus.isWhiteCastlingKingSidePossible()))) {
                        // Encode white's right to castle using a special rook piece
                        piece = WHITE_ROOK_PRE_CASTLE_INDEX;
                    } else if (piece == Board.blackRook &&
                            ((field == Board.a8 && gameStatus.isBlackCastlingQueenSidePossible())
                                    || (field == Board.h8 && gameStatus.isBlackCastlingKingSidePossible()))) {
                        // Encode black's right to castle using a special rook piece
                        piece = BLACK_ROOK_PRE_CASTLE_INDEX;
                    }

                    final var bitmap = PIECE_BITMAP[piece];
                    bitSet.set(pieceBitIndex    , bitmap[0]);
                    bitSet.set(pieceBitIndex + 1, bitmap[1]);
                    bitSet.set(pieceBitIndex + 2, bitmap[2]);
                    bitSet.set(pieceBitIndex + 3, bitmap[3]);

                    pieceBitIndex += 4;
                }

                fieldBitIndex++;
            }
        }

        // TODO remove
        if (fieldBitIndex != 64) {
            throw new IllegalStateException("Wrong fieldBitIndex: " + fieldBitIndex);
        }
        if (pieceBitIndex > 192) {
            throw new IllegalStateException("Wrong pieceBitIndex: " + pieceBitIndex);
        }
        return bitSet.toLongArray();
    }

    static Board decode(long[] encoded, int plyCount, int lastMove, int halfMoveClock) {
        final var bitSet = BitSet.valueOf(encoded);
        final var rawBoard = Board.createEmptyRawBoard();
        int fieldBitIndex = 0;
        int pieceBitIndex = 64;
        int turn = GameStatus.TURN_WHITE;
        byte enPassantField = 0;
        int castlingBitSet = 0;
        boolean whiteCastlingPossible = false;
        boolean blackCastlingPossible = false;

        for (int field = Board.a1; field <= Board.h8; field++) {
            if (rawBoard[field] != Board.illegal) {
                if (bitSet.get(fieldBitIndex)) {
                    byte piece = decodePiece(bitSet, pieceBitIndex);

                    if (piece == WHITE_KING_BLACKS_TURN) {
                        turn = GameStatus.TURN_BLACK;
                        piece = WHITE_KING;
                    } else if (piece == WHITE_PAWN_EN_PASSANT) {
                        if (field <= Board.h1) {
                            // rook pre castle
                            castlingBitSet = setBit(castlingBitSet, field == Board.a1 ? GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE : GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE, true);
                            whiteCastlingPossible = true;
                            piece = WHITE_ROOK;
                        } else {
                            // en passant pawn
                            enPassantField = (byte) (field - Board.LENGTH);
                            piece = WHITE_PAWN;
                        }
                    } else if (piece == BLACK_PAWN_EN_PASSANT) {
                        if (field >= Board.a8) {
                            // rook pre castle
                            castlingBitSet = setBit(castlingBitSet, field == Board.a8 ? GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE : GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE, true);
                            blackCastlingPossible = true;
                            piece = BLACK_ROOK;
                        } else {
                            // en passant pawn
                            enPassantField = (byte) (field + Board.LENGTH);
                            piece = BLACK_PAWN;
                        }
                    }

                    rawBoard[field] = pieceMap[piece];
                    pieceBitIndex += 4;
                }

                fieldBitIndex++;
            }
        }

        // TODO set hasCastled bit correctly - where to get info from?
        if (!whiteCastlingPossible) {
            castlingBitSet = setBit(castlingBitSet, GameStatus.BIT_WHITE_HAS_CASTLED, true);
        } else if (!blackCastlingPossible) {
            castlingBitSet = setBit(castlingBitSet, GameStatus.BIT_BLACK_HAS_CASTLED, true);
        }

        var gameStatusTmp = new GameStatus(plyCount, turn, lastMove, halfMoveClock, castlingBitSet, enPassantField, 0);
        long positionHash = Board.calculatePositionHash(rawBoard, gameStatusTmp);

        var gameStatus = new GameStatus(plyCount, turn, lastMove, halfMoveClock, castlingBitSet, enPassantField, positionHash);
        return new Board(rawBoard, gameStatus);
    }

    static private byte decodePiece(BitSet bitSet, int pieceBitIndex) {
        byte piece = 0;
        if (bitSet.get(pieceBitIndex)) {
            piece += 8;
        }
        if (bitSet.get(pieceBitIndex + 1)) {
            piece += 4;
        }
        if (bitSet.get(pieceBitIndex + 2)) {
            piece += 2;
        }
        if (bitSet.get(pieceBitIndex + 3)) {
            piece += 1;
        }

        return piece;
    }
}
