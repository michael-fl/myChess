#!/usr/bin/env python3
"""Screen a king-safety candidate before anyone builds it into the evaluation.

**Why this exists.** Establishing that attack units carry about 80 cp at their loudest and 1.3 %
of the residual variance took four attempts, a fitted curve, a 22 % NPS regression and a
−42.9 Elo match spread over three weeks. The same number came out of an hour with this method and
a cached Stockfish pass, before a line of production code existed. Every further candidate gets
measured this way first.

**What it measures.** For every position,

    Stockfish static NNUE evaluation  −  myChess's own static evaluation

both from White's point of view, both static — Stockfish's `eval` runs no search, so this compares
two evaluation functions rather than an evaluation against a search. Regressing that residual on a
candidate feature says how much of what myChess is missing that feature accounts for. It is
already controlled for everything the evaluation has, king piece-square table included, because
the target is the residual *after* it.

**What it cannot say.** A **flat** result is a reliable stop signal: a feature that explains
nothing of the gap cannot help. A **strong** one is not a promise — attack units screened at
1.3 % and still lost 42.9 Elo, because a static term also has to survive the search, the clock
and its own cost. Reject with it; do not predict with it.

**Two traps, both paid for once already.**

*Fit against an evaluation that does not already carry the term.* The target is
`stockfish − myChess`, so screening against a build that already applies the candidate measures
the residual *after* it. On branch `attack-units` the same corpus that gives 1.30 % against
master's evaluation gives 0.000 % against the branch's — the right answer to a different question,
and indistinguishable from "no signal".

*A coefficient is worth nothing without its occupancy.* The first pawn-storm encoding returned
141.5 cp at its top index, on 0.5 % of samples. Re-encoded densely so the mass spread, the same
idea returned 28.5 cp. The column of shares below is not decoration.

Usage::

    ../lichess-bot/venv/bin/python tools/king-safety-screen.py
    ../lichess-bot/venv/bin/python tools/king-safety-screen.py --limit 5000 --replicates 40

**Only the Stockfish half is stored.** `test-results/king-safety-stockfish-evals.jsonl` holds one
static evaluation per position and nothing else, because that is the only part a rerun cannot
recompute in seconds. Everything derived from this repository's own code — myChess's evaluation and
every feature column — is regenerated on every run, deliberately: a stored copy would go stale the
moment `WeightingFunction` or `KingSafetyFeatures` changes, and a stale column looks exactly like a
fresh one. Positions absent from the store are measured and appended, so **do not trigger the first
run of a new corpus while a time-controlled match is live** — it starts Stockfish at full tilt.

@author Michael Fleischhauer
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import isotonic_fit as iso                                          # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_EPD = REPO_ROOT / "tuning-data" / "mychess-selfplay-960.epd"
STOCKFISH_EVALS = REPO_ROOT / "test-results" / "king-safety-stockfish-evals.jsonl"
DEFAULT_OUTPUT = REPO_ROOT / "test-results" / "king-safety-screen.json"

JAVA = "/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home/bin/java"
CLASSPATH = "target/classes:target/test-classes:target/dependency/*"
PROBE = "org.michaelfl.mychess.KingSafetyFeatureProbe"
STOCKFISH = "/opt/homebrew/bin/stockfish"
RESULT_TAG = " c9 "

# Virtual-queen mobility runs 0..27; this many squares per index level, clamped at MAX_INDEX.
# A different width gives different numbers — it is a choice, and it is reported.
MOBILITY_PER_LEVEL = 3

#: name -> (index for white's king, index for black's king), each read as danger to that king.
CANDIDATES = {
    "Liniengefahr, 6 Stufen":
        lambda f: (min(f["split_white"], iso.MAX_INDEX), min(f["split_black"], iso.MAX_INDEX)),
    "Liniengefahr (offen/halboffen)":
        lambda f: (min(f["file_danger_white"], iso.MAX_INDEX),
                   min(f["file_danger_black"], iso.MAX_INDEX)),
    "virtuelle Damen-Mobilitaet":
        lambda f: (min(f["mobility_white"] // MOBILITY_PER_LEVEL, iso.MAX_INDEX),
                   min(f["mobility_black"] // MOBILITY_PER_LEVEL, iso.MAX_INDEX)),
    "Mobilitaet, Kontrollfeld":
        lambda f: (min(f["placebo_white"] // MOBILITY_PER_LEVEL, iso.MAX_INDEX),
                   min(f["placebo_black"] // MOBILITY_PER_LEVEL, iso.MAX_INDEX)),
    "Liniengefahr, Kontrollfenster":
        lambda f: (min(f["placebo_file_white"], iso.MAX_INDEX),
                   min(f["placebo_file_black"], iso.MAX_INDEX)),
    "Bauernsturm (dicht)":
        lambda f: (f["dense_storm_white"], f["dense_storm_black"]),
    "Bauernsturm (Erstfassung)":
        lambda f: (f["storm_white"], f["storm_black"]),
}

PROBE_FIELDS = ["storm_white", "storm_black", "mobility_white", "mobility_black",
                "placebo_white", "placebo_black", "dense_storm_white", "dense_storm_black",
                "file_danger_white", "file_danger_black",
                "split_white", "split_black",
                "placebo_file_white", "placebo_file_black"]


def read_fens(epd, limit):
    fens = []

    for line in epd.read_text(encoding="utf-8", errors="ignore").splitlines():
        if not line.strip():
            continue

        fens.append(line.split(RESULT_TAG)[0].strip() if RESULT_TAG in line else line.strip())

        if limit and len(fens) >= limit:
            break

    return fens


def run_probe(main_class, fens):
    proc = subprocess.run([JAVA, "-cp", CLASSPATH, main_class], cwd=REPO_ROOT,
                          input="\n".join(fens), capture_output=True, text=True, check=True)
    lines = proc.stdout.splitlines()

    if len(lines) != len(fens):
        raise SystemExit(f"{main_class} returned {len(lines)} lines for {len(fens)} positions — "
                         "no longer aligned, refusing to fit on that")

    return lines


def stockfish_evals(fens, report_every=10000):
    """Static NNUE evaluation from White's point of view, None where Stockfish declines."""
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
            print(f"  stockfish {index + 1:,}/{len(fens):,} ({time.time() - started:.0f}s)",
                  flush=True)

    proc.stdin.write("quit\n")
    proc.stdin.flush()
    proc.wait(timeout=10)

    return out


def stored_evals(path):
    """Stockfish evaluation per FEN from the committed store; the first line is metadata."""
    known = {}

    if not path.exists():
        return known

    with path.open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)

            if "_meta" in row:
                continue

            known[row["fen"]] = row["sf"]

    return known


def append_evals(path, fens, values):
    """Add newly measured positions to the store, preserving its metadata header."""
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("a", encoding="utf-8") as sink:
        for fen, value in zip(fens, values):
            if value is not None:
                sink.write(json.dumps({"fen": fen, "sf": value}, separators=(",", ":")) + "\n")
                sink.flush()


def load_rows(epd, limit, store=STOCKFISH_EVALS):
    """Per-position rows for the fit: stored Stockfish evaluations, everything else recomputed.

    Shared with `king-safety-orthogonalize.py` and `file-danger-vs-attack-units.py` so all three
    see the same corpus assembled the same way. The split is the point: what comes out of the store
    does not depend on this repository's code, and what does depend on it is never stored.
    """
    fens = read_fens(epd, limit)
    known = stored_evals(Path(store))
    print(f"{len(known):,} Stockfish-Bewertungen im Bestand", flush=True)
    missing = [fen for fen in fens if fen not in known]

    if missing:
        print(f"{len(missing):,} neu zu vermessen — startet Stockfish", flush=True)
        measured = stockfish_evals(missing)
        append_evals(Path(store), missing, measured)

        for fen, value in zip(missing, measured):
            if value is not None:
                known[fen] = value

    usable = [fen for fen in fens if fen in known]
    print(f"{len(usable):,} Stellungen, berechne Eval und Merkmale neu ...", flush=True)
    feats = run_probe(PROBE, usable)
    rows = []

    for fen, feat in zip(usable, feats):
        if feat == "skip":
            continue

        values = [int(v) for v in feat.split(";")]
        row = {"fen": fen, "sf": known[fen], "my": values[0], "phase": values[1]}
        row.update(dict(zip(PROBE_FIELDS, values[2:])))
        rows.append(row)

    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--epd", default=str(DEFAULT_EPD))
    parser.add_argument("--limit", type=int, default=0, help="positions to read, 0 for all")
    parser.add_argument("--replicates", type=int, default=iso.REPLICATES)
    parser.add_argument("--seed", type=int, default=20260901)
    parser.add_argument("--store", default=str(STOCKFISH_EVALS))
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    args = parser.parse_args()

    source = Path(args.epd)

    if not source.exists():
        raise SystemExit(f"corpus not found: {source}")

    print("HINWEIS: die Zielgroesse ist stockfish - myChess. Traegt dieses myChess den Kandidaten\n"
          "         bereits, misst der Fit die Restluecke NACH ihm, nicht die Luecke selbst.\n", flush=True)

    started = time.time()
    data = load_rows(source, args.limit, args.store)
    print(f"{len(data):,} verwertbare Stellungen ({time.time() - started:.0f}s)", flush=True)
    result = {"epd": str(source), "positions": len(data), "candidates": {}}

    for label, index_of in CANDIDATES.items():
        rows, total = [], 0.0
        occupancy = [0] * (iso.MAX_INDEX + 1)

        for row in data:
            white, black = index_of(row)
            target = max(-iso.CLIP_CP, min(iso.CLIP_CP, row["sf"] - row["my"]))
            rows.append((iso.features(black, white, row["phase"]), target))
            total += target * target
            occupancy[min(white, iso.MAX_INDEX)] += 1
            occupancy[min(black, iso.MAX_INDEX)] += 1

        samples = sum(occupancy)
        shares = [100 * occupancy[k + 1] / samples for k in range(iso.MAX_INDEX)]
        fitted = iso.fit(rows, total, args.replicates, args.seed)
        curve = iso.report(label, fitted, shares)
        result["candidates"][label] = {
            "curve": curve,
            "explained_percent": 100 * (fitted["no_term"] - fitted["residual"]) / fitted["no_term"],
            "p5": [iso.percentile(fitted["draws"][k], 5) for k in range(iso.MAX_INDEX)],
            "p95": [iso.percentile(fitted["draws"][k], 95) for k in range(iso.MAX_INDEX)],
            "share_percent": shares}

    print(f"\n\n{'Kandidat':<30}{'Spitze cp':>11}{'p5 dort':>10}{'erklaert':>11}")

    for label, entry in result["candidates"].items():
        print(f"{label:<30}{entry['curve'][-1]:>11}{entry['p5'][-1]:>10.1f}"
              f"{entry['explained_percent']:>10.3f}%")

    Path(args.output).write_text(json.dumps(result, indent=1), encoding="utf-8")
    print(f"\n-> {args.output}")
    print(f"Mobilitaets-Bucketing: {MOBILITY_PER_LEVEL} Felder je Stufe, geklemmt bei "
          f"{iso.MAX_INDEX}.")
    print("Ein flaches Ergebnis ist ein Stoppsignal; ein starkes ist keine Zusage. Und ein "
          "Koeffizient ohne seinen Besetzungsanteil sagt nichts.")


if __name__ == "__main__":
    main()
