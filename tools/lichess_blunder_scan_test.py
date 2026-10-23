#!/usr/bin/env python3
"""Tests for the --variant selection in tools/lichess-blunder-scan.py.

Run from the repository root with an interpreter that has python-chess and requests:
    ../lichess-bot/venv/bin/python tools/lichess_blunder_scan_test.py
"""

import importlib.util
import pathlib
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "lichess_blunder_scan", pathlib.Path(__file__).with_name("lichess-blunder-scan.py"))
scan = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scan)

STANDARD_PGN = '[Event "Rated blitz game"]\n[Variant "Standard"]\n\n1. e4 e5 *\n'
CHESS960_PGN = ('[Event "Rated Chess960 game"]\n[Variant "Chess960"]\n'
                '[FEN "bbnqrkrn/pppppppp/8/8/8/8/PPPPPPPP/BBNQRKRN w KQkq - 0 1"]\n\n1. e4 e5 *\n')
NO_VARIANT_PGN = '[Event "Casual game"]\n\n1. d4 d5 *\n'


class SelectGamesTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.tmp.name)
        self.paths = []

        for name, text in [("std1", STANDARD_PGN), ("c960a", CHESS960_PGN),
                           ("plain", NO_VARIANT_PGN), ("c960b", CHESS960_PGN)]:
            path = root / f"{name}.pgn"
            path.write_text(text, encoding="utf-8")
            self.paths.append(path)

    def tearDown(self):
        self.tmp.cleanup()

    def stems(self, done, variant):
        return [path.stem for path in scan.select_games(self.paths, done, variant)]

    def test_noVariant_takesEveryUnscannedGame(self):
        self.assertEqual(["c960a", "c960b", "plain", "std1"], self.stems(set(), None),
                         "without --variant every unscanned game must be scanned")

    def test_chess960_takesOnlyChess960Games(self):
        self.assertEqual(["c960a", "c960b"], self.stems(set(), "chess960"),
                         "--variant chess960 must select only games with a Chess960 Variant header")

    def test_standard_takesGamesWithoutChess960Header(self):
        self.assertEqual(["plain", "std1"], self.stems(set(), "standard"),
                         "--variant standard must select standard games and games without a Variant header")

    def test_alreadyScannedGames_areLeftOut(self):
        self.assertEqual(["c960b"], self.stems({"c960a"}, "chess960"),
                         "a game already scanned must not be scanned again")

    def test_skippedGames_stayUnscannedForALaterRun(self):
        # a chess960 run marks only the games it scanned; the standard games were skipped
        done = {path.stem for path in scan.select_games(self.paths, set(), "chess960")}

        self.assertEqual(["plain", "std1"], self.stems(done, None),
                         "after a chess960 run, a plain run must still pick up the skipped standard games")


class PendingCandidatesTest(unittest.TestCase):

    @staticmethod
    def hit(move_number, loss_cp, verified_depth=None):
        hit = {"move_number": move_number, "scan": {"loss_cp": loss_cp}}

        if verified_depth is not None:
            hit["verified"] = {"depth": verified_depth}

        return hit

    def setUp(self):
        self.findings = {
            "std": {"chess960": False, "findings": [self.hit(10, 400)]},
            "c960": {"chess960": True, "findings": [self.hit(5, 300), self.hit(7, 50),
                                                    self.hit(9, 500, verified_depth=20)]},
        }

    def ids(self, variant):
        return [(game_id, hit["move_number"])
                for game_id, _, hit in scan.pending_candidates(self.findings, 150, 20, None, variant)]

    def test_chess960_verifiesOnlyChess960Candidates(self):
        self.assertEqual([("c960", 5)], self.ids("chess960"),
                         "--variant chess960 must verify only unverified Chess960 candidates above the threshold")

    def test_noVariant_verifiesEveryPendingCandidate(self):
        self.assertEqual([("std", 10), ("c960", 5)], self.ids(None),
                         "without --variant every pending candidate must be verified")


if __name__ == "__main__":
    unittest.main()
