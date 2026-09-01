#!/usr/bin/env python3
"""Do file danger and attack units measure the same thing, or different things?

**The decision this feeds.** If file danger is built, it is built on master, where attack units do
not exist -- so the fitted table fills the whole gap and nothing double-counts. But the question of
whether the two terms *could* coexist is separate and worth knowing before either is shipped:
overlapping terms stacked on top of each other count the shared part twice, and virtual queen
mobility already turned out to be almost entirely inside file danger.

**Why this tool is branch-only.** It reads `KingAttackUnits`, which reads
`WeightingFunction.ATTACK_UNIT_*`, which exist only on branch `attack-units`. That is the same tie
that keeps `king-attack-vs-stockfish.py` off master. The mathematics lives in `isotonic_fit.py`
and the file-danger side in `king-safety-screen.py`, both of which run anywhere; only this
combination is pinned to the branch. If attack units are shelved, this tool goes with them and the
recorded result in `test-results/king-safety-feature-screen.log` is what survives.

**This tool cannot reproduce its own recorded result, and that is structural.** It needs two
evaluations at once: the attack-unit feature, which only exists on this branch, and a target of
`stockfish - myChess` measured against **master's** evaluation, which a branch checkout cannot
produce. The recorded figures in `test-results/king-safety-feature-screen.log` (attack units
1.301 %, file danger 2.238 %, and the two orthogonalized directions) were computed while the
myChess side came from a stored master pass. Since `king-safety-screen.load_rows` now recomputes
that side from the current checkout -- deliberately, so a stale column cannot masquerade as a fresh
one -- running this on the branch measures the residual *after* the attack-unit term and will
report roughly zero for it. The log entry is the surviving record; treat a rerun as a different
question rather than a contradiction.

**The production gate is applied.** `WeightingFunction.calcKingAttackPenalty` returns zero below
two distinct attackers, and that suppresses a large share of the term's mass. Fitting ungated
measures a term the engine does not have -- an error already made once in this series, and the
refit that followed changed the curve substantially.

**Depends on tooling that lives on master.** `isotonic_fit.py`, `king-safety-screen.py` and
`king-safety-orthogonalize.py` were committed to `master`, because they carry no tie to this
branch. This file is the only one of the group that does, so it is committed here instead. Reviving
the branch therefore means merging master first, or the imports below fail.

Usage::

    ../lichess-bot/venv/bin/python tools/file-danger-vs-attack-units.py

@author Michael Fleischhauer
"""

import importlib.util
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import isotonic_fit as iso                                          # noqa: E402

REPO_ROOT = Path(__file__).resolve().parent.parent
JAVA = "/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home/bin/java"
CLASSPATH = "target/classes:target/test-classes:target/dependency/*"
ATTACK_PROBE = "org.michaelfl.mychess.KingAttackProbe"
REPLICATES = 60

#: WeightingFunction.calcKingAttackPenalty scores zero below this many distinct attackers.
MIN_ATTACKERS = 2

FILE_DANGER = "Liniengefahr"
ATTACK_UNITS = "Attack-Units (getort)"


def load_screen():
    """The screen module, imported despite the hyphens in its file name."""
    spec = importlib.util.spec_from_file_location(
        "king_safety_screen", Path(__file__).resolve().parent / "king-safety-screen.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    return module


def load_orthogonalize():
    """The orthogonalization module, imported despite the hyphens in its file name."""
    spec = importlib.util.spec_from_file_location(
        "king_safety_orthogonalize", Path(__file__).resolve().parent / "king-safety-orthogonalize.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    return module


def attack_indices(rows):
    """Gated attack units per side, from the branch's own probe."""
    proc = subprocess.run([JAVA, "-cp", CLASSPATH, ATTACK_PROBE], cwd=REPO_ROOT,
                          input="\n".join(row["fen"] for row in rows),
                          capture_output=True, text=True, check=True)
    lines = proc.stdout.splitlines()

    if len(lines) != len(rows):
        raise SystemExit(f"probe returned {len(lines)} lines for {len(rows)} positions")

    out = []

    for line in lines:
        if line == "skip":
            out.append(None)
            continue

        # The probe reports by ATTACKER. KingAttackUnits.of(board, WHITE) is what white's pieces
        # bear on black's king, so it is danger to BLACK. Everything downstream is indexed by the
        # endangered king, so the two swap here. Getting this backwards does not raise: an inverted
        # feature under a monotone non-negative constraint fits to a flat zero curve and 0.000 %
        # explained, which reads exactly like "this feature carries nothing".
        parts = line.split(";")
        against_black, attackers_on_black = int(parts[2]), int(parts[3])
        against_white, attackers_on_white = int(parts[4]), int(parts[5])
        out.append((against_white if attackers_on_white >= MIN_ATTACKERS else 0,
                    against_black if attackers_on_black >= MIN_ATTACKERS else 0))

    return out


def main():
    screen = load_screen()
    rows = screen.load_rows(Path(screen.DEFAULT_EPD), 0)
    print(f"{len(rows):,} Stellungen, hole Attack-Units vom Branch-Probe ...", flush=True)
    gated = attack_indices(rows)
    usable = [(row, pair) for row, pair in zip(rows, gated) if pair is not None]
    print(f"{len(usable):,} davon verwertbar\n", flush=True)

    for row, pair in usable:
        row["attack_white"], row["attack_black"] = pair

    rows = [row for row, _ in usable]
    base = [max(-iso.CLIP_CP, min(iso.CLIP_CP, row["sf"] - row["my"])) for row in rows]
    candidates = {
        FILE_DANGER: lambda f: (min(f["file_danger_white"], iso.MAX_INDEX),
                                min(f["file_danger_black"], iso.MAX_INDEX)),
        ATTACK_UNITS: lambda f: (min(f["attack_white"], iso.MAX_INDEX),
                                 min(f["attack_black"], iso.MAX_INDEX))}
    ortho = load_orthogonalize()

    for first, second in ((ATTACK_UNITS, FILE_DANGER), (FILE_DANGER, ATTACK_UNITS)):
        ortho.sequence(first, second, candidates, rows, base, REPLICATES, 20260901)

    print("Faellt die Zweitzahl einer Richtung weit unter den Alleinwert desselben Merkmals,\n"
          "steckte es groesstenteils schon im anderen — dann duerfen die beiden Terme nicht\n"
          "addiert werden, egal auf welchem Branch.")


if __name__ == "__main__":
    main()
