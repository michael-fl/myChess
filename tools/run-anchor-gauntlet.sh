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
OPENINGS="2moves_v2.pgn"
RESULTS="test-results"
JAVA_HOME_DIR="/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home"

# Identical to run-anchor-bracket.sh, so the games stay comparable to the bracket.
TC="40/120"
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

echo "=========================================================================="
echo "[$(date '+%F %T')] anchor gauntlet: $LABEL"
echo "TC=$TC  rounds=$ROUNDS  ${ARM_COUNT} arms x ${ANCHOR_COUNT} anchors"
echo "  = $PAIRINGS pairings -> $GAMES games   concurrency=$CONCURRENCY"

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

# --- pre-flight 1: provenance ----------------------------------------------
echo
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

PGN="$RESULTS/gauntlet-$LABEL.pgn"
LOG="$RESULTS/gauntlet-$LABEL-stdout.log"

echo
echo "[$(date '+%F %T')] starting the tournament -> $PGN"

"$CUTECHESS" "$@" \
    -each tc="$TC" \
    -rounds "$ROUNDS" -games 2 -repeat \
    -openings file="$OPENINGS" format=pgn order=random plies=4 policy=round \
    -concurrency "$CONCURRENCY" -ratinginterval "$RATING_INTERVAL" \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout "$PGN" \
    | tee "$LOG"

echo
echo "[$(date '+%F %T')] tournament finished — points per engine:"
grep -E "^Score of" "$LOG" | tail -20

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
        -p "$PGN" \
        -m "$anchors_csv" \
        -s "$ORDO_SIMULATIONS" \
        -o "$RESULTS/ordo-gauntlet-$LABEL.txt" \
        -c "$RESULTS/ordo-gauntlet-$LABEL.csv"
    cat "$RESULTS/ordo-gauntlet-$LABEL.txt"
fi
