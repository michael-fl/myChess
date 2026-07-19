package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StaticExchangeEvaluation}. The tests are grouped per
 * method so every building block of the swap evaluation is exercised in
 * isolation:
 *
 * <ul>
 *   <li>{@link StaticExchangeEvaluation#getDirection} — the pure ray-step helper.</li>
 *   <li>{@code SEEMovesContainer} — the least-valuable-attacker-ordered attacker list.</li>
 *   <li>{@link StaticExchangeEvaluation#collectCaptureMovesInto} — attacker collection per color.</li>
 *   <li>{@link StaticExchangeEvaluation#findRevealedAttackerOrDefenderField} — the X-ray battery reveal.</li>
 *   <li>{@link StaticExchangeEvaluation#calcSEE(byte, int, byte)} — the recursive swap value.</li>
 *   <li>{@link StaticExchangeEvaluation#see} — the top-level entry point and its side effects.</li>
 * </ul>
 *
 * <p>Positions are built with {@link Fen#importFEN(String)}; each test documents
 * the FEN it uses. Square and piece references use the {@code Board} constants
 * throughout so the intent stays readable.
 *
 * @author Michael Fleischhauer
 */
class StaticExchangeEvaluationTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private static StaticExchangeEvaluation seeFor(String fen) {
        return new StaticExchangeEvaluation(Fen.importFEN(fen));
    }

    private static boolean contains(StaticExchangeEvaluation.SEEMovesContainer container, int field) {
        final byte[] fields = container.getFromFields();

        for (int i = 0; i < container.getSize(); i++) {
            if (fields[i] == (byte) field) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("SameParameterValue")
    private static void collectBoth(StaticExchangeEvaluation see, int toField) {
        see.collectCaptureMovesInto(see.getSEEMovesContainer(WHITE), (byte) toField, WHITE);
        see.collectCaptureMovesInto(see.getSEEMovesContainer(BLACK), (byte) toField, BLACK);
    }

    private static int captureMove(int fromField, int toField, byte capturedPiece) {
        return Move.create(fromField, toField, capturedPiece, Move.typeNormal);
    }

    @Nested
    class GetDirectionTests {

        @Test
        void east() {
            assertEquals(1, StaticExchangeEvaluation.getDirection((byte) Board.a1, (byte) Board.h1), "step east along a rank");
        }

        @Test
        void west() {
            assertEquals(-1, StaticExchangeEvaluation.getDirection((byte) Board.h1, (byte) Board.a1), "step west along a rank");
        }

        @Test
        void north() {
            assertEquals(Board.LENGTH, StaticExchangeEvaluation.getDirection((byte) Board.a1, (byte) Board.a8), "step north along a file");
        }

        @Test
        void south() {
            assertEquals(-Board.LENGTH, StaticExchangeEvaluation.getDirection((byte) Board.a8, (byte) Board.a1), "step south along a file");
        }

        @Test
        void northEast() {
            assertEquals(Board.LENGTH + 1, StaticExchangeEvaluation.getDirection((byte) Board.a1, (byte) Board.h8), "step north-east diagonal");
        }

        @Test
        void northWest() {
            assertEquals(Board.LENGTH - 1, StaticExchangeEvaluation.getDirection((byte) Board.h1, (byte) Board.a8), "step north-west diagonal");
        }

        @Test
        void southEast() {
            assertEquals(-Board.LENGTH + 1, StaticExchangeEvaluation.getDirection((byte) Board.a8, (byte) Board.h1), "step south-east diagonal");
        }

        @Test
        void southWest() {
            assertEquals(-Board.LENGTH - 1, StaticExchangeEvaluation.getDirection((byte) Board.h8, (byte) Board.a1), "step south-west diagonal");
        }
    }

    @Nested
    class SeeMovesContainerTests {

        // FEN: pawn a2, knight b1, rook c1, queen d1, white king h1, black king h8.
        private static final String FEN_MIXED_WEIGHTS = "7k/8/8/8/8/8/P7/1NRQ3K w - - 0 1";
        // FEN: white rooks a1/b1/c1, white king h1, black king h8.
        private static final String FEN_THREE_ROOKS = "7k/8/8/8/8/8/8/RRR4K w - - 0 1";

        @Test
        void addFieldOrdersByPieceWeightAscending() {
            var see = seeFor(FEN_MIXED_WEIGHTS);
            var container = see.getSEEMovesContainer(WHITE);

            // Insert in a jumbled order; the container must sort by piece weight.
            container.addField((byte) Board.d1);
            container.addField((byte) Board.a2);
            container.addField((byte) Board.c1);
            container.addField((byte) Board.b1);

            assertEquals(4, container.getSize(), "container size after four inserts");
            assertEquals(Board.a2, container.getFromFields()[0], "lightest attacker (pawn) first");
            assertEquals(Board.b1, container.getFromFields()[1], "knight second");
            assertEquals(Board.c1, container.getFromFields()[2], "rook third");
            assertEquals(Board.d1, container.getFromFields()[3], "queen (heaviest) last");
        }

        @Test
        void takeReturnsLeastValuableAttackerFirst() {
            var see = seeFor(FEN_MIXED_WEIGHTS);
            var container = see.getSEEMovesContainer(WHITE);

            container.addField((byte) Board.d1);
            container.addField((byte) Board.a2);
            container.addField((byte) Board.c1);
            container.addField((byte) Board.b1);

            assertEquals(Board.a2, container.take(), "first take: pawn");
            assertEquals(Board.b1, container.take(), "second take: knight");
            assertEquals(Board.c1, container.take(), "third take: rook");
            assertEquals(Board.d1, container.take(), "fourth take: queen");
        }

        @Test
        void hasPiecesLeftTracksConsumption() {
            var see = seeFor(FEN_THREE_ROOKS);
            var container = see.getSEEMovesContainer(WHITE);

            assertFalse(container.hasPiecesLeft(), "empty container has no pieces");

            container.addField((byte) Board.a1);
            container.addField((byte) Board.b1);

            assertTrue(container.hasPiecesLeft(), "two rooks available");

            container.take();
            assertTrue(container.hasPiecesLeft(), "one rook still available");

            container.take();
            assertFalse(container.hasPiecesLeft(), "all rooks consumed");
        }

        @Test
        void kingIsTakenLastAfterHeavierPieces() {
            var see = seeFor(FEN_MIXED_WEIGHTS);
            var container = see.getSEEMovesContainer(WHITE);

            // The king (on h1) must be played last, after the queen (on d1).
            container.addField((byte) Board.h1);
            container.addField((byte) Board.d1);

            assertEquals(Board.d1, container.take(), "queen is used before the king");
            assertEquals(Board.h1, container.take(), "king is the last attacker");
        }

        @Test
        void moveFieldToFrontReordersMiddleField() {
            var see = seeFor(FEN_THREE_ROOKS);
            var container = see.getSEEMovesContainer(WHITE);

            container.addField((byte) Board.a1);
            container.addField((byte) Board.b1);
            container.addField((byte) Board.c1);

            container.moveFieldToFront((byte) Board.c1);

            assertEquals(Board.c1, container.getFromFields()[0], "moved field is now first");
            assertEquals(Board.a1, container.getFromFields()[1], "previously-first field shifts back");
            assertEquals(Board.b1, container.getFromFields()[2], "middle field shifts back");
        }

        @Test
        void moveFieldToFrontIsNoOpWhenAlreadyFirst() {
            var see = seeFor(FEN_THREE_ROOKS);
            var container = see.getSEEMovesContainer(WHITE);

            container.addField((byte) Board.a1);
            container.addField((byte) Board.b1);
            container.addField((byte) Board.c1);

            container.moveFieldToFront((byte) Board.a1);

            assertEquals(Board.a1, container.getFromFields()[0], "front field stays first");
            assertEquals(Board.b1, container.getFromFields()[1], "order otherwise unchanged");
            assertEquals(Board.c1, container.getFromFields()[2], "order otherwise unchanged");
        }

        @Test
        void moveFieldToFrontThrowsWhenFieldMissing() {
            var see = seeFor(FEN_THREE_ROOKS);
            var container = see.getSEEMovesContainer(WHITE);

            container.addField((byte) Board.a1);
            container.addField((byte) Board.b1);
            container.addField((byte) Board.c1);

            assertThrows(IllegalStateException.class,
                    () -> container.moveFieldToFront((byte) Board.d1),
                    "moving an absent field must fail loudly");
        }
    }

    @Nested
    class CollectCaptureMovesTests {

        @Test
        void collectsWhitePawnAttackers() {
            // FEN: white pawns d3 and f3 attack e4.
            var see = seeFor("k7/8/8/8/8/3P1P2/8/K7 w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(2, container.getSize(), "two pawn attackers of e4");
            assertTrue(contains(container, Board.d3), "d3 pawn attacks e4");
            assertTrue(contains(container, Board.f3), "f3 pawn attacks e4");
        }

        @Test
        void collectsKnightAttacker() {
            // FEN: white knight f2 attacks e4.
            var see = seeFor("k7/8/8/8/8/8/5N2/K7 w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(1, container.getSize(), "one knight attacker of e4");
            assertTrue(contains(container, Board.f2), "f2 knight attacks e4");
        }

        @Test
        void collectsKingAttacker() {
            // FEN: white king d5 is adjacent to e4; black king a8 far away.
            var see = seeFor("k7/8/8/3K4/8/8/8/8 w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(1, container.getSize(), "one king attacker of e4");
            assertTrue(contains(container, Board.d5), "d5 king attacks e4");
        }

        @Test
        void collectsBishopAndQueenOnDiagonals() {
            // FEN: white bishop h7 and white queen b1 both bear on e4 diagonally.
            var see = seeFor("k7/7B/8/8/8/8/8/1Q4K1 w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(2, container.getSize(), "two diagonal attackers of e4");
            assertTrue(contains(container, Board.h7), "h7 bishop attacks e4");
            assertTrue(contains(container, Board.b1), "b1 queen attacks e4 diagonally");
        }

        @Test
        void collectsRookAndQueenOnOrthogonals() {
            // FEN: white rook e1 and white queen a4 both bear on e4 orthogonally.
            var see = seeFor("k7/8/8/8/Q7/8/8/4R2K w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(2, container.getSize(), "two orthogonal attackers of e4");
            assertTrue(contains(container, Board.e1), "e1 rook attacks e4 on the file");
            assertTrue(contains(container, Board.a4), "a4 queen attacks e4 on the rank");
        }

        @Test
        void blockedRayIsNotCollected() {
            // FEN: white rook e1 is blocked by the white pawn e2, so it does not attack e4.
            var see = seeFor("k7/8/8/8/8/8/4P3/4R2K w - - 0 1");
            var container = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(container, (byte) Board.e4, WHITE);

            assertEquals(0, container.getSize(), "the blocking pawn hides the rook and is no diagonal attacker itself");
        }

        @Test
        void collectsOnlyRequestedColor() {
            // FEN: black pawns d5 and f5 attack e4; there are no white attackers.
            var see = seeFor("k7/8/8/3p1p2/8/8/8/K7 w - - 0 1");
            var whiteContainer = see.getSEEMovesContainer(WHITE);
            var blackContainer = see.getSEEMovesContainer(BLACK);

            see.collectCaptureMovesInto(whiteContainer, (byte) Board.e4, WHITE);
            see.collectCaptureMovesInto(blackContainer, (byte) Board.e4, BLACK);

            assertEquals(0, whiteContainer.getSize(), "no white attackers of e4");
            assertEquals(2, blackContainer.getSize(), "two black pawn attackers of e4");
            assertTrue(contains(blackContainer, Board.d5), "d5 pawn attacks e4");
            assertTrue(contains(blackContainer, Board.f5), "f5 pawn attacks e4");
        }

        @Test
        void collectsSliderAttackerOnTheIndex64Square() {
            // Edge case: field index of c4 equals Board.illegal (64). A white rook on
            // c4 attacks the c8 target up the file and must be collected — the ray walk
            // lands on index 64, which the collection must not confuse with the border.
            var see = seeFor("k1p5/8/8/8/2R5/8/8/K7 w - - 0 1");
            var whiteContainer = see.getSEEMovesContainer(WHITE);

            see.collectCaptureMovesInto(whiteContainer, (byte) Board.c8, WHITE);

            assertEquals(1, whiteContainer.getSize(), "the c4 rook is a real attacker of c8");
            assertTrue(contains(whiteContainer, Board.c4), "rook on c4 (index 64) attacks c8 up the file");
        }
    }

    @Nested
    class FindRevealedAttackerTests {

        // FEN with an enemy pawn on the e4 target square; the bishop on c2 is the
        // piece that has just captured, the b1 slot holds the potential X-ray piece.
        private static String diagonalBattery(char b1Piece) {
            return "4k3/8/8/8/4p3/8/2B5/1" + b1Piece + "4K1 w - - 0 1";
        }

        // --- Orthogonal batteries (rook behind rook), one per compass direction.
        // The direction names the way the capture travels toward the e4 target;
        // the revealed rook sits behind the front rook on the far side of e4.

        @Test
        void revealsRookBehindRookOnFileNorth() {
            // FEN: front rook e2 captures northward toward e4; rook e1 is revealed behind it.
            var see = seeFor("7k/8/8/8/4p3/8/4R3/K3R3 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.e2, (byte) Board.e4);

            assertEquals(Board.e1, revealed, "rook south of the front rook is revealed on the file");
        }

        @Test
        void revealsRookBehindRookOnFileSouth() {
            // FEN: front rook e6 captures southward toward e4; rook e7 is revealed behind it.
            var see = seeFor("7k/4R3/4R3/8/4p3/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.e6, (byte) Board.e4);

            assertEquals(Board.e7, revealed, "rook north of the front rook is revealed on the file");
        }

        @Test
        void revealsRookBehindRookOnRankEast() {
            // FEN: front rook c4 captures eastward toward e4; rook a4 is revealed behind it.
            var see = seeFor("7k/8/8/8/R1R1p3/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c4, (byte) Board.e4);

            assertEquals(Board.a4, revealed, "rook west of the front rook is revealed on the rank");
        }

        @Test
        void revealsRookBehindRookOnRankWest() {
            // FEN: front rook g4 captures westward toward e4; rook h4 is revealed behind it.
            var see = seeFor("7k/8/8/8/4p1RR/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.g4, (byte) Board.e4);

            assertEquals(Board.h4, revealed, "rook east of the front rook is revealed on the rank");
        }

        // --- Diagonal batteries (queen behind bishop), one per compass direction.

        @Test
        void revealsQueenBehindBishopOnDiagonalNorthEast() {
            // FEN: front bishop c2 captures north-east toward e4; queen b1 is revealed behind it.
            var see = seeFor(diagonalBattery('Q'));

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c2, (byte) Board.e4);

            assertEquals(Board.b1, revealed, "queen south-west of the bishop is revealed on the diagonal; actual field: "
                    + ChessUtil.fieldToString(revealed) + ", actual piece: "+ ChessUtil.pieceToDebugString(see.getRawBoard()[revealed]));
        }

        @Test
        void revealsQueenBehindBishopOnDiagonalSouthEast() {
            // FEN: front bishop c6 captures south-east toward e4; queen b7 is revealed behind it.
            var see = seeFor("7k/1Q6/2B5/8/4p3/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c6, (byte) Board.e4);

            assertEquals(Board.b7, revealed, "queen north-west of the bishop is revealed on the diagonal");
        }

        @Test
        void revealsQueenBehindBishopOnDiagonalSouthWest() {
            // FEN: front bishop g6 captures south-west toward e4; queen h7 is revealed behind it.
            var see = seeFor("7k/7Q/6B1/8/4p3/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.g6, (byte) Board.e4);

            assertEquals(Board.h7, revealed, "queen north-east of the bishop is revealed on the diagonal");
        }

        @Test
        void revealsQueenBehindBishopOnDiagonalNorthWest() {
            // FEN: front bishop g2 captures north-west toward e4; queen h1 is revealed behind it.
            var see = seeFor("7k/8/8/8/4p3/8/6B1/K6Q w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.g2, (byte) Board.e4);

            assertEquals(Board.h1, revealed, "queen south-east of the bishop is revealed on the diagonal");
        }

        @Test
        void doesNotRevealKnightBehind() {
            // A knight on b1 is not a sliding attacker; nothing may be revealed.
            var see = seeFor(diagonalBattery('N'));

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c2, (byte) Board.e4);

            assertEquals(StaticExchangeEvaluation.NO_FIELD, revealed, "a knight cannot be an X-ray attacker");
        }

        @Test
        void revealsEnemySliderAsDefender() {
            // A black queen on b1 sits behind the c2 bishop on the diagonal to e4.
            // The reveal finds sliders of both colors: the enemy queen is a valid
            // defender (the caller routes it into the opponent's attacker set).
            var see = seeFor(diagonalBattery('q'));

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c2, (byte) Board.e4);

            assertEquals(Board.b1, revealed, "an enemy slider behind the capturer is revealed as a defender");
        }

        @Test
        void doesNotRevealRookOnDiagonal() {
            // A rook on b1 cannot attack along the diagonal, so it is not revealed.
            var see = seeFor(diagonalBattery('R'));

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c2, (byte) Board.e4);

            assertEquals(StaticExchangeEvaluation.NO_FIELD, revealed, "a rook does not attack along a diagonal");
        }

        @Test
        void returnsNoFieldSentinelWhenNothingIsRevealed() {
            // The ray behind the c2 bishop (away from the e4 target) is empty all the way
            // to the border, so there is nothing to reveal. The method must return its
            // NO_FIELD sentinel — a non-field value (-1), never a real square and never
            // Board.empty (0), which is a border index the reveal must not produce.
            var see = seeFor("4k3/8/8/8/4p3/8/2B5/6K1 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.c2, (byte) Board.e4);

            assertEquals(StaticExchangeEvaluation.NO_FIELD, revealed, "an empty ray reveals nothing and yields the NO_FIELD sentinel");
        }

        @Test
        void revealsSliderStandingOnTheIndex64Square() {
            // Edge case: field index of c4 equals Board.illegal (64). A reveal walk
            // from d5 away from the e6 target steps d5 -> c4; a white bishop there is a
            // legitimate piece and must not be discarded as if it were off-board.
            var see = seeFor("k7/8/8/8/2B5/8/8/K7 w - - 0 1");

            byte revealed = see.findRevealedAttackerOrDefenderField((byte) Board.d5, (byte) Board.e6);

            assertEquals(Board.c4, revealed, "bishop on c4 (index 64) is revealed, not mistaken for the border");
        }
    }

    @Nested
    class CalcSeeTests {

        @Test
        void noAttackersYieldsZero() {
            // Containers are never populated, so there is nothing to capture.
            var see = seeFor("4k3/8/8/4p3/3P4/8/8/6K1 w - - 0 1");

            assertEquals(0, see.calcSEE((byte) Board.e5, WHITE, Board.blackPawn),
                    "an empty attacker set gains nothing");
        }

        @Test
        void winsUndefendedPawn() {
            // FEN: white pawn d4 captures the undefended black pawn e5.
            var see = seeFor("4k3/8/8/4p3/3P4/8/8/6K1 w - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(100, see.calcSEE((byte) Board.e5, WHITE, Board.blackPawn),
                    "capturing an undefended pawn wins a full pawn");
        }

        @Test
        void equalTradePawnTakesDefendedPawn() {
            // FEN: white pawn d4 x e5, black pawn f6 recaptures.
            var see = seeFor("4k3/8/5p2/4p3/3P4/8/8/6K1 w - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(0, see.calcSEE((byte) Board.e5, WHITE, Board.blackPawn),
                    "pawn takes a pawn defended by a pawn: even exchange");
        }

        @Test
        void winsKnightForPawn() {
            // FEN: white pawn d4 x knight e5, black pawn f6 recaptures the pawn.
            var see = seeFor("4k3/8/5p2/4n3/3P4/8/8/6K1 w - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(200, see.calcSEE((byte) Board.e5, WHITE, Board.blackKnight),
                    "win a knight (300) for a pawn (100): net +200");
        }

        @Test
        void recapturesUndefendedKnight() {
            // FEN: black pawn f6 captures the undefended white knight e5.
            var see = seeFor("4k3/8/5p2/4N3/8/8/8/6K1 b - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(300, see.calcSEE((byte) Board.e5, BLACK, Board.whiteKnight),
                    "capturing an undefended knight wins its full value");
        }

        @Test
        void winsPawnWhenAttackersOutnumberDefenders() {
            // FEN: white pawn d4 and rook e2 attack e5, only the black f6 pawn defends.
            var see = seeFor("4k3/8/5p2/4p3/3P4/8/4R3/6K1 w - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(100, see.calcSEE((byte) Board.e5, WHITE, Board.blackPawn),
                    "two attackers versus one defender win a pawn");
        }

        @Test
        void revealedRookBatteryChangesTheOutcome() {
            // FEN: white rooks stacked on e2 (front) and e1 (behind) attack the black
            // rook e5, defended by the f6 pawn. The rook behind the front rook must
            // join the exchange once the front rook captures.
            var see = seeFor("4k3/8/5p2/4r3/8/8/4R3/4R1K1 w - - 0 1");
            collectBoth(see, Board.e5);

            assertEquals(100, see.calcSEE((byte) Board.e5, WHITE, Board.blackRook),
                    "the revealed second rook lets white come out a pawn ahead");
        }
    }

    @Nested
    class SeeEntryPointTests {

        @Test
        void populatesBothContainersAndMovesInitiatorToFront() {
            // FEN: white queen e2 and pawn d4 attack the black rook e5, defended by f6.
            // The initiating move is the queen capture, even though the pawn is the
            // cheaper attacker — so the queen's field must be sorted to the front.
            var see = seeFor("4k3/8/5p2/4r3/3P4/8/4Q3/6K1 w - - 0 1");
            int move = Move.create(Board.e2, Board.e5, Board.blackRook, Move.typeNormal);

            see.see(move);

            var whiteContainer = see.getSEEMovesContainer(WHITE);
            var blackContainer = see.getSEEMovesContainer(BLACK);

            assertEquals(2, whiteContainer.getSize(), "white pawn d4 and queen e2 attack e5");
            assertEquals(Board.e2, whiteContainer.getFromFields()[0], "the initiating queen is moved to the front");
            assertTrue(contains(whiteContainer, Board.d4), "the pawn attacker is still present");
            assertEquals(1, blackContainer.getSize(), "the f6 pawn is the sole black defender");
            assertTrue(contains(blackContainer, Board.f6), "f6 pawn defends e5");
        }

        // --- Return value of the public entry point: the static exchange value of the
        // given capture, from the moving side's perspective (positive = the capture
        // wins material). All positions are white to move, so the sign is unambiguous.

        @Test
        void returnsGainWhenWinningAnUndefendedPawn() {
            // FEN: white pawn d4 captures the undefended black pawn e5.
            var see = seeFor("4k3/8/8/4p3/3P4/8/8/6K1 w - - 0 1");
            int move = captureMove(Board.d4, Board.e5, Board.blackPawn);

            assertEquals(100, see.see(move), "capturing an undefended pawn wins a full pawn");
        }

        @Test
        void returnsGainWhenWinningAKnightForAPawn() {
            // FEN: white pawn d4 x knight e5, defended only by the f6 pawn.
            var see = seeFor("4k3/8/5p2/4n3/3P4/8/8/6K1 w - - 0 1");
            int move = captureMove(Board.d4, Board.e5, Board.blackKnight);

            assertEquals(200, see.see(move), "win a knight (300) for a pawn (100): net +200");
        }

        @Test
        void returnsLossForALosingCapture() {
            // FEN: white knight d3 x pawn e5, defended by the f6 pawn — the knight is lost.
            var see = seeFor("4k3/8/5p2/4p3/8/3N4/8/6K1 w - - 0 1");
            int move = captureMove(Board.d3, Board.e5, Board.blackPawn);

            assertEquals(-200, see.see(move), "win a pawn (100) but lose the knight (300): net -200");
        }

        @Test
        void returnsGainWhenWinningAnUndefendedRook() {
            // FEN: white rook e2 captures the undefended black rook e5.
            var see = seeFor("4k3/8/8/4r3/8/8/4R3/6K1 w - - 0 1");
            int move = captureMove(Board.e2, Board.e5, Board.blackRook);

            assertEquals(500, see.see(move), "capturing an undefended rook wins its full value");
        }

        @Test
        void returnsPositiveWhenAttackersOutnumberDefenders() {
            // The black knight on c4 is heavily contested — both sides stack attackers
            // and defenders on it, including X-ray batteries (white: queen e2 and bishop
            // f1 behind the d3 pawn on the c4-d3-e2-f1 diagonal; black: queen a6 behind
            // b5 and bishop e6 behind d5). The sign, however, is decided by the very
            // first capture: dxc4 wins a knight (300) for a pawn (100), and white can
            // stop right after black's cheapest recapture, netting about +200 — so the
            // exchange is positive no matter how the deeper melee would play out.
            var see = seeFor("4kb2/p1p1pppp/qnr1b3/1p1pP3/1rn4R/NPKPN3/P1P1QPPP/R1B2B2 w - - 0 1");
            int move = captureMove(Board.d3, Board.c4, Board.blackKnight);

            assertTrue(see.see(move) > 0, "dxc4 wins a knight for a pawn and white can stop early: positive exchange");
        }

        @Test
        void returnsPositiveWithEqualCountBecauseWhiteCanStopEarly() {
            // Same position as above but the white king now stands on d1 instead of c3,
            // so it no longer attacks c4: 7 white attackers versus 7 black defenders.
            // The exchange is still positive for white — white captures the knight first
            // (pawn takes knight) and can stop before any unfavorable continuation.
            var see = seeFor("4kb2/p1p1pppp/qnr1b3/1p1pP3/1rn4R/NP1PN3/P1P1QPPP/R1BK1B2 w - - 0 1");
            int move = captureMove(Board.d3, Board.c4, Board.blackKnight);

            assertTrue(see.see(move) > 0, "with equal counts the first-striking side can stop early and stays positive");
        }

        @Test
        void returnsPositiveCapturingPawnWithAttackerMajority() {
            // The target on c4 is now a black pawn, attacked by 8 white attackers versus
            // 7 black defenders. Here the SE diagonal battery behind the d3 pawn is
            // bishop e2 then queen f1; black again has the queen a6 behind b5 and the
            // bishop e6 behind d5. With the attacker majority white wins a clean pawn,
            // so the exchange is positive for white.
            var see = seeFor("4kb2/p2npppp/qnr1b3/1p1pP3/1rp4R/NPKPN3/P1P1BPPP/R1B2Q2 w - - 0 1");
            int move = captureMove(Board.d3, Board.c4, Board.blackPawn);

            assertTrue(see.see(move) > 0, "the attacker majority wins a pawn: positive exchange for white");
        }

        @Test
        void returnsPositiveWhenBlackDeclinesToRecaptureIntoAMinority() {
            // White has the attacker majority on the c4 pawn: the battery pawn d3 ->
            // bishop e2 -> queen f1 on the c4-d3-e2-f1 diagonal adds two white pieces on
            // top of the direct attackers, one more than black's defenders (rooks a4/c6,
            // knight b6, pawns b5/d5 with queen a6 behind b5 and bishop e6 behind d5).
            // Because of that surplus every recapture sequence loses more for black, so
            // after dxc4 black simply declines to recapture and concedes the pawn — the
            // exchange resolves immediately (black stands pat at the first reply), it is
            // NOT fought out to the end. A SEE swap can never dip negative for the moving
            // side and then recover, because the side that is ahead would just stop there.
            // Same majority mechanism as returnsPositiveCapturingPawnWithAttackerMajority,
            // on a different layout. SEE = +100.
            var see = seeFor("4kb2/p1p1p1pp/qnr1b3/1p1p1n2/r1p4R/NPKPN3/P1P1BPPP/R1B2Q2 w - - 0 1");
            int move = captureMove(Board.d3, Board.c4, Board.blackPawn);

            assertEquals(100, see.see(move), "white nets one pawn: black cannot profitably recapture into the minority");
        }

        @Test
        void capturingRevealsAHiddenEnemyDefender() {
            // The white rook e2 is the only direct attacker of the black pawn e5; the
            // black rook e1 sits behind it on the e-file and is initially blocked. When
            // the white rook captures on e5 and vacates e2, that black rook is revealed
            // as a defender and recaptures — so white loses a rook (500) for a pawn (100).
            // This is the opposite-color X-ray reveal: a departing white attacker uncovers
            // a black defender behind it.
            var see = seeFor("k7/8/8/4p3/8/8/4R3/K3r3 w - - 0 1");
            int move = captureMove(Board.e2, Board.e5, Board.blackPawn);

            assertEquals(-400, see.see(move), "the revealed black rook recaptures: win a pawn (100), lose a rook (500)");
        }

        @Test
        void returnsNegativeForBlackWhenHiddenWhiteDefendersRecapture() {
            // Black captures the white pawn on d4. It looks winning — black has three
            // direct attackers (rooks b4/d3, bishop e3) and white has no visible
            // defender. But every white defender is X-ray-hidden behind a black
            // attacker: rook a4 behind b4, rook d2 and queen d1 behind d3, bishop f2
            // behind e3. When the black bishop takes on d4 it uncovers the f2 bishop,
            // which recaptures, so black loses a bishop (300) for a pawn (100).
            var see = seeFor("k7/2p1p3/8/8/Rr1P4/3rb3/3R1B2/K2Q4 b - - 0 1");
            int move = captureMove(Board.e3, Board.d4, Board.whitePawn);

            int weight = see.see(move);
            assertTrue(weight < 0, "the hidden white defenders make the capture a losing one for black; actual weight: " + weight);
        }

        @Test
        void returnsNegativeForBlackAgainstAnAlternatingFileBattery() {
            // The whole d-file is a strictly alternating stack. From the d2 target
            // upward: white pawn (d2), black rook (d3), white rook (d4), black queen
            // (d5), white queen (d6), black rook (d7), white rook (d8). Black grabbing
            // the pawn with Rd3xd2 looks free, but the white rook d4 is hidden right
            // behind the capturing rook and recaptures — with the white queen d6 and
            // rook d8 waiting further up. Black loses a rook (500) for a pawn (100).
            var see = seeFor("kn1R4/3r4/3Q4/3q4/3R4/3r4/3P4/K7 b - - 0 1");
            int move = captureMove(Board.d3, Board.d2, Board.whitePawn);

            assertTrue(see.see(move) < 0, "the alternating file battery hides enough defenders to punish the capture");
        }
    }

    /**
     * {@link StaticExchangeEvaluation#see} is designed to be called repeatedly on
     * the same instance (one instance per board, one {@code see} call per candidate
     * capture). Each call must start from empty attacker containers — both the write
     * index (size) and the read index must be reset — so leftover state from a prior
     * call cannot corrupt the next one. These tests seed stale container state through
     * the package-private accessors and assert the next {@code see} call still returns
     * the correct value.
     */
    @Nested
    class SeeReusabilityTests {

        @Test
        void clearsLeftoverWhiteAttackersBeforeUse() {
            // Equal trade: white pawn d4 x pawn e5, defended by the f6 pawn -> SEE 0.
            var see = seeFor("4k3/8/5p2/4p3/3P4/8/8/6K1 w - - 0 1");

            // Seed a stale attacker (as if left over from a previous run). If the
            // container is not emptied, white has a phantom second attacker and the
            // even trade wrongly becomes a won pawn.
            see.getSEEMovesContainer(WHITE).addField((byte) Board.g1);

            int result = see.see(captureMove(Board.d4, Board.e5, Board.blackPawn));

            assertEquals(0, result, "see() must empty the container (reset the write index) and ignore the stale attacker");
        }

        @Test
        void resetsWhiteReadIndexBeforeUse() {
            // Win a knight for a pawn: white pawn d4 x knight e5, defended by f6 -> SEE +200.
            var see = seeFor("4k3/8/5p2/4n3/3P4/8/8/6K1 w - - 0 1");

            // Seed and consume an entry so the read index is advanced. If it is not
            // reset, take() skips the initiating pawn and the wrong piece captures first.
            var white = see.getSEEMovesContainer(WHITE);
            white.addField((byte) Board.g1);
            white.take();

            int result = see.see(captureMove(Board.d4, Board.e5, Board.blackKnight));

            assertEquals(200, result, "see() must reset the read index so the initiating capture is played first");
        }

        @Test
        void clearsLeftoverBlackDefendersBeforeUse() {
            // Win an undefended pawn: white pawn d4 x pawn e5, no black defender -> SEE +100.
            var see = seeFor("4k3/8/8/4p3/3P4/8/8/6K1 w - - 0 1");

            // Seed a stale defender in black's container. If it is not emptied, the
            // capture looks defended and the value wrongly drops from +100 to 0.
            see.getSEEMovesContainer(BLACK).addField((byte) Board.e8);

            int result = see.see(captureMove(Board.d4, Board.e5, Board.blackPawn));

            assertEquals(100, result, "see() must empty black's container so the phantom defender does not lower the value");
        }

        @Test
        void isReusableForSuccessiveCapturesOnTheSameInstance() {
            // One board, two independent captures: b4xc5 (undefended pawn, +100) and
            // g4xh5 (undefended knight, +300). The same instance must evaluate each
            // correctly regardless of order.
            var see = seeFor("k7/8/8/2p4n/1P4P1/8/8/K7 w - - 0 1");

            int first = see.see(captureMove(Board.b4, Board.c5, Board.blackPawn));
            int second = see.see(captureMove(Board.g4, Board.h5, Board.blackKnight));

            assertEquals(100, first, "first capture: undefended pawn");
            assertEquals(300, second, "second capture on the reused instance: undefended knight");
        }
    }

    @Nested
    class ConstructorTests {

        @Test
        void wrapsTheBoardsRawArray() {
            var board = Fen.importFEN("7k/8/8/8/8/8/8/RRR4K w - - 0 1");
            var see = new StaticExchangeEvaluation(board);

            assertSame(board.getRawBoard(), see.getRawBoard(), "SEE operates directly on the board's raw array");
            assertEquals(Board.whiteRook, see.getRawBoard()[Board.a1], "a1 rook is visible through the raw board");
        }
    }
}
