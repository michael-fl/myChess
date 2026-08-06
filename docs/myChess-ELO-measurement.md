# Absolute Elo Measurement for myChess

How to get a defensible **absolute** Elo estimate for myChess — a number on
the CCRL/FIDE scale, not just a relative "is this patch better?" delta.

- **Relative** A/B testing (self-play SPRT) is covered by
  [elo-testing.md](elo-testing.md) and the
  [sprt-cutechess-template](../../.claude/projects/-Users-mf--PRIVAT--New-Stuff-myChess/memory/reference_sprt_cutechess_template.md)
  memory — that is the everyday tool for measuring a change.
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

myChess's current (propagated) estimate is **~1800** (post-v4.3.1; the last
*direct* measurement was 1422.7 at v3.1.x — see §7). The anchors are chosen to
**bracket ~1800**, with one anchor close to it (closest to 50% → most
information) and anchors clearly below and above.

| Engine | CCRL Blitz | Role | Proto | Wrapper |
|---|---:|---|---|---|
| **TSCP 1.81** | 1607 | anchor — **lower** (fixed) | xboard | `engines/tscp-1.81-elo1607/tscp.sh` |
| **Zeta Dva 0402** | ~1801 | anchor — **near** (fixed), carries the most info | xboard | `engines/ZetaDva-0402-elo1801/zetadva.sh` |
| **Princhess 0.7.0** | 1985 | anchor — **upper** (fixed) | UCI | `engines/princhess-0.7.0-elo1985/princhess.sh` |
| **Kojiro 0.1.4** | (1984) | **free** — Ordo estimates it; cross-checks the upper end | UCI | `engines/Kojiro-0.1.4-elo1984/kojiro.sh` |

**Why Kojiro is left free.** Its version rating is the shakiest (0.1.3 = 2033 vs
0.1.4 = 1984) and it emits cosmetic "Illegal PV move" warnings — exactly the
engine not to pin. If Ordo places it near Princhess (~1985), the upper end is
double-confirmed; if not, the free slot absorbs the discrepancy without touching
the myChess number.

**Optional 5th anchor:** **Pulse 1.7.3 = 1505** (`engines/pulse-1.7.3-elo1505/pulse.sh`,
UCI) as a firmer floor — adds one more gauntlet (~an extra night).

> **Version trap — always verify the *exact* version's CCRL entry before a run.**
> A rating can swing hundreds of Elo across an engine's release history, so
> "latest" is a reliable way to pick a useless anchor. Cases hit during setup:
> Kojiro 0.1.3 = 2033 vs 0.1.4 = 1984; Princhess 0.7.0 = 1985 vs 0.21 = 3329;
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

**TSCP 1.81** (xboard) — single-file C:
```sh
cd /tmp && curl -O http://www.tckerrigan.com/Chess/TSCP/tscp181.zip
unzip tscp181.zip -d tscp181
clang -O2 -o tscp181/tscp tscp181/*.c
mkdir -p engines/tscp-1.81-elo1607 && mv tscp181 engines/tscp-1.81-elo1607/
```
Wrapper `tscp.sh`: `cd` into `tscp181`, `exec ./tscp`. Use `proto=xboard`.

**Zeta Dva 0402** (xboard) — GitLab source; drop `--static` from the Makefile
CFLAGS (no full-static libc on macOS):
```sh
cd engines && git clone https://gitlab.com/smatovic/ZetaDva.git ZetaDva-0402-elo1801
cd ZetaDva-0402-elo1801 && git checkout v0402 && cd src && make   # after removing -static
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
    -engine name=TSCP          cmd=./engines/tscp-1.81-elo1607/tscp.sh proto=xboard \
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
"TSCP",1607
"ZetaDva",1801
"Princhess",1985
EOF
/Users/mf/_PRIVAT_/New-Stuff/ordo/ordo \
    -p /tmp/bracket.pgn -m /tmp/anchors.csv \
    -o test-results/ordo-anchor-4.3.2.txt -c test-results/ordo-anchor-4.3.2.csv
```

`-m` pins the listed engines with zero uncertainty. For per-anchor CCRL error
bars, use `-y anchors-with-error.csv` (a third σ column) so Ordo propagates the
anchor uncertainty into the myChess number.

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

Everything after v3.1.x in [version-history.md](version-history.md)'s absolute
column is **propagated** from per-version SPRT deltas, not re-measured. A fresh
bracket replaces that propagated number with a measured one.

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

See the [project_absolute_reanchor](../../.claude/projects/-Users-mf--PRIVAT--New-Stuff-myChess/memory/project_absolute_reanchor.md)
memory for the standing plan and the exact launch command.

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
