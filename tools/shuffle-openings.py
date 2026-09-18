#!/usr/bin/env python3
"""Shuffle an opening book once, deterministically, so sequential play stays representative.

WHY. Resuming a match needs `-openings order=sequential start=N` - random order cannot be
resumed, because cutechess would redraw from the seed and replay the openings the earlier
segment already used. But sequential play exposes whatever order the book happens to have,
and 2moves_v2.pgn is NOT in random order: measured over its 12,092 openings, b4 is 17.6 % of
the first 2100 against a book-wide share that does not reach the top six, while e4 falls from
11.9 % to 8.1 %. The bias is a gradient rather than blocks - the longest run of one first move
is 4 - so it cannot be dodged by starting further in.

A match drawing the first 2100 openings would therefore be played mostly on flank and
irregular lines. Each side gets every opening once, so nothing is unfair, but the *style* of
the sample is skewed, and an engine that handles a whole series of similar openings badly
would be judged on that rather than on chess.

Shuffling once with a fixed seed gives all three properties at once: representative, exactly
reproducible, and resumable.

    tools/shuffle-openings.py 2moves_v2.pgn 2moves_v2-shuffled.pgn [--seed 1618]

The seed is recorded in a comment at the top of the output, so the file can always be
regenerated and checked.
"""

import argparse
import pathlib
import random
import sys


def split_games(text):
    """The book as a list of game blocks, each keeping its own tags and movetext."""
    games, current = [], []

    for line in text.splitlines():
        if line.startswith("[Event ") and current:
            games.append("\n".join(current).strip())
            current = []

        current.append(line)

    if current:
        games.append("\n".join(current).strip())

    return [g for g in games if g]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("target")
    parser.add_argument("--seed", type=int, default=1618)
    args = parser.parse_args()

    source = pathlib.Path(args.source)

    if not source.exists():
        sys.exit(f"{source} not found")

    games = split_games(source.read_text(errors="ignore"))

    if not games:
        sys.exit("no games parsed - is this a PGN?")

    random.Random(args.seed).shuffle(games)

    header = (f"% shuffled from {source.name} with tools/shuffle-openings.py, seed {args.seed}\n"
              f"% {len(games)} openings; regenerate with the same seed to reproduce exactly\n\n")

    pathlib.Path(args.target).write_text(header + "\n\n".join(games) + "\n")
    print(f"{len(games)} openings shuffled into {args.target} (seed {args.seed})")


if __name__ == "__main__":
    main()
