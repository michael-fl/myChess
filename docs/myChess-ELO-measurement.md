# Absolute Elo Measurement for myChess

How to get a defensible **absolute** Elo estimate for myChess — a number on
the CCRL/FIDE scale, not just a relative "is this patch better?" delta.

- **Relative** A/B testing (self-play SPRT) is covered by
  [elo-testing.md](elo-testing.md), which carries the canonical cutechess command
  and the project's conventions (candidate engine first, `elo0=-3 elo1=15`,
  `-ratinginterval 10`) — that is the everyday tool for measuring a change.
- **This** document is the occasional, expensive measurement that re-anchors
  the whole [version-history.md](version-history.md) absolute-Elo column to
  reality: play myChess against externally-rated engines and let Ordo place it
  on the shared scale.

The executable form of everything below lives in
[`tools/run-anchor-bracket.sh`](../tools/run-anchor-bracket.sh) — one command
runs the whole bracket unattended.

---

## 1. Why not Stockfish's strength limiter

The obvious idea — play Stockfish with `UCI_LimitStrength=true` + `UCI_Elo=…` —
does **not** give a trustworthy absolute number. SF's limiter is a
**skill-emulation** mode: it still searches to full depth and evaluates
accurately, then deliberately picks a less-than-best move to hit the target
rating. Its noise model is calibrated against **humans**, not engines.

The symptom: myChess beat SF-1900-skill 9-0 (test 09) — all games genuinely
played out and won, no adjudication bug. But SF-1900-skill plays *synthetic*
1900-style blunders (e.g. retreating a knight to its start square, walking the
king to d1 instead of the obvious `Bd2`) that a tactically solid engine
punishes far more reliably than a real 1900 human would. So the 9-0 says
nothing safe about "myChess vs FIDE 1900".

**Conclusion:** SF-skill is fine for relative A/B, useless for absolute Elo. For
absolute Elo we need opponents that always play the move their search returned —
**honest engines with a known CCRL rating.**

---

## 2. Method — honest anchors combined by Ordo

The setup is **N independent 1-on-1 matches**, myChess against each anchor
engine, then **[Ordo](https://github.com/michiguel/Ordo)** combines the results
into one Elo estimate. *Not* a single cutechess gauntlet — Ordo does the
cross-engine maximum-likelihood math better when each pairing is its own match.

- Most anchors are **pinned** to their published CCRL rating (`-m anchors.csv`);
  myChess's rating is then whatever best fits its scores against those fixed
  points. Triangulating from several anchors gives a much tighter interval than
  any single match.
- At least one engine is left **free** (not pinned) as a sanity check: if Ordo
  computes its rating far from its nominal CCRL value, that engine's build/port
  is off — and leaving it free means it did not corrupt the myChess number.

**Intuition (Ordo does the real work).** A single match's win rate `p` maps to
an Elo difference by `Δ = −400·log10(1/p − 1)` — cutechess prints this as
`Elo difference: <Δ> ± <margin>`. So `myChess ≈ anchor_rating + Δ`. Ordo
generalizes this across all matches at once.

**CCRL ≈ FIDE, ± ~100.** CCRL ratings come from engine-vs-engine round-robins;
they align loosely with FIDE (a CCRL-2000 engine ≈ a FIDE-2000 club player),
but engines are relatively stronger tactically and weaker strategically at the
same number. Treat the result as a "Gefühl" rating, not a precise FIDE claim.
We anchor on the **CCRL Blitz** list (that is how the chosen engines are rated
below).

---

## 3. The anchor set

myChess **measures 1928 ± 21** as of v4.4.1 (direct, 2026-08-17 — see § 7; the
previous direct measurement was 1422.7 at v3.1.x). The anchors are chosen to
**bracket** that value, with anchors close to it (closest to 50 % → most
information) and anchors clearly below and above. The set below did exactly that:
myChess beat the two lower anchors and lost to the three upper ones.

Ratings below were read off the CCRL Blitz list on **2026-08-16**, with the sample
size that backs each one. **Quote the date**: the list is revised continuously —
TSCP moved 1607 → 1609 between May and August 2026.

| Engine | CCRL Blitz | Sample | Role | Proto | Wrapper |
|---|---:|---|---|---|---|
| **TSCP 1.81** | 1609 | ±19, 1067 games | anchor — **lower** (fixed) | xboard | `engines/tscp-1.81-elo1609/tscp.sh` |
| **Zeta Dva 0402** | ~1801 | ±52, **114 games** | anchor — **near** (fixed), see caveat | xboard | `engines/ZetaDva-0402-unrated/zetadva.sh` |
| **Princhess 0.7.0** | 1985 | ±18, 1202 games | anchor — **upper** (fixed) | UCI | `engines/princhess-0.7.0-elo1985/princhess.sh` |
| **BBC 1.1** | 2019 | ±17, 1243 games | anchor — **upper** (fixed), best-sampled of the set | UCI | `engines/BBC-1.1-elo2019/bbc.sh` |
| **Kojiro 0.1.4** | (1984) | ±85, **40 games** | **free** — Ordo estimates it; cross-checks the upper end | UCI | `engines/Kojiro-0.1.4-elo1984/kojiro.sh` |

**The sample size decides whether an anchor may be pinned.** CCRL publishes a
rating from 100 games upward and warns that "early ratings may fluctuate", so a
listed number is not automatically a usable anchor:

- **Kojiro 0.1.4 rests on 40 games with ±85** — below CCRL's own publication
  threshold. Leaving it free was right for a second reason too: its neighbouring
  version rates *higher* (0.1.3 = 2033), and it emits "Illegal PV move" warnings
  by the thousand. Exactly the engine not to pin. On the v4.4.1 bracket Ordo
  placed it at 2003.9 ± 35.3 (all four anchors pinned) and 1999.5 ± 37.3 (only the
  well-sampled three pinned) against its listed 1984 — 15 to 20 Elo, inside its own
  ±35, and the cross-check the free slot exists for.
- **Zeta Dva is the open caveat.** The 1801 belongs to version **0310** and rests
  on 114 games with ±52; the build in this repo is **0402**, which the list does
  not carry at all. Its predecessor 0303 rates 1899 — 98 Elo *above* 0310 on
  seven times the games, which is the signature of a noisy number rather than a
  real regression. Freeing it as well moves the myChess estimate by only ~4 Elo
  (1930.8 → 1926.5) and places Zeta Dva itself at 1785 rather than 1801. That the
  effect shrank from ~10 Elo on four engines to ~4 on five is what the fifth anchor
  bought: the bracket is stiff enough that one thin value barely shows.

**A version's rating never transfers to a neighbouring version**, in either
direction: Kojiro 0.1.3 = 2033 vs 0.1.4 = 1984, Princhess 0.7.0 = 1985 vs
0.21 = 3327, Zeta Dva 0303 = 1899 vs 0310 = 1801, BBC 1.1 = 2019 vs 1.2 = 1945.
Build the exact tag, and verify what the engine reports about itself.

**Every UCI anchor needs an explicit `option.Hash`** — see
[§ 5 `option.Hash`](#optionhash--the-parameter-that-invalidated-a-whole-match).
Skipping it invalidated a whole 400-game match once.

**Optional further anchor:** **Pulse 1.7.3 = 1505** (`engines/pulse-1.7.3-elo1505/pulse.sh`,
UCI) as a firmer floor — adds one more gauntlet (~an extra night).

> **Version trap — always verify the *exact* version's CCRL entry before a run.**
> A rating can swing hundreds of Elo across an engine's release history, so
> "latest" is a reliable way to pick a useless anchor. Cases hit during setup:
> Kojiro 0.1.3 = 2033 vs 0.1.4 = 1984; Princhess 0.7.0 = 1985 vs 0.21 = 3327;
> Stash 35 = 3347 (not the ~2400 an old draft assumed); Bagatur 1.7e is gone
> (only v5.x ~2900+ remains). Both Stash and Bagatur are far too strong and are
> **not used**. Pin the version *and* its rating in the directory name and
> re-confirm on [computerchess.org.uk/404](https://www.computerchess.org.uk/ccrl/404/)
> before a run that goes into the record.

Lower-rated engines from the original v3.1.x bracket are still on disk but **not
used** for the current band: `PurplePanda-14-elo1445`, `DoctorB-1.2.1-elo1326`,
`Zagreus-5.0-elo1414`, plus the human-calibrated `maia-1900-elo1900` (an Lc0
network at 1 node/move — a "human-feel" curiosity, *not* a CCRL anchor).

---

## 4. Building the anchors

All engines live under `engines/<name>-<version>-elo<rating>/` (git-ignored).
**The rating in the path is always the externally verifiable CCRL value, never one
Ordo estimated** — a self-derived number in a directory name invites exactly the
circular pinning § 5 warns against. Builds the list does not carry are marked
`-unrated` instead, as `ZetaDva-0402-unrated` is: the 1801 belongs to version 0310.
Renamed 2026-08-17; `tscp-1.81-elo1607` became `-elo1609` when the list moved.

**TSCP 1.81** (xboard) — single-file C:
```sh
cd /tmp && curl -O http://www.tckerrigan.com/Chess/TSCP/tscp181.zip
unzip tscp181.zip -d tscp181
clang -O2 -o tscp181/tscp tscp181/*.c
mkdir -p engines/tscp-1.81-elo1609 && mv tscp181 engines/tscp-1.81-elo1609/
```
Wrapper `tscp.sh`: `cd` into `tscp181`, `exec ./tscp`. Use `proto=xboard`.

**Zeta Dva 0402** (xboard) — GitLab source; drop `--static` from the Makefile
CFLAGS (no full-static libc on macOS):
```sh
cd engines && git clone https://gitlab.com/smatovic/ZetaDva.git ZetaDva-0402-unrated
cd ZetaDva-0402-unrated && git checkout v0402 && cd src && make   # after removing -static
```
Wrapper `zetadva.sh`: `cd` into `src`, `exec ./zetadva`. Use `proto=xboard`.

**Kojiro 0.1.4** (UCI) — GitHub source; reduce the makefile `LDFLAGS`
`--whole-archive … --no-whole-archive` block to `-lpthread -lm` (macOS ld):
```sh
cd engines && git clone https://github.com/Babak-SSH/Kojiro.git Kojiro-0.1.4-elo1984
cd Kojiro-0.1.4-elo1984 && git checkout 0.1.4 && cd src && make   # after patching LDFLAGS
```
Wrapper `kojiro.sh`: `exec …/src/kojiro`. Use `proto=uci`.

**Princhess 0.7.0** (UCI) — Rust (`brew install rust` provides `cargo`):
```sh
cd engines && git clone https://github.com/princesslana/princhess.git princhess-0.7.0-elo1985
cd princhess-0.7.0-elo1985 && git checkout 0.7.0 && cargo build --release
```
Wrapper `princhess.sh`: `exec …/target/release/princhess`. Use `proto=uci`.
Note: a source build reports its UCI id as `Princhess 0.0.0-dev`; the
CCRL-relevant version is the git tag `0.7.0` (hence the directory name).

**Pulse 1.7.3** (optional, UCI) — Java JAR from
[github.com/fluxroot/pulse/releases](https://github.com/fluxroot/pulse/releases):
drop the JAR in `engines/pulse-1.7.3-elo1505/` and wrap in
`exec java -jar …/pulse-java-1.7.3.jar`.

---

## 5. Running the bracket

### The easy way — the driver script

[`tools/run-anchor-bracket.sh`](../tools/run-anchor-bracket.sh) runs the four
matches **sequentially** (never in parallel — one match already pegs the cores,
and overlap distorts the time-based TC), then runs Ordo. It re-execs under
`caffeinate` so an overnight run does not sleep, and `--wait-for-cores` defers
the start until any running SPRT finishes.

```sh
nohup tools/run-anchor-bracket.sh <versions-subdir> --wait-for-cores \
      > test-results/anchor-driver-<versions-subdir>.log 2>&1 &
```

`<versions-subdir>` is the built myChess under `versions/` to measure (e.g.
`4.3.2`). Output: `test-results/match-<v>-vs-<engine>.pgn` per match and
`test-results/ordo-anchor-<v>.txt`/`.csv`. The script keeps TSCP/Zeta
Dva/Princhess pinned and Kojiro free (matching §3). **Laptop must stay plugged
in with the lid open** (caffeinate can't beat clamshell sleep).

### The manual form (what the script does)

Per anchor, one match — replace the myChess path and the anchor:

```sh
/Users/mf/_PRIVAT_/New-Stuff/cutechess/build/cutechess-cli \
    -engine name=myChess-4.3.2 cmd=./versions/4.3.2/mychess-uci.sh proto=uci \
    -engine name=TSCP          cmd=./engines/tscp-1.81-elo1609/tscp.sh proto=xboard \
    -each tc=40/120 \
    -rounds 200 -games 2 -repeat \
    -openings file=2moves_v2.pgn format=pgn order=random plies=8 \
    -concurrency 4 -ratinginterval 10 \
    -recover \
    -draw movenumber=40 movecount=8 score=40 \
    -resign movecount=4 score=600 \
    -pgnout test-results/match-4.3.2-vs-tscp.pgn \
    | tee test-results/match-4.3.2-vs-tscp-stdout.log
```

Then combine with Ordo — the fixed anchors go in `anchors.csv`, Kojiro does
**not** (so Ordo rates it freely):

```sh
cat test-results/match-4.3.2-vs-*.pgn > /tmp/bracket.pgn
cat > /tmp/anchors.csv <<'EOF'
"TSCP",1609
"ZetaDva",1801
"Princhess",1985
"BBC",2019
EOF
/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo \
    -W -D -s 1000 \
    -p /tmp/bracket.pgn -m /tmp/anchors.csv \
    -o test-results/ordo-anchor-4.3.2.txt -c test-results/ordo-anchor-4.3.2.csv
```

`-m` pins the listed engines with zero uncertainty. For per-anchor CCRL error
bars, use `-y anchors-with-error.csv` (a third σ column) so Ordo propagates the
anchor uncertainty into the myChess number.

#### The three switches that are not optional — `-W -D -s`

Ordo's defaults are silent placeholders, not measurements. Left off, the output
carries **no error margin at all**, and it reports `White advantage = 0.00` /
`Draw rate = 50.00 %` — which reads like a result and is merely the default.

| switch | what it does | why |
|---|---|---|
| `-s 1000` | 1000 simulations to derive error margins | without it there is **no** `ERROR` column, and any ± quoted alongside the number is guesswork |
| `-W` | fits the white advantage instead of assuming 0 | measured 29.3 ± 8.8 Elo over the 1600-game 4.4.1 bracket — real, and worth knowing |
| `-D` | fits the draw rate instead of assuming 50 % | actual rates ran 8.5 % (vs TSCP) to 24 % (vs Kojiro), pooled 17.7 % — the default was off by a factor of three |

**Measured on the v4.4.1 bracket (2026-08-17), the point estimate barely moves:**
1924.6 with the defaults against 1925.0 ± 20.3 with `-W -D -s 1000`. That is not
an argument for omitting them — it is the reason to use them without worrying
about comparability with older runs. Colour-balanced matches (`-games 2 -repeat`)
cancel the white advantage out of the ratings, and the draw rate acts mainly on
the simulation, so the switches buy the error bars almost for free.

What the pinned anchors do **not** carry is their own uncertainty: they print
`----` in the `ERROR` column because `-m` fixes them exactly. The CCRL sample
behind each one (±17 to ±19 for a well-measured engine, ±52 for a thin one) has
to be added on top by hand, or fed in through `-y` instead.

#### Never pin an engine at a rating Ordo itself estimated

Tempting after a finished run: the free engines now have numbers, so why not feed
them back as anchors and get a tighter result? Because it is circular, and the
tightening is fake.

Measured on the v4.4.1 bracket:

| | myChess | Kojiro |
|---|---|---|
| Kojiro **free** (as run) | 1925.0 ± 20.3 | 1998.5 ± 37.3 |
| Kojiro **pinned at its own 1998.5** | 1925.0 ± **17.0** | 1998.5 `----` |

The estimate does not move at all and the error margin shrinks by 3.3 Elo. No
uncertainty was removed — it was hidden. **An anchor has to carry information from
outside the tournament**, and Kojiro's 1998.5 was derived from the very 1600 games
being evaluated: it adds no bit that is not already in the PGN, it only removes a
degree of freedom.

That degree of freedom had a job. While Kojiro is free it can absorb disagreement
between the anchors — TSCP implies myChess ≈ 1865, Princhess ≈ 1931 — and the
residual spreads across everything unpinned. Pin Kojiro and the residual has to go
somewhere, and the only remaining candidate is the myChess estimate. That it did
not move here is luck, not method.

The worse failure is interpretive: re-running with the self-estimate pinned and
reading the unchanged 1925.0 as *confirmation* mistakes an **identity** for an
agreement. The number cannot come out differently — same data, plus an assertion
drawn from that same data.

And it destroys the one real cross-check the bracket provides. Kojiro's value is
worth something **because** it was free: Ordo said 1998.5 where CCRL lists 1984, a
14-Elo agreement that tests whether the whole scale holds. Pin it and nothing
checks the scale any more.

Zeta Dva is the same rule from the other side: there the **fixed** value is the
doubtful one (1801 for version 0310, 114 games, ±52 — the build here is 0402), so
the correct move is to **free it**, not to keep it. That costs 10 Elo on the
estimate (1925 → 1914) and replaces a poorly-supported assertion with a
measurement.

**The rule: pin exactly those anchors whose rating was well measured outside this
tournament** — here TSCP (±19, 1067 games), Princhess (±18, 1202) and BBC (±17,
1243) — **and leave everything else free.** Five engines, three anchors, two
touchstones.

### Parameter rationale

- **TC 40/120** (~3 s/move): faster than CCRL Blitz but the same strength tier —
  the *ratio* between engines is what matters, not the absolute TC. CCRL-exact TC
  would cost ~8× the wall-clock for no better anchor.
- **`-rounds 200 -games 2 -repeat`** = 400 games/match; every opening played
  twice with swapped colors so per-pairing color bias cancels. ~3–4.5 h/match,
  ~15 h for four → an overnight run. Combined Ordo error ≈ ±18 Elo.
- **`score=40` draw / `score=600` resign adjudication** — the current standard
  (see the SPRT-template memory). Keep one setting across the whole bracket so
  the matches are mutually comparable.
- **`-recover`** — one misbehaving game does not abort the match.
- **No `-sprt`** — this is a precision estimate, not a hypothesis test; run the
  full 400-game budget per match.
- **`plies=4`** for the opening suite — and note that `2moves_v2.pgn` carries
  exactly 4 plies per line, so cutechess takes `min(requested, available)` and any
  larger value is a no-op. The script read `plies=8` until 2026-08-17, which
  suggested an opening phase twice as deep as the one actually played.

### `option.Hash` — the parameter that invalidated a whole match

**Every UCI anchor gets an explicit `option.Hash`.** This is not tuning: CCRL's
published blitz conditions require *"the same value of either 128 or 256 MB for
all engines in a match or tourney"*, so it is part of the conditions the anchor
rating was established under. Engine defaults are not comparable to anything.

The cost of learning this, on 2026-08-16: the first Princhess match of the v4.4.1
bracket ran at Princhess's own default of **16 MB**. Princhess is an MCTS engine —
every tree node lives in the hash — so the table filled after ~9 600 nodes and the
search **stopped**: 0.07 s per move against myChess's 2.89 s, a factor of 40. The
match scored 86.4 % for myChess, implying ~2300 Elo. Re-run at 256 MB it scored
**42.2 %**. Same board, same engines, same time control: **a 310-Elo swing from one
setting.** Defaults seen so far: Princhess 16 MB, Kojiro **1 MB**, BBC 64 MB
(capped at 128, so BBC gets 128 and not 256).

**The check that catches this class of defect in a minute** — compare the per-move
times recorded in the PGN comments. Two engines at one time control belong within
a factor of ~2 of each other, never 40:

```sh
# mean seconds per move, per engine, from the {+0.60/9 1.5s} comments
grep -o '{[^}]*}' test-results/match-<v>-vs-<engine>.pgn | grep -oE '[0-9.]+s'
```

Run it after **every** match, before believing any score. On the finished v4.4.1
bracket all five engines sat between 1.46 s and 2.92 s against myChess's 2.89 s.

---

## 6. Optional: an old myChess version (progress bar + validation)

For fun and as a cross-check, add an **old myChess version** — currently
**v3.5.2** — as an extra participant. Run it **only against the same fixed
anchors** (its own gauntlet) and leave it **out of `anchors.csv`** so Ordo rates
it freely. This is **Variante A**.

**Do not let the old version play the current myChess directly.** With the two
disconnected, the current-myChess rating is fixed solely by *its own* games
against the pinned anchors, and the old version hangs off the same anchors as a
separate spoke — the two estimates **decouple, so the current number is not
influenced**. A direct old-vs-current edge (plus the correlation of
same-engine-family games) could shift/bias it, so skip that match.

Two payoffs, free of cost to the current number:

- **Progress bar** — v3.5.2 and the current version land on the *same absolute
  scale*, so you read the total gain since 3.5.2 directly.
- **Validation** — v3.5.2 sits at **~1441** (the v3.1.x-era level, the only
  version ever measured directly against anchors: 1422.7 in May 2026). Ordo
  reproducing ~1441 confirms the anchor calibration. To also validate the
  *propagation* chain (self-play Elo → absolute), add a big-jump milestone such
  as **v4.0.0** (TT, propagated ~1562) or **v4.1.0** (NMP, ~1663).

**Cost:** one extra gauntlet (old × N anchors) ≈ another night.
**Caveat:** confirm the old version still *builds* from its git tag with the
current toolchain and *runs cleanly* against one anchor before committing a full
gauntlet — v3.5.2 is the safer pick (its early bugs were fixed; v3.1.x is
buggier). The driver script currently measures only one myChess version; a
second participant means a second run (or a small script extension).

---

## 7. Reading the output & the historical baseline

`ordo-anchor-*.txt` lists every engine on the anchored scale, e.g. the
**May-2026 run against v3.1.x** (anchors then: Pulse/TSCP/PurplePanda + free
DoctorB — the older, lower bracket that fit myChess at the time):

```
   # PLAYER      :  RATING  ERROR  POINTS  PLAYED   (%)
   1 TSCP        :  1607.0   ----   305.5     397    77
   2 Pulse       :  1505.0   ----   236.5     400    59
   3 myChess     :  1422.7   17.5   835.0    1573    53
   4 PurplePanda :  1281.8   32.0   116.0     376    31
   5 DoctorB     :  1180.5   33.8    80.0     400    20
```

- Anchored rows show `----` for ERROR (zero by construction). The free engine
  (here DoctorB) gets a computed rating + σ.
- **myChess = 1422.7 ± 17.5** — the last *direct* absolute measurement. Ordo's σ
  is much tighter than any single-match delta because the bracket triangulates
  from several anchors.
- **DoctorB 1180 vs nominal 1326** = a ~145-Elo port artifact, made visible
  precisely *because* it was left free. This is the pattern to watch on the free
  slot (now Kojiro).

### The v4.4.1 re-anchor — measured 2026-08-17

The second direct measurement, and the one that closes the propagated chain.
**2000 games, five opponents, TC 40/120**, anchors read off the CCRL Blitz list on
2026-08-16:

| opponent | role | CCRL | sample | myChess score | Δ Elo |
|---|---|---:|---|---|---:|
| BBC 1.1 | anchor | 2019 | ±17 / 1243 | 113-197-90, 39.5 % | −74.1 ± 30.5 |
| Kojiro 0.1.4 | **free** | (1984) | ±85 / 40 | 112-192-96, 40.0 % | −70.4 ± 30.1 |
| Princhess 0.7.0 | anchor | 1985 | ±18 / 1202 | 139-201-60, 42.2 % | −54.3 ± 31.8 |
| Zeta Dva 0402 | anchor | 1801 | ±52 / 114 | 248-94-58, 69.3 % | +141.0 ± 33.7 |
| TSCP 1.81 | anchor | 1609 | ±19 / 1067 | 309-57-34, 81.5 % | +257.6 ± 41.0 |

```
   # PLAYER           :  RATING  ERROR  POINTS  PLAYED   (%)
   1 BBC              :  2019.0   ----   242.0     400    60
   2 Kojiro           :  2003.9   35.3   240.0     400    60
   3 Princhess        :  1985.0   ----   231.0     400    58
   4 myChess-4.4.1    :  1930.8   17.2  1090.0    2000    54
   5 ZetaDva          :  1801.0   ----   123.0     400    31
   6 TSCP             :  1609.0   ----    74.0     400    18

White advantage = 26.18 +/- 7.69
Draw rate (equal opponents) = 18.99 % +/- 0.95
```

**Result: myChess 4.4.1 ≈ 1928 ± 21 CCRL Blitz.** The two anchor choices bracket
it — 1930.8 ± 17.2 with all four fixed, 1926.5 ± 20.5 with only the well-sampled
three fixed and Zeta Dva freed as well. Four Elo apart, which is why the number is
quoted rounded. Add the CCRL uncertainty of the pinned anchors (±17 to ±19) on top,
and subtract nothing for the two deviations recorded in § 5: no endgame tablebases
(which makes our anchors slightly weaker than their rating, so the number errs
high) and TC 40/120 instead of CCRL's own 2'+1".

**The free slot did its job twice.** Kojiro, never pinned, came out at 2003.9
(variant A) / 1999.5 (variant B) against its listed 1984 — 15 to 20 Elo, inside its
own ±35. Zeta Dva, freed in variant B, came out at 1785.2 against the 1801 assumed
for it, which supports the suspicion that a 114-game rating for the wrong version
was reading slightly high. Neither discrepancy is the port artifact DoctorB showed
in May; the scale holds.

**TSCP is the outlier and was expected to be.** Its implied value for myChess is
~1867, against ~1929 to ~1945 from the other four. At 258 Elo of separation almost
every game is decisive (8.5 % draws) and the Elo scale compresses. Ordo weights it
accordingly rather than averaging it in — which is the entire reason for solving the
graph instead of computing per-anchor differences by hand.

**What the measurement settles.** Everything after v3.1.x in
[version-history.md](version-history.md)'s absolute column was **propagated** from
per-version SPRT deltas, and the docs put the accumulated uncertainty at ±40 while
warning that self-play gains are not expected to transfer one-for-one. The chain
predicted **~1915**; the measurement says **1928**. Over roughly 500 Elo and a dozen
versions the self-play deltas transferred almost exactly. That is a result about the
*method*, not just about this version, and it is the strongest argument the project
has for continuing to steer by self-play SPRTs between re-anchors.

---

## 8. When to re-run

An absolute measurement is **not** a per-patch tool (that is self-play SPRT).
Re-run the bracket when the cumulative delta against the last *measured* baseline
is **≥ ~50 Elo**, i.e. at milestones:

- After v3.1.x (1422.7, May 2026), the next legitimate re-measure is once the
  v4.x gains have accumulated well past +50 — which they have (propagated
  ~1795 at v4.3.1). So the **next real re-anchor is due** and should target the
  current v4.3.x.
- For smaller per-version deltas, trust the version-history SPRT deltas — a full
  bracket (~15 h) would land inside its own error bar.

The launch command is at the top of
[`tools/run-anchor-bracket.sh`](../tools/run-anchor-bracket.sh), which is also where
the anchor set and the tuning knobs live — the script is the standing plan.

---

## 9. Caveats

- **Opening book.** Both sides play the same lines via `-openings … -repeat`; a
  structurally unbalanced book (`2moves_v2.pgn` is white-favored) adds per-game
  variance but barely moves the Elo-difference estimate, since each opening is
  played twice with swapped colors. A balanced book (`8moves_v3.pgn`,
  `4moves_noob.pgn`) reduces variance but is not required.
- **CCRL ratings drift** as engines get re-rated. Re-verify the anchor numbers on
  the CCRL site before a run destined for the record (see the version-trap box).
- **Hardware.** CCRL uses standardized machines; a dev laptop is not. The effect
  is usually small (<50 Elo) because the TC is moves-per-time, not nodes — both
  engines see the same wall clock.
- **Correlated games.** myChess-vs-myChess pairings (an old version, or two of
  our candidates) share engine DNA and opening behavior; keep them out of the
  anchored estimate (see §6).
