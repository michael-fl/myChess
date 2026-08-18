#!/usr/bin/env python3
"""
Separate depth-limited STS themes from evaluation-limited ones.

WHY THIS EXISTS. The per-theme table in docs/sts-history.md is read as an *evaluation*
diagnostic -- "which component is weakest". That reading is not free: a theme can score low
because myChess misjudges its positions, or simply because its positions need more plies
than the measurement depth allows. The two call for opposite work (a new evaluation term
versus LMR/PVS), so telling them apart decides where effort goes.

The test is a second run of the same suite at a greater depth. A theme whose score rises
sharply was depth-limited; a theme that stays flat is evaluation-limited, and those are the
real candidates for roadmap section 12.7.

What made the question urgent: of the zero-scoring positions at depth 8, 25 of 25 turned out
to be horizon effects (tools/scan-sts-misses.py plus a depth sweep). If the worst results of
a theme are mostly reach, its score may be too.

Only positions present in BOTH files are compared, so this can be run against a partial
second run -- the numbers are then valid for the subset and the per-theme n says how far it
got. Depth 8 measured 2026-08-18 needed 33 min; depth 10 about 4 h.

INTERPRETER. python-chess is not needed here, so any python3 works.

Usage:
    tools/compare-sts-depths.py                     # the tracked d8 run vs the d10 calibration
    tools/compare-sts-depths.py FILE_A FILE_B       # any two StsRunner reports
"""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_A = REPO_ROOT / "test-results" / "sts-4.4.2-d8.txt"
DEFAULT_B = REPO_ROOT / "test-results" / "sts-4.4.2-d10-calibration.txt"

#: Share of the *remaining headroom* the deeper run captures, above which the theme counts as
#: depth-limited.
#:
#: Raw points per position will not do, and Recapturing is why: at 87.4 % it has only 12.6
#: points of headroom left, while Undermine at 65.2 % has 34.8. The same +1.0 means something
#: entirely different in the two, so an absolute threshold marks every already-strong theme
#: "evaluation-limited" by ceiling effect alone. Normalizing by (100 - shallow) removes that.
#:
#: The 0.20 sits in the gap the first seven complete themes leave (3 % to 33 %); it is a
#: reading aid, not a finding.
DEPTH_LIMITED_HEADROOM_SHARE = 0.20

#: A per-position line from StsRunner. The label is captured non-greedily up to " played ",
#: never as \S+: eleven of the fifteen theme names contain spaces, and a \S+ label silently
#: matched only the four single-word themes when scan-sts-misses.py first did this.
LINE = re.compile(r"^\s*\d+/\d+\s+(.+?)\s+played\s+(\S+)\s+pts\s+(\d+)\s")

#: Suite file, read only to map a theme NAME back to its number.
SUITE = REPO_ROOT / "src" / "test" / "resources" / "sts" / "STS1-STS15_LAN_v6.epd"

ID_FIELD = re.compile(r'id "STS\(v(\d+)\.\d+\) (.+?)\.\d+"')


def theme_numbers() -> dict[str, int]:
    """
    Map each theme name in the suite to its theme number.

    Grouping by name would be wrong: theme 3 appears under two orderings of the same name
    ("Knight Outposts/Repositioning/Centralization" 85 times and
    ".../Centralization/Repositioning" once), so a name-keyed report splits it and shows the
    single stray position as its own theme -- with a meaningless swing computed from n=1.
    ``Sts.aggregate`` keys on the number for exactly this reason.
    """
    numbers = {}

    for line in SUITE.read_text().splitlines():
        match = ID_FIELD.search(line)

        if match:
            numbers[match.group(2)] = int(match.group(1))

    return numbers


def read_run(path: Path) -> dict[str, tuple[str, int]]:
    """Map ``<theme>.<nnn>`` to the move played and the points earned."""
    if not path.exists():
        raise SystemExit(f"no such run file: {path}")

    measured = {}

    for line in path.read_text().splitlines():
        match = LINE.match(line)

        if match:
            measured[match.group(1).strip()] = (match.group(2), int(match.group(3)))

    if not measured:
        raise SystemExit(f"{path} holds no position lines -- is it an StsRunner report?")

    return measured


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shallow", nargs="?", type=Path, default=DEFAULT_A)
    parser.add_argument("deeper", nargs="?", type=Path, default=DEFAULT_B)
    args = parser.parse_args()

    shallow = read_run(args.shallow)
    deeper = read_run(args.deeper)
    common = [label for label in deeper if label in shallow]

    if not common:
        raise SystemExit("the two runs share no positions")

    numbers = theme_numbers()
    # Keyed by theme number, with the most common spelling of the name for display.
    per_theme: dict[int, list[int]] = defaultdict(lambda: [0, 0, 0, 0])
    names: dict[int, dict[str, int]] = defaultdict(lambda: defaultdict(int))

    for label in common:
        name = label.rsplit(".", 1)[0]
        theme = numbers.get(name, 0)
        names[theme][name] += 1
        row = per_theme[theme]
        row[0] += shallow[label][1]
        row[1] += deeper[label][1]
        row[2] += 1
        row[3] += 1 if shallow[label][0] == deeper[label][0] else 0

    print(f"{args.shallow.name}  vs  {args.deeper.name}")
    print(f"{len(common)} positions in both "
          f"({len(shallow)} and {len(deeper)} measured respectively)")
    print()
    print(f"{'theme':<44} {'n':>4} {'shallow':>9} {'deeper':>9} {'gain':>7} {'of room':>8}"
          f" {'same':>6}  verdict")
    print("-" * 108)

    def headroom_share(row):
        points_a, points_b, count = row[0], row[1], row[2]
        shallow_pct = 100 * points_a / (100 * count)
        room = 100 - shallow_pct

        return ((points_b - points_a) / count) / room if room > 0 else 0.0

    for theme, row in sorted(per_theme.items(), key=lambda kv: -headroom_share(kv[1])):
        points_a, points_b, count, agree = row
        gain = (points_b - points_a) / count
        share = headroom_share(row)
        verdict = ("depth-limited" if share >= DEPTH_LIMITED_HEADROOM_SHARE
                   else "EVALUATION-limited")
        display = max(names[theme].items(), key=lambda kv: kv[1])[0]
        label = f"{theme:>2} {display}" if theme else display
        print(f"{label:<44} {count:>4} {100 * points_a / (100 * count):>8.1f}% "
              f"{100 * points_b / (100 * count):>8.1f}% {gain:>+7.1f} {100 * share:>7.0f}%"
              f" {100 * agree / count:>5.0f}%  {verdict}")

    total_a = sum(shallow[label][1] for label in common)
    total_b = sum(deeper[label][1] for label in common)
    agreed = sum(1 for label in common if shallow[label][0] == deeper[label][0])

    print("-" * 108)
    print(f"{'all compared positions':<44} {len(common):>4} "
          f"{100 * total_a / (100 * len(common)):>8.1f}% {100 * total_b / (100 * len(common)):>8.1f}% "
          f"{(total_b - total_a) / len(common):>+7.1f} {'':>8} {100 * agreed / len(common):>5.0f}%")
    print()
    print("Read the 'of room' column, not 'gain': it is the share of the headroom left at the")
    print("shallow depth that two more plies captured, which is comparable across themes with")
    print("different baselines. A theme that captures little of its headroom is where")
    print("evaluation work would pay. 'same' is how often the move was unchanged -- a second,")
    print("independent read on the same split. Themes with n below their full size are still")
    print("incomplete; the figures are valid for the subset measured so far.")


if __name__ == "__main__":
    main()
