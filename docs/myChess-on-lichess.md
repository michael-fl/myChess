# myChess on Lichess — Bot Setup Guide

Preparation notes for running myChess as a bot on [lichess.org](https://lichess.org).
This is a planning/reference document; nothing here is wired up yet.

---

## 1. What is a Lichess bot?

A Lichess **bot** is a dedicated account (flagged `BOT`) that plays through the
[Lichess Bot API](https://lichess.org/api#tag/Bot) instead of the web UI. You do
**not** implement that HTTP API yourself. The community-standard bridge

- **lichess-bot** — <https://github.com/lichess-bot-devs/lichess-bot> (Python)

does all the Lichess-side work: it subscribes to the account's event stream,
accepts/creates challenges, and for every game streams the moves to and from a
local **UCI** (or XBoard) engine. Your only obligation is to provide a compliant
UCI engine — which myChess already is (see §5).

```
Lichess  <--HTTPS/stream-->  lichess-bot (Python)  <--UCI stdin/stdout-->  myChess
```

The bridge must stay running the whole time the bot should be online (on your
machine or a small always-on server).

---

## 2. Setup steps

1. **Create a fresh Lichess account** for the bot. It must have **zero rated
   games** — upgrading to a BOT account is only allowed on a virgin account and
   is **irreversible** (a BOT account can never play as a normal human account
   again). Pick the bot's public name here; it cannot be changed later.

   **Planned bot account name: `myChessJava`** (free as of 2026-08; permanent
   once the account is upgraded to BOT). The account username is independent of
   the engine's UCI `id name` (`myChess`) and needs no relation to it — Lichess
   adds a `BOT` tag automatically regardless of the username.

2. **Generate an API token** at
   <https://lichess.org/account/oauth/token/create> with the scope
   **“Play games with the Bot API” (`bot:play`)** — and *only* that scope
   (least privilege; the token then has full play control of the account).
   Treat it like a password.

   > ⚠️ **Create the token while logged in as the bot account** (`myChessJava`),
   > **not** your personal account. The `bot:play` checkbox is **greyed out on any
   > account that has already played games** — only a fresh, bot-eligible account
   > can select it. So: register `myChessJava`, do **not** play a single game with
   > it, then create the token there. (If `bot:play` is unclickable, you are on the
   > wrong account.)

3. **Install lichess-bot** (needs Python 3.9+):
   ```sh
   git clone https://github.com/lichess-bot-devs/lichess-bot.git
   cd lichess-bot
   python3 -m venv venv && source venv/bin/activate
   pip install -r requirements.txt
   ```

4. **Provide the engine.** Point lichess-bot at myChess's UCI wrapper. The
   simplest path is to copy a built version directory (e.g. `versions/4.3.x/`
   with `my-chess-<v>.jar`, `lib/`, and `mychess-uci.sh`) into lichess-bot's
   `engines/` directory, or reference it by absolute path in the config.
   `mychess-uci.sh` already exports `JAVA_HOME` and the JVM flags, so lichess-bot
   just needs to execute it.

5. **Write `config.yml`** (see §3). Put the token in it (and keep it secret).

6. **Upgrade the account to BOT** — either let lichess-bot do it on first run
   (`python3 lichess-bot.py -u`), or manually:
   ```sh
   curl -d '' https://lichess.org/api/bot/account/upgrade \
     -H "Authorization: Bearer <YOUR_TOKEN>"
   ```

7. **Run the bot** (see §4) and issue/accept a first (casual) challenge (see §6).

---

## 3. `config.yml` (representative)

> ⚠️ The lichess-bot config schema **evolves between versions**. Always start
> from the `config.yml.default` shipped with the version you cloned and adapt it.
> The example below shows the keys that matter for myChess; treat it as a guide,
> not a guaranteed-current schema.

**Where to find `config.yml.default`:**
- **In your clone** (from step 3, the authoritative reference for your version):
  it sits at the repo root — `lichess-bot/config.yml.default`. Copy it and edit
  the copy:
  ```sh
  cd lichess-bot
  cp config.yml.default config.yml
  ```
- **Online:** <https://github.com/lichess-bot-devs/lichess-bot/blob/master/config.yml.default>
  (the `master` version may be newer than your clone — prefer your local copy).
- **Field documentation:** the lichess-bot wiki —
  <https://github.com/lichess-bot-devs/lichess-bot/wiki>.

```yaml
token: "xxxxxxxxxxxxxxxxxxxx"        # bot:play OAuth token — KEEP SECRET

url: "https://lichess.org/"

engine:
  dir: "./engines/myChess/"          # directory containing the wrapper + jar + lib/
  name: "mychess-uci.sh"             # the executable lichess-bot launches
  protocol: "uci"
  ponder: false                      # myChess does not support pondering (§5)

  # Per-game UCI options sent via `setoption`. UCI_Chess960 is toggled
  # automatically by the bridge for 960 games — no need to set it here.
  uci_options:
    # (myChess currently exposes only UCI_Chess960; add Hash/Threads here if
    #  those options are implemented later)

  # Safety margin subtracted from the clock the bridge reports, to cover
  # process/network latency. Increment handling is on the myChess side (§7).
  move_overhead: 100                 # milliseconds

challenge:                           # which incoming challenges to auto-accept
  concurrency: 1                     # one game at a time (single-threaded engine)
  accept_bot: true
  only_bot: false
  variants:
    - standard
    - chess960                       # myChess supports both
  time_controls:                     # real-time only; 'correspondence' omitted -> long/Fernschach games auto-declined
#   - bullet                         # declined on purpose -- see "Bullet - declined on purpose" in section 6
    - blitz
    - rapid
    - classical
  max_base: 1800                     # max base time in SECONDS (30 min) -> reject marathon games (caps the real clock, not just the category)
  max_increment: 30                  # max increment in seconds
  # min_base: 60                     # optional: exclude ultra-short formats
  modes:
    - casual
    - rated

matchmaking:
  allow_matchmaking: false           # set true (+ tune) to auto-seek opponents
```

**Do not commit a real token.** If `config.yml` lives in a repo, add it to
`.gitignore` and commit only a `config.yml.example` with a placeholder token.

---

## 4. Running the bot

```sh
cd lichess-bot
source venv/bin/activate
python3 lichess-bot.py            # normal run
python3 lichess-bot.py -u         # first run: also upgrades the account to BOT
```

- The process runs in the foreground and logs to the console (and to a log file
  if configured). Keep it alive for the bot to stay online — e.g. under `tmux`,
  a `systemd`/`launchd` service, or on a small VPS.
- myChess's own stderr goes to `mychess-stderr.log` inside its engine directory
  (the wrapper appends to it) — useful for debugging.

### Monitoring / watching the bot

To check whether the bot is currently playing — and to watch live:

- **Profile page:** `https://lichess.org/@/myChessJava` — shows online status and,
  when playing, the game in progress with a link to it.
- **TV link (bookmark this):** `https://lichess.org/@/myChessJava/tv` — auto-follows
  the bot's current (or most recent) game, move by move. The easiest "watch my bot" link.
- **Watch live:** open any of the bot's game URLs in a browser — Lichess games are
  public and spectatable in real time (board, clocks, optional eval bar); no login
  needed, any number of spectators.
- **Locally:** the lichess-bot console/log prints each accepted challenge, game
  start/finish, and move — the most direct "is something running right now" view.
- **Programmatically (API):**
  - `GET /api/user/myChessJava` → includes a `playing` field carrying the current
    game's URL while a game is in progress.
  - `GET /api/users/status?ids=myChessJava&withGameIds=true` → compact online /
    playing status plus the current game id.

---

## 5. Required UCI commands (and Chess960)

lichess-bot drives the engine with a small UCI subset. myChess's
[`UciHandler`](../src/main/java/org/michaelfl/mychess/UciHandler.java) already
covers all of it:

| Command | Purpose | myChess |
|---|---|---|
| `uci` | handshake → `id name` / `id author` / options / `uciok` | ✅ |
| `isready` | sync → `readyok` | ✅ |
| `ucinewgame` | reset for a new game | ✅ |
| `position startpos [moves ...]` / `position fen <FEN> [moves ...]` | set up the position | ✅ |
| `go wtime <ms> btime <ms> [winc <ms>] [binc <ms>] [movestogo <n>]`, `go movetime <ms>`, `go depth <d>`, `go infinite` | start searching | ✅ |
| `stop` | stop and return `bestmove` | ✅ |
| `bestmove <move>` (engine → GUI) | the chosen move (always emitted; legality-checked, `0000` fallback) | ✅ |
| `quit` | shut down | ✅ |
| `setoption name UCI_Chess960 value true/false` | enable Fischer Random | ✅ |

**Chess960.** myChess advertises the `UCI_Chess960` check option and plays 960
correctly (verified in cutechess `-variant fischerandom` matches). The bridge
sets it per game, sends the 960 start position as a FEN (Shredder or classical
castling rights), and myChess emits/accepts moves in UCI long-algebraic form,
including the Chess960 castling convention. To offer 960, just list `chess960`
under `challenge.variants`.

**Move format.** UCI long algebraic: `e2e4`, `e7e8q` (promotion), and castling
per the UCI/UCI_Chess960 convention — all handled by
[`UciMoveParser`](../src/main/java/org/michaelfl/mychess/UciMoveParser.java).

**Not supported (fine to skip):** pondering (`go ponder` / `ponderhit`). Keep
`ponder: false` in the config.

---

## 6. Creating and accepting challenges

**Accepting (incoming).** With the `challenge:` block in `config.yml`, lichess-bot
**auto-accepts** any challenge that matches the rules (variant, time control,
rated/casual, bot/human). Anything outside the rules is declined automatically.

**Being challenged by a human.** Anyone can open `https://lichess.org/@/<BOTNAME>`
and click **Challenge to a game**; if it matches the config it starts immediately.

**Creating (outgoing).** Three ways:
- **matchmaking** — set `matchmaking.allow_matchmaking: true` (and tune its
  filters); lichess-bot then periodically seeks opponents on its own.
- **Manual API call:**
  ```sh
  curl https://lichess.org/api/challenge/<OPPONENT> \
    -H "Authorization: Bearer <TOKEN>" \
    -d clock.limit=300 -d clock.increment=3 -d variant=chess960 -d rated=false
  ```
- **From the web UI** while logged in as the bot account (challenge another user
  or bot).

### Matchmaking (`allow_matchmaking`) — and keeping it considerate

`matchmaking.allow_matchmaking` decides whether the bot **proactively seeks
opponents** or only reacts to incoming challenges:

- `false` (**recommended to start**) — the bot plays **only games others
  initiate**. Full control; nothing happens while it is idle.
- `true` — whenever the bot is idle it **issues its own challenges** (usually to
  other online bots), keeping it active and building a rating on its own.

Because self-initiated challenges are unsolicited, matchmaking must be **moderate
and polite** — an aggressive setup spams the rest of the bot pool. Principles:

- **Challenge near your own strength** (a bounded rating window), not the whole pool.
- **Offer one clear, moderate time control**, not a spray of formats.
- **Prefer casual** for games you initiate — don't force a rated result on others;
  let *rated* games arrive via **incoming** challenges, where the opponent opts in.
- **Be patient**: give challenges time to be accepted and don't hammer.
- **Don't re-challenge bots that decline** (respect a block/decline list).

Representative *considerate* configuration (keys are version-dependent — always
check your `config.yml.default`):

```yaml
matchmaking:
  allow_matchmaking: true
  challenge_mode: "casual"          # polite: don't force rated games on others
  challenge_variant: "standard"     # one variant at a time (or "chess960")
  challenge_initial_time: [300]     # offer a single moderate control: 5+3
  challenge_increment: [3]
  opponent_min_rating: 1600         # stay near myChess's own strength ...
  opponent_max_rating: 2100         # ... instead of spamming the extremes
  opponent_rating_difference: 300   # (alternative to the fixed min/max window)
  challenge_timeout: 30             # minutes of patience between attempts — no hammering
  opponent_allow_tos_violation: false   # skip accounts Lichess has flagged for a ToS violation (e.g. cheating)
```

If you *do* want matchmaking to play rated (`challenge_mode: "rated"`), keep the
rating window tight and the cadence low — a rated game you initiate also moves the
opponent's rating, so restraint matters even more. A good rollout is: start with
`allow_matchmaking: false`, confirm the bot plays cleanly on incoming challenges,
then enable considerate casual matchmaking, and only later consider rated.

### The daily challenge limit — measured 2026-08-11

Lichess caps how many challenges an account may **create** per day. On 2026-08-11
`myChessJava` ran into it: from 15:50 onward every `POST /api/challenge/{user}`
came back `429`.

What the log showed, counted for that single calendar day:

| | count | notes |
|---|---|---|
| challenges **created** | 202 | 185 succeeded before the first `429`; 8 × `429`, 9 × `400` |
| incoming challenges accepted | 25 | 11 × `400` — the challenge was already gone |
| incoming challenges declined | 11 | |
| games actually played | 78 | ≈ 35 % of created challenges became a game |

Three things are worth carrying forward.

**It is a cumulative limit, not a burst limit.** The creations before the first
`429` were 63 s to 1 909 s apart, and an earlier hour on the same day carried 26
creations without a single complaint. Spacing requests further apart therefore does
not help once the budget is spent — only a smaller daily total does. The exact
threshold is not documented; 185 successful creations is an observed upper bound,
not a published figure.

**There are two different rate limits, and only one of them is self-describing.**
lichess-bot knows a named limit `bot.vsBot.day`: when it fires, the server sends a
`ratelimit` block containing the exact number of seconds to wait, and the client
honors it (`lib/lichess.py`, `get_challenge_timeout`). It arrives as `429` for your
own bot and as `400` when the *opponent* is the one at their limit — the 9 × `400`
above are other bots hitting theirs, so this is routine. A plain challenge-quota
`429` carries no such block, and lichess-bot then falls back to *guessing*:
60 → 120 → 240 → 480 s, capped at 600 s (upstream commit `38d3446`, "fix: respect
challenge rate limits (#1211)"). For a limit that resets once a day, a ten-minute
ceiling is far too low — the bot keeps poking every ten minutes for the rest of the
day.

**Restarting the bot makes it worse, not better.** The backoff lives in the client
and is initialized to 60 s in the constructor, so a restart resets it to its floor
while the server-side counter is untouched. The bot then retries ten times as often
as it did before the restart and floods the log with `429`s — which reads exactly
like the problem getting worse and is easy to misread as a new fault.

The response was to raise the acceptance rate rather than just slow the cadence:

- `challenge_timeout: 5` → `15` — at five minutes the matchmaker created a
  challenge roughly every 5-6 minutes (`min_wait_time` is 60 s on top), about
  250-290 per day. At fifteen it is around 90, with no loss in games played,
  because most challenges never became games anyway.
- `challenge_increment: [0]` → `[0, 2, 3]` — increment-free games are widely
  ignored by other bots, which is what the 35 % conversion reflects. Fewer wasted
  challenges per game is the efficient fix; raw cadence is the blunt one.
  *(Superseded on 2026-08-16 — see [Standard formats instead of random
  combinations](#standard-formats-instead-of-random-combinations--2026-08-16).)*
- `allow_matchmaking: false` until the quota window rolls over. Incoming
  challenges are unaffected — the event stream has its own limits and the bot
  keeps playing normally.

Watch one side effect of adding increments: Lichess derives the rating category
from an *estimated* duration, `base + 40 × increment` (`game_category` in
`lib/matchmaking.py`). Base and increment are drawn independently
(`random.choice` on each), so every pair can occur. A 1 440 s base is rapid at
increment 0 but classical at increment 2 or 3, which shifts the mix toward
classical — longer games, a separate rating that stays provisional longer, and
much more CPU per game if a measurement run is going on in parallel.

### Standard formats instead of random combinations — 2026-08-16

That last observation — base and increment are drawn **independently** — turned
out to matter for more than the rating category. A list of six base times and
three increments does not offer six or three formats; it offers all **eighteen
cross products**. The bot was therefore challenging bots with `24+2`, `30+3` and
`3+3`: combinations no human plays and few bots recognize, which is a poor use of
a daily challenge quota that had already run out once.

lichess-bot has exactly one mechanism for expressing a *pair*: `matchmaking.overrides`,
a map of named blocks, each overriding only the keys it names. `Matchmaking.choose_opponent`
(`lib/matchmaking.py`) draws uniformly between the default block and the overrides —
`random.choice(overrides.keys() + [None])` — so **one single-valued block per format**
is the way to offer real time controls. The bot now offers five:

| format | category | estimated duration | defined in |
|---|---|---:|---|
| 3+2 | blitz | 260 s | `overrides: blitz_3_2` |
| 5+3 | blitz | 420 s | `overrides: blitz_5_3` |
| 10+5 | rapid | 800 s | `overrides: rapid_10_5` |
| 15+10 | rapid | 1300 s | `overrides: rapid_15_10` |
| 30+0 | classical | 1800 s | the default block |

Uniform draw, so 40 % blitz / 40 % rapid / 20 % classical; the eighteen-combination
draw gave 33 / 39 / 28 %. Three things are load-bearing about this layout:

- **The default block has to be one of the formats.** It is always in the draw, so
  leaving multi-valued lists there would keep producing cross products for a fifth
  of all challenges — the overrides alone do not fix anything.
- **Exactly one classical slot**, and it is the expensive one: at
  `challenge.concurrency: 1` a 30+0 game can occupy the bot for an hour, so one
  game in five takes as long as the other four together. A second classical format
  (30+20 would be the natural one) doubles that cost against a rating that
  incoming challenges barely feed anyway.
- **Increment 10 is the safest of the five, not the riskiest.** myChess settles at
  spending roughly the increment per move while holding about `6.2 × increment` in
  reserve (see [§ 7](#7-time-increment--implemented)), so 15+10 keeps ~62 s in hand
  and 3+2 only ~12 s. Time trouble lives at the short end.

`config.yml` is read at startup only — a running bot keeps the format list it
started with until it is restarted.

### Bullet — declined on purpose

`challenge.time_controls` leaves `bullet` commented out. The original reason was
that myChess forfeited the increment entirely, which in a 1+1 game is two thirds
of the total time; that reason expired with [§ 7](#7-time-increment--implemented).
What remains is that bullet is myChess's weakest format — a 1 min game leaves
about 1.9 s for the first move and 0.16 s near the end, i.e. depth 4-5 instead of
7-8 — and that the engine cannot ponder on the opponent's clock.

Enabling it takes **three** changes, not one, and the second is easy to miss:

1. uncomment `bullet` in `challenge.time_controls`;
2. lower `challenge.min_base` from `180` to `60` — at a 180 s minimum base time
   no bullet challenge can pass regardless of what `time_controls` says;
3. set `challenge.bullet_requires_increment: true` — a `1+0` game gains nothing
   from the increment handling and is exactly the case the old rationale called
   hopeless.

Worth doing only after the increment path has played real games under a clock
that carries an increment; bullet is the least forgiving format for a
time-management defect, and 60 seconds leave no room to notice one.

---

## 7. Time increment — implemented

Most Lichess time controls carry an increment (3+2, 5+3, …). myChess reads
`winc` / `binc` and folds the increment of the side to move into the per-move
budget ([`UciHandler.computeClockBudgetMillis`](../src/main/java/org/michaelfl/mychess/UciHandler.java)):

    budget = ourClock / (movestogo + 1) + 80 % × ourIncrement

capped at `ourClock − 50 ms` and floored at 50 ms. `movestogo` falls back to 30
when the GUI omits it — which every Lichess control does, since none of them has
a moves-to-go phase.

Three details are easy to get wrong; each is pinned by a test in
[`UciHandlerTest`](../src/test/java/org/michaelfl/mychess/UciHandlerTest.java):

- **Only 80 % of the increment is spent.** `movestogo = 30` is a spending *rate*,
  not a prognosis of the game's length: it is re-applied to the shrinking
  remainder on every move, so the clock share decays geometrically (≈ 14 % of the
  start clock left after 60 moves). Spending the increment in full would cancel
  that decay and turn a 3+2 game into one played at exactly two seconds per move,
  with the base time never touched.
- **The remaining clock is a hard cap.** The increment is credited *after* the
  move, so a budget of `clock + increment` flags on a short clock. `wtime 2000
  winc 5000` therefore budgets 1950 ms, not 6000.
- **The tokens are read independently.** UCI does not guarantee that `winc`
  arrives together with `wtime`, and python-chess — what lichess-bot speaks —
  emits each token on its own. An increment for one color only is accepted, and a
  `winc` without any clock is ignored rather than mistaken for a budget.

**Which controls myChess can play: all of them.** Sudden death (`10+0`, `5+0`),
fixed `movetime`, and increment controls (`3+2`, `5+3`, `15+10`) are all budgeted
from the clock. Rated versus casual makes no difference at any point — the engine
is never told which it is playing.

**What is still missing** is everything *beyond* a flat per-move slice: no time
hoarding across moves, no panic mode on a low clock, no complexity scaling. That
is roadmap [§ 12.12 (Real time management heuristics)](roadmap.md#1212-real-time-management-heuristics--s--m--3060-elo);
the increment handling is its first slice, not the whole entry.

**Not yet measured in a match, and the usual SPRT would not measure it.** At
`tc=40/60` — the control every measurement in this project uses — no `winc` is
sent, so the increment branch never runs. What a self-play SPRT there *would*
pick up is the other half of the same change: the per-move budget went from
`clock / (movestogo + 1) − 50 ms` to `clock / (movestogo + 1)`, about **+3.5 %
thinking time**, worth a couple of Elo at most and far inside the noise of any
affordable run. Measuring the increment itself needs a control that carries one
(e.g. `tc=60+1` or `3+2`) against a 4.4.1 baseline, which ignores it.

---

## 8. Additional notes / gotchas

- **Always-on host.** The bot is online only while lichess-bot runs. For 24/7,
  use a small VPS or an always-on machine; otherwise start it when you want the
  bot available.
- **Token security.** The `bot:play` token grants full play control of the
  account. Never commit it; git-ignore `config.yml`.
- **Separate rating pool.** Bots are rated in their own pool and clearly flagged
  `BOT`; they cannot play in human tournaments (except explicit bot arenas).
- **Concurrency.** myChess is single-threaded; keep `challenge.concurrency: 1`
  unless you run one engine process per game and have the cores to spare.
- **JVM warmup / memory.** The engine process is reused across a whole game
  (handshake once, then `position`/`go` per move), so JIT warmup only mildly
  slows the first moves. The 256 MB heap in `mychess-uci.sh` is plenty; raise it
  only if a larger transposition table is configured.
- **Test locally first.** Before going live, drive `mychess-uci.sh` by hand
  (`uci`, `isready`, `position …`, `go movetime 1000`) or play a few cutechess
  games — exactly what this repo already does.
- **Strength context.** myChess measures **1928 ± 21 CCRL Blitz** as of v4.4.1
  ([re-anchor 2026-08-17](myChess-ELO-measurement.md#the-v441-re-anchor--measured-2026-08-17),
  2000 games against five externally rated engines) — roughly rank 700 of 2918 on that
  list, the upper half. Its lichess blitz rating sits near the same value, which is a
  coincidence worth not over-reading: different pool, different time control.
- **Be a good citizen.** Don't spam challenges via matchmaking; respect Lichess's
  API rate limits (lichess-bot handles back-off, but aggressive config can still
  trip limits). It did trip them on 2026-08-11 — see
  [The daily challenge limit](#the-daily-challenge-limit--measured-2026-08-11) for
  the measured numbers and why restarting the bot is the wrong reflex.

---

## Summary checklist

- [ ] Fresh Lichess account (0 rated games)
- [ ] `bot:play` OAuth token
- [ ] lichess-bot installed
- [ ] built myChess version wired into `config.yml` (`ponder: false`, `chess960` listed)
- [ ] account upgraded to BOT (irreversible)
- [x] `winc`/`binc` handling in myChess — done, so any time control is fair game (§7)
- [ ] `python3 lichess-bot.py` running on an always-on host
