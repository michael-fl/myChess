#!/usr/bin/env python3
"""Tests for tools/match-elo.py: the gate rounding rule of 2026-10-06.

Run from the repository root:
    python3 tools/match_elo_test.py
"""

import contextlib
import importlib.util
import io
import pathlib
import unittest

SPEC = importlib.util.spec_from_file_location(
    "match_elo", pathlib.Path(__file__).with_name("match-elo.py"))
match_elo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(match_elo)

# 862 - 841 - 740 over 2443 games: the interim standing of the 0.02 match that printed
# "elo +3.0" next to a FAIL, because the unrounded point estimate is +2.987.
BOUNDARY_SCORE = (862, 841, 740)
K2_AT_THREE = match_elo.parse_gate("K2:elo>=3")


def gate_lines(gates, elo, lower, upper):
    """Captures what print_gates writes, one string per gate."""
    out = io.StringIO()

    with contextlib.redirect_stdout(out):
        match_elo.print_gates(gates, elo, lower, upper)

    return [line for line in out.getvalue().splitlines() if line.strip().startswith("K")]


class GateRoundingTest(unittest.TestCase):

    def test_boundary_score_is_just_below_three(self):
        elo = match_elo.elo_and_interval(*BOUNDARY_SCORE)[0]

        self.assertAlmostEqual(elo, 2.987, places=3, msg="unrounded point estimate of 862-841-740")

    def test_rounded_gate_passes_what_prints_as_three(self):
        elo = match_elo.elo_and_interval(*BOUNDARY_SCORE)[0]
        lines = gate_lines([K2_AT_THREE], elo, -8.5, 14.5)

        self.assertIn("PASS", lines[0], "a printed +3.0 must pass >= +3.0")
        self.assertIn("(elo +3.0)", lines[0], "the gate line shows the value it was judged on")

    def test_rounding_follows_the_printed_digit(self):
        self.assertEqual(match_elo.judged_value(2.94), 2.9, "2.94 prints and judges as 2.9")
        self.assertEqual(match_elo.judged_value(2.96), 3.0, "2.96 prints and judges as 3.0")
        self.assertEqual(match_elo.judged_value(-10.04), -10.0, "a lower bound rounds the same way")

    def test_lower_bound_gate_uses_the_rounded_value(self):
        gate = match_elo.parse_gate("K1:lower>=-10")
        lines = gate_lines([gate], 0.0, -10.04, 10.0)

        self.assertIn("PASS", lines[0], "a lower bound printed as -10.0 passes >= -10")


if __name__ == "__main__":
    unittest.main()
