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

These are the externally-anchored engines currently built and verified on
this machine. Each lives under a self-describing directory in `engines/`
that carries the engine's exact version **and** its approximate CCRL Blitz
rating, e.g. `engines/pulse-1.7.3-elo1505/`. Java engines need only
`java -jar`; the C/C++/Rust engines are compiled from source (build patches
noted in the install section below).

| Engine          | CCRL Blitz | Proto  | Wrapper                                              |
|-----------------|-----------:|--------|------------------------------------------------------|
| Pulse 1.7.3     |    1505    | UCI    | `engines/pulse-1.7.3-elo1505/pulse.sh`               |
| TSCP 1.81       |    1607    | xboard | `engines/tscp-1.81-elo1607/tscp.sh`                  |
| Zeta Dva 0402   |   ~1801    | xboard | `engines/ZetaDva-0402-elo1801/zetadva.sh`            |
| Kojiro 0.1.4    |    1984    | UCI    | `engines/Kojiro-0.1.4-elo1984/kojiro.sh`             |
| Princhess 0.7.0 |    1985    | UCI    | `engines/princhess-0.7.0-elo1985/princhess.sh`       |

Lower-rated anchors from the original (v3.1.x) bracket are still on disk:
`PurplePanda-14-elo1445`, `DoctorB-1.2.1-elo1326`, `Zagreus-5.0-elo1414`,
and the human-calibrated `maia-1900-elo1900` (Lc0 network, 1 node/move).

> **Version trap — always verify the *exact* version's CCRL entry.** An
> engine's rating can swing by *hundreds* of Elo across its own release
> history, so grabbing "latest" is a reliable way to pick an anchor that is
> useless for our strength band. Concrete cases hit during setup:
> Kojiro **0.1.4 = 1984** but **0.1.3 = 2033**; Princhess **0.7.0 = 1985**
> but **0.21 = 3329**; Stash **35 = 3347** (not the ~2400 an earlier draft
> of this doc assumed); Bagatur is now only distributed as v5.x (~2900+),
> the old 1.7e download is gone. Pin the version in the directory name — and
> the rating alongside it — and confirm that exact build on the CCRL list
> before trusting the number.

For the current myChess strength (~1795, post-v4.3.0) the useful bracket is
Pulse/TSCP below, Zeta Dva just above, and Kojiro/Princhess as the ~1985
ceiling. The first match should go against an anchor within ~150 Elo of the
estimate — close enough to expect a balanced result, decisive enough that a
clear win/loss narrows the range.

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

### TSCP 1.81 (1607)

```sh
cd /tmp
curl -O http://www.tckerrigan.com/Chess/TSCP/tscp181.zip
unzip tscp181.zip -d tscp181
# tscp.c is the single-file engine; compile with any C compiler
clang -O2 -o tscp181/tscp tscp181/*.c
mkdir -p /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/tscp-1.81-elo1607
mv tscp181 /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/tscp-1.81-elo1607/
```

TSCP speaks the older xboard/winboard protocol natively, not UCI.
Use cutechess's protocol bridge: `proto=xboard` instead of `proto=uci`
in the cutechess command.

### Pulse 1.7.3 (1505)

```sh
# Download the latest release from https://github.com/fluxroot/pulse/releases
# (current version at the time of writing: 1.7.3 — adjust the filename if a
# newer release is available).
cd /tmp
curl -LO https://github.com/fluxroot/pulse/releases/download/v1.7.3/pulse-java-1.7.3.zip
unzip pulse-java-1.7.3.zip
mkdir -p /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse-1.7.3-elo1505
mv pulse-java-1.7.3/* /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse-1.7.3-elo1505/
```

Wrap in a shell script `engines/pulse-1.7.3-elo1505/pulse.sh`:

```sh
#!/bin/sh
exec java -jar /Users/mf/_PRIVAT_/New-Stuff/myChess/engines/pulse-1.7.3-elo1505/pulse-java-1.7.3.jar
```

`chmod +x engines/pulse-1.7.3-elo1505/pulse.sh`. Pulse is native UCI, use
`proto=uci`. The filename inside the JAR archive carries the release
version — adjust the script when you upgrade Pulse.

### Not used: Stash 35 and Bagatur 1.7e — too strong / unavailable

An earlier draft of this doc listed **Stash 35** and **Bagatur 1.7e** at
~2400 as upper anchors. Both entries were wrong:

- **Stash 35 = ~3347 CCRL Blitz** — grandmaster-class, hundreds of Elo
  above anything myChess will reach in this project. Useless as an anchor
  (every game a loss carries no rating information).
- **Bagatur 1.7e** is no longer distributed; the old download URL is dead
  and only the v5.x line (~2900+) remains — again far too strong.

Neither is installed. The upper end of the bracket is instead covered by
Zeta Dva, Kojiro, and Princhess below.

### Zeta Dva 0402 (~1801)

Source build (GitLab). Speaks xboard, not UCI. The macOS toolchain has no
full-static libc, so `--static` must be dropped from the compiler flags.

```sh
cd /Users/mf/_PRIVAT_/New-Stuff/myChess/engines
git clone https://gitlab.com/smatovic/ZetaDva.git ZetaDva-0402-elo1801
cd ZetaDva-0402-elo1801
git checkout v0402
cd src
# remove the -static / --static flag from CFLAGS in the Makefile (macOS), then:
make
```

Wrap in `engines/ZetaDva-0402-elo1801/zetadva.sh` (`cd` into `src`, then
`exec ./zetadva`). Use `proto=xboard`.

### Kojiro 0.1.4 (1984)

Source build (GitHub). Native UCI. GNU-ld's `--whole-archive` is not
supported by the macOS linker, so the makefile's `LDFLAGS` must be reduced
to `-lpthread -lm`.

```sh
cd /Users/mf/_PRIVAT_/New-Stuff/myChess/engines
git clone https://github.com/Babak-SSH/Kojiro.git Kojiro-0.1.4-elo1984
cd Kojiro-0.1.4-elo1984
git checkout 0.1.4
cd src
# patch LDFLAGS: replace the `--whole-archive ... --no-whole-archive` block
# with `-lpthread -lm`, then:
make
```

Wrap in `engines/Kojiro-0.1.4-elo1984/kojiro.sh` (`exec .../src/kojiro`).
Use `proto=uci`. Note: Kojiro emits cosmetic "Illegal PV move" warnings on
stderr; the games themselves are legal.

### Princhess 0.7.0 (1985)

Source build (GitHub, Rust). Native UCI. Requires the Rust toolchain
(`brew install rust`, which provides `cargo` — Rust's Maven-equivalent).

```sh
cd /Users/mf/_PRIVAT_/New-Stuff/myChess/engines
git clone https://github.com/princesslana/princhess.git princhess-0.7.0-elo1985
cd princhess-0.7.0-elo1985
git checkout 0.7.0
cargo build --release   # binary at target/release/princhess
```

Wrap in `engines/princhess-0.7.0-elo1985/princhess.sh`
(`exec .../target/release/princhess`). Use `proto=uci`. Note: a
source build reports its UCI id as `Princhess 0.0.0-dev` (no release
version stamped in); the CCRL-relevant version is the checked-out git tag
`0.7.0`, which is why it lives in the directory name.

> **Note on the version numbers in this table.** The CCRL lists move
> over time as engines get updated. The ratings quoted here are
> snapshot values from late-2025. If you want the exact current value
> for the version you downloaded, search the engine name on
> [computerchess.org.uk/ccrl/4040](https://www.computerchess.org.uk/ccrl/4040/)
> or [/404](https://www.computerchess.org.uk/ccrl/404/) (the 40/15 list).

## Concrete recipe: 4-engine anchor-bracket measurement

This is the recipe that produced the canonical absolute-strength measurement of myChess so far. The original run (May-June 2026, against v3.1.x) produced **myChess ≈ 1422.7 ± 17.5 Ordo-Elo** with three CCRL Blitz anchors. The recipe is preserved here verbatim so that future re-measurements (after each milestone version) follow the same protocol and produce directly-comparable numbers.

The setup is **four independent 1-on-1 matches** against externally-anchored engines, then Ordo combines them into a single Elo estimate with proper inter-engine cross-comparison. *Not* a single cutechess gauntlet — Ordo handles the cross-engine math better when each pairing is a separate match.

### Engines and anchors

Current CCRL Blitz values (verified live on [computerchess.org.uk/404](https://computerchess.org.uk/404/rating_list_all.html) before each run):

| Engine | CCRL Blitz | Rolle | Wrapper |
|---|---|---|---|
| Pulse 1.7.3 | 1505 | Anker (mid-range) | `./engines/pulse-1.7.3-elo1505/pulse.sh` (UCI) |
| TSCP 1.81 | 1607 | Anker (upper) | `./engines/tscp-1.81-elo1607/tscp.sh` (xboard) |
| PurplePanda 14 | 1445 | Anker (close-to-myChess) | `./engines/PurplePanda-14-elo1445/purplepanda.sh` (UCI) |
| DoctorB 1.2.1 | 1326 (nominal) | **frei** — Ordo schätzt selbst | `./engines/DoctorB-1.2.1-elo1326/doctorb.sh` (UCI) |

**Why DoctorB is intentionally not anchored.** The 1326 CCRL nominal rating reflects a different port / build of DoctorB. The version that runs reliably on the macOS-build path measures ~145 Elo weaker than the CCRL number in self-play vs the others — most plausibly a port artifact, not a real strength claim. Leaving DoctorB free in Ordo turns this into a sanity check: if Ordo computes DoctorB at ~1180, the port artifact is confirmed and the other three anchors are doing the actual work.

### Cutechess invocations (one per opponent)

Replace `4.0.1` in the engine path with whichever `versions/<X>/` directory holds the myChess build being measured. Use the new `match-<slug>` naming convention (these are precision-estimate matches, not SPRT — see the [sprt-cutechess-template](../../.claude/projects/-Users-mf--PRIVAT--New-Stuff-myChess/memory/reference_sprt_cutechess_template.md) memory for the SPRT/fixed-N distinction).

```bash
# === Match A: myChess vs Pulse ===
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess cmd=./versions/4.0.1/mychess-uci.sh proto=uci \
    -engine name=Pulse   cmd=./engines/pulse-1.7.3-elo1505/pulse.sh        proto=uci \
    -each tc=40/120 \
    -rounds 200 -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=random plies=8 \
    -concurrency 4 -ratinginterval 20 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout test-results/match-mychess-vs-pulse.pgn \
    | tee test-results/match-mychess-vs-pulse-stdout.log

# === Match B: myChess vs TSCP (xboard, not uci) ===
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess cmd=./versions/4.0.1/mychess-uci.sh proto=uci \
    -engine name=TSCP    cmd=./engines/tscp-1.81-elo1607/tscp.sh          proto=xboard \
    -each tc=40/120 \
    -rounds 200 -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=random plies=8 \
    -concurrency 4 -ratinginterval 20 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout test-results/match-mychess-vs-tscp.pgn \
    | tee test-results/match-mychess-vs-tscp-stdout.log

# === Match C: myChess vs DoctorB ===
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess cmd=./versions/4.0.1/mychess-uci.sh proto=uci \
    -engine name=DoctorB cmd=./engines/DoctorB-1.2.1-elo1326/doctorb.sh    proto=uci \
    -each tc=40/120 \
    -rounds 200 -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=random plies=8 \
    -concurrency 4 -ratinginterval 20 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout test-results/match-mychess-vs-doctorb.pgn \
    | tee test-results/match-mychess-vs-doctorb-stdout.log

# === Match D: myChess vs PurplePanda ===
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess     cmd=./versions/4.0.1/mychess-uci.sh         proto=uci \
    -engine name=PurplePanda cmd=./engines/PurplePanda-14-elo1445/purplepanda.sh    proto=uci \
    -each tc=40/120 \
    -rounds 200 -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=random plies=8 \
    -concurrency 4 -ratinginterval 20 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout test-results/match-mychess-vs-purplepanda.pgn \
    | tee test-results/match-mychess-vs-purplepanda-stdout.log
```

**Total wall-clock:** Each match runs ~400 games at TC 40/120 with `concurrency=4` — roughly **3-4 hours per match**, **12-16 hours for all four matches**. Run them sequentially (no two matches in parallel, because each already pegs all four cores). PurplePanda historically had stalling issues that required a separate `tools/clean-pgn.sh` pass — see the `cleaned.pgn` variants in `test-results/` for the original run.

### Parameter rationale

- **TC 40/120**: 40 moves in 120 seconds = ~3 sec/move. Faster than CCRL Blitz (40/15 = 22.5 sec/move) but reaches the same strength tier; the ratio is what matters across engines, not the absolute TC. Re-running at CCRL-exact TC would burn ~8× more wall-clock and is not worth it for a relative anchor.
- **2moves_v2.pgn**: shared opening book, every position played twice with swapped colors → color bias cancels per-pairing.
- **score=40 for draw, score=600 for resign**: matches the current standard documented in the [sprt-cutechess-template](../../.claude/projects/-Users-mf--PRIVAT--New-Stuff-myChess/memory/reference_sprt_cutechess_template.md) memory. The original test12-15 runs (2026-05) used `score=20` for draw — slightly more games went to natural conclusion, slightly fewer adjudications. The current `score=40` produces a higher draw-adjudication rate and shorter games on average; both are valid, but stick with one across the whole bracket so the four matches are mutually comparable.
- **`-recover`**: critical — if one engine misbehaves on a single game (PurplePanda stalls, DoctorB crashes), the match continues instead of aborting.
- **No `-sprt`**: this is a precision-estimate match, not a hypothesis test. Run the full 400-game budget per match.

### Ordo combination step

After all four PGNs are produced:

```bash
# 1. Concatenate all four anchor matches into one PGN stream
cat test-results/match-mychess-vs-pulse.pgn \
    test-results/match-mychess-vs-tscp.pgn \
    test-results/match-mychess-vs-doctorb.pgn \
    test-results/match-mychess-vs-purplepanda.pgn \
    > /tmp/bracket.pgn

# 2. Anchor file: fixed CCRL Blitz ratings for the three reliable engines.
#    DoctorB is intentionally NOT anchored — Ordo computes its implied
#    strength from the match, and a substantial gap to the CCRL nominal
#    confirms the port artifact.
cat > /tmp/anchors.csv <<'EOF'
"Pulse",1505
"TSCP",1607
"PurplePanda",1445
EOF

# 3. Run Ordo
/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo \
    -p /tmp/bracket.pgn \
    -m /tmp/anchors.csv \
    -o test-results/ordo-bracket.txt \
    -c test-results/ordo-bracket.csv
```

The `-m anchors.csv` switch forces Ordo to treat the listed engines as having their stated rating (zero uncertainty). If you want each anchor to carry its own CCRL CI, use `-y anchors-with-error.csv` instead and provide a third column with the per-anchor σ — Ordo then propagates the uncertainty through to myChess.

### Reading the output

`ordo-bracket.txt` looks like this (from the May-2026 run, against v3.1.x):

```
   # PLAYER         :  RATING  ERROR  POINTS  PLAYED   (%)
   1 TSCP           :  1607.0   ----   305.5     397    77
   2 Pulse          :  1505.0   ----   236.5     400    59
   3 myChess        :  1422.7   17.5   835.0    1573    53
   4 PurplePanda    :  1281.8   32.0   116.0     376    31
   5 DoctorB        :  1180.5   33.8    80.0     400    20
```

- Anchored rows show `----` for ERROR (zero by construction).
- myChess ERROR (17.5) is Ordo's posterior σ on the rating, given all four matches. This is much tighter than any single-anchor delta would give, because the bracket triangulates from three independent anchors.
- DoctorB at 1180 vs nominal CCRL 1326 = the port artifact mentioned above. The 145-Elo gap is the empirical evidence; without leaving DoctorB free, this would have been invisible.
- PurplePanda at 1282 vs nominal CCRL 1445 hints at a similar (smaller) artifact, but PurplePanda was kept anchored anyway because the alternative (only 2 anchors) tightens the bracket too narrowly around Pulse-TSCP. Keep an eye on this in future runs.

### When to re-run this bracket

After every milestone version where the cumulative delta against the last-measured baseline is **≥ 50 Elo**. Concretely:

- After v3.1.x was measured (May 2026 → 1422.7), the next legitimate re-measurement is after v4.0.x lands (cumulative +~120 Elo measured between 3.1.x and 4.0.x). The expected Ordo result at 4.0.x is **~1543 ± 18 Elo**, give or take CCRL-list drift on the three anchors.
- For smaller per-version deltas (<50 Elo), trust the per-version SPRT/match deltas in [version-history.md](version-history.md) instead — Ordo re-measurement would cost ~12-16 hours for a result within the existing error bar.

### Cross-references

- The original run's notes (CCRL bracket table, decision rationale for which anchors to fix) live in `myChess-notes.txt` lines ~99-198. The recipe above is the canonical version going forward.
- The [version-history.md](version-history.md) absolute-Elo column carries forward from this measurement via per-version SPRT deltas. A fresh bracket re-anchors that column.
- The methodology context (why CCRL anchors, why ~1500 Elo is the right tier for myChess) is the earlier sections of this document, especially [§ Approach](#approach-gauntlet-against-honest-engines) and [§ Recommended opponent shortlist](#recommended-opponent-shortlist) — note that the CCRL numbers in the shortlist there were sourced before the live-list re-verification of May 2026, and the values used in this recipe (Pulse 1505, TSCP 1607, PurplePanda 1445) are the verified current ones.

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
