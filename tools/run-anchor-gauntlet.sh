#!/usr/bin/env bash
# Three-arm gauntlet against the five foreign anchor engines: base, placebo, candidate.
#
# The generalization of run-king-line-gauntlet.sh, which was written for one experiment
# and hard-coded it in about twenty places. Everything specific to a feature now arrives
# as an argument; what stays is the shape of the measurement.
#
# WHY THREE ARMS, ALWAYS. A candidate-versus-base match reports one number: the sum of
# what the feature gains and what computing it costs under the clock. Those pull in
# opposite directions, so a zero can mean "does nothing" or "helps as much as it costs",
# and the two call for opposite decisions. The placebo - the term computed and the result
# discarded - separates them:
#
#   placebo vs base        the COST of computing it, in Elo under the clock
#   candidate vs placebo   the EFFECT of applying it, with the cost held constant
#
# WHY ANCHORS RATHER THAN SELF-PLAY. In self-play the opponent exploits a weakness
# exactly as well as the candidate does, because it is the same engine. A defensive term
# shows least there. The target metric is standard-chess Elo against foreign opponents.
#
# THE PLACEBO'S ONE HAZARD. "Compute and discard" invites the JIT to delete the
# computation, after which the middle arm measures nothing. Consume the result
# observably - a Statistics counter, as the material-only counter does - rather than
# hiding it behind a tiny factor, which perturbs the evaluation you are trying to hold
# fixed. Pre-flight 3 fails the run when the placebo is not measurably slower than base.
#
# WHERE A PLACEBO CANNOT EXIST. A change that alters the search tree by construction
# (pruning, move ordering) has nothing to discard - the decision is the point. A pure
# speed change has a placebo identical to its base. For those, --no-placebo "<reason>"
# runs a two-arm gauntlet. The reason is mandatory and is printed, stored in the event
# name and echoed at the end, so a result that cannot separate cost from effect always
# says so on its face. Never reach for it to save a build: a two-arm run reports the sum
# of gain and cost, and a zero there has two opposite readings.
#
# Shape: `-tournament gauntlet -seeds 3` plays the three seeds against the anchors and
# nothing else - no anchor-vs-anchor games, whose ranking is already known, and no
# variant-vs-variant games, which would be self-play again. `-openings policy=round`
# reuses one opening per round, so all three arms meet each anchor from the same
# position: a paired comparison for free.
#
# Read POINTS, not Elo. All three arms face the same opponents equally often, so the raw
# point totals are a sufficient statistic and need no rating model. Ordo runs at the end
# for the absolute picture.
#
# Usage:
#   tools/run-anchor-gauntlet.sh --label <slug> \
#       --base <dir> --placebo <dir> --candidate <dir> \
#       [--rounds N] [--probe-fen FEN] [--preflight-only] [--wait-for-cores]
#
#   tools/run-anchor-gauntlet.sh --label <slug> \
#       --base <dir> --candidate <dir> --no-placebo "<why none is possible>"
#
# The three directories are under versions/ and each must hold exactly one
# my-chess-*.jar plus a mychess-uci.sh. Example:
#
#   tools/run-anchor-gauntlet.sh --label king-line \
#       --base versions/4.6.1-gauntlet-base \
#       --placebo versions/4.6.1-gauntlet-placebo \
#       --candidate versions/4.6.1-gauntlet-king-line-v2
#
# Outputs, all named after --label:
#   test-results/gauntlet-<label>.pgn             all games
#   test-results/gauntlet-<label>-stdout.log      cutechess log, standings every 10 games
#   test-results/ordo-gauntlet-<label>.txt        Ordo rating list

set -u

if [ -z "${GAUNTLET_CAFFEINATED:-}" ]; then
    exec caffeinate -is env GAUNTLET_CAFFEINATED=1 "$0" "$@"
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

. "$REPO_ROOT/tools/anchors.inc.sh"

CUTECHESS="/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli"
ORDO="/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo"
# The SHUFFLED book, and that is load-bearing now that the openings are taken in
# sequential order so the run can resume. The original 2moves_v2.pgn is ORDERED:
# b4 accounts for 17.6 % of its first 2100 openings against 3.4 % book-wide, so a
# sequential prefix of it is not a sample of the book. tools/shuffle-openings.py
# produced this one with a fixed seed; its first 2100 sit within 0.4 pp of the
# book-wide distribution.
OPENINGS="2moves_v2-shuffled.pgn"
RESULTS="test-results"
JAVA_HOME_DIR="/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home"

# 40/60, the control every other measurement in this project uses - self-play matches, the
# SPRTs, the Elo numbers in version-history.md.
#
# This was 40/120 until 2026-09-20, matching run-anchor-bracket.sh so the games stayed
# comparable to bracket-4.4.1.pgn. What that comparability buys is Ordo's ABSOLUTE placement,
# which rests on the anchors' ratings from that bracket. It is not what a three-arm gauntlet
# decides: the verdict is candidate-minus-base, a comparison between two of our own arms
# facing the same opponents equally often, and king-line-gauntlet.md says so itself - "read
# POINTS, not Elo ... a sufficient statistic ... no rating model on top". No rating model, no
# need for the anchors' absolute numbers.
#
# Two things are gained. The wall clock halves: at 40/120 this run measured 43 games/hour
# against the 87 the doc projects, so 4200 games take four days rather than two. And the cost
# arm stops understating - that same doc notes a computation cost "weighs more the less time
# there is", making a 40/120 figure a lower bound on the cost at 40/60. Measuring at 40/60
# prices the term where the rest of the evidence was gathered.
#
# What is given up: Ordo's absolute rating line at the end is no longer anchored by a bracket
# run at the same control. It stays informative and decides nothing.
TC="40/60"
CONCURRENCY=4
RATING_INTERVAL=10
ORDO_SIMULATIONS=1000

# Depth of the pre-flight signature check. Six is a couple of minutes per arm; the
# identical-tree property it verifies holds at any fixed depth.
PREFLIGHT_DEPTH=6

ROUNDS=140
LABEL=""
BASE_DIR=""
PLACEBO_DIR=""
CANDIDATE_DIR=""
PROBE_FEN=""
NO_PLACEBO_REASON=""
WAIT_FOR_CORES=0
PREFLIGHT_ONLY=0
FORCE_PREFLIGHT=0

usage() {
    sed -n '2,60p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-2}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --label)          shift; LABEL="${1:-}" ;;
        --base)           shift; BASE_DIR="${1:-}" ;;
        --placebo)        shift; PLACEBO_DIR="${1:-}" ;;
        --no-placebo)     shift; NO_PLACEBO_REASON="${1:-}" ;;
        --candidate)      shift; CANDIDATE_DIR="${1:-}" ;;
        --rounds)         shift; ROUNDS="${1:-}" ;;
        --probe-fen)      shift; PROBE_FEN="${1:-}" ;;
        --wait-for-cores) WAIT_FOR_CORES=1 ;;
        --preflight-only) PREFLIGHT_ONLY=1 ;;
        --force-preflight) FORCE_PREFLIGHT=1 ;;
        -h|--help)        usage 0 ;;
        *) echo "ERROR: unknown argument: $1" >&2; usage ;;
    esac
    shift
done

if [ -n "$PLACEBO_DIR" ] && [ -n "$NO_PLACEBO_REASON" ]; then
    echo "ERROR: --placebo and --no-placebo are mutually exclusive" >&2
    exit 2
fi

for required in LABEL BASE_DIR CANDIDATE_DIR; do
    eval "value=\$$required"

    if [ -z "$value" ]; then
        echo "ERROR: --$(echo "$required" | tr 'A-Z_' 'a-z-' | sed 's/-dir$//') is required" >&2
        exit 2
    fi
done

# The placebo is the default and skipping it has to be deliberate. An empty --no-placebo
# is refused as well: "because I did not build one" is not a reason, and the whole point
# of demanding one is that it gets written down next to the result.
if [ -z "$PLACEBO_DIR" ] && [ -z "$NO_PLACEBO_REASON" ]; then
    echo "ERROR: --placebo is required, or --no-placebo \"<reason>\" to run two arms." >&2
    echo "       Without the cost arm the run reports gain and cost summed, and a zero" >&2
    echo "       then has two opposite readings. Only a feature that cannot HAVE a" >&2
    echo "       placebo qualifies: one that changes the search tree by construction," >&2
    echo "       or a pure speed change whose placebo would equal its base." >&2
    exit 2
fi

# The jar name follows the pom version, which every branch bumps, so it cannot be
# derived from the directory name. Globbing and refusing on anything but exactly one
# match is what caught three identically-named jars during the king-line preparation.
jar_of() {
    directory="$1"
    set -- "$directory"/my-chess-*.jar

    # An unmatched glob stays literal here, so the -e test is what distinguishes
    # "no jar" from "one jar"; without it the count would report a phantom 1.
    if [ ! -e "$1" ]; then
        echo "ERROR: no my-chess-*.jar in $directory" >&2
        return 1
    fi

    if [ "$#" -ne 1 ]; then
        echo "ERROR: expected exactly one my-chess-*.jar in $directory, found $#" >&2
        return 1
    fi

    basename "$1"
}

BASE_JAR="$(jar_of "$BASE_DIR")" || exit 1
CANDIDATE_JAR="$(jar_of "$CANDIDATE_DIR")" || exit 1

ARMS="
base|$BASE_DIR|$BASE_JAR
"

if [ -n "$PLACEBO_DIR" ]; then
    PLACEBO_JAR="$(jar_of "$PLACEBO_DIR")" || exit 1
    ARMS="$ARMS
placebo|$PLACEBO_DIR|$PLACEBO_JAR"
fi

ARMS="$ARMS
$LABEL|$CANDIDATE_DIR|$CANDIDATE_JAR
"

ARM_COUNT=$(printf '%s\n' "$ARMS" | grep -c '|')
PAIRINGS=$((ARM_COUNT * ANCHOR_COUNT))
GAMES=$((PAIRINGS * 2 * ROUNDS))

GAMES_PER_ROUND=$((PAIRINGS * 2))

# --- one stable file to follow the run in ----------------------------------
# Everything from here on - banners, pre-flight, cutechess's own output - is teed into a
# single log whose name does not change between segments, so `tail -f` on it survives an
# interruption and a resume. The per-segment logs stay as the record of each segment; this
# one is for watching. It APPENDS, so a resumed run continues the same file.
#
# Without this the log path depended on how the run was launched, which is no use to
# somebody who did not launch it - and that is the normal case here, since the run is
# started detached or from an agent session.
mkdir -p "$RESULTS"
LIVE="$RESULTS/gauntlet-$LABEL-live.log"
exec > >(tee -a "$LIVE") 2>&1

echo
echo "[$(date '+%F %T')] follow this run with:"
echo "    tail -f $REPO_ROOT/$LIVE"

# --- resume ----------------------------------------------------------------
# cutechess has no --resume. What it does have is `order=sequential start=N`, and with
# `policy=round` one opening serves a whole round, so a round is the natural unit of
# progress: complete rounds already in the segments say which opening comes next.
#
# A ROUND, NOT A GAME, is the unit here - unlike the two-engine runner, where it is a
# pair. All arms must meet each anchor from the same opening or the paired comparison
# that buys +/-17 instead of +/-23 falls apart, so a round interrupted halfway is not
# banked: its opening is played again in the next segment. That costs at most
# GAMES_PER_ROUND - 1 duplicated games per interruption, which the run reports rather
# than hides, because those games are real and sit in the PGN.
#
# THE LENGTH IS LOCKED ON THE FIRST RUN. Resumability is also the ability to extend, and
# extending because the standing looks promising is exactly the optional stopping that a
# pre-registered game count exists to prevent. FORCE_ROUNDS=1 overrides it, says so, and
# leaves a dated line in the state file.
STATE="$RESULTS/gauntlet-$LABEL.rounds"

if [ -f "$STATE" ]; then
    DECLARED=$(head -1 "$STATE")

    if [ "$DECLARED" != "$ROUNDS" ]; then
        if [ "${FORCE_ROUNDS:-0}" = "1" ]; then
            echo "WARNING: changing the declared length from $DECLARED to $ROUNDS rounds." >&2
            printf '# changed from %s to %s on %s\n' \
                "$DECLARED" "$ROUNDS" "$(date '+%Y-%m-%d %H:%M')" >> "$STATE"
            sed -i '' "1s/.*/$ROUNDS/" "$STATE"
        else
            echo "ERROR: this gauntlet was declared as $DECLARED rounds, not $ROUNDS." >&2
            echo "       Re-run with the declared length, or FORCE_ROUNDS=1 to override." >&2
            exit 2
        fi
    fi
else
    printf '%s\n' "$ROUNDS" > "$STATE"
fi

# SIGINT ABANDONS the games in flight rather than finishing them - measured, not assumed:
# a ten-game probe interrupted at concurrency 4 wrote 25 games, 21 of them usable and
# exactly 4 carrying [Result "*"] / [Termination "unterminated"]. So each interruption
# costs CONCURRENCY games, and they land in the PGN as junk that must not count as
# progress and must not reach Ordo. Both filters below drop them.
ROUNDS_DONE=0
PARTIAL_GAMES=0
SPOILED=0

count_segment() {
    awk -v target="$1" '
        /^\[Round /  { round = $0; sub(/^\[Round "/, "", round); sub(/"\]$/, "", round) }
        /^\[Result / { if ($0 ~ /"\*"/) { spoiled++ } else { played[round]++ } }
        END {
            for (r in played) {
                if (played[r] == target) { complete++ } else { partial += played[r] }
            }
            print complete + 0, partial + 0, spoiled + 0
        }
    ' "$2"
}

for seg in "$RESULTS"/gauntlet-"$LABEL"-seg*.pgn; do
    [ -e "$seg" ] || continue
    set -- $(count_segment "$GAMES_PER_ROUND" "$seg")
    ROUNDS_DONE=$((ROUNDS_DONE + $1))
    PARTIAL_GAMES=$((PARTIAL_GAMES + $2))
    SPOILED=$((SPOILED + $3))
done

START=$((ROUNDS_DONE + 1))
REMAINING=$((ROUNDS - ROUNDS_DONE))
SEGMENT=$(printf '%02d' $(( $(ls "$RESULTS"/gauntlet-"$LABEL"-seg*.pgn 2>/dev/null | wc -l) + 1 )))

echo "=========================================================================="
echo "[$(date '+%F %T')] anchor gauntlet: $LABEL"
echo "TC=$TC  rounds=$ROUNDS  ${ARM_COUNT} arms x ${ANCHOR_COUNT} anchors"
echo "  = $PAIRINGS pairings -> $GAMES games   concurrency=$CONCURRENCY"
echo "  $GAMES_PER_ROUND games per round, declared length locked in $STATE"

if [ "$ROUNDS_DONE" -gt 0 ] || [ "$PARTIAL_GAMES" -gt 0 ]; then
    echo "  RESUMING: $ROUNDS_DONE complete rounds banked, next opening $START,"
    echo "            $REMAINING rounds to go, writing segment $SEGMENT"
    [ "$PARTIAL_GAMES" -gt 0 ] && echo "            $PARTIAL_GAMES game(s) sit in interrupted rounds and will be replayed"
    [ "$SPOILED" -gt 0 ] && echo "            $SPOILED game(s) were abandoned mid-play and are excluded everywhere"
fi

if [ -z "$PLACEBO_DIR" ]; then
    echo
    echo "  NO COST ARM — gain and cost will be reported summed."
    echo "  reason given: $NO_PLACEBO_REASON"
fi
echo "=========================================================================="

# --- prerequisite checks ----------------------------------------------------
fail=0
check() { [ -e "$1" ] || { echo "MISSING: $1" >&2; fail=1; }; }

check "$CUTECHESS"
check "$ORDO"
check "$OPENINGS"

printf '%s\n' "$ARMS" > /tmp/gauntlet-arms.txt
while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    check "$dir/$jar"
    check "$dir/mychess-uci.sh"
    [ -e "$dir/BUILT-FROM.txt" ] || echo "WARNING: no BUILT-FROM.txt in $dir — provenance unverifiable" >&2
done < /tmp/gauntlet-arms.txt

printf '%s\n' "$ANCHORS" > /tmp/gauntlet-anchors.txt
while IFS='|' read -r name wrapper proto rating opts; do
    [ -z "$name" ] && continue
    check "$wrapper"
done < /tmp/gauntlet-anchors.txt

[ "$fail" -eq 0 ] || { echo "ERROR: prerequisites missing, aborting." >&2; exit 1; }

# The pre-flight verifies the BUILDS, which cannot change between segments, and its third
# step is a timing comparison that can fail on noise. A spurious failure must never block a
# resume, so it runs on the first segment only; --force-preflight asks for it anyway.
if [ "$ROUNDS_DONE" -gt 0 ] && [ "$FORCE_PREFLIGHT" -eq 0 ] && [ "$PREFLIGHT_ONLY" -eq 0 ]; then
    SKIP_PREFLIGHT=1
    echo
    echo "[$(date '+%F %T')] resuming - skipping the pre-flight (same builds; --force-preflight to run it)"
else
    SKIP_PREFLIGHT=0
fi

# --- pre-flight 1: provenance ----------------------------------------------
echo
if [ "$SKIP_PREFLIGHT" -eq 0 ]; then

echo "[$(date '+%F %T')] --- pre-flight 1/3: provenance -----------------------"
while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    echo "--- $name"
    [ -e "$dir/BUILT-FROM.txt" ] && sed 's/^/    /' "$dir/BUILT-FROM.txt"
    printf '    live jar: '
    shasum -a 256 "$dir/$jar" | awk '{print $1}'
done < /tmp/gauntlet-arms.txt

# --- pre-flight 2: what each arm makes of one position ---------------------
# Informative, never asserting: what "correct" looks like depends on the feature, so
# the expectation lives with the person reading it, not in this script.
if [ -n "$PROBE_FEN" ]; then
    echo
    echo "[$(date '+%F %T')] --- pre-flight 2/3: behavior on the probe position ---"
    echo "    $PROBE_FEN"
    printf 'fen %s\nw\nquit\n' "$PROBE_FEN" > /tmp/gauntlet-probe.txt

    while IFS='|' read -r name dir jar; do
        [ -z "$name" ] && continue
        printf '    %-16s ' "$name"
        ( cd "$dir" && JAVA_HOME="$JAVA_HOME_DIR" "$JAVA_HOME_DIR/bin/java" \
            -cp "$jar:lib/*" org.michaelfl.mychess.MyChessMain < /tmp/gauntlet-probe.txt 2>/dev/null ) \
            | grep -E "^weight:|Penalty|Bonus" | tr '\n' '|'
        echo
    done < /tmp/gauntlet-arms.txt

    echo "    Check by eye: base and placebo must show the SAME total; the candidate a"
    echo "    different one. A candidate equal to base means the feature did not fire here."
else
    echo
    echo "[$(date '+%F %T')] --- pre-flight 2/3: skipped, no --probe-fen given ---"
fi

# --- pre-flight 3: the two signature properties the design rests on --------
echo
echo "[$(date '+%F %T')] --- pre-flight 3/3: signature and cost, depth $PREFLIGHT_DEPTH ---"

bench_arm() {
    printf 'bench %d\nquit\n' "$PREFLIGHT_DEPTH" | ( cd "$1" && \
        JAVA_HOME="$JAVA_HOME_DIR" "$JAVA_HOME_DIR/bin/java" -cp "$2:lib/*" \
        org.michaelfl.mychess.MyChessMain 2>/dev/null )
}

nodes_of() { printf '%s' "$1" | grep 'Nodes searched' | tr -dc '0-9'; }
nps_of()   { printf '%s' "$1" | grep '^NPS' | tr -dc '0-9'; }

BASE_OUT="$(bench_arm "$BASE_DIR" "$BASE_JAR")"
CAND_OUT="$(bench_arm "$CANDIDATE_DIR" "$CANDIDATE_JAR")"

base_nodes="$(nodes_of "$BASE_OUT")"; base_nps="$(nps_of "$BASE_OUT")"
cand_nodes="$(nodes_of "$CAND_OUT")"; cand_nps="$(nps_of "$CAND_OUT")"

echo "    base      nodes=$base_nodes  nps=$base_nps"

if [ -n "$PLACEBO_DIR" ]; then
    PLAC_OUT="$(bench_arm "$PLACEBO_DIR" "$PLACEBO_JAR")"
    plac_nodes="$(nodes_of "$PLAC_OUT")"; plac_nps="$(nps_of "$PLAC_OUT")"
    echo "    placebo   nodes=$plac_nodes  nps=$plac_nps"
fi

echo "    candidate nodes=$cand_nodes  nps=$cand_nps"

if [ -n "$PLACEBO_DIR" ]; then

    if [ "$base_nodes" != "$plac_nodes" ]; then
        echo "ABORT: base and placebo have different signatures, so the placebo's evaluation is" >&2
        echo "       not base's and it is not a pure cost arm. Its factor is not exactly zero," >&2
        echo "       or it changes something besides the discarded term." >&2
        exit 1
    fi

    if [ "$plac_nps" -ge "$base_nps" ]; then
        echo "ABORT: the placebo is not slower than base, so the computation was optimized away" >&2
        echo "       and the middle arm measures nothing. Consume the result observably (a" >&2
        echo "       Statistics counter, as the material-only counter does) rather than" >&2
        echo "       perturbing the factor." >&2
        exit 1
    fi

fi

# The mirror image of the check above, and the one a two-arm run never makes: if the
# candidate searches the same tree as base, the feature changed no decision anywhere in
# 151 million nodes and the two days would measure noise against noise.
if [ "$cand_nodes" = "$base_nodes" ]; then
    echo "ABORT: the candidate's signature equals base's, so the feature changed no search" >&2
    echo "       decision at depth $PREFLIGHT_DEPTH. Either it is inert, or it is not wired in." >&2
    exit 1
fi

if [ -n "$PLACEBO_DIR" ]; then
    echo "    OK: base == placebo, placebo slower by $(( (base_nps - plac_nps) * 1000 / base_nps ))/1000,"
    echo "        candidate's tree differs from base's."
else
    echo "    OK: candidate's tree differs from base's. No cost arm, so the match cannot say"
    echo "        whether a negative result is the feature or its price."
fi

fi   # end of the pre-flight block

if [ "$PREFLIGHT_ONLY" -eq 1 ]; then
    echo
    echo "[$(date '+%F %T')] --preflight-only: stopping here."
    exit 0
fi

# --- optionally wait until the cores are free -------------------------------
if [ "$WAIT_FOR_CORES" -eq 1 ]; then
    echo
    echo "[$(date '+%F %T')] --wait-for-cores: waiting for other cutechess-cli runs..."

    while pgrep -f cutechess-cli >/dev/null 2>&1; do
        sleep 300
    done

    echo "[$(date '+%F %T')] cores free, starting."
fi

# --- build the engine arguments --------------------------------------------
set -- \
    -tournament gauntlet -seeds "$ARM_COUNT" \
    -event "$LABEL ${ARM_COUNT}-arm anchor gauntlet"

while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    set -- "$@" -engine name="mychess-$name" cmd="./$dir/mychess-uci.sh" proto=uci
done < /tmp/gauntlet-arms.txt

while IFS='|' read -r name wrapper proto rating opts; do
    [ -z "$name" ] && continue
    # $opts is deliberately unquoted: it carries zero or one cutechess engine option.
    set -- "$@" -engine name="$name" cmd="$wrapper" proto="$proto" $opts
done < /tmp/gauntlet-anchors.txt

PGN="$RESULTS/gauntlet-$LABEL-seg$SEGMENT.pgn"
LOG="$RESULTS/gauntlet-$LABEL-seg$SEGMENT.log"
COMBINED="$RESULTS/gauntlet-$LABEL.pgn"
PIDFILE="$RESULTS/gauntlet-$LABEL.pid"

if [ "$REMAINING" -le 0 ]; then
    echo
    echo "[$(date '+%F %T')] gauntlet complete: $ROUNDS_DONE of $ROUNDS rounds already played."
else
    echo
    echo "[$(date '+%F %T')] starting segment $SEGMENT -> $PGN"
    echo "[$(date '+%F %T')] to interrupt: Ctrl-C once, or kill -INT on the cutechess pid."
    echo "[$(date '+%F %T')] ALWAYS stop it before closing the lid - a suspended process"
    echo "                   wakes with the clocks run down and loses its games on time."

    # cutechess runs in the BACKGROUND with its pid in a file, rather than at the head of a
    # `| tee` pipeline. Two reasons, and the second is the operational one: in a pipeline $!
    # is tee's pid, not cutechess's; and whoever has to stop this run is usually not the
    # person who started it - the run is launched from an agent session or detached, so there
    # is no terminal to press Ctrl-C in. tools/stop-gauntlet.sh reads the file.
    "$CUTECHESS" "$@" \
        -each tc="$TC" \
        -rounds "$REMAINING" -games 2 -repeat \
        -openings file="$OPENINGS" format=pgn order=sequential start="$START" plies=4 policy=round \
        -concurrency "$CONCURRENCY" -ratinginterval "$RATING_INTERVAL" \
        -recover \
        -draw movenumber=40 movecount=8 score=40 \
        -resign movecount=4 score=600 \
        -pgnout "$PGN" \
        > >(tee "$LOG") 2>&1 &

    CUTECHESS_PID=$!
    printf '%s\n' "$CUTECHESS_PID" > "$PIDFILE"

    echo "[$(date '+%F %T')] cutechess pid $CUTECHESS_PID, recorded in $PIDFILE"
    echo "[$(date '+%F %T')] to stop it cleanly:  tools/stop-gauntlet.sh $LABEL"

    wait "$CUTECHESS_PID"
    rm -f "$PIDFILE"
fi

# One game per [Event ...] block; a block carrying [Result "*"] never reaches the combined
# file, so neither the point totals nor Ordo see a game that was cut off mid-play.
awk '
    /^\[Event /  { if (block != "" && block !~ /\[Result "\*"\]/) printf "%s", block; block = "" }
                  { block = block $0 "\n" }
    END           { if (block != "" && block !~ /\[Result "\*"\]/) printf "%s", block }
' "$RESULTS"/gauntlet-"$LABEL"-seg*.pgn > "$COMBINED"

echo
echo "[$(date '+%F %T')] segment finished — points per engine, all segments:"
grep -hE "^Score of" "$RESULTS"/gauntlet-"$LABEL"-seg*.log 2>/dev/null | tail -20
echo "banked so far: $(grep -c '^\[Event' "$COMBINED") games in $COMBINED"

if [ -z "$PLACEBO_DIR" ]; then
    echo
    echo "REMINDER: two arms, so candidate - base is gain AND cost together."
    echo "          No cost arm because: $NO_PLACEBO_REASON"
fi

# --- Ordo ------------------------------------------------------------------
if [ -x "$ORDO" ]; then
    anchors_csv="$RESULTS/anchors-gauntlet-$LABEL.csv"
    : > "$anchors_csv"

    while IFS='|' read -r name wrapper proto rating opts; do
        [ -z "$name" ] && continue
        [ -n "$rating" ] && printf '"%s",%s\n' "$name" "$rating" >> "$anchors_csv"
    done < /tmp/gauntlet-anchors.txt

    echo
    echo "[$(date '+%F %T')] Ordo:"
    "$ORDO" -Q \
        -p "$COMBINED" \
        -m "$anchors_csv" \
        -s "$ORDO_SIMULATIONS" \
        -o "$RESULTS/ordo-gauntlet-$LABEL.txt" \
        -c "$RESULTS/ordo-gauntlet-$LABEL.csv"
    cat "$RESULTS/ordo-gauntlet-$LABEL.txt"
fi
