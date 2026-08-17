#!/bin/sh
#
# run-anchor-bracket.sh — unattended absolute-Elo re-anchor measurement.
#
# Runs the anchor matches SEQUENTIALLY (never in parallel — a single cutechess
# match already saturates the cores, and overlapping runs would distort the
# time-based TC), then combines them with Ordo into one absolute Elo estimate.
# Designed to be launched in the background and left overnight: it re-execs
# itself under `caffeinate` so the Mac will not sleep mid-run.
#
# The anchor bracket (for myChess ~1920, post-v4.4.1):
#   TSCP 1.81      1609   fixed   (lower anchor,  xboard)
#   Zeta Dva 0402  1801   fixed   (near anchor,   xboard)   <- see the caveat below
#   Princhess 0.7  1985   fixed   (upper anchor,  UCI)
#   BBC 1.1        2019   fixed   (upper anchor,  UCI)      <- best-sampled of the set
#   Kojiro 0.1.4  (1984)  FREE    (Ordo estimates it -> cross-checks the upper end)
#
# The opening suite bounds the book depth, not `plies`: 2moves_v2.pgn carries
# exactly 4 plies per line, so cutechess takes min(requested, available) and any
# value above 4 is a no-op. It read `plies=8` until 2026-08-17, which suggested
# an opening phase twice as deep as the one actually played.
#
# GIVE EVERY UCI ANCHOR A REAL HASH. Learned the hard way on 2026-08-16: the
# first Princhess match ran at the engine's own default of 16 MB and produced
# 86.4 % for myChess, which would have implied ~2300. Princhess is an MCTS
# engine -- every tree node lives in the hash -- so 16 MB fills after ~9 600
# nodes and the search simply stops: 0.07 s per move against myChess's 2.89 s,
# a factor of 40. The match measured a crippled opponent, not an 1985 one. The
# same trap is set for Kojiro, whose default Hash is 1 MB (less damaging for an
# alpha-beta engine, which loses hit rate rather than stopping, but still far
# from what CCRL measured). Anything an anchor's rating was established with has
# to be passed explicitly; defaults are not comparable.
#
# The symptom is cheap to check and worth checking after every match: compare
# the per-move times in the PGN comments. Two engines at the same TC should be
# within a factor of ~2 of each other, never 40.
#
# Usage:
#   tools/run-anchor-bracket.sh <mychess-version-dir> [--wait-for-cores] [--only A,B]
#
#   <mychess-version-dir>  a subdirectory of versions/ that holds a built
#                          myChess, e.g. "4.4.0" -> versions/4.4.0/mychess-uci.sh
#   --wait-for-cores       block until no other cutechess-cli process runs
#                          before starting Match A (use when an SPRT is still up)
#   --only A,B             run only these anchors (comma-separated, names as in
#                          the ANCHORS table). Use it to resume a bracket after
#                          a match had to be redone; the Ordo step still picks
#                          up every match PGN that exists on disk.
#
# An existing PGN/log for a match is never overwritten: it is moved aside to
# *-superseded-<timestamp>.* first, so a rerun cannot destroy the evidence of
# why it was rerun.
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
VERSION=""
WAIT_FOR_CORES=0
ONLY=""

while [ $# -gt 0 ]; do
    case "$1" in
        --wait-for-cores) WAIT_FOR_CORES=1 ;;
        --only) shift; ONLY="${1:-}" ;;
        --only=*) ONLY="${1#--only=}" ;;
        -*) echo "ERROR: unknown option: $1" >&2; exit 2 ;;
        *) [ -z "$VERSION" ] && VERSION="$1" || { echo "ERROR: unexpected argument: $1" >&2; exit 2; } ;;
    esac
    shift
done

if [ -z "$VERSION" ]; then
    echo "ERROR: no myChess version given. Usage: $0 <versions-subdir> [--wait-for-cores] [--only A,B]" >&2
    exit 2
fi

MYCHESS="./versions/${VERSION}/mychess-uci.sh"

# --- anchor set: "Name|wrapper|proto|fixedRatingOrEmpty|cutechessEngineOptions"
# An empty rating field means the engine is left FREE for Ordo to estimate.
#
# The options field is appended verbatim to that engine's -engine block and is
# word-split on purpose, so several options can be given. Hash is not a tuning
# knob here but a comparability requirement -- see the header. 256 MB is the
# defensive choice: it is inside the range CCRL uses for its blitz lists, so it
# does not lift an anchor above the rating it was measured at. The xboard
# anchors get nothing; TSCP has no options at all and ZetaDva none that matter.
#
# BBC is the exception: it caps Hash at 128 MB (max 128 in its option line), the
# other value CCRL permits. Its own default is 64, so passing nothing would
# repeat the mistake that invalidated the first Princhess match.
ANCHOR_HASH_MB=256
BBC_HASH_MB=128

# Ratings read off the CCRL Blitz list on 2026-08-16, with the sample size that
# backs each one -- the number matters as much as the rating:
#   TSCP 1.81       1609  +-19  1067 games   solid
#   Princhess 0.7.0 1985  +-18  1202 games   solid
#   BBC 1.1         2019  +-17  1243 games   solid; NB BBC 1.2 lists 74 Elo LOWER
#   Zeta Dva 0310   1801  +-52   114 games   thin, and we built 0402, not listed
#   Kojiro 0.1.4    1984  +-85    40 games   below CCRL's own 100-game threshold
# Kojiro is therefore left FREE by design. Zeta Dva is kept fixed here so the
# automated result stays comparable with earlier runs; the variant that frees it
# as well is a one-line anchors.csv change on the finished bracket PGN.
ANCHORS="
TSCP|./engines/tscp-1.81-elo1607/tscp.sh|xboard|1609|
ZetaDva|./engines/ZetaDva-0402-elo1801/zetadva.sh|xboard|1801|
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
check "$MYCHESS"

# check the anchor wrappers in-process (a `... | while` subshell could not
# set `fail`, so the paths are listed explicitly here)
for wrapper in \
    ./engines/tscp-1.81-elo1607/tscp.sh \
    ./engines/ZetaDva-0402-elo1801/zetadva.sh \
    ./engines/princhess-0.7.0-elo1985/princhess.sh \
    ./engines/BBC-1.1-elo2019/bbc.sh \
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
    name="$1"; wrapper="$2"; proto="$3"; opts="$4"
    slug="$(echo "$name" | tr '[:upper:]' '[:lower:]')"
    pgn="$RESULTS/match-${VERSION}-vs-${slug}.pgn"
    log="$RESULTS/match-${VERSION}-vs-${slug}-stdout.log"

    # Never overwrite a previous match: a rerun usually happens because the old
    # result was wrong, and that wrongness is the evidence worth keeping.
    if [ -s "$pgn" ]; then
        stamp="$(date '+%Y%m%d-%H%M%S')"
        echo "[$(date '+%F %T')] existing $pgn moved aside (-superseded-$stamp)"
        mv "$pgn" "$RESULTS/match-${VERSION}-vs-${slug}-superseded-${stamp}.pgn"
        [ -s "$log" ] && mv "$log" "$RESULTS/match-${VERSION}-vs-${slug}-stdout-superseded-${stamp}.log"
    fi

    echo
    echo "[$(date '+%F %T')] ===== Match: myChess $VERSION vs $name ($proto) ====="
    [ -n "$opts" ] && echo "[$(date '+%F %T')] $name engine options: $opts"

    # $opts is deliberately unquoted: it carries zero or more cutechess engine
    # options and has to word-split.
    # shellcheck disable=SC2086
    "$CUTECHESS" \
        -engine name="myChess-$VERSION" cmd="$MYCHESS" proto=uci \
        -engine name="$name" cmd="$wrapper" proto="$proto" $opts \
        -each tc="$TC" \
        -rounds "$ROUNDS" -games 2 -repeat \
        -openings file="$OPENINGS" format=pgn order=random plies=4 \
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

echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating opts; do
    [ -z "$name" ] && continue

    if [ -n "$ONLY" ]; then
        case ",$ONLY," in
            *",$name,"*) ;;
            *) echo "[$(date '+%F %T')] skipping $name (--only $ONLY)"; continue ;;
        esac
    fi

    run_match "$name" "$wrapper" "$proto" "$opts" || \
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
echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating opts; do
    [ -z "$name" ] && continue
    [ -n "$rating" ] && echo "\"$name\",$rating" >> "$anchors_csv"
done

# concatenate every match PGN that was actually produced
: > "$bracket_pgn"
found=0
echo "$ANCHORS" | while IFS='|' read -r name wrapper proto rating opts; do
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
