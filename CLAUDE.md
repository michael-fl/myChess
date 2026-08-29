# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

Maven project, Java 25, JUnit Jupiter 5.

The build is configured via `maven.compiler.release` in `pom.xml`. Run with `JAVA_HOME` pointing to a JDK 25 (the system default JDK may be older — Maven uses `JAVA_HOME` to pick the compiler).

```sh
mvn compile                                  # compile
mvn test                                     # run all tests (~6 min)
mvn test -DexcludedGroups=slow               # fast tests only (~20 s)
mvn -Dtest=BoardTest test                    # run one test class
mvn -Dtest=BoardTest#methodName test         # run one test method
mvn exec:java                                # launch REPL (org.michaelfl.mychess.MyChessMain)
mvn package                                  # build jar in target/
```

Tests that take more than ~10 s on this machine are annotated with JUnit 5's
`@Tag("slow")` so they can be skipped during iterative development. Tagged at
the class level: `BlunderTest`, `EngineTest`, `DeepWeightTest`,
`EvalRegressionTest`, `IllegalPvRegressionTest`, and the dataset-backed Texel
adapter tests (`AllPstTexelDataTest`, `MaterialPstTexelDataTest`,
`CombinedTexelDataTest`, `FactorTexelDataTest`, `PawnPstTexelDataTest`);
`PerftTest` additionally tags its deep-perft subset. Together these are the
bulk of full-suite wall-clock. Add the tag at the class level when a new test
reliably crosses the 10 s mark on a fresh JVM; prefer method-level tags only
when a class has a clear fast/slow split.

The REPL opens `db/openings.db` (MapDB) on start and creates it on first run if missing. `db/` is git-ignored.

## REPL command surface

`MyChessMain` runs an interactive loop dispatched through `CommandHandler`. Each command is a nested `Command` subclass with `canHandle(line)`/`handle(line)`. To add a command, add a new inner class and register it in the command list inside `CommandHandler`. Existing commands:

`quit`/`exit`/`q`, `new`, `auto` (engine self-play), `import <pgn-or-moves>`/`imp`, `l` (last imported), `print`/`p`, `board`, `export`/`exp`, `pgn` (game as PGN move text), `revert`/`r`, `tip`, `last`, `dw` (deep weight), `weight`/`w`, `go`/`g` (engine plays one move), `moves`, `fen` (print) / `fen <FEN>` (load), `hash`, `bench [depth]` (node signature), `o…` (opening DB lookup). Anything else is parsed as a move in algebraic notation.

## Architecture

Single Java package `org.michaelfl.mychess` plus two sub-packages: `engines` (search) and `openingdb` (MapDB-backed opening book). Recent history removed two earlier engine versions — only `MyChessEngine` remains.

### Board representation (`Board.java`)

12×12 byte array with a 2-square illegal border on all sides — off-board detection is a single comparison against `Board.illegal` instead of bounds math. Square constants `a1…h8` are precomputed indices into this array. Pieces are encoded as small byte constants (`whitePawn`=8 … `blackKing`=21); white/black share the low 3 bits so `piece & 7` gives the piece kind.

The board also owns:
- a `GameStatus` stack — every `makeMove` pushes status (turn, castling rights, en-passant square, half-move clock, last move, Zobrist hash) so `revertMove()` is a pop, not a recomputation.
- Zobrist hashing via `RandomNumbers` (precomputed table) → `calculatePositionKey()` feeds threefold-repetition detection and the opening DB lookup.

### Move encoding (`Move.java`, `BitOps.java`)

Moves are packed into a single `int` (`fromField | toField<<8 | capturedPiece<<16 | moveType<<24`). All hot-path APIs (`Moves`, `MovesArray`, `IntArray`, `MoveGenerator`, search) pass moves as `int`, not as `Move` objects, to avoid allocation. The `Move` wrapper class exists for printing/equality at boundaries.

### Search (`engines/PositionSearch.java`)

`MyChessEngine.calculateNextMoveSub` delegates to `PositionSearch`. The search is:

- **Iterative deepening** from depth 1 up to `EngineConfig.maxDepth`, bounded by `millisPerMove` (timeout checked every 10 000 nodes via `Statistics.getPositionsCount()`). Before each iteration `PositionSearch.shouldSkipIteration` consults `IterationTimings` — a process-static per-depth SMA — and bails out early if the next deepening iteration is unlikely to complete in the remaining budget. A probing override with a remaining-time ratio gate keeps the SMA from freezing. All tuning knobs live in `engines/EngineTuning.java`.
- **Negamax alpha-beta** with a principal-variation table flattened into a single `int[pvMaxLength * pvMaxLength]` indexed by `depth * pvMaxLength + depth`.
- **Best-known-move ordering**: the previous iteration's PV is passed in as `bestKnownPath` and the `MoveSorterImpl` places it first; an `__assert` in `PositionSearch` enforces this invariant.
- **Killer-move heuristic** via `KillerMoves` (only non-capturing moves that caused beta cut-offs).
- **Quiescence search** (`QuiescenceSearch`) runs at **every** horizon leaf — `PositionSearch.alphaBetaSearchMain` calls it unconditionally when `ctx.remainingDepth() == 0`, not only after a capture. What is capture-conditional is the *continuation*: the quiescence move generator is capture-only (`MoveGenerator.forQuiescenceSearch()`, `onlyCaptures == true`), so a leaf with no captures available returns its stand-pat immediately and the extension stops there. Capped at `EngineConfig.getMaxQuiescenceDepth()` (20) plies **beyond** the entry depth (`maxDepth = depth + maxQuiescenceDepth`).
- **Material-only shortcut**: if cumulative material delta during search exceeds `EVALUATE_MATERIAL_ONLY_THRESHOLD` (200 centipawns) the full positional eval (`WeightingFunction` + `PieceSquareTables`) is skipped — only material is returned. This is a load-bearing pruning heuristic, not a defensive bail-out.
- **Async execution**: `ChessEngine.nextMoveAsync` runs the search on a single-thread executor and returns a `NextMoveTask` that exposes a `Future`-style API plus cooperative cancellation (`task.isCanceled()` is polled inside the search and throws `CancellationException`).

`ChessEngine.calculateNextMove` short-circuits the search when the game is already over, when the 50-move / threefold-repetition rule fires, or when the opening DB has a candidate move (≥100 occurrences, ≥20% win, <45% loss — weighted random pick by frequency). The `weightFactor` (`+1` for white, `−1` for black) is applied at the boundary so the search itself runs in pure negamax form. The search itself uses a **stricter** repetition rule than the root check: `PositionSearch.alphaBetaSearchPre` asks `Board.isTwofoldRepetition()`, so a position recurring along the search path scores as a draw at the *second* occurrence rather than the third. That check must stay above the transposition-table lookup and must not store its result — both properties are load-bearing (roadmap § 12.23).

### Game lifecycle (`Game.java`)

`Game` owns three engines: `engineWhite`, `engineBlack`, and a `statusEngine` (always `MyChessEngine` at depth 2) used only by `calculateGameResult()` to detect checkmate/stalemate after each move. After every successful `makeMove`, `calculateAndSetGameResult()` runs the status engine — if no legal reply exists the result transitions from `ONGOING` to `CHECKMATE`/`STALEMATE`. On any failure during move validation or post-move verification the move is reverted, so `Board`'s status stack stays consistent.

### Opening database (`openingdb/`)

`OpeningDB` wraps a MapDB `BTreeMap<String, byte[]>` at `db/openings.db` with transactions enabled. Keys are Zobrist position strings; values are `DBValue` byte blobs encoding `(positionCount, [move, win, draw, loss]*)`. `OpeningDBImporter` builds the DB from PGN files (hard-coded path `/Users/mf/_PRIVAT_/Schach/KingBase2019-pgn/`, `maxMoveDepth=16`). The DB is opened in `MyChessMain` via try-with-resources — `OpeningDB.close()` must run on shutdown or MapDB leaves the file locked.

### PGN / FEN / notation

- `Fen` — full FEN export/import (used by `fen`/`export` REPL commands and by `Board.exportFEN`).
- `Pgn` + `PGNImporter` — parse PGN files into `MoveDescription` lists.
- `MoveDescription` — symbolic move (piece, target square, disambiguation, capture/check/checkmate/promotion flags). `Board.resolveMoveDescription` turns a symbolic move into a concrete `Move` using the current `MoveGenerator`.
- `SimpleNotationImporter` — pure long-algebraic input from the REPL.

## Project-specific conventions

- **Don't allocate in the search hot path.** Moves are `int`s, move lists are `Moves`/`MovesArray` backed by reusable `int[]`, and `Board.makeMove`/`revertMove` mutate the same board (no copy-on-make in the inner loop — `calculateNextMove` copies the board once at the root). New code in `PositionSearch`, `MoveGenerator`, `WeightingFunction`, `QuiescenceSearch` should preserve this.
- **The `GameStatus` stack is the source of truth for reversibility.** Any mutation of board state inside `Board.makeMove` must have a matching undo in `revertMove`, or threefold-repetition and the search's `makeMove`/`revertMove` pairing will silently corrupt state.
- **Invariants are encoded via `Assert.__assert(Supplier, Supplier)`** with lazy message construction — use the same pattern when adding new invariants in the search. **They cost nothing measurable, and that is measured, not assumed** (2026-08-28): a depth-6 bench over three variants — assertions active, `Assert.ENABLED = false`, and all eleven hot-path call sites physically removed — came out at 126,993 / 126,679 / 129,588 ms best-of-three. The removal variant is the *slowest*, which cannot be a real effect and is therefore the proof that the spread within each variant (1.5 / 8.1 / 4.8 %) swamps any difference between them. So keep them on, keep adding them, and do not trade the diagnostics for a saving that does not exist — the "first move must be the best known move" invariant alone once surfaced 6290 illegal PVs in a single 1600-game match. Note there are **eleven** call sites, not the eight an incomplete grep suggests: three live in `StaticExchangeEvaluation`, which runs in move ordering for every capture.
- **The `engines/` package is a one-way dependency** on the root package, not vice-versa. Root-package classes (`Game`, `Board`, …) reference engines only through the abstract `ChessEngine` base class.
- **US English everywhere — no British spellings.** Identifiers, comments, JavaDoc, log/exception messages, commit subjects, doc files under `docs/`, and chat-facing summaries about code all use US English. `color` not `colour`, `center` not `centre`, `behavior` not `behaviour`, `analyze` not `analyse`, `optimize` not `optimise`, `serialize` not `serialise`, `cancel(l)ed` (single `l`), `favor` not `favour`. The global rule in `~/.claude/CLAUDE.md` covers this; this entry is a local reminder because the convention is easy to slip on when writing prose comments.

## Working a task list: start the next task in the same turn

Given a list and "arbeite sie selbständig ab", **finish a task and begin the next unblocked one without handing back**. End the turn only when (a) a measurement is running and nothing can proceed until it reports, (b) a decision is needed that cannot be derived from the code and the brief, or (c) the list is empty.

**Waiting on a machine is a legitimate turn end. Ending a turn with nothing running and tasks open is a bug.** That state occurred twice on 2026-08-28 and the user asked both times what was happening; the answer was "nothing", which is the failure.

**The mechanism, found on the second attempt after the first fix failed within ten minutes: announcing the next task in prose is what substitutes for starting it.** Every failure that day had the same shape — the turn ended with a sentence *about* the next task instead of the tool call that begins it:

| how the turn ended | state | |
|---|---|---|
| "Ich melde mich, wenn der SPRT entschieden hat." | measurement running | correct |
| "Soll ich mit #36 loslegen?" | nothing running | bug |
| "Ich mache mit #34 weiter." | nothing running | bug |
| "Weiter mit #35." | nothing running | bug |

Writing "weiter mit X" *feels* like the transition, and once written, the turn feels complete. So the rule is mechanical rather than motivational:

> **Never end a turn with a sentence announcing the next task.** If it is unblocked, the next thing after the tool result is the tool call that starts it — prose comes after, or not at all.

Self-check: any future-tense sentence about own work ("ich mache weiter mit", "als nächstes", "soll ich") with nothing running is the bug, regardless of how reasonable the surrounding message looks.

Three contributing causes, because they recur:

- **Long reports end turns.** The rhythm slips from "do, do, do, brief report" to "do, long report, stop". A report that reads like a conclusion becomes one. Detail belongs in the commit message and the roadmap, which is where it gets read later anyway.
- **Mid-turn corrections train it.** After six or seven interruptions in a row, the learned pattern becomes "deliver a small unit and hand back". The corrections were right; the generalization was not.
- **A correction about *how* something was done does not revoke permission to do it.** Being told off for pushing unasked is a rule about pushes, not about forward motion.

Autonomy covers *doing the work*. Commits and pushes still each need their own instruction.

**Arm the idle watchdog in the same turn autonomous work starts** — the user's idea, 2026-08-28, after the written rule alone failed three times in one evening. A persistent `Monitor` that fires every 10 minutes and reports whether any job process is alive:

```sh
while true; do
  sleep 600
  busy=$(ps -eo command | grep -cE '(cutechess-cli -engine|java .*MyChessMain|<other job patterns>)')
  if [ "$busy" -gt 0 ]; then
    echo "machine BUSY ($busy job process(es)) — nothing to check"
  else
    echo "IDLE — nothing is running. If the task list has an unblocked task, resume it NOW with a tool call, not with a sentence about resuming it."
  fi
done
```

Not elegant, and deliberately so: it moves the check outside my own state, which is the point — the three failures that evening were all failures of a good intention. Over a night that is roughly 60 messages and the user has explicitly accepted the volume; stretch to 20 minutes only if asked.

Three things it is not. **"BUSY" only means something is running**, not that the right thing is running or that I am not waiting on the wrong signal. **It dies with the session**, which is why the rule lives here as well. And **it detects rather than prevents** — up to ten minutes late. The real fix is the no-announcement rule above; treating the watchdog as the mechanism would build in that ten-minute delay as normal.

## Hot-path production code stays on a branch until a measurement justifies it

**An unchanged bench signature proves the behavior is identical. It proves nothing about cost.** Those are different questions, and conflating them is how four separate changes to `WeightingFunction` and `PositionSearch` reached the mainline in one evening (2026-08-28) — each individually "proven neutral", together an unmeasured refactor of the evaluation's hot path. The one number that argued against it, a wall clock 12 % above the previous run, went into a commit message instead of stopping the commit.

So, for anything inside the search or the evaluation:

- **Branch first.** The mainline gets it after a measurement says it is at worst free, not before. `bench-history.md` rule 6 already draws this distinction; this entry exists because writing it down was not enough.
- **A signature match is the entry ticket, not the verdict.** It says the change is safe to measure. It does not say the change should ship.
- **Cheap and provably-neutral is still not free.** "One or two percent of evaluation time, unmeasurable" is a prediction. Several of them in a row are a refactor.
- **A timing reading that points the wrong way is a stop signal**, even when it is a single unpaired number that proves nothing. Unproven and ignorable are not the same thing: measure it properly or leave the change on the branch.

Docs, tests and measurement tooling are exempt — they cannot cost plies.

## Long-running processes: persist as you go, and always watch them

Measurement work here routinely runs for minutes to hours — engine matches, STS runs, Stockfish scans, depth sweeps. Two rules apply to **every** process expected to run longer than a minute. Both exist because each was violated and cost real time.

**1. The process must persist interim results, not report only at the end.** Append each result to a file as it completes (one JSON object per line works well), and on restart skip what is already there. A run that collects everything in memory and prints at the end loses *all* of it when it is killed, and shows nothing while it works. `tools/scan-sts-misses.py` used `pool.map` and did exactly that; the depth-classification sweeps were rewritten to `imap_unordered` + line-append after two of them died and took every result with them. Prefer `imap_unordered` over `map`, `flush()` after each write, and a resume check against the output file.

**2. The process must be wrapped in a two-minute heartbeat that reports liveness and, where possible, progress.** Use `Monitor` with a `sleep 120` loop that prints on every tick — not only on change, and not only on completion. It must state whether the process is still alive, how far it has come, and on exit whether a result exists. The reason for the fixed tick: a monitor that only speaks when something changes makes silence ambiguous, and a stalled run then looks exactly like a working one. Two runs were assumed to be progressing while they were dead. (The interval was 60 s until 2026-08-18 and was widened to 120 s — a minute produced more chat noise than the added resolution was worth.)

**Every long run reports in the chat, whether or not it writes a log file** (stated 2026-08-28, correcting the rule that stood here before). Whether the process persists its own output is irrelevant to this: a log file the user is not watching is not a report. If it runs longer than a couple of minutes, it ticks here.

An earlier version of this rule said the opposite — that a self-reporting process needs only a completion notification, because line count and mtime answer "alive and how far" whenever somebody asks. That reasoning served *me*, not the user: it optimized for my ability to check on demand rather than for the user seeing progress without asking. It was written after an objection to a two-minute tick on a six-hour SPRT, and generalized far too widely from it.

**The objection it came from was about frequency, not about reporting.** ~180 messages over a six-hour run displace real work, and reading interim SPRT standings without their error interval is how a run gets called too early — both still true. So scale the interval to the run, and never quote an interim figure without its interval:

| run length | interval |
|---|---|
| up to ~30 min (bench, test suite) | 2 min |
| 30 min – 2 h | 10 min |
| multi-hour (SPRT, anchor bracket) | 30 min, and the standing must carry its error interval |

Report progress, not just liveness: "position 36 of 55", "game 420 of 1600", not "still running".

**Numbers first in the message, and keep the monitor description short.** The description is static and cannot change per tick, so it names the run and nothing more (`bench 8 · 55 positions`); a wordy one just crowds out the line that carries data. Put the counts at the very start of the emitted line, because that is what survives if the view truncates:

```
36/55 done, 4min elapsed, pos 37 running 14min (Nr.37 = die langsame, ~15min)
```

**Say when a standing counter is expected.** The bench spends ~15 of its ~17 minutes on position 37, so seven consecutive ticks read the same count and look exactly like a hang — which is the ambiguity the heartbeat exists to remove. Carry the per-item elapsed time and name the known-slow item.

**The heartbeat must end itself when the run does.** Watch for the process, not for log lines: check whether it still exists, and `break` out of the loop when it is gone — printing either the finished result or a resume hint. A `tail -f` on a log file never exits on its own; one left over from the anchor-bracket run was still going **2 days and 18 hours** after that run finished, and nobody noticed. A self-terminating monitor also makes its final message the answer: "done, here is the footer" or "interrupted at N, resume with this command".

**Keep the PID. Do not pattern-match for a process you started yourself.**

```sh
mycmd &                      # or: nohup ./runner.sh & …
PID=$!
while kill -0 "$PID" 2>/dev/null; do sleep 120; echo "läuft seit $(ps -o etime= -p $PID)"; done
```

`kill -0 <pid>` asks the kernel a question that has one right answer. Every text filter asks a question about *strings*, and the string you are searching for is, by construction, also present in the command line of the thing doing the searching. That is not an edge case to be outsmarted — it is the normal situation, and it has now misfired **four times** in this project:

- `pgrep -fc <pattern>` returned 0 while `pgrep -f <pattern>` listed four PIDs.
- `ps | grep my-chess-4.4.2.jar` found nothing, because `mychess-uci.sh` launches with `-cp target/classes:target/dependency/*` and carries no jar name.
- `ps -eo etime,command | grep <script>.py` matched the *launching shell* as well, whose command line was the whole heredoc that wrote the script — so the "elapsed time" field came back as several lines of Python source.
- **2026-08-29, the expensive one.** `while ps -eo args | grep -q surefirebooter; do sleep 20; done` — the loop's own shell carries the word `surefirebooter`, so the condition was permanently true. Two such waiters sat spinning for 26 and 19 minutes and *would never have exited*; a monitor built the same way reported a 26-minute elapsed time for a run two minutes old. The bracket trick (`[s]urefirebooter`) does not save this: it defeats a self-match on the *pattern* while the surrounding script text still contains the plain word.

The first two produced a confident "the run is hung" about a run at 99 % CPU. Note that this last one happened **while following the advice this paragraph used to give** — anchor the match on the interpreter. The anchor was on the wrong field, and no amount of care with regexes removes the class of error. Hence the rule above: keep the PID.

**Only when you did not start it** — an orphan, something from a previous session — is matching unavoidable. Then use `ps -eo pid,args` with the anchor on the *executable field* (`awk '$2 ~ /bin\/java$/ && /surefirebooter/'`, since a shell has `-c` there and cannot match), take the PID once, and from that point on ask `kill -0` rather than re-running the filter. Verify the filter finds something *known to be running* before trusting a zero.

**The same applies to counting inside log files.** Surefire prints `<<< FAILURE!` twice per failing test — once on the class summary line, once on the method line — so `grep -c "<<< FAILURE"` reports double and a heartbeat built on it announces failures that do not exist. Count the thing you actually mean (`grep -c "^\[ERROR\] org\.michaelfl"` for methods, or read the final `Tests run: … Failures: …` line) and cross-check a surprising count against a second source before reporting it.

**Scope kills to your own processes.** `pkill -f "MyChessMain uci"` also matches the lichess bot's engine, because the bot runs the same main class. Match the specific wrapper path, or keep the PIDs you started. Killing a Python parent also leaves its engine children orphaned at full CPU — check for and clean up strays afterwards.
