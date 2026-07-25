# Tuning data

Training data for the offline Texel evaluation tuner
(`src/test/java/org/michaelfl/mychess/tuning/`).

The data files themselves are **not** committed — they are large (~79 MB),
re-downloadable, third-party data. This directory is git-ignored except for
this README (see `.gitignore`). Fetch the dataset locally with the command
below.

## Zurichess `quiet-labeled` (v7)

The canonical Texel tuning set: ~1.43 million quiet positions, each labeled
with the result of the game it came from.

Fetch (from the repository root):

```sh
mkdir -p tuning-data
curl -sL --max-time 180 \
  https://bitbucket.org/zurichess/tuner/downloads/quiet-labeled.v7.epd.gz \
  | gunzip > tuning-data/quiet-labeled.epd
```

### Format

One position per line, EPD (no half-move / full-move counters):

```
r2qkr2/p1pp1ppp/1pn1pn2/2P5/3Pb3/2N1P3/PP3PPP/R1B1KB1R b KQq - c9 "0-1";
```

- Fields 1–4 are the FEN board, side to move, castling rights and en-passant
  square. **There are no move counters**, so append `" 0 1"` before handing a
  line to `Fen.importFEN`.
- The `c9 "..."` tag is the game result from **White's** point of view:
  `"1-0"` → `1.0`, `"1/2-1/2"` → `0.5`, `"0-1"` → `0.0`. This matches the
  White-POV centipawn convention of `WeightingFunction.calculate`, so the label
  can be used directly.

Label distribution in v7: 559,762 `1-0`, 346,981 `1/2-1/2`, 521,257 `0-1`.
