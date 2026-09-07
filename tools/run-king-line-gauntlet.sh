#!/usr/bin/env bash
#
# Three-arm gauntlet: base / placebo / king-line-v2 against the five anchor engines.
#
# Answers the one question every king-line match so far could not: does the term do
# anything against *foreign* opponents? All previous runs were self-play, which is the
# constellation in which a defensive term shows least — its opponent is exactly as good
# at exploiting king exposure as it is, because it is the same engine.
#
# The placebo arm (term computed, factor 0) is what makes this more than another A/B:
#
#   placebo vs base          the COST of computing the term, in Elo under the clock
#   king-line-v2 vs placebo  the EFFECT of applying it, with the cost held constant
#
# A single candidate-vs-baseline match only ever reports the sum of the two.
#
# Shape: `-tournament gauntlet -seeds 3` plays the three seeds against the five anchors
# and nothing else — 15 pairings, no anchor-vs-anchor games (36 % of a round robin's
# budget for a ranking already known from bracket-4.4.1.pgn) and no variant-vs-variant
# games (self-play again). `-openings policy=round` reuses one opening per round, so all
# three arms meet each anchor from the same position: a paired comparison for free.
#
# Read POINTS, not Elo. All three arms face the same opponents equally often, so the raw
# point totals are a sufficient statistic and need no rating model. Ordo runs at the end
# anyway, for the absolute picture.
#
# Resolution: about 87 games/hour at concurrency 4, so ~2100/day. Two days give ~1400
# games per arm against the anchors, i.e. roughly +/- 17 Elo on the difference between
# two arms. That finds a large effect and cannot separate +2 from 0 — which is the right
# trade, because the hypothesis is "substantial against opponents that do not defend",
# not "worth 3 Elo".
#
# Full write-up: docs/king-line-gauntlet.md
#
# Usage:
#   tools/run-king-line-gauntlet.sh [--wait-for-cores] [--rounds N] [--preflight-only]
#
# Outputs:
#   test-results/gauntlet-king-line.pgn            all games
#   test-results/gauntlet-king-line-stdout.log     cutechess log, standings every 10 games
#   test-results/ordo-gauntlet-king-line.txt       Ordo rating list

set -u

# --- keep the machine awake for the whole run (idempotent re-exec) ----------
if [ -z "${GAUNTLET_CAFFEINATED:-}" ]; then
    exec caffeinate -is env GAUNTLET_CAFFEINATED=1 "$0" "$@"
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

CUTECHESS="/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli"
ORDO="/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo"
OPENINGS="2moves_v2.pgn"
RESULTS="test-results"
JAVA_HOME_DIR="/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home"

# Identical to tools/run-anchor-bracket.sh, so the games are comparable to the bracket.
TC="40/120"
CONCURRENCY=4
RATING_INTERVAL=10
ANCHOR_HASH_MB=256
BBC_HASH_MB=128
ORDO_SIMULATIONS=1000

# 15 pairings x 2 games per encounter x ROUNDS. 140 -> 4200 games, about two days.
ROUNDS=140

# Depth of the pre-flight signature check. Six is a couple of minutes per arm; the
# identical-tree property it verifies holds at any fixed depth.
PREFLIGHT_DEPTH=6

WAIT_FOR_CORES=0
PREFLIGHT_ONLY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --wait-for-cores) WAIT_FOR_CORES=1 ;;
        --preflight-only) PREFLIGHT_ONLY=1 ;;
        --rounds) shift; ROUNDS="${1:-}" ;;
        --rounds=*) ROUNDS="${1#--rounds=}" ;;
        *) echo "ERROR: unknown argument: $1" >&2; exit 2 ;;
    esac
    shift
done

# --- the three arms, seeds first --------------------------------------------
# name|directory|jar
ARMS="
base|versions/4.6.1-gauntlet-base|my-chess-4.6.1-gauntlet-base.jar
placebo|versions/4.6.1-gauntlet-placebo|my-chess-4.6.1-gauntlet-placebo.jar
king-line-v2|versions/4.6.1-gauntlet-king-line-v2|my-chess-4.6.1-gauntlet-king-line-v2.jar
"

# name|wrapper|protocol|CCRL rating (empty = free in Ordo)|engine options
# Identical to tools/run-anchor-bracket.sh, Kojiro deliberately left free.
ANCHORS="
TSCP|./engines/tscp-1.81-elo1609/tscp.sh|xboard|1609|
ZetaDva|./engines/ZetaDva-0402-unrated/zetadva.sh|xboard|1801|
Princhess|./engines/princhess-0.7.0-elo1985/princhess.sh|uci|1985|option.Hash=$ANCHOR_HASH_MB
BBC|./engines/BBC-1.1-elo2019/bbc.sh|uci|2019|option.Hash=$BBC_HASH_MB
Kojiro|./engines/Kojiro-0.1.4-elo1984/kojiro.sh|uci||option.Hash=$ANCHOR_HASH_MB
"

# --- prerequisite checks ----------------------------------------------------
fail=0
check() { [ -e "$1" ] || { echo "MISSING: $1" >&2; fail=1; }; }

check "$CUTECHESS"
check "$ORDO"
check "$OPENINGS"

# Listed explicitly rather than looped over $ARMS / $ANCHORS through a pipe: a
# `... | while` runs in a subshell and could not set `fail`. run-anchor-bracket.sh
# carries the same note for the same reason.
for d in versions/4.6.1-gauntlet-base \
         versions/4.6.1-gauntlet-placebo \
         versions/4.6.1-gauntlet-king-line-v2; do
    check "$d/mychess-uci.sh"
    check "$d/BUILT-FROM.txt"
    ls "$d"/my-chess-*.jar >/dev/null 2>&1 || { echo "MISSING: $d/my-chess-*.jar" >&2; fail=1; }
done

for wrapper in ./engines/tscp-1.81-elo1609/tscp.sh \
               ./engines/ZetaDva-0402-unrated/zetadva.sh \
               ./engines/princhess-0.7.0-elo1985/princhess.sh \
               ./engines/BBC-1.1-elo2019/bbc.sh \
               ./engines/Kojiro-0.1.4-elo1984/kojiro.sh; do
    check "$wrapper"
done

[ "$fail" -eq 0 ] || { echo "ERROR: prerequisites missing, aborting." >&2; exit 1; }

mkdir -p "$RESULTS"

echo "=========================================================================="
echo "[$(date '+%F %T')] three-arm anchor gauntlet"
echo "TC=$TC  rounds=$ROUNDS  -> $((15 * 2 * ROUNDS)) games  concurrency=$CONCURRENCY"
echo "=========================================================================="

# --- pre-flight 1: provenance ----------------------------------------------
# bench-history.md section 6 rule 7. The one error this project actually made was an
# A/B against a baseline jar that predated three behavior-relevant commits.
echo
echo "[$(date '+%F %T')] --- pre-flight 1/3: provenance -----------------------"
printf '%s\n' "$ARMS" | while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    echo "--- $name"
    sed 's/^/    /' "$dir/BUILT-FROM.txt"
    printf '    live jar: '
    shasum -a 256 "$dir/$jar" | awk '{print $1}'
done

# --- pre-flight 2: does each arm behave as its name claims? -----------------
# Hashes prove the jars differ, never that they differ the way you think. White is
# settled on g1 with a half-open g-file, black still holds both rights, so the term
# applies to white only: base has no kingLinePenalty line at all, the candidate scores
# it, the placebo computes it and applies zero.
echo
echo "[$(date '+%F %T')] --- pre-flight 2/3: behavior -------------------------"
PROBE_FEN="r3k2r/pppppppp/8/8/8/8/PPPPPP1P/RNBQ1RK1 w kq - 0 20"
printf 'fen %s\nw\nquit\n' "$PROBE_FEN" > /tmp/gauntlet-probe.txt

printf '%s\n' "$ARMS" | while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    printf '    %-14s ' "$name"
    ( cd "$dir" && JAVA_HOME="$JAVA_HOME_DIR" "$JAVA_HOME_DIR/bin/java" \
        -cp "$jar:lib/*" org.michaelfl.mychess.MyChessMain < /tmp/gauntlet-probe.txt 2>/dev/null ) \
        | grep -E "kingLinePenalty|^weight:" | tr '\n' '|'
    echo
done
echo "    expected: base has no kingLinePenalty line and the same total as placebo;"
echo "              candidate shows weight=-0.15, placebo shows weight=0.0"

# --- pre-flight 3: identical tree, measurably higher cost -------------------
# The placebo is only a cost arm if its evaluation is base's (identical signature) and
# the walk is really executed (lower NPS). Equal NPS is the alarm that the multiplication
# by zero was folded away; the fix is then a Statistics counter, not a micro-factor.
echo
echo "[$(date '+%F %T')] --- pre-flight 3/3: signature and cost, depth $PREFLIGHT_DEPTH ---"

bench_arm() {
    dir="$1"; jar="$2"
    printf 'bench %d\nquit\n' "$PREFLIGHT_DEPTH" | ( cd "$dir" && \
        JAVA_HOME="$JAVA_HOME_DIR" "$JAVA_HOME_DIR/bin/java" -cp "$jar:lib/*" \
        org.michaelfl.mychess.MyChessMain 2>/dev/null )
}

BASE_OUT="$(bench_arm versions/4.6.1-gauntlet-base my-chess-4.6.1-gauntlet-base.jar)"
PLAC_OUT="$(bench_arm versions/4.6.1-gauntlet-placebo my-chess-4.6.1-gauntlet-placebo.jar)"

base_nodes="$(printf '%s' "$BASE_OUT" | grep 'Nodes searched' | tr -dc '0-9')"
plac_nodes="$(printf '%s' "$PLAC_OUT" | grep 'Nodes searched' | tr -dc '0-9')"
base_nps="$(printf '%s' "$BASE_OUT" | grep '^NPS' | tr -dc '0-9')"
plac_nps="$(printf '%s' "$PLAC_OUT" | grep '^NPS' | tr -dc '0-9')"

echo "    base    nodes=$base_nodes  nps=$base_nps"
echo "    placebo nodes=$plac_nodes  nps=$plac_nps"

if [ "$base_nodes" != "$plac_nodes" ]; then
    echo "ABORT: signatures differ — the placebo's evaluation is not base's, so it is not a" >&2
    echo "       pure cost arm. Check kingLinePenaltyFactor on king-line-v2-placebo." >&2
    exit 1
fi

if [ "$plac_nps" -ge "$base_nps" ]; then
    echo "ABORT: the placebo is not slower than base, so the walk was optimized away and the" >&2
    echo "       middle arm measures nothing. Consume the result observably (a Statistics" >&2
    echo "       counter, as the material-only counter does) rather than perturbing the factor." >&2
    exit 1
fi

echo "    OK: identical signature, placebo slower by $(( (base_nps - plac_nps) * 1000 / base_nps ))/1000"

if [ "$PREFLIGHT_ONLY" -eq 1 ]; then
    echo
    echo "[$(date '+%F %T')] --preflight-only: stopping here."
    exit 0
fi

# --- optionally wait until the cores are free -------------------------------
# At a fixed time control the result depends on how many cores the engines get, so a
# tournament started next to another match measures the load as much as the engines.
if [ "$WAIT_FOR_CORES" -eq 1 ]; then
    echo
    echo "[$(date '+%F %T')] --wait-for-cores: waiting for other cutechess-cli runs..."

    while pgrep -f cutechess-cli | grep -qv "^$$\$"; do
        sleep 300
    done

    echo "[$(date '+%F %T')] cores free, starting."
fi

# --- build the engine arguments --------------------------------------------
set -- \
    -tournament gauntlet -seeds 3 \
    -event "king-line three-arm anchor gauntlet"

printf '%s\n' "$ARMS" > /tmp/gauntlet-arms.txt
while IFS='|' read -r name dir jar; do
    [ -z "$name" ] && continue
    set -- "$@" -engine name="mychess-$name" cmd="./$dir/mychess-uci.sh" proto=uci
done < /tmp/gauntlet-arms.txt

printf '%s\n' "$ANCHORS" > /tmp/gauntlet-anchors.txt
while IFS='|' read -r name wrapper proto rating opts; do
    [ -z "$name" ] && continue
    # $opts is deliberately unquoted: it carries zero or one cutechess engine option.
    set -- "$@" -engine name="$name" cmd="$wrapper" proto="$proto" $opts
done < /tmp/gauntlet-anchors.txt

echo
echo "[$(date '+%F %T')] starting the tournament"

"$CUTECHESS" "$@" \
    -each tc="$TC" \
    -rounds "$ROUNDS" -games 2 -repeat \
    -openings file="$OPENINGS" format=pgn order=random plies=4 policy=round \
    -concurrency "$CONCURRENCY" -ratinginterval "$RATING_INTERVAL" \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout "$RESULTS/gauntlet-king-line.pgn" \
    | tee "$RESULTS/gauntlet-king-line-stdout.log"

echo
echo "[$(date '+%F %T')] tournament finished — points per engine:"
grep -E "^Score of" "$RESULTS/gauntlet-king-line-stdout.log" | tail -20

if [ -x "$ORDO" ]; then
    echo
    echo "[$(date '+%F %T')] Ordo rating list (anchors fixed at their CCRL ratings,"
    echo "                   Kojiro left free — same convention as the anchor bracket)"

    anchors_csv="$RESULTS/anchors-gauntlet-king-line.csv"
    : > "$anchors_csv"

    while IFS='|' read -r name wrapper proto rating opts; do
        [ -z "$name" ] && continue
        [ -n "$rating" ] && echo "\"$name\",$rating" >> "$anchors_csv"
    done < /tmp/gauntlet-anchors.txt

    "$ORDO" -W -D -s "$ORDO_SIMULATIONS" \
        -p "$RESULTS/gauntlet-king-line.pgn" \
        -m "$anchors_csv" \
        -o "$RESULTS/ordo-gauntlet-king-line.txt" \
        -c "$RESULTS/ordo-gauntlet-king-line.csv"
    cat "$RESULTS/ordo-gauntlet-king-line.txt"
fi
