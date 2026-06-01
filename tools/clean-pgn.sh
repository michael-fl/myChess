#!/usr/bin/env bash
# clean-pgn.sh — drop PGN games terminated by "stalled connection".
#
# Cutechess marks connection stalls (engine stops responding mid-game,
# Adjudicated to the opponent) with [Termination "stalled connection"].
# Those are tool artifacts, not real chess results, and they inflate the
# opponent's score against the non-stalling side. Removing them gives a
# clean dataset for Ordo / Elo computations without affecting any other
# game outcomes.
#
# Usage:
#   clean-pgn.sh <input.pgn>              # filtered PGN → stdout
#   clean-pgn.sh <input.pgn> <output.pgn> # filtered PGN → output.pgn
#
# A one-line summary "N scanned, N kept, N dropped" goes to stderr in
# both cases, so it never contaminates the cleaned PGN even when piped.
#
# All other terminations (adjudication, fifty-move-rule, 3-fold,
# illegal-move forfeits, time, mate, stalemate, ...) are preserved as
# legitimate chess outcomes.

set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    cat >&2 <<'USAGE'
Usage: clean-pgn.sh <input.pgn> [output.pgn]

  Drops PGN game blocks with [Termination "stalled connection"] — a
  cutechess connection-stall tool artifact, not a real chess outcome.
  All other terminations are preserved as-is.

  If <output.pgn> is omitted, the filtered PGN goes to stdout.
  A summary "N scanned, N kept, N dropped" always goes to stderr.
USAGE
    exit 1
fi

input="$1"

if [ ! -f "$input" ]; then
    printf '%s: input file not found: %s\n' "$0" "$input" >&2
    exit 1
fi

# Redirect script stdout to output file if a second arg was given;
# stderr (where the summary goes) stays attached to the user's terminal.
if [ "$#" -eq 2 ]; then
    exec > "$2"
fi

exec awk '
    /^\[Event / && game != "" {
        flush_game()
        game = ""
    }

    { game = game $0 "\n" }

    END {
        if (game != "") {
            flush_game()
        }
        printf "clean-pgn: %d games scanned, %d kept, %d dropped (stalled connection)\n", \
            total, kept, total - kept > "/dev/stderr"
    }

    function flush_game() {
        total++
        if (index(game, "[Termination \"stalled connection\"]") == 0) {
            printf "%s", game
            kept++
        }
    }
' "$input"
