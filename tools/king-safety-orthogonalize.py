#!/usr/bin/env python3
"""Ask whether one king-safety candidate adds anything the other does not already carry.

**Why this is a separate question.** `king-safety-screen.py` fits each candidate on its own, so
two features that measure overlapping things both get credit for the shared part. Virtual queen
mobility and file danger are the case in point: both are ways of asking how open the lines around
the king are, and their solo figures (0.813 % and 2.238 %) cannot simply be added.

**The method.** Fit A, subtract what A predicts from the target, fit B on what is left. What B
explains of *that residual* is what B contributes beyond A. Run it in both directions, because the
pair of numbers is the answer and either one alone is misleading:

    A allein 0.8 %, danach B zusaetzlich 1.5 %      B allein 2.2 %, danach A zusaetzlich 0.2 %

reads as *B subsumes A* — A's 0.8 % was almost entirely inside B, while B keeps 1.5 % that A never
had. Two genuinely independent features instead keep most of their solo figure in both directions.

**What it does not settle.** Subsumption is about information, not about cost or about what
survives a search. A subsumed feature may still be the cheaper of the two to compute, and the
screen's standing caveat holds here as well: a strong number is not a promise, only a flat one is
a verdict.

**The other two-candidate question, under ``--difference``.** When the two are not different
features but two *encodings of the same* feature, orthogonalizing them is meaningless — the honest
question is which one explains more. Two solo figures do not answer that: comparing a point
estimate against the other run's interval is the wrong test, and it has been made here once
already. ``--difference`` fits both on the *same* resampled blocks, so the shared corpus noise
cancels, and reports an interval on the difference itself.

Candidates and their index definitions come from `king-safety-screen.py`, so the two tools cannot
drift apart. Names must match its ``CANDIDATES`` keys::

    ../lichess-bot/venv/bin/python tools/king-safety-orthogonalize.py \\
        "Liniengefahr (offen/halboffen)" "virtuelle Damen-Mobilitaet"

    ../lichess-bot/venv/bin/python tools/king-safety-orthogonalize.py --difference \\
        "Liniengefahr (offen/halboffen)" "Liniengefahr, 6 Stufen"

@author Michael Fleischhauer
"""

import argparse
import importlib.util
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import isotonic_fit as iso                                          # noqa: E402

TOOLS = Path(__file__).resolve().parent
DEFAULT_REPLICATES = 60


def load_screen():
    """The screen module, imported despite the hyphens in its file name."""
    spec = importlib.util.spec_from_file_location("king_safety_screen",
                                                  TOOLS / "king-safety-screen.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    return module


def build(index_of, rows, targets):
    """Design rows and the sum of squared targets, for one candidate against a given target."""
    design, total = [], 0.0

    for row, target in zip(rows, targets):
        white, black = index_of(row)
        design.append((iso.features(black, white, row["phase"]), target))
        total += target * target

    return design, total


def predict(index_of, rows, curve):
    """What a fitted curve says each position's target should be."""
    out = []

    for row in rows:
        white, black = index_of(row)
        for_white = curve[min(white, iso.MAX_INDEX) - 1] if white > 0 else 0.0
        for_black = curve[min(black, iso.MAX_INDEX) - 1] if black > 0 else 0.0
        out.append((for_black - for_white) * row["phase"] / iso.MAX_PHASE)

    return out


def explained(fitted):
    return 100 * (fitted["no_term"] - fitted["residual"]) / fitted["no_term"]


def sequence(first, second, candidates, rows, base, replicates, seed):
    """Fit `first` alone, then `second` on what `first` left over."""
    design, total = build(candidates[first], rows, base)
    solo = iso.fit(design, total, replicates, seed)
    residual = [t - p for t, p in zip(base, predict(candidates[first], rows, solo["curve"]))]
    design, total = build(candidates[second], rows, residual)
    extra = iso.fit(design, total, replicates, seed)

    print(f"  {first:<32} allein: {explained(solo):.3f} %")
    print(f"  danach {second:<26} zusaetzlich: {explained(extra):.3f} %")
    print(f"    dessen Kurve danach: {[int(round(v)) for v in extra['curve']]}\n")

    return {"first": first, "second": second, "first_alone": explained(solo),
            "second_after": explained(extra),
            "second_curve": [0] + [int(round(v)) for v in extra["curve"]]}


def block_totals(targets, block=iso.BLOCK):
    """Sum of squared targets per block, so a replicate's denominator is resampled too."""
    return [sum(t * t for t in targets[start:start + block])
            for start in range(0, len(targets), block)]


def explained_from(blocks, counts, totals):
    left, right = iso.combine(blocks, counts)
    total = sum(times * value for times, value in zip(counts, totals))
    curve, _ = iso.solve_isotonic(left, right)

    return 100 * (total - iso.residual(left, right, curve, total)) / total


def difference(first, second, candidates, rows, base, replicates, seed):
    """Paired block bootstrap on `second` minus `first`, in explained residual variance."""
    normals = {name: iso.block_normals(build(candidates[name], rows, base)[0])
               for name in (first, second)}
    totals = block_totals(base)
    count = len(totals)
    ones = [1] * count
    point = {name: explained_from(normals[name], ones, totals) for name in normals}
    print(f"  {first:<32} {point[first]:.3f} %")
    print(f"  {second:<32} {point[second]:.3f} %")
    print(f"  Differenz (Punktschaetzer)       {point[second] - point[first]:+.3f} pp\n")
    rng = random.Random(seed)
    draws = []

    for _ in range(replicates):
        counts = [0] * count

        for _ in range(count):
            counts[rng.randrange(count)] += 1

        draws.append(explained_from(normals[second], counts, totals)
                     - explained_from(normals[first], counts, totals))

    low, high = (iso.percentile(draws, p) for p in iso.PERCENTILES)
    verdict = ("ueber null" if low > 0 else "unter null" if high < 0
               else "NICHT von null zu trennen")
    print(f"  gepaarter Bootstrap: {sum(draws) / len(draws):+.3f} pp, "
          f"90%-Intervall [{low:+.3f}, {high:+.3f}] -> {verdict}")

    return {"first": first, "second": second, "first_alone": point[first],
            "second_alone": point[second], "difference_p5": low, "difference_p95": high,
            "difference_mean": sum(draws) / len(draws), "verdict": verdict}


def main():
    screen = load_screen()
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("first", help=f"candidate name, one of: {list(screen.CANDIDATES)}")
    parser.add_argument("second")
    parser.add_argument("--epd", default=str(screen.DEFAULT_EPD))
    parser.add_argument("--limit", type=int, default=0, help="positions to read, 0 for all")
    parser.add_argument("--replicates", type=int, default=DEFAULT_REPLICATES)
    parser.add_argument("--seed", type=int, default=20260901)
    parser.add_argument("--output", default="")
    parser.add_argument("--difference", action="store_true",
                        help="two encodings of the same feature: which explains more, with an "
                             "interval on the difference rather than on each figure")
    args = parser.parse_args()

    for name in (args.first, args.second):
        if name not in screen.CANDIDATES:
            raise SystemExit(f"unknown candidate {name!r} — known: {list(screen.CANDIDATES)}")

    source = Path(args.epd)

    if not source.exists():
        raise SystemExit(f"corpus not found: {source}")

    rows = screen.load_rows(source, args.limit)
    base = [max(-iso.CLIP_CP, min(iso.CLIP_CP, row["sf"] - row["my"])) for row in rows]
    print(f"{len(rows):,} Stellungen\n")

    if args.difference:
        result = difference(args.first, args.second, screen.CANDIDATES, rows, base,
                            args.replicates, args.seed)
        print("\nEin Intervall, das die Null einschliesst, heisst: die beiden Kodierungen sind auf\n"
              "diesem Korpus nicht zu unterscheiden — dann gewinnt die einfachere.")
    else:
        result = [sequence(args.first, args.second, screen.CANDIDATES, rows, base,
                           args.replicates, args.seed),
                  sequence(args.second, args.first, screen.CANDIDATES, rows, base,
                           args.replicates, args.seed)]

        print("Beide Richtungen zusammen lesen: faellt die zweite Zahl einer Richtung weit unter\n"
              "den Alleinwert desselben Merkmals, steckte es groesstenteils schon im anderen.")

    if args.output:
        Path(args.output).write_text(json.dumps(result, indent=1), encoding="utf-8")
        print(f"\n-> {args.output}")


if __name__ == "__main__":
    main()
