#!/usr/bin/env python3
"""Tests for tools/match-elo-history.py.

Run from the repository root:
    python3 tools/match_elo_history_test.py
"""

import datetime
import importlib.util
import pathlib
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "match_elo_history", pathlib.Path(__file__).with_name("match-elo-history.py"))
history_tool = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(history_tool)

CANDIDATE = "cand"
BASE = "base"
STEP = datetime.timedelta(minutes=30)


def pgn_game(white, black, result, end):
    """One minimal cutechess-style game with the tags the tool reads."""
    return (f'[White "{white}"]\n[Black "{black}"]\n[Result "{result}"]\n'
            f'[GameEndTime "{end}.000 GMT+2"]\n\n1. e4 e5 {result}\n\n')


def at(hhmm):
    return datetime.datetime.fromisoformat(f"2026-10-07T{hhmm}:00")


class OutcomeTest(unittest.TestCase):

    def test_outcome_is_seen_from_the_candidate(self):
        self.assertEqual(history_tool.outcome_for({"White": CANDIDATE, "Black": BASE, "Result": "1-0"}, CANDIDATE),
                         "w", "candidate won with white")
        self.assertEqual(history_tool.outcome_for({"White": BASE, "Black": CANDIDATE, "Result": "1-0"}, CANDIDATE),
                         "l", "candidate lost with black")
        self.assertEqual(history_tool.outcome_for({"White": BASE, "Black": CANDIDATE, "Result": "1/2-1/2"}, CANDIDATE),
                         "d", "a draw is a draw from either side")

    def test_unfinished_or_foreign_games_are_skipped(self):
        self.assertIsNone(history_tool.outcome_for({"White": CANDIDATE, "Black": BASE, "Result": "*"}, CANDIDATE),
                          "an unfinished game has no outcome")
        self.assertIsNone(history_tool.outcome_for({"White": "x", "Black": "y", "Result": "1-0"}, CANDIDATE),
                          "a game without the candidate is not counted")


class HistoryTest(unittest.TestCase):

    def test_rows_are_cumulative_per_step(self):
        played = [(at("05:40"), "w"), (at("05:50"), "l"), (at("06:20"), "d"), (at("06:30"), "w")]
        rows = history_tool.history(played, STEP)

        self.assertEqual([(w, l, d) for _, w, l, d in rows], [(1, 1, 0), (2, 1, 1)],
                         "first step holds two games, the second step adds two more")

    def test_a_pause_is_skipped_not_repeated(self):
        played = [(at("05:40"), "w"), (at("09:00"), "l")]
        rows = history_tool.history(played, STEP)

        self.assertEqual(len(rows), 2, "the empty steps of a pause produce no lines")
        self.assertEqual(rows[-1][0], at("09:00"), "the last line is stamped with the last game, not a later step")

    def test_games_are_read_from_pgn_text(self):
        text = (pgn_game(CANDIDATE, BASE, "1-0", "2026-10-07T05:40:00")
                + pgn_game(BASE, CANDIDATE, "1-0", "2026-10-07T05:45:00")
                + pgn_game(BASE, "other", "1-0", "2026-10-07T05:46:00"))

        with tempfile.NamedTemporaryFile("w", suffix=".pgn", delete=False) as file:
            file.write(text)

        played = list(history_tool.games([file.name], CANDIDATE))
        pathlib.Path(file.name).unlink()

        self.assertEqual([outcome for _, outcome in played], ["w", "l"], "two candidate games, the third is foreign")
        self.assertEqual(played[0][0], at("05:40"), "end time parsed from GameEndTime")


if __name__ == "__main__":
    unittest.main()
