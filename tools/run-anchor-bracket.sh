#!/bin/sh
#
# run-anchor-bracket.sh — unattended absolute-Elo re-anchor measurement.
#
# Runs the four anchor matches SEQUENTIALLY (never in parallel — a single
# cutechess match already saturates the cores, and overlapping runs would
# distort the time-based TC), then combines them with Ordo into one absolute
# Elo estimate. Designed to be launched in the background and left overnight:
# it re-execs itself under `caffeinate` so the Mac will not sleep mid-run.
#
# The anchor bracket (for myChess ~1795, post-v4.3.0):
#   TSCP 1.81      1607   fixed   (lower anchor,  xboard)
#   Zeta Dva 0402  1801   fixed   (near anchor,   xboard)   <- carries the most info
#   Princhess 0.7  1985   fixed   (upper anchor,  UCI)
#   Kojiro 0.1.4  (1984)  FREE    (Ordo estimates it -> cross-checks the upper end)
#
# Usage:
#   tools/run-anchor-bracket.sh <mychess-version-dir> [--wait-for-cores]
#
#   <mychess-version-dir>  a subdirectory of versions/ that holds a built
#                          myChess, e.g. "4.4.0" -> versions/4.4.0/mychess-uci.sh
#   --wait-for-cores       block until no other cutechess-cli process runs
#                          before starting Match A (use when an SPRT is still up)
#
# Launch unattended (survives closing the terminal; will not sleep):
#   nohup tools/run-anchor-bracket.sh 4.4.0 --wait-for-cores \
#         > test-results/anchor-driver-4.4.0.log 2>&1 &
#
# Output:
#   test-results/match-<version>-vs-<engine>.pgn        per-match games
#   test-results/match-<version>-vs-<engine>-stdout.log per-match cutechess log
#   test-results/ordo-anchor-<version>.txt / .csv       combined Elo estimate

set -u

# --- keep the machine awake for the whole run (idempotent re-exec) ----------
if [ -z "${ANCHOR_CAFFEINATED:-}" ]; then
    exec caffeinate -is env ANCHOR_CAFFEINATED=1 "$0" "$@"
fi

# --- paths ------------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT" || exit 1

CUTECHESS="/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli"
ORDO="/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo"
OPENINGS="2moves_v2.pgn"
RESULTS="test-results"

# --- tuning knobs (kept identical to the v3.1.x baseline for comparability) -
TC="40/120"
ROUNDS=200            # -> 400 games per match (games=2, repeat)
CONCURRENCY=4         # matches the baseline timing conditions; 8 P-cores allow 6 if you accept a small drift
RATING_INTERVAL=10

# --- arguments --------------------------------------------------------------
VERSION="${1:-}"
WAIT_FOR_CORES=0
[ "${2:-}" = "--wait-for-cores" ] && WAIT_FOR_CORES=1

if [ -z "$VERSION" ]; then
    echo "ERROR: no myChess version given. Usage: $0 <versions-subdir> [--wait-for-cores]" >&2
    exit 2
fi

MYCHESS="./versions/${VERSION}/mychess-uci.sh"

# --- anchor set: "Name|wrapper|proto|fixedRatingOrEmpty" ---------------------
# An empty rating field means the engine is left FREE for Ordo to estimate.
ANCHORS='
TSCP|./engines/tscp-1.81-elo1607/tscp.sh|xboard|1607
ZetaDva|./engines/ZetaDva-0402-elo1801/zetadva.sh|xboard|1801
Princhess|./engines/princhess-0.7.0-elo1985/princhess.sh|uci|1985
Kojiro|./engines/Kojiro-0.1.4-elo1984/kojiro.sh|uci|
'

# --- prerequisite checks ----------------------------------------------------
fail=0
check() { [ -e "$1" ] || { echo "MISSING: $1" >&2; fail=1; }; }

check "$CUTECHESS"
check "$ORDO"
check "$OPENINGS"
check "$MYCHESS"

# check the anchor wrappers in-process (a `... | while` subshell could not
# set `fail`, so the paths are listed explicitly here)
for wrapper in \
    ./engines/tscp-1.81-elo1607/tscp.sh \
    ./engines/ZetaDva-0402-elo1801/zetadva.sh \
    ./engines/princhess-0.7.0-elo1985/princhess.sh \
    ./engines/Kojiro-0.1.4-elo1984/kojiro.sh; do
    check "$wrapper"
done

if [ "$fail" -ne 0 ]; then
    echo "Aborting: prerequisites missing (see above)." >&2
    exit 3
fi

mkdir -p "$RESULTS"

# --- optionally wait until the cores are free -------------------------------
if [ "$WAIT_FOR_CORES" -eq 1 ]; then
    echo "[$(date '+%F %T')] --wait-for-cores: waiting for other cutechess-cli runs to finish..."

    while pgrep -f cutechess-cli | grep -qv "^$$\$"; do
        # another cutechess process is running; check again in 5 minutes
        sleep 300
    done

    echo "[$(date '+%F %T')] cores free, starting."
fi

# --- run the four matches sequentially --------------------------------------
echo "========================================================================"
echo "Anchor bracket for myChess version: $VERSION"
echo "TC=$TC  rounds=$ROUNDS (400 games/match)  concurrency=$CONCURRENCY"
echo "started: $(date '+%F %T')"
echo "========================================================================"

run_match() {
    name="$1"; wrapper="$2"; proto="$3"
    slug="$(echo "$name" | tr '[:upper:]' '[:lower:]')"
    pgn="$RESULTS/match-${VERSION}-vs-${slug}.pgn"
    log="$RESULTS/match-${VERSION}-vs-${slug}-stdout.log"

    echo
    echo "[$(date '+%F %T')] ===== Match: myChess $VERSION vs $name ($proto) ====="

    "$CUTECHESS" \
        -engine name="myChess-$VERSION" cmd="$MYCHESS" proto=uci \
        -engine name="$name" cmd="$wrapper" proto="$proto" \
        -each tc="$TC" \
        -rounds "$ROUNDS" -games 2 -repeat \
        -openings file="$OPENINGS" format=pgn order=random plies=8 \
        -concurrency "$CONCURRENCY" -ratinginterval "$RATING_INTERVAL" \
        -recover \
        -draw movenumber=40 movecount=8 score=40 \
        -resign movecount=4 score=600 \
        -pgnout "$pgn" \
        2>&1 | tee "$log"

    status=$?
    echo "[$(date '+%F %T')] Match vs $name finished (exit=$status)"

    return $status
}

echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating; do
    [ -z "$name" ] && continue
    run_match "$name" "$wrapper" "$proto" || \
        echo "WARNING: match vs $name exited non-zero — continuing with the rest."
done

# --- Ordo combination -------------------------------------------------------
echo
echo "[$(date '+%F %T')] ===== Ordo combination ====="

anchors_csv="$RESULTS/anchors-${VERSION}.csv"
bracket_pgn="$RESULTS/bracket-${VERSION}.pgn"
ordo_txt="$RESULTS/ordo-anchor-${VERSION}.txt"
ordo_csv="$RESULTS/ordo-anchor-${VERSION}.csv"

# anchors.csv: only the FIXED anchors (Kojiro is deliberately omitted -> FREE)
: > "$anchors_csv"
echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating; do
    [ -z "$name" ] && continue
    [ -n "$rating" ] && echo "\"$name\",$rating" >> "$anchors_csv"
done

# concatenate every match PGN that was actually produced
: > "$bracket_pgn"
found=0
echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating; do
    [ -z "$name" ] && continue
    slug="$(echo "$name" | tr '[:upper:]' '[:lower:]')"
    pgn="$RESULTS/match-${VERSION}-vs-${slug}.pgn"
    [ -s "$pgn" ] && cat "$pgn" >> "$bracket_pgn"
done
[ -s "$bracket_pgn" ] && found=1

if [ "$found" -ne 1 ]; then
    echo "ERROR: no match PGNs were produced — skipping Ordo." >&2
    exit 4
fi

"$ORDO" -p "$bracket_pgn" -m "$anchors_csv" -o "$ordo_txt" -c "$ordo_csv"

echo
echo "========================================================================"
echo "[$(date '+%F %T')] DONE. Combined estimate:"
echo "------------------------------------------------------------------------"
cat "$ordo_txt"
echo "========================================================================"
