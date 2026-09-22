#!/usr/bin/env bash
# Stop a running anchor gauntlet cleanly, so it can be resumed.
#
# WHY THIS EXISTS. The run is normally launched detached or from an agent session, so the
# person who has to stop it - before closing the lid, or to free the machine - has no
# terminal to press Ctrl-C in. run-anchor-gauntlet.sh records the cutechess pid; this
# reads it. Never pattern-match for the process instead: every text filter also matches
# the thing doing the filtering, which has misfired four times in this project.
#
# WHAT AN INTERRUPTION COSTS, measured rather than assumed. SIGINT makes cutechess stop
# starting new games and ABANDON the ones in flight - it does not let them finish. A
# ten-game probe at concurrency 4 wrote 25 games, 21 usable and exactly 4 carrying
# [Result "*"] / [Termination "unterminated"]. So one interruption costs CONCURRENCY games
# plus the remainder of the round in progress, whose opening is played again on resume.
# Both are filtered out of the round count and out of the combined PGN.
#
# ALWAYS run this before closing the lid. A suspended process wakes with the engine clocks
# run down and loses whatever was in flight on time, which is a worse outcome than this.
#
# Usage:
#   tools/stop-gauntlet.sh <label> [--timeout SECONDS]

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

LABEL="${1:-}"
TIMEOUT=300

if [ -z "$LABEL" ]; then
    echo "usage: tools/stop-gauntlet.sh <label> [--timeout SECONDS]" >&2
    exit 2
fi

shift

while [ $# -gt 0 ]; do
    case "$1" in
        --timeout) shift; TIMEOUT="${1:-300}" ;;
        *) echo "ERROR: unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

PIDFILE="test-results/gauntlet-$LABEL.pid"

if [ ! -f "$PIDFILE" ]; then
    echo "no pid file at $PIDFILE - nothing recorded as running for '$LABEL'." >&2
    exit 1
fi

PID=$(head -1 "$PIDFILE")

if ! kill -0 "$PID" 2>/dev/null; then
    echo "pid $PID is not alive; the run has already stopped. Removing the stale pid file."
    rm -f "$PIDFILE"
    exit 0
fi

# Guard against pid reuse: the recorded number must still BE a cutechess.
COMMAND=$(ps -o comm= -p "$PID" 2>/dev/null)

case "$COMMAND" in
    *cutechess*) ;;
    *)
        echo "ERROR: pid $PID is '$COMMAND', not a cutechess - refusing to signal it." >&2
        echo "       The pid was probably reused. Remove $PIDFILE by hand once you have checked." >&2
        exit 1
        ;;
esac

echo "sending SIGINT to cutechess pid $PID; games in flight will be abandoned."
kill -INT "$PID"

WAITED=0

while kill -0 "$PID" 2>/dev/null; do
    if [ "$WAITED" -ge "$TIMEOUT" ]; then
        echo "still alive after ${TIMEOUT}s. NOT escalating to SIGKILL - that would lose the" >&2
        echo "segment's buffered output. Wait longer, or decide by hand." >&2
        exit 1
    fi

    sleep 2
    WAITED=$((WAITED + 2))

    if [ $((WAITED % 20)) -eq 0 ]; then
        echo "  waiting for it to shut down... ${WAITED}s"
    fi
done

rm -f "$PIDFILE"

echo "stopped after ${WAITED}s. It is now safe to close the lid."

LAST_SEGMENT=$(ls test-results/gauntlet-"$LABEL"-seg*.pgn 2>/dev/null | tail -1)

if [ -n "$LAST_SEGMENT" ]; then
    echo "  segment: $LAST_SEGMENT"
    echo "  usable games in it:    $(grep -c '^\[Result "[01/]' "$LAST_SEGMENT" 2>/dev/null || echo 0)"
    echo "  abandoned by the stop: $(grep -c '^\[Result "\*"\]' "$LAST_SEGMENT" 2>/dev/null || echo 0)"
fi

echo
echo "to resume, re-run the same command that started it - it works the rest out itself."
