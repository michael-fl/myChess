#!/usr/bin/env bash
#
# Defect-count curve against castlingFactor, over the whole characterization collection.
#
# WHAT IT ANSWERS. How many of the pinned positions in BlunderTest and EngineTest change
# their verdict as the castling term is weakened. Those tests fix one move per position at
# a fixed depth, so a run is deterministic and a flipped verdict is a fact rather than an
# estimate - which is what makes this usable where an Elo match is not: the whole sweep
# costs under an hour and resolves differences no 3000-game match could.
#
# WHAT IT DOES NOT ANSWER. Strength. It is a defect count, not Elo, over a collection
# deliberately biased toward king safety and known weaknesses. And a flipped verdict is
# not automatically "worse": EngineTest.testPosition12 pins a move its own comment records
# as v4.6.0's most expensive regression, so a flip there is an improvement. Every flip has
# to be read against the test's own comment before it is called a defect.
#
# WHY IT EXISTS. castlingFactor was chosen on 2026-09-17 at 0.1875, and that value sits
# directly on a measured edge - BlunderTest's h3 case holds at 0.1875 and fails at
# 0.15625. Any evaluation change that shifts that position can push it over without
# anyone noticing the connection, so this sweep is the check that catches it.
#
# USAGE
#   tools/castling-factor-defect-sweep.sh [factor ...]
#
# Defaults to the four values the original measurement used. Runs in a throwaway detached
# worktree, so the caller's working tree is never modified; results land in
# test-results/factor-sweep/ and one file is written per value as it finishes, so an
# interrupted run keeps what it has and a re-run skips it.

set -uo pipefail

REPO=$(git rev-parse --show-toplevel) || exit 1
WT="${SWEEP_WORKTREE:-$REPO/../myChess-factor-sweep-wt}"
OUT="$REPO/test-results/factor-sweep"
EVAL="src/main/java/org/michaelfl/mychess/WeightingFunction.java"
TESTS="BlunderTest,EngineTest"

FACTORS=("$@")

if [ ${#FACTORS[@]} -eq 0 ]; then
    FACTORS=(0.25 0.1875 0.125 0.0)
fi

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null)
    export JAVA_HOME
fi

mkdir -p "$OUT"

if [ ! -d "$WT" ]; then
    echo "creating throwaway worktree at $WT"
    git -C "$REPO" worktree add --detach "$WT" HEAD || exit 1
fi

cd "$WT" || exit 1

for f in "${FACTORS[@]}"; do
    result="$OUT/factor-$f.txt"

    if [ -s "$result" ]; then
        echo "[$(date '+%H:%M:%S')] factor $f already done, skipping"
        continue
    fi

    sed -i '' "s/private static final float castlingFactor = [0-9.]*f;/private static final float castlingFactor = ${f}f;/" "$EVAL"

    # Read the value back out rather than trusting the substitution: a renamed constant
    # would leave the sweep silently measuring the same build four times.
    actual=$(grep -o 'castlingFactor = [0-9.]*f' "$EVAL")

    if [ "$actual" != "castlingFactor = ${f}f" ]; then
        echo "ERROR: wanted castlingFactor = ${f}f, source says '$actual' - aborting" >&2
        exit 1
    fi

    echo "[$(date '+%H:%M:%S')] factor $f - running $TESTS"

    log="$OUT/mvn-factor-$f.log"

    mvn -Dtest="$TESTS" -DfailIfNoSpecifiedTests=false test > "$log" 2>&1

    {
        echo "castlingFactor = $f"
        echo "worktree HEAD:  $(git rev-parse --short HEAD)"
        echo "finished:       $(date '+%Y-%m-%d %H:%M:%S')"
        echo
        grep -E '^\[(INFO|ERROR|WARNING)\] Tests run: [0-9]+, Fail' "$log" | tail -4
        echo
        echo "--- flipped methods ---"
        grep '^\[ERROR\] org\.michaelfl' "$log" | sed 's/ -- Time elapsed.*//'
        echo
        echo "--- assertion messages ---"
        grep -A1 '^\[ERROR\] org\.michaelfl' "$log" | grep 'AssertionFailedError'
    } > "$result"

    echo "[$(date '+%H:%M:%S')] factor $f done, $(grep -c '^\[ERROR\] org\.michaelfl' "$log") flipped"
done

echo "[$(date '+%H:%M:%S')] sweep complete - results in $OUT"
echo "remove the worktree with: git -C $REPO worktree remove $WT --force"
