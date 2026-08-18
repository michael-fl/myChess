#!/bin/sh
#
# run-sts.sh — Strategic Test Suite measurement.
#
# Runs the 1188-position STS through myChess at a fixed depth and prints the
# per-theme score, weakest theme first. The point of the run is the ranking: it
# names which evaluation component is weakest instead of leaving it to guesswork.
#
# Usage:
#   tools/run-sts.sh                 # full suite at the default depth (~30 min)
#   tools/run-sts.sh 8               # same, explicit depth
#   tools/run-sts.sh 6 king          # King Activity only, depth 6 (~30 s)
#   tools/run-sts.sh 8 11 5          # 5 positions of theme 11, for a shape check
#
# Arguments are passed straight through to StsRunner: [depth] [theme] [limit] [--out FILE].
#
# INTERRUPTIBLE RUNS. Anything past the default depth belongs in a file via --out, and the
# same command then resumes it:
#
#   tools/run-sts.sh 10 all 0 --out test-results/sts-4.4.2-d10.txt
#
# With --out the runner writes the file itself and flushes after every position, and on a
# second invocation it reads back what is already there and measures only the rest. Shell
# redirection cannot do either: stdout is block-buffered into a file, so ~80 measured
# positions are unwritten at any moment and a kill loses them with nothing to resume from.
# Depth 10 measured ~6 h on an M1 Pro, which is longer than a laptop stays awake.
#
# WHY THIS SCRIPT AND NOT `mvn exec:java`. exec:java runs the class inside the
# Maven JVM and then waits for every non-daemon thread to finish. `Game.shutdown()`
# releases engineWhite and engineBlack but not statusEngine, so a run that
# constructs 1188 Games leaves the plugin waiting indefinitely — the work
# completes, the command never returns. Measured 2026-08-18: five positions at
# depth 4 take 0.2 s directly and hung exec:java past seven minutes. A forked JVM
# has no such problem.
#
# DEPTH IS PART OF THE MEASUREMENT. The per-release series in docs/sts-history.md
# is only comparable at one depth, and that depth is StsRunner's default. A run at
# any other depth is a calibration or a spot check — do not add it to the series.
# For the same reason the number is not comparable to published STS ratings, which
# are measured at fixed time rather than fixed depth.
#
# Redirect stdout if the run should be archived; the runner writes no files:
#   tools/run-sts.sh 8 > test-results/sts-4.4.2-d8.txt 2>&1

set -eu

cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25)"
    export JAVA_HOME
fi

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

# test-compile builds the runner (it lives in the test sources); copy-dependencies
# fills target/dependency, which the classpath below needs for MapDB and friends.
mvn -q test-compile dependency:copy-dependencies -DoutputDirectory=target/dependency

exec "$JAVA" -cp "target/classes:target/test-classes:target/dependency/*" \
    org.michaelfl.mychess.StsRunner "$@"
