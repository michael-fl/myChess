#!/usr/bin/env python3
"""Fit the king-attack curve against Stockfish's static evaluation instead of game results.

`tools/king-attack-curve.py` and `king-attack-bootstrap.py` fit `KING_ATTACK_PENALTY` by labelling
each position with the result of the game it came from. For the Chess960 corpus that label is a
**myChess self-play result**, and there the method eats itself: if myChess is blind to king
attacks then neither side converts one, so the fit learns "attack units are worthless" from games
that the blindness produced. The positions are fine; only the label is circular.

This replaces the label. For every position the target is

    Stockfish static NNUE evaluation  -  WeightingFunction.calculate

both from White's point of view and both **static** — Stockfish's `eval` command runs no search,
so this compares two evaluation functions on the same board rather than an evaluation against a
search. What the regression reports per attack-unit index is the evaluation myChess is missing
there, and no game outcome enters at any point. Stockfish's NNUE is itself trained on games, so
this is not a first-principles ground truth; it is an independent and far stronger yardstick that
myChess's own play cannot have contaminated, which is the weaker and sufficient claim.

Three things it does that the outcome-labelled tools do not:

**Monotonicity as a constraint of the fit.** Unconstrained, the fit falls somewhere in its range —
more attackers scoring as less danger, which is not a curve anyone would ship. Rather than
repairing that by hand afterwards it solves

    minimise  ||X b - y||^2      subject to  0 <= b1 <= b2 <= ... <= b8

by projected gradient descent with a pool-adjacent-violators projection onto the isotonic cone.
Where the constraint binds, PAVA merges the offending indices into one level: equal neighbouring
entries are the measurement saying it cannot separate them, not a rounding artifact.

**The gate, applied or not.** Production scores zero unless at least two distinct pieces bear on
the zone. A curve fitted without that gate cannot be shipped with it — the gate suppresses about
40 % of the term's mass, and the ungated index pools a lone queen with a rook and a knight, which
are not the same thing. `--gate` fits the quantity the engine actually computes.

**A placebo zone.** Bearing on the king zone also means having active pieces deep in enemy
territory, and a regression cannot separate the two. The placebo column repeats the whole fit over
a 3x3 zone on the king's rank, four files away — same depth, same side of the board, no king.
Whatever the placebo also earns is activity rather than danger.

**Fit against an evaluation that does NOT already carry the term.** This is the easiest way to
get a wrong curve, and the output gives no hint that it happened. The target is
`stockfish - myChess`, so running the tool against a build that already applies
`KING_ATTACK_PENALTY` fits the *residual after the term*, not the gap the term exists to close.
Measured on branch `attack-units`: the same corpus that yields 1.30 % explained variance against
master's evaluation yields **0.000 %** against the branch's, and the gated curve comes out all
zeros. That is the correct answer to a different question — it says the shipped curve has already
absorbed what it was fitted for — but it reads exactly like "there is no signal here". Fit on
master, or on a build with the term disabled.

Usage::

    ../lichess-bot/venv/bin/python tools/king-attack-vs-stockfish.py \\
        --epd tuning-data/mychess-selfplay-960.epd --gate
    ../lichess-bot/venv/bin/python tools/king-attack-vs-stockfish.py \\
        --epd tuning-data/human-dense.epd --limit 20000 --both

The expensive half is the per-position Stockfish call, so it is cached: `--probe-cache` holds one
JSON object per position and is reused on the next run. Delete it when the corpus or the
evaluation changes. Fitting alone then takes seconds.

**Do not run this while a time-controlled match is live.** It starts a Stockfish process and a
JVM at full tilt; an SPRT at `tc=40/60` would measure the contention.

@author Michael Fleischhauer
"""

import argparse
import json
import random
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_EPD = REPO_ROOT / "tuning-data" / "mychess-selfplay-960.epd"
DEFAULT_CACHE = REPO_ROOT / "test-results" / "king-attack-probe-cache.jsonl"
DEFAULT_OUTPUT = REPO_ROOT / "test-results" / "king-attack-vs-stockfish.json"

JAVA = "/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home/bin/java"
CLASSPATH = "target/classes:target/test-classes:target/dependency/*"
PROBE = "org.michaelfl.mychess.KingAttackProbe"
STOCKFISH = "/opt/homebrew/bin/stockfish"

MAX_UNITS = 8
MAX_PHASE = 24
CLIP_CP = 2000          # a handful of extreme static evals must not dominate least squares
BLOCK = 8               # consecutive corpus lines come from the same game
REPLICATES = 200
PGD_STEPS = 20000
PGD_TOL = 1e-9
PERCENTILES = (5, 95)
RESULT_TAG = " c9 "


# --------------------------------------------------------------------------- collecting


def read_fens(epd, limit):
    """The FEN of every usable line, with any result tag stripped."""
    fens = []

    for line in epd.read_text(encoding="utf-8", errors="ignore").splitlines():
        if not line.strip():
            continue

        fens.append(line.split(RESULT_TAG)[0].strip() if RESULT_TAG in line else line.strip())

        if limit and len(fens) >= limit:
            break

    return fens


def probe(fens):
    """Static eval, phase, units and attacker counts for both sides, plus the placebo zones."""
    proc = subprocess.run([JAVA, "-cp", CLASSPATH, PROBE], cwd=REPO_ROOT,
                          input="\n".join(fens), capture_output=True, text=True, check=True)
    lines = proc.stdout.splitlines()

    if len(lines) != len(fens):
        raise SystemExit(f"probe returned {len(lines)} lines for {len(fens)} positions — "
                         "the two are no longer aligned, refusing to fit on that")

    return lines


def stockfish_evals(fens, report_every=10000):
    """Static NNUE evaluation from White's point of view, or None where Stockfish declines."""
    proc = subprocess.Popen([STOCKFISH], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            text=True, bufsize=1)
    proc.stdin.write("uci\n")
    proc.stdin.flush()

    for line in proc.stdout:
        if line.startswith("uciok"):
            break

    proc.stdin.write("setoption name UCI_Chess960 value true\nisready\n")
    proc.stdin.flush()

    for line in proc.stdout:
        if line.startswith("readyok"):
            break

    out = []
    started = time.time()

    for index, fen in enumerate(fens):
        proc.stdin.write(f"position fen {fen}\neval\nisready\n")
        proc.stdin.flush()
        value = None

        for line in proc.stdout:
            if line.startswith("Final evaluation"):
                token = line.split()[2]
                value = None if token == "none" else float(token) * 100
            elif line.startswith("readyok"):
                break

        out.append(value)

        if (index + 1) % report_every == 0:
            print(f"  stockfish {index + 1:,}/{len(fens):,} "
                  f"({time.time() - started:.0f}s)", flush=True)

    proc.stdin.write("quit\n")
    proc.stdin.flush()
    proc.wait(timeout=10)

    return out


def collect(epd, limit, cache):
    """Per-position raw numbers, reusing `cache` for anything already measured."""
    fens = read_fens(epd, limit)
    known = {}

    if cache.exists():
        for line in cache.open(encoding="utf-8"):
            row = json.loads(line)
            known[row["fen"]] = row

        print(f"{len(known):,} Stellungen aus dem Cache", flush=True)

    missing = [fen for fen in fens if fen not in known]

    if missing:
        print(f"{len(missing):,} neu zu vermessen", flush=True)
        probed = probe(missing)
        theirs = stockfish_evals(missing)
        cache.parent.mkdir(parents=True, exist_ok=True)

        with cache.open("a", encoding="utf-8") as sink:
            for fen, raw, static in zip(missing, probed, theirs):
                if raw == "skip" or static is None:
                    continue

                my, phase, uw, aw, ub, ab, pw, pb = (int(v) for v in raw.split(";"))
                row = {"fen": fen, "my": my, "sf": static, "phase": phase,
                       "uw": uw, "aw": aw, "ub": ub, "ab": ab, "pw": pw, "pb": pb}
                known[fen] = row
                sink.write(json.dumps(row) + "\n")
                sink.flush()

    return [known[fen] for fen in fens if fen in known]


# --------------------------------------------------------------------------- fitting


def features(units_white, units_black, phase):
    """Phase-scaled indicator, identical to what the evaluation computes."""
    vector = [0.0] * MAX_UNITS
    scale = phase / MAX_PHASE

    if units_white > 0:
        vector[min(units_white, MAX_UNITS) - 1] += scale

    if units_black > 0:
        vector[min(units_black, MAX_UNITS) - 1] -= scale

    return vector


def rows_for(data, gated, placebo):
    """Design rows and the total sum of squares, for one variant of the feature."""
    rows = []
    total = 0.0

    for row in data:
        if placebo:
            white, black = row["pw"], row["pb"]
        else:
            white, black = row["uw"], row["ub"]

            if gated:
                white = white if row["aw"] >= 2 else 0
                black = black if row["ab"] >= 2 else 0

        target = max(-CLIP_CP, min(CLIP_CP, row["sf"] - row["my"]))
        rows.append((features(white, black, row["phase"]), target))
        total += target * target

    return rows, total


def block_normals(rows, block):
    """Per-block (X'X, X'y) so a bootstrap replicate is a sum rather than a re-scan."""
    blocks = []

    for start in range(0, len(rows), block):
        left = [[0.0] * MAX_UNITS for _ in range(MAX_UNITS)]
        right = [0.0] * MAX_UNITS

        for vector, target in rows[start:start + block]:
            for i in range(MAX_UNITS):
                if vector[i] == 0.0:
                    continue

                right[i] += vector[i] * target

                for j in range(MAX_UNITS):
                    left[i][j] += vector[i] * vector[j]

        blocks.append((left, right))

    return blocks


def combine(blocks, counts):
    left = [[0.0] * MAX_UNITS for _ in range(MAX_UNITS)]
    right = [0.0] * MAX_UNITS

    for (block_left, block_right), times in zip(blocks, counts):
        if not times:
            continue

        for i in range(MAX_UNITS):
            right[i] += times * block_right[i]

            for j in range(MAX_UNITS):
                left[i][j] += times * block_left[i][j]

    return left, right


def pava(values):
    """Projection onto the isotonic cone: pool adjacent violators, equal weights."""
    pooled, weights = [], []

    for value in values:
        pooled.append(value)
        weights.append(1.0)

        while len(pooled) > 1 and pooled[-2] > pooled[-1]:
            weight = weights[-2] + weights[-1]
            mean = (pooled[-2] * weights[-2] + pooled[-1] * weights[-1]) / weight
            pooled[-2:] = [mean]
            weights[-2:] = [weight]

    out = []

    for mean, weight in zip(pooled, weights):
        out.extend([mean] * int(weight))

    return out


def solve_isotonic(left, right, start=None):
    """Projected gradient descent over {0 <= b1 <= ... <= b8}; returns the curve and the steps."""
    lipschitz = max(sum(abs(value) for value in row) for row in left) or 1.0
    step = 1.0 / lipschitz
    current = list(start) if start else [0.0] * MAX_UNITS

    for taken in range(PGD_STEPS):
        gradient = [sum(left[i][j] * current[j] for j in range(MAX_UNITS)) - right[i]
                    for i in range(MAX_UNITS)]
        nxt = [max(0.0, value)
               for value in pava([current[i] - step * gradient[i] for i in range(MAX_UNITS)])]
        shift = max(abs(nxt[i] - current[i]) for i in range(MAX_UNITS))
        current = nxt

        if shift < PGD_TOL:
            return current, taken + 1

    return current, PGD_STEPS


def solve_free(left, right):
    """Unconstrained normal equations, for the reference column."""
    matrix = [row[:] + [right[i]] for i, row in enumerate(left)]

    for column in range(MAX_UNITS):
        pivot = max(range(column, MAX_UNITS), key=lambda r: abs(matrix[r][column]))
        matrix[column], matrix[pivot] = matrix[pivot], matrix[column]

        if abs(matrix[column][column]) < 1e-12:
            continue

        for row in range(MAX_UNITS):
            if row == column:
                continue

            factor = matrix[row][column] / matrix[column][column]

            for k in range(column, MAX_UNITS + 1):
                matrix[row][k] -= factor * matrix[column][k]

    return [matrix[i][MAX_UNITS] / matrix[i][i] if abs(matrix[i][i]) > 1e-12 else 0.0
            for i in range(MAX_UNITS)]


def residual(left, right, curve, total):
    quadratic = sum(curve[i] * sum(left[i][j] * curve[j] for j in range(MAX_UNITS))
                    for i in range(MAX_UNITS))

    return quadratic - 2 * sum(right[i] * curve[i] for i in range(MAX_UNITS)) + total


def percentile(values, share):
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round(share / 100.0 * (len(ordered) - 1)))))

    return ordered[index]


def fit(data, gated, placebo, replicates, seed):
    """Point estimate, bootstrap draws and diagnostics for one variant."""
    rows, total = rows_for(data, gated, placebo)
    blocks = block_normals(rows, BLOCK)
    left, right = combine(blocks, [1] * len(blocks))
    free = solve_free(left, right)
    curve, steps = solve_isotonic(left, right)
    rng = random.Random(seed)
    draws = [[] for _ in range(MAX_UNITS)]

    for _ in range(replicates):
        counts = [0] * len(blocks)

        for _ in range(len(blocks)):
            counts[rng.randrange(len(blocks))] += 1

        replicate_left, replicate_right = combine(blocks, counts)
        estimate, _ = solve_isotonic(replicate_left, replicate_right, start=curve)

        for k in range(MAX_UNITS):
            draws[k].append(estimate[k])

    feasible = all(curve[i] >= curve[i - 1] - 1e-9 for i in range(1, MAX_UNITS)) and min(curve) >= -1e-9

    return {"free": free, "curve": curve, "draws": draws, "steps": steps, "feasible": feasible,
            "residual": residual(left, right, curve, total), "no_term": total}


def report(label, fitted):
    print(f"\n=== {label} ===")
    print(f"PGD: {fitted['steps']} Schritte, zulaessig={fitted['feasible']}")
    print(f"Residuum {fitted['residual']:.6e} gegen {fitted['no_term']:.6e} ohne Term "
          f"({100 * (fitted['no_term'] - fitted['residual']) / fitted['no_term']:.3f} % erklaert)")
    print(f"\n{'units':>6}{'free':>9}{'monotone':>11}{'p5':>9}{'p95':>9}   evidence")
    print("-" * 62)

    for k in range(MAX_UNITS):
        low, high = (percentile(fitted["draws"][k], p) for p in PERCENTILES)
        verdict = "above zero" if low > 0.5 else "not separable from zero"
        print(f"{k + 1:>6}{fitted['free'][k]:>9.1f}{fitted['curve'][k]:>11.1f}"
              f"{low:>9.1f}{high:>9.1f}   {verdict}")

    rounded = [0] + [int(round(v)) for v in fitted["curve"]]
    print(f"\nKING_ATTACK_PENALTY = {{ {', '.join(str(v) for v in rounded)} }}")

    return rounded


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--epd", default=str(DEFAULT_EPD))
    parser.add_argument("--limit", type=int, default=0, help="positions to read, 0 for all")
    parser.add_argument("--gate", action="store_true",
                        help="zero a side's units below two attackers, as the evaluation does")
    parser.add_argument("--both", action="store_true", help="fit gated and ungated side by side")
    parser.add_argument("--placebo", action="store_true",
                        help="also fit the control zone four files from the king")
    parser.add_argument("--replicates", type=int, default=REPLICATES)
    parser.add_argument("--seed", type=int, default=20260830)
    parser.add_argument("--probe-cache", default=str(DEFAULT_CACHE))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    source = Path(args.epd)

    if not source.exists():
        raise SystemExit(f"corpus not found: {source}")

    print("HINWEIS: die Zielgroesse ist stockfish - myChess. Laeuft dieses myChess bereits mit\n"
          "         KING_ATTACK_PENALTY, misst der Fit die Restluecke NACH dem Term, nicht die\n"
          "         Luecke, die er schliessen soll. Auf master fitten, oder mit Nullkurve.\n", flush=True)

    started = time.time()
    data = collect(source, args.limit, Path(args.probe_cache))
    print(f"\n{len(data):,} verwertbare Stellungen ({time.time() - started:.0f}s)", flush=True)

    variants = []

    if args.both:
        variants = [("ohne Tor", False), ("mit Tor (>= 2 Angreifer)", True)]
    else:
        variants = [("mit Tor (>= 2 Angreifer)" if args.gate else "ohne Tor", args.gate)]

    if args.placebo:
        variants.append(("Placebo-Zone (Kontrolle)", False))

    result = {"epd": str(source), "positions": len(data), "variants": {}}

    for label, gated in variants:
        placebo = label.startswith("Placebo")
        fitted = fit(data, gated, placebo, args.replicates, args.seed)
        result["variants"][label] = {
            "curve": report(label, fitted),
            "free": fitted["free"],
            "residual": fitted["residual"],
            "p5": [percentile(fitted["draws"][k], 5) for k in range(MAX_UNITS)],
            "p95": [percentile(fitted["draws"][k], 95) for k in range(MAX_UNITS)]}

    Path(args.output).write_text(json.dumps(result, indent=1), encoding="utf-8")
    print(f"\n-> {args.output}")
    print(f"fertig nach {time.time() - started:.0f}s")
    print("\nZero lies on the boundary of the constraint set, so a point estimate near it is not "
          "evidence — only p5 > 0 is. Equal neighbouring entries mean the data do not separate "
          "those indices, not that they were rounded together.")


if __name__ == "__main__":
    main()
