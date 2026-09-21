#!/usr/bin/env bash
# Run the engine's bench from the command line, against whatever is in target/.
#
# There is no CLI subcommand for this: MyChessMain reads exactly one argument, "uci", and
# everything else is a REPL command. So the bench is driven by piping it in, which is what
# the gauntlet runners have been doing inline. This puts it in one place and adds the parts
# that a measurement needs and an inline pipe does not.
#
# WHAT THE TWO NUMBERS MEAN, because they answer different questions and get confused.
#
#   Nodes searched  the SIGNATURE. Deterministic, machine-independent, and the only thing
#                   here that can prove something: two builds with the same signature search
#                   an identical tree and therefore play identically. No statistic can
#                   establish that. It says nothing whatever about cost.
#   NPS / time      the COST, and it is machine-dependent, load-dependent and noisy. One
#                   wall clock proves nothing. That is why --repeat exists.
#
# An unchanged signature is the entry ticket for a change, never the verdict - the four
# changes that reached the mainline in one evening on that reasoning are why CLAUDE.md says
# so. Pair it with a timing run over several repeats, and read the mean together with the
# spread: the spread says whether the machine was quiet enough for the mean to mean anything.
#
# DO NOT RUN THIS WHILE A MATCH IS PLAYING. It takes every core it can get, so it will both
# corrupt the match's time control and be corrupted by it. The script refuses if it finds a
# cutechess running.
#
# Usage:
#   tools/bench.sh [depth]                 bench at that depth, default 8
#   tools/bench.sh --v2 [depth]            benchv2, the half-uncastled castling-mix suite
#   tools/bench.sh --repeat 3 [depth]      run it N times, report each and the best
#   tools/bench.sh --jar <path> [depth]    a specific jar instead of target/
#   tools/bench.sh --classes [depth]       target/classes, skipping the jar entirely
#
# Examples:
#   tools/bench.sh 8
#   tools/bench.sh --v2 --repeat 3 8
#   tools/bench.sh --jar versions/4.7.1/my-chess-4.7.1.jar 9

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

JAVA_HOME_DIR="/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home"

COMMAND="bench"
DEPTH=8
REPEAT=1
JAR=""
USE_CLASSES=0

while [ $# -gt 0 ]; do
    case "$1" in
        --v2)     COMMAND="benchv2" ;;
        --repeat) shift; REPEAT="${1:-1}" ;;
        --jar)    shift; JAR="${1:-}" ;;
        --classes) USE_CLASSES=1 ;;
        -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
        -*)       echo "ERROR: unknown option: $1" >&2; exit 2 ;;
        *)        DEPTH="$1" ;;
    esac
    shift
done

case "$DEPTH" in
    ''|*[!0-9]*) echo "ERROR: depth must be a number, got '$DEPTH'" >&2; exit 2 ;;
esac

if pgrep -x cutechess-cli > /dev/null 2>&1; then
    echo "ERROR: a cutechess-cli is running. A bench would take its cores, wrecking both" >&2
    echo "       that match's time control and this measurement. Stop the match first." >&2
    exit 1
fi

# --- what to run against ---------------------------------------------------
# The jar is preferred because it is what gets shipped and measured elsewhere. target/classes
# is the fallback so this works straight after `mvn compile`, without a packaging round.
if [ -n "$JAR" ]; then
    [ -f "$JAR" ] || { echo "ERROR: no such jar: $JAR" >&2; exit 1; }
    JAR_DIR="$(cd "$(dirname "$JAR")" && pwd)"

    if [ -d "$JAR_DIR/lib" ]; then
        CLASSPATH="$JAR:$JAR_DIR/lib/*"
    else
        CLASSPATH="$JAR:target/dependency/*"
    fi

    SOURCE="$JAR"
else
    JARS=$(ls target/my-chess-*.jar 2>/dev/null | wc -l | tr -d ' ')
    [ "$USE_CLASSES" -eq 1 ] && JARS=0

    if [ "$JARS" -gt 1 ]; then
        echo "ERROR: more than one jar in target/ - which one is current?" >&2
        ls target/my-chess-*.jar >&2
        echo "       Run 'mvn clean package', or name one with --jar." >&2
        exit 1
    elif [ "$JARS" -eq 1 ]; then
        SOURCE=$(ls target/my-chess-*.jar)
        CLASSPATH="$SOURCE:target/dependency/*"

        # A jar older than the sources measures code that is no longer there. This has
        # already produced one "verified" result against a build predating the change it
        # was verifying, so it aborts rather than warns.
        NEWER=$(find src/main/java -name '*.java' -newer "$SOURCE" -print -quit 2>/dev/null)

        if [ -n "$NEWER" ]; then
            echo "ERROR: $SOURCE is older than the sources - $NEWER has changed since." >&2
            echo "       Measuring it would measure code that is no longer there." >&2
            echo "       Run 'mvn clean package', or pass --classes to use target/classes." >&2
            exit 1
        fi
    elif [ -d target/classes ]; then
        SOURCE="target/classes (no jar - run 'mvn package' for one)"
        CLASSPATH="target/classes:target/dependency/*"
    else
        echo "ERROR: nothing to run - no jar and no target/classes. Run 'mvn package'." >&2
        exit 1
    fi
fi

echo "$COMMAND depth $DEPTH, $REPEAT run(s)"
echo "  against: $SOURCE"
echo

BEST_NPS=0
WORST_NPS=0
SUM_NODES=0
SUM_MS=0
FIRST_NODES=""
MISMATCH=0
run=1

while [ "$run" -le "$REPEAT" ]; do
    OUT=$(printf '%s %d\nquit\n' "$COMMAND" "$DEPTH" \
        | JAVA_HOME="$JAVA_HOME_DIR" "$JAVA_HOME_DIR/bin/java" \
            -Xms256m -Xmx256m -XX:+AlwaysPreTouch -XX:+UseSerialGC \
            -cp "$CLASSPATH" org.michaelfl.mychess.MyChessMain 2>/dev/null)

    NODES=$(printf '%s' "$OUT" | grep 'Nodes searched' | tr -dc '0-9')
    NPS=$(printf '%s' "$OUT" | grep '^NPS' | tr -dc '0-9')
    MS=$(printf '%s' "$OUT" | grep 'Total time' | tr -dc '0-9')

    if [ -z "$NODES" ]; then
        echo "ERROR: no bench output - the run failed. Raw tail:" >&2
        printf '%s\n' "$OUT" | tail -5 >&2
        exit 1
    fi

    printf '  run %d/%d   nodes %'"'"'d   time %'"'"'d ms   NPS %'"'"'d\n' \
        "$run" "$REPEAT" "$NODES" "$MS" "$NPS"

    if [ -z "$FIRST_NODES" ]; then
        FIRST_NODES="$NODES"
    elif [ "$NODES" != "$FIRST_NODES" ]; then
        MISMATCH=1
    fi

    SUM_NODES=$((SUM_NODES + NODES))
    SUM_MS=$((SUM_MS + MS))
    [ "$NPS" -gt "$BEST_NPS" ] && BEST_NPS="$NPS"
    { [ "$WORST_NPS" -eq 0 ] || [ "$NPS" -lt "$WORST_NPS" ]; } && WORST_NPS="$NPS"
    run=$((run + 1))
done

echo
# The signature stays unformatted: it is meant to be copied and compared against
# bench-history.md, and separators get in the way of that. NPS is meant to be read.
printf 'signature : %s\n' "$FIRST_NODES"

# Total work over total time, NOT the arithmetic mean of the per-run NPS figures. NPS is a
# rate, and averaging rates arithmetically over-weights the fast runs; summing nodes and
# summing milliseconds is the harmonic mean, which is what "how fast was it overall" means.
# With identical node counts per run the two answers are close, but only one of them is the
# right question.
#
# The mean rather than the best of N: interference only ever makes a run slower, so the
# fastest run is the one least disturbed, and best-of-N used to be the implicit guard
# against a busy machine. The spread check below now does that explicitly and says so out
# loud, which leaves no reason to throw away every run but one.
MEAN_NPS=$((SUM_NODES * 1000 / SUM_MS))
printf 'mean NPS  : %'"'"'d\n' "$MEAN_NPS"

# SPREAD BETWEEN RUNS is a statement about the machine, not about the build. With the
# warm-up in place, consecutive runs on an idle machine land within a few tenths of a
# percent - 1,196,391 against 1,199,548 when this was calibrated, i.e. 0.26 %. Anything
# near a percent means something else was competing for cores, and then the timing is not
# comparable to another build's. The signature is unaffected either way, which is exactly
# why the two numbers are reported separately.
if [ "$REPEAT" -gt 1 ] && [ "$BEST_NPS" -gt 0 ]; then
    SPREAD_PERMILLE=$(( (BEST_NPS - WORST_NPS) * 1000 / BEST_NPS ))
    printf 'spread    : %d.%d %% between the fastest and slowest run\n' \
        $((SPREAD_PERMILLE / 10)) $((SPREAD_PERMILLE % 10))

    if [ "$SPREAD_PERMILLE" -gt 20 ]; then
        echo
        echo "WARNING: the runs differ by more than 2 %, so the machine was probably not idle." >&2
        echo "         Treat the timing as indicative only - it cannot be compared against" >&2
        echo "         another build measured under different load. The node count is still" >&2
        echo "         valid, since it does not depend on speed." >&2
    fi
fi

if [ "$MISMATCH" -eq 1 ]; then
    echo
    echo "WARNING: the node counts differ between runs. The bench is supposed to be" >&2
    echo "         deterministic, so this is a defect, not noise - a signature that moves" >&2
    echo "         cannot be used to prove two builds behave the same." >&2
    exit 1
fi
