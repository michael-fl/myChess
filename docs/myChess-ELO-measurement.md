# Absolute ELO Measurement for myChess

Working document for getting a defensible absolute ELO estimate for
myChess that can be compared, with the usual caveats, to the FIDE /
human rating scale. Companion to [elo-testing.md](elo-testing.md),
which covers cutechess-cli mechanics; this file covers *which
opponent to pick and what the resulting number actually means*.

## Why this document exists

Tests 06–09 all used Stockfish with `UCI_LimitStrength=true` +
`UCI_Elo=…` as the calibration partner. That approach has limitations
that are not obvious from the cutechess output alone:

- myChess beat SF-1700-skill 9-0 (test 08).
- myChess beat SF-1900-skill 9-0 (test 09).

The 9-0 result against SF-1900 is real in the sense that all games
were played out and won; the win count is not the result of an
adjudication bug or sign error (those were both fixed in earlier
phases). But interpreting the score as "myChess > 1900 FIDE" is
unsafe. Stockfish's strength-limiter is a **skill-emulation mode**:
the engine still searches to full depth (typically 28-32 plies in
test 09) and reports an accurate evaluation, then deliberately
chooses a less-than-best move from the top-N candidates to land the
chosen strength target. The noise model is calibrated against
**human** opponents at the target rating, not against engines.

Concrete symptom in test 09 Round 1 Game 2 (Caro-Kann after book):

```
3. Nc3 d5  4. Qe2 d4  5. Nb1 e5  6. d3 Be6  7. f4 Bb4+  8. Kd1
```

SF plays `Nb1` (knight retreats to its starting square), `f4`
(weakens the diagonal already exposed by the early queen), and
accepts `Kd1` instead of the obvious `Bd2`. These are not
1900-typical human mistakes — they are deliberate "noise" moves from
the skill model. A real 1900-rated human would have played `Bd2`.
A tactically solid engine like myChess punishes these synthetic
blunders systematically, far better than the average human 1900
would.

Conclusion: SF-UCI_Elo is fine for **relative** A/B measurements (is
a patch better than before?) but **not suitable** for absolute ELO
estimation. For absolute ELO we need opponents that play their
nominal strength honestly — i.e. they always pick the move their
search returned, no synthetic noise.

## How CCRL ratings relate to FIDE / human ratings

[CCRL](https://www.computerchess.org.uk/ccrl/) (Computer Chess Rating
Lists) is the standard reference for engine strengths. CCRL ratings
come from large engine-vs-engine round-robins, separate from FIDE,
but the two scales are roughly aligned by historical anchor matches:

- A **CCRL 2000** engine plays at approximately the strength of a
  FIDE 2000 club player.
- The alignment is loose, ±100 ELO. Engines tend to be relatively
  stronger tactically and relatively weaker positionally / strategically
  than humans at the same nominal rating, so a CCRL-2200 engine might
  beat a FIDE-2200 human in tactical positions and lose in endgames.
  For a "Gefühl" rating these differences average out.

For this document's purposes, treat CCRL ELO ≈ FIDE ELO with a
±100 grain of salt.

Two CCRL lists exist with slightly different time controls. We use
the **CCRL 40/15** list as the reference (40 moves in 15 minutes,
close to the `tc=40/1200` setup myChess has been using). The 40/40
list rates engines a hair lower; the Blitz list a hair higher.

## Approach: gauntlet against honest engines

Bisect through the CCRL ladder with one match per step until you find
an opponent where myChess scores roughly 50%. Three matches typically
suffice for a ±50 ELO estimate.

The mathematical conversion from win rate `p` to ELO difference is

```
Δ = -400 * log10(1/p - 1)
```

Examples: `p=0.50 → Δ=0`, `p=0.60 → Δ≈+72`, `p=0.75 → Δ≈+190`,
`p=0.90 → Δ≈+382`, `p=0.10 → Δ≈-382`. cutechess prints this number
directly as `Elo difference: <Δ> +/- <margin>` at the end of every
match — no manual calculation needed.

myChess ELO = opponent's CCRL rating + Δ.

## Recommended opponent shortlist

All four are free, UCI-compliant, and run on macOS. Java-based options
need only `java -jar` and integrate trivially with cutechess. C/C++
binaries are available for macOS from their release pages (or compile
from source in seconds).

| Engine             | CCRL 40/15 | Language | macOS install                          |
|--------------------|-----------:|----------|----------------------------------------|
| TSCP 1.81          |    ~1750   | C        | compile from a single `.c` file        |
| Pulse 1.7          |    ~2000   | Java     | drop the JAR in, wrap in shell script  |
| Stash 35           |    ~2400   | C++      | Homebrew or pre-built binary           |
| Bagatur 1.7e       |    ~2400   | Java     | drop the JAR in, wrap in shell script  |

A pre-bisection guess for myChess based on tests 06–09: somewhere in
the **1900–2200** range. The first match should therefore go against
**Pulse 1.7** (~CCRL 2000) — close enough to expect a balanced result,
high enough that a clear win/loss narrows the range usefully.

## The three-step procedure

### Step 1 — first match against Pulse 1.7 (~2000)

Run cutechess against Pulse with the same SPRT setup that test 08 / 09
used. SPRT terminates after 30-80 games typically:

```sh
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess cmd=./mychess-uci.sh proto=uci \
    -engine name=Pulse-2000 cmd=./pulse-uci.sh proto=uci \
    -each tc=40/1200 \
    -rounds 40 -games 2 -repeat \
    -sprt elo0=-50 elo1=50 alpha=0.05 beta=0.05 \
    -openings file=2moves_v2.pgn format=pgn order=random plies=4 \
    -concurrency 2 -ratinginterval 2 \
    -recover \
    -draw movenumber=50 movecount=10 score=40 \
    -pgnout mychess-vs-pulse2000.pgn
```

Read off `Elo difference: <Δ> +/- <margin>` from the final line.
myChess ELO ≈ 2000 + Δ.

### Step 2 — decide next opponent

Based on Δ from step 1:

| Outcome              | Δ range    | Next opponent                  |
|----------------------|-----------:|--------------------------------|
| Clear loss           |  < -100    | TSCP (~1750) — confirm lower bound |
| Edge of bound (loss) |  -100..-50 | Stop — myChess ≈ 1900 ± 50         |
| Balanced             |   -50..+50 | Stop — myChess ≈ 2000 ± 50         |
| Edge of bound (win)  |   +50..+150 | Stop — myChess ≈ 2100 ± 50         |
| Clear win            |  > +150    | Stash or Bagatur (~2400) — narrow upper bound |

A "Stop" row is a good-enough estimate for a Gefühl-rating. The other
two rows need a second match to nail the bound on the side that
swung wide.

### Step 3 — final estimate

After at most one follow-up match:

- Average the implied myChess ELO from both matches (`opponent + Δ`).
- The uncertainty is roughly the `+/-` margin cutechess printed for
  each match, propagated by `sqrt(2)` if you take the simple average.

A typical outcome on this protocol: **"myChess ≈ CCRL/FIDE 2050 ± 60"**.
Translated to the human scale: a solid club player, in the rough range
of a German club-Klasse-Spieler with vocally underrated FIDE — not yet
NM/FM strength, but more than a casual hobbyist.

## Installation instructions

All commands assume the myChess repo as the working directory.

### TSCP 1.81 (~1750)

```sh
cd /tmp
curl -O http://www.tckerrigan.com/Chess/TSCP/tscp181.zip
unzip tscp181.zip -d tscp
cd tscp
# tscp.c is the single-file engine; compile with any C compiler
clang -O2 -o tscp main.c board.c book.c data.c eval.c search.c
mv tscp /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/
```

TSCP speaks the older xboard/winboard protocol natively, not UCI.
Use cutechess's protocol bridge: `proto=xboard` instead of `proto=uci`
in the cutechess command.

### Pulse 1.7 (~2000)

```sh
# Download the latest release from https://github.com/fluxroot/pulse/releases
# (current version at the time of writing: 1.7.3 — adjust the filename if a
# newer release is available).
cd /tmp
curl -LO https://github.com/fluxroot/pulse/releases/download/v1.7.3/pulse-java-1.7.3.zip
unzip pulse-java-1.7.3.zip
mkdir -p /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse
mv pulse-java-1.7.3/* /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse/
```

Wrap in a shell script `engines/pulse.sh`:

```sh
#!/bin/sh
exec java -jar /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse/pulse-java-1.7.3.jar
```

`chmod +x engines/pulse.sh`. Pulse is native UCI, use `proto=uci`. The
filename inside the JAR archive carries the release version — adjust
the script when you upgrade Pulse.

### Stash 35 (~2400)

```sh
# Homebrew: most reliable on macOS
brew tap mhouppin/stash
brew install stash

# Or download a pre-built macOS binary from
#   https://gitlab.com/mhouppin/stash-bot/-/releases
# and place it at engines/stash
```

Native UCI, `cmd=stash` (if installed via brew) or `cmd=./engines/stash`.

### Bagatur 1.7e (~2400)

```sh
cd /tmp
curl -LO https://github.com/bagaturchess/Bagatur/releases/download/1.7e/bagatur_engine_1.7e.zip
unzip bagatur_engine_1.7e.zip -d /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/bagatur
```

Wrap in `engines/bagatur-uci.sh`:

```sh
#!/bin/sh
cd /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/bagatur
exec java -jar bagatur-1.7e.jar
```

`chmod +x`. Native UCI.

> **Note on the version numbers in this table.** The CCRL lists move
> over time as engines get updated. The ratings quoted here are
> snapshot values from late-2025. If you want the exact current value
> for the version you downloaded, search the engine name on
> [computerchess.org.uk/ccrl/4040](https://www.computerchess.org.uk/ccrl/4040/)
> or [/404](https://www.computerchess.org.uk/ccrl/404/) (the 40/15 list).

## Notes on time control

The CCRL 40/15 list uses **40 moves in 15 minutes** as its time control.
The `tc=40/1200` used in test 08/09 is 40 moves in **20 minutes** —
slightly more generous, slightly favors the side with better search
scaling. Net effect on the ELO estimate: probably +20-40 ELO for
myChess relative to what a strict 40/15 match would show. For
"Gefühl" purposes this is well within the ±50-100 noise floor.

If you want a tighter match to the CCRL methodology, change
`tc=40/1200` to `tc=40/900` (40 moves in 15 minutes). The matches
will run faster and the resulting number is more directly comparable
to CCRL's quoted ratings.

## Re-running rhythm

An absolute ELO measurement is **not** something you re-run after every
patch. Self-play A/B (see [elo-testing.md](elo-testing.md)) is the
right tool for that.

Re-measure the absolute ELO when:

- A milestone is reached (new feature lands, major version bump,
  before/after a refactor that touches search or eval semantics).
- The Gefühl number from before is more than 6 months old.
- You want to ground a public claim ("myChess plays at ELO X") in a
  current dataset.

Otherwise leave the current estimate in place — it does not drift on
its own, and burning a couple of CPU-hours per absolute measurement
is not in proportion to a +5 ELO patch.

## Caveats

- **Opening book matters.** Both engines play the same opening lines
  via `-openings file=… -repeat`. If the book is structurally
  unbalanced (as `2moves_v2.pgn` is — test 07 showed 84% white wins
  there), individual matches will be noisier per game, but the
  ELO-difference estimate is largely unaffected because each
  opening is played twice with swapped colors. A more balanced book
  (e.g. `8moves_v3.pgn` or `4moves_noob.pgn`) reduces game variance
  and shortens the SPRT, but is not strictly required.
- **CCRL ratings can drift** as engines get re-rated by ongoing
  tournaments. The shortlist's quoted ratings should be re-checked
  on the CCRL site before a measurement run that goes into a
  permanent record.
- **Hardware matters for absolute comparison.** CCRL runs on
  standardized hardware; macOS dev laptops are not standardized.
  Effect is usually small (<50 ELO) because the time control is
  *moves-per-time*, not nodes — both engines see the same wall-clock
  budget. But if your laptop is much slower than the CCRL test
  machine, both engines lose absolute strength equally, and the
  *difference* still measures correctly.
