#!/usr/bin/env bash
# A cutechess match that survives being interrupted.
#
#   tools/run-resumable-match.sh NAME CANDIDATE BASELINE TOTAL_ROUNDS [TC]
#
# NAME                a short label; segments land in test-results/match-NAME-segNN.pgn
# CANDIDATE/BASELINE  directory names under versions/
# TOTAL_ROUNDS        rounds for the WHOLE match; one round is two games with colours swapped
# TC                  time control, default 40/60
#
# WHY. cutechess-cli has no --resume, and the acceptance criteria are bounds on the Elo
# interval: criterion 0 wants the lower bound at +3, which needs an interval near +/-9 and
# therefore thousands of games. A machine that must stay untouched for two days is not a
# realistic requirement, so the match has to be stoppable instead.
#
# HOW. Openings are taken in SEQUENTIAL order, and cutechess's start=N says which one to
# begin at. Each run writes its own segment; on the next call this script counts the games
# already in the segments, works out the next opening, and asks only for the rounds still
# missing. match-elo.py then adds the segments up.
#
# Sequential rather than random is what makes a resume exact: with random openings a second
# run would sample independently, which stays statistically valid but can repeat openings and
# cannot be reproduced. The book holds 12,092 openings, far more than any match needs.
#
# STOPPING SAFELY IS THE PART THAT MATTERS. Kill the process FIRST, then close the lid or
# start the other job. A machine suspended mid-game produces time forfeits, and those are
# written to the PGN as real results - a permanent distortion, not a lost game. Games still
# running when the process dies are simply absent and cost nothing.
#
# cutechess writes the PGN in ROUND order, so games finishing out of sequence wait for the
# older one. Measured on a live match the backlog sat at 4 to 6 games and did not grow; it is
# bounded by how many finish while the slowest in flight is still going, not by a timer.
set -eu
export LC_ALL=C

if [ $# -lt 4 ]; then
    sed -n '2,12p' "$0"
    exit 1
fi

NAME=$1
CANDIDATE=$2
BASELINE=$3
TOTAL_ROUNDS=$4
TC=${5:-40/60}

# Repo-relative, so the script travels with the checkout. The two external tools are not in
# the repository and are overridable, with this machine's locations as the defaults.
REPO=$(git rev-parse --show-toplevel)
CUTECHESS=${CUTECHESS:-/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli}
PYTHON=${PYTHON:-/Users/mf/_PRIVAT_/New-Stuff/lichess-bot/venv/bin/python}

cd "$REPO"

if [ ! -x "$CUTECHESS" ]; then
    echo "cutechess-cli not found at $CUTECHESS - set CUTECHESS=/path/to/cutechess-cli" >&2
    exit 1
fi

if pgrep -x cutechess-cli > /dev/null 2>&1; then
    echo "a cutechess-cli is already running - refusing to start a second one" >&2
    exit 1
fi

for version in "$CANDIDATE" "$BASELINE"; do
    if [ ! -x "versions/$version/mychess-uci.sh" ]; then
        echo "versions/$version/mychess-uci.sh missing or not executable" >&2
        exit 1
    fi
done

# Games already banked, across every segment written so far.
DONE=0

for pgn in test-results/match-"$NAME"-seg*.pgn; do
    [ -e "$pgn" ] || continue
    DONE=$((DONE + $(grep -c '^\[Event' "$pgn")))
done

ROUNDS_DONE=$((DONE / 2))
START=$((ROUNDS_DONE + 1))
REMAINING=$((TOTAL_ROUNDS - ROUNDS_DONE))

if [ "$REMAINING" -le 0 ]; then
    echo "match complete: $DONE games, $ROUNDS_DONE of $TOTAL_ROUNDS rounds"
    exec "$PYTHON" "$REPO/tools/match-elo.py" "$CANDIDATE" test-results/match-"$NAME"-seg*.pgn
fi

SEGMENT=$(printf '%02d' $(( $(ls test-results/match-"$NAME"-seg*.pgn 2>/dev/null | wc -l) + 1 )))
PGN=test-results/match-"$NAME"-seg$SEGMENT.pgn
LOG=test-results/match-"$NAME"-seg$SEGMENT.log

echo "segment $SEGMENT: $DONE games banked, resuming at opening $START, $REMAINING rounds to go"

if [ "$DONE" -gt 0 ]; then
    "$PYTHON" "$REPO/tools/match-elo.py" "$CANDIDATE" test-results/match-"$NAME"-seg*.pgn || true
fi

if [ $((DONE % 2)) -ne 0 ]; then
    echo "note: $DONE is odd, so one opening was played once only - a one-game colour imbalance"
fi

exec caffeinate -is "$CUTECHESS" \
    -engine name="$CANDIDATE" cmd=./versions/"$CANDIDATE"/mychess-uci.sh proto=uci \
    -engine name="$BASELINE"  cmd=./versions/"$BASELINE"/mychess-uci.sh  proto=uci \
    -each tc="$TC" \
    -rounds "$REMAINING" -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=sequential plies=8 start="$START" \
    -concurrency 4 -ratinginterval 10 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout "$PGN" \
    | tee "$LOG"
