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
# TOTAL_ROUNDS IS LOCKED ON THE FIRST RUN, and that is the point of the state file next to the
# segments. Resumability and optional stopping are the same mechanism seen from two sides: a
# match you can continue is a match you can extend "just another 2000 games" until the interval
# finally clears the threshold, or end early while it happens to sit on the right side. Either
# one biases the result in the direction you were hoping for, which is how three SPRT stops in
# this project's history came in high (+42.4 -> ~+15, +18.4 -> +14.8, +39.8 -> +32.6).
#
# Interrupting for an EXTERNAL reason costs nothing: the machine being needed is unrelated to
# the standing, so the estimate stays unbiased and only the interval is wider. Interrupting or
# extending BECAUSE of the standing is what must not happen. Seeing the running score is fine
# and unavoidable; acting on it is not.
#
# So a different TOTAL_ROUNDS on a later call is refused. Overriding needs FORCE_ROUNDS=1, which
# is deliberately awkward and leaves a line in the state file.
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

for version in "$CANDIDATE" "$BASELINE"; do
    if [ ! -x "versions/$version/mychess-uci.sh" ]; then
        echo "versions/$version/mychess-uci.sh missing or not executable" >&2
        exit 1
    fi
done

STATE=test-results/match-"$NAME".rounds

# The declared length, locked on the first run. A later call asking for a different number is
# refused rather than silently honoured.
if [ -f "$STATE" ]; then
    DECLARED=$(head -1 "$STATE")

    if [ "$DECLARED" != "$TOTAL_ROUNDS" ]; then
        if [ "${FORCE_ROUNDS:-0}" = "1" ]; then
            echo "WARNING: changing the declared length from $DECLARED to $TOTAL_ROUNDS rounds." >&2
            echo "  The estimate is only unbiased if this was decided WITHOUT reference to the" >&2
            echo "  running score. Recorded in $STATE." >&2
            printf '# changed from %s to %s on %s\n' "$DECLARED" "$TOTAL_ROUNDS" "$(date '+%Y-%m-%d %H:%M')" >> "$STATE"
            sed -i '' "1s/.*/$TOTAL_ROUNDS/" "$STATE"
        else
            echo "this match was declared as $DECLARED rounds, not $TOTAL_ROUNDS." >&2
            echo "Changing the length after seeing results biases the estimate - that is what" >&2
            echo "the lock is for. Re-run with $DECLARED, or FORCE_ROUNDS=1 if the new length" >&2
            echo "was decided without reference to the standing." >&2
            exit 1
        fi
    fi
else
    printf '%s\n' "$TOTAL_ROUNDS" > "$STATE"
    echo "declared length: $TOTAL_ROUNDS rounds ($((TOTAL_ROUNDS * 2)) games), locked in $STATE"
fi

if pgrep -x cutechess-cli > /dev/null 2>&1; then
    echo "a cutechess-cli is already running - refusing to start a second one" >&2
    exit 1
fi

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
