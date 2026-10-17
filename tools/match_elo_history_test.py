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


class TimeLeftTest(unittest.TestCase):

    def test_declared_total_is_read_from_the_runners_state_file(self):
        with tempfile.TemporaryDirectory() as folder:
            pathlib.Path(folder, "match-x.rounds").write_text("2100\n# tc=60+1\n")
            segment = pathlib.Path(folder, "match-x-seg01.pgn")

            self.assertEqual(history_tool.declared_total([segment]), 4200,
                             "2100 declared rounds of two games are 4200 games")

    def test_declared_total_without_state_file_is_unknown(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertIsNone(history_tool.declared_total([pathlib.Path(folder, "match-x-seg01.pgn")]),
                              "without the .rounds file the length is unknown")

    def test_pace_is_the_median_gap_so_a_pause_does_not_count(self):
        # gaps of 10 min, 10 min and one 3 h pause: the median is 10 min, so 6 games per hour
        ends = [at("05:00"), at("05:10"), at("05:20"), at("08:20")]
        played, to_go, rate, left, finish = history_tool.time_left(ends, total=10)

        self.assertEqual((played, to_go), (4, 6), "four of ten games played")
        self.assertAlmostEqual(rate, 6.0, msg="the 3 h pause must not slow the pace down")
        self.assertEqual(left, datetime.timedelta(minutes=60), "six games at ten minutes each")
        self.assertEqual(finish, at("09:20"), "counted from the last game's end")

    def test_no_estimate_without_a_total_or_with_a_finished_match(self):
        ends = [at("05:00"), at("05:10")]

        self.assertIsNone(history_tool.time_left(ends, total=None), "unknown length gives no estimate")
        self.assertIsNone(history_tool.time_left(ends, total=2), "a finished match has nothing left")
        self.assertIsNone(history_tool.time_left(ends[:1], total=10), "one game gives no pace")


if __name__ == "__main__":
    unittest.main()
