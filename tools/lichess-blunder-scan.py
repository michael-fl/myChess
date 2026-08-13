#!/usr/bin/env python3
"""Download new myChess games from lichess and find its gross blunders with Stockfish.

Four phases, each resumable and idempotent, each skippable:

1. **Fetch** — pull every game newer than the last run from
   ``GET /api/games/user/{username}`` into ``test-results/lichess/games/<id>.pgn``.
2. **Scan** — walk every not-yet-scanned game at ``--scan-depth`` (15) and record each
   of myChess's moves that loses at least ``RECORD_FLOOR_CP``. Cheap and wide.
3. **Verify** — re-evaluate only the *candidate positions* from the scan (those losing
   at least ``--scan-threshold``, 150 cp) at ``--verify-depth`` (20). Two evaluations per
   candidate instead of ~80 per game, so the expensive depth costs little.
4. **Report** — list every verified loss of at least ``--threshold`` (300 cp) in
   ``blunders.md`` and copy those games to ``test-results/lichess/blunders/``.

The two-stage design exists because a shallow search **systematically understates** how
much a blunder costs: it does not yet see the consequence, so the position after the
mistake is scored too kindly. Measured on game NMc7sp8h, whose two blunders were found by
hand first: at depth 15 they read 164 and 154 cp, at depth 22-24 they read 189 and ~257.
A single-pass scan at depth 15 with a 300 cp threshold would have missed both.

Usage::

    ../lichess-bot/venv/bin/python tools/lichess-blunder-scan.py
    ../lichess-bot/venv/bin/python tools/lichess-blunder-scan.py --report-only --threshold 200
    ../lichess-bot/venv/bin/python tools/lichess-blunder-scan.py --verify-only --verify-depth 24

The lichess-bot virtualenv is used because it already provides ``python-chess``,
``requests`` and ``PyYAML``; any interpreter with those three works.

**A token is required.** ``/api/games/user/{username}`` carries an OAuth2 security
requirement, and lichess answers an unauthenticated request with **404**, not 401 — a
confusing failure, so it is reported explicitly below. The token comes from
``$LICHESS_TOKEN`` if set, otherwise from ``../lichess-bot/config.yml`` read with a real
YAML parser: that file carries a trailing ``# Lichess OAuth2 Token.`` comment on the same
line, and a ``sed``-style extraction silently appends it, after which lichess replies
``No such token``.

@author Michael Fleischhauer
"""

import argparse
import io
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    import chess
    import chess.engine
    import chess.pgn
    import requests
except ImportError as missing:
    sys.exit(f"missing dependency: {missing.name}. Run this with a Python that has "
             f"python-chess and requests, e.g. ../lichess-bot/venv/bin/python")

DEFAULT_USER = "myChessJava"
DEFAULT_STOCKFISH = "/opt/homebrew/bin/stockfish"

DEFAULT_SCAN_DEPTH = 15
DEFAULT_SCAN_THRESHOLD_CP = 150
DEFAULT_VERIFY_DEPTH = 20
DEFAULT_THRESHOLD_CP = 300
DEFAULT_WINDOW_MOVES = 3
DEFAULT_PHASE_THRESHOLD_CP = 250

# A phase starting with myChess already worse than this is skipped: once the game is lost,
# further losses say nothing about the mistake that lost it.
DEFAULT_HOPELESS_CP = 300

# Losses at or above this are recorded during the scan even when they stay below
# --scan-threshold, so a lower candidate threshold can be applied later without
# re-scanning. Below this everything is noise for our purposes.
RECORD_FLOOR_CP = 50

# Mate scores are mapped onto this many centipawns so losses stay comparable integers.
MATE_SCORE_CP = 10_000

# Evaluations are clamped to +/- this before a loss is computed. Without it the ranking is
# dominated by mate arithmetic rather than by mistakes: mate-in-3 becoming mate-in-8 reads
# as a 5000 cp "loss", and a position already won by force cannot be improved on, so every
# move in it looks catastrophic. Ten pawns is decisive by any measure, so clamping there
# loses no information about who is winning while making the numbers comparable.
CLAMP_CP = 1_000


def clamp(centipawns: int) -> int:
    """Clamp an evaluation to +/- `CLAMP_CP` so losses stay meaningful."""
    return max(-CLAMP_CP, min(CLAMP_CP, centipawns))


def loss_of(before: int, after: int) -> int:
    """Return the clamped loss between two evaluations, both from the mover's side."""
    return clamp(before) - clamp(after)

REPO_ROOT = Path(__file__).resolve().parent.parent
BASE_DIR = REPO_ROOT / "test-results" / "lichess"
GAMES_DIR = BASE_DIR / "games"
BLUNDERS_DIR = BASE_DIR / "blunders"
STATE_FILE = BASE_DIR / "state.json"
FINDINGS_DIR = BASE_DIR / "findings"
REPORT_FILE = BASE_DIR / "blunders.md"


def game_date(path: Path) -> str:
    """
    Return the game's ``YYYY-MM-DD`` date from its PGN headers, or ``"unknown"``.

    Read from the file rather than from a parsed game so the cheap callers — the storage
    layout and the monthly grouping — do not have to parse the moves.
    """
    with path.open() as handle:
        for line in handle:
            if line.startswith("[UTCDate "):
                return line.split('"')[1].replace(".", "-")
            if line.startswith("1."):
                break

    return "unknown"


def dated_path(root: Path, date: str, name: str) -> Path:
    """
    Return `root`/YYYY/MM/DD/`name`, keeping any one directory small.

    A flat directory would hold thousands of PGNs after a few months; worse, in a tracked
    `test-results/` every listing and diff would grow with it. Dated directories stop
    changing once a day is over.
    """
    parts = date.split("-")
    if len(parts) != 3:
        return root / "unknown" / name

    return root / parts[0] / parts[1] / parts[2] / name


def load_findings() -> dict:
    """Merge every monthly findings file into one dictionary keyed by game id."""
    merged: dict = {}
    if FINDINGS_DIR.is_dir():
        for path in sorted(FINDINGS_DIR.glob("*.json")):
            with path.open() as handle:
                merged.update(json.load(handle))

    return merged


def save_findings(findings: dict) -> None:
    """
    Write the findings back, one file per calendar month.

    Monthly files exist so a completed month never changes again: a single findings.json
    is rewritten on every game, and in a tracked directory git then stores a fresh copy of
    the whole thing each time.
    """
    by_month: dict[str, dict] = {}
    for game_id, entry in findings.items():
        month = entry.get("date", "unknown")[:7] or "unknown"
        by_month.setdefault(month, {})[game_id] = entry

    for month, subset in by_month.items():
        save_json(FINDINGS_DIR / f"{month}.json", subset)


def read_token() -> str:
    """
    Return the lichess API token, from the environment or the lichess-bot config.

    :raises SystemExit: if no token can be found.
    """
    from_env = os.environ.get("LICHESS_TOKEN")
    if from_env:
        return from_env.strip()

    config = REPO_ROOT.parent / "lichess-bot" / "config.yml"
    if not config.is_file():
        sys.exit(f"no token: set $LICHESS_TOKEN or provide {config}")

    try:
        import yaml
    except ImportError:
        sys.exit("no token: set $LICHESS_TOKEN, or install PyYAML to read it from config.yml")

    with config.open() as handle:
        token = yaml.safe_load(handle).get("token")

    if not token:
        sys.exit(f"no token: {config} has no 'token' key")

    return str(token).strip()


def load_json(path: Path, fallback: object) -> object:
    """Return the JSON content of `path`, or `fallback` if it does not exist yet."""
    if not path.is_file():
        return fallback

    with path.open() as handle:
        return json.load(handle)


def save_json(path: Path, payload: object) -> None:
    """Write `payload` as indented, key-sorted JSON so diffs stay readable."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
        handle.write("\n")


def game_timestamp_ms(headers: dict) -> int:
    """
    Return the game's start time in epoch milliseconds, from its PGN headers.

    The PGN export carries `UTCDate` / `UTCTime` rather than a numeric timestamp, so it
    is reassembled here to drive the `since` parameter on the next run. Returns 0 when
    the headers are missing or malformed, leaving the caller with id-based deduplication.
    """
    date, time = headers.get("UTCDate"), headers.get("UTCTime")
    if not date or not time:
        return 0

    try:
        stamp = datetime.strptime(f"{date} {time}", "%Y.%m.%d %H:%M:%S")
    except ValueError:
        return 0

    return int(stamp.replace(tzinfo=timezone.utc).timestamp() * 1000)


def fetch_new_games(user: str, state: dict) -> list[str]:
    """
    Download every game newer than the last run and store it as its own PGN file.

    :param user: the lichess account whose games are exported.
    :param state: persisted state; its `last_game_ms` and `known_ids` are updated.
    :return: the ids of games written by this call.
    """
    params = {"clocks": "false", "evals": "false", "opening": "true"}
    if state["last_game_ms"]:
        # +1 ms so the newest known game is not returned again.
        params["since"] = state["last_game_ms"] + 1

    response = requests.get(
        f"https://lichess.org/api/games/user/{user}",
        params=params,
        headers={"Authorization": f"Bearer {read_token()}",
                 "Accept": "application/x-chess-pgn"},
        timeout=180,
    )

    if response.status_code == 404:
        sys.exit("lichess answered 404. This endpoint requires a token, and an "
                 "unauthenticated request is answered with 404 rather than 401 — "
                 "check $LICHESS_TOKEN or the config.yml token.")
    response.raise_for_status()

    known = set(state["known_ids"])
    written: list[str] = []
    newest_ms = state["last_game_ms"]
    stream = io.StringIO(response.text)

    GAMES_DIR.mkdir(parents=True, exist_ok=True)

    while (game := chess.pgn.read_game(stream)) is not None:
        game_id = game.headers.get("GameId") or game.headers.get("Site", "").rsplit("/", 1)[-1]
        if not game_id or game_id in known:
            continue

        date = game.headers.get("UTCDate", "").replace(".", "-") or "unknown"
        target = dated_path(GAMES_DIR, date, f"{game_id}.pgn")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(str(game) + "\n\n")
        known.add(game_id)
        written.append(game_id)
        newest_ms = max(newest_ms, game_timestamp_ms(game.headers))

    state["known_ids"] = sorted(known)
    state["last_game_ms"] = newest_ms

    return written


def pov_cp(score: chess.engine.PovScore, color: chess.Color) -> int:
    """Return `score` in centipawns from `color`'s point of view, mate included."""
    return score.pov(color).score(mate_score=MATE_SCORE_CP)


def evaluate_move(engine: chess.engine.SimpleEngine, fen: str, uci: str,
                  color: chess.Color, depth: int, chess960: bool = False) -> dict:
    """
    Evaluate one move: what was available before it, and what it actually reached.

    Both evaluations are taken from `color`'s point of view, so the difference is the
    loss that side incurred. This is the single primitive both the scan and the verify
    phase use, which keeps their numbers directly comparable.

    `chess960` must be passed for Fischer-random games. Rebuilding the board from a bare
    FEN loses that property, and a castling move is then parsed against the wrong rules:
    Stockfish answers with its own castling encoding, python-chess rejects it as illegal,
    the UCI dialogue breaks, and the next `analyse` call waits forever. The flag on the
    board is enough: python-chess derives `UCI_Chess960` from it and refuses an attempt to
    set that option by hand ("cannot set UCI_Chess960 which is automatically managed").
    """
    board = chess.Board(fen, chess960=chess960)
    limit = chess.engine.Limit(depth=depth)

    before = pov_cp(engine.analyse(board, limit)["score"], color)
    board.push(chess.Move.from_uci(uci))
    after = pov_cp(engine.analyse(board, limit)["score"], color)

    return {"loss_cp": loss_of(before, after),
            "eval_before_cp": before, "eval_after_cp": after, "depth": depth}


def scan_game(path: Path, engine: chess.engine.SimpleEngine,
              depth: int, user: str) -> tuple[str, chess.Color, list[dict], list[list[int]], bool]:
    """
    Scan one stored game and return every notable evaluation loss by `user`.

    Only the moves played by `user` are examined. The position *before* each move is
    recorded as a FEN together with the move in UCI form, which is what lets the verify
    phase re-check a candidate without replaying the game.

    :return: the game id, the color `user` played, the detailed findings, and a compact
        ``[move_number, loss_cp, eval_before_cp]`` entry for *every* move `user` played,
        which is what the phase detection needs.
    :raises ValueError: if `user` did not play in this game.
    """
    with path.open() as handle:
        game = chess.pgn.read_game(handle)

    headers = game.headers
    if headers.get("White") == user:
        color = chess.WHITE
    elif headers.get("Black") == user:
        color = chess.BLACK
    else:
        raise ValueError(f"{path.name}: {user} did not play in this game")

    board = game.board()
    chess960 = board.chess960

    findings: list[dict] = []
    losses: list[list[int]] = []

    for move in game.mainline_moves():
        if board.turn != color:
            board.push(move)
            continue

        # Capture the position BEFORE the move — the verify phase needs exactly this.
        fen_before = board.fen()
        move_number = board.fullmove_number
        san = board.san(move)

        measured = evaluate_move(engine, fen_before, move.uci(), color, depth, chess960)
        board.push(move)

        # Every move's loss is recorded, however small: the phase detection sums
        # consecutive losses, and dropping the small ones would understate a window
        # made of several modest mistakes.
        losses.append([move_number, measured["loss_cp"], measured["eval_before_cp"]])

        if measured["loss_cp"] >= RECORD_FLOOR_CP:
            findings.append({"move_number": move_number,
                             "move": san,
                             "uci": move.uci(),
                             "fen_before": fen_before,
                             "scan": measured})

    return headers.get("GameId", path.stem), color, findings, losses, chess960


def fmt_loss(centipawns: int) -> str:
    """
    Format a loss. Losses are never clamped themselves — only the evaluations they are
    computed from — so a plain figure is honest here. The maximum possible is 20.00, from
    a clamped +10 down to a clamped -10.
    """
    return f"{centipawns / 100:+.2f}"


def fmt_cp(centipawns: int) -> str:
    """
    Format a centipawn value for the report, naming mate rather than printing 92.39.

    Losses that run into a mate score are inflated by `MATE_SCORE_CP`, which is correct
    for ranking — allowing mate *is* the worst blunder — but unreadable as a pawn count.
    """
    if centipawns >= MATE_SCORE_CP - 1000:
        return "mate"
    if centipawns <= -(MATE_SCORE_CP - 1000):
        return "mated"
    if centipawns >= CLAMP_CP:
        return f"\u2265 +{CLAMP_CP / 100:.2f}"
    if centipawns <= -CLAMP_CP:
        return f"\u2264 -{CLAMP_CP / 100:.2f}"

    return f"{centipawns / 100:+.2f}"


def find_phases(losses: list[list[int]], window: int, threshold: int,
                hopeless: int = DEFAULT_HOPELESS_CP) -> list[dict]:
    """
    Find stretches in which myChess bled away at least `threshold` centipawns.

    A phase is `window` consecutive *own* moves whose losses sum to at least
    `threshold`. Summing myChess's own per-move losses — rather than comparing the
    evaluation at the start and the end of the window — keeps the opponent out of the
    figure: a gift from the other side must not offset our own mistakes.

    Windows are taken greedily from the start of the game and do not overlap, so a long
    slide is reported as one phase rather than as every sub-window of it.

    :param losses: ``[move_number, loss_cp]`` for every move myChess played, in order.
    :param window: how many consecutive own moves form a phase.
    :param threshold: minimum summed loss for the phase to count.
    :param hopeless: skip a phase that already starts with myChess worse than this many
        centipawns. Losing another two pawns from -8 is not the mistake worth studying,
        and without this the report fills up with the tail end of already-lost games.
        Phases starting from an already *forced* win (at or above `CLAMP_CP`) are skipped
        too, for the mirror-image reason — but note the two bounds are deliberately
        different: being merely two pawns up is exactly the interesting starting point.
    """
    phases: list[dict] = []
    index = 0

    while index + window <= len(losses):
        chunk = losses[index:index + window]
        total = sum(entry[1] for entry in chunk)
        eval_at_start = chunk[0][2] if len(chunk[0]) > 2 else 0

        # Skipped at both ends, but not symmetrically: a phase starting from a won
        # position is the whole point (the top findings start around +2 to +5), while one
        # starting from a *forced* win is arithmetic — there is nothing left to improve on.
        if total >= threshold and -hopeless < eval_at_start < CLAMP_CP:
            phases.append({"from_move": chunk[0][0],
                           "to_move": chunk[-1][0],
                           "total_loss_cp": total,
                           "eval_at_start_cp": eval_at_start,
                           "moves": chunk})
            index += window
        else:
            index += 1

    return phases


def phase_move_numbers(findings: dict, window: int, threshold: int,
                       hopeless: int = DEFAULT_HOPELESS_CP) -> set[tuple[str, int]]:
    """Return every (game id, move number) that sits inside a detected losing phase."""
    inside: set[tuple[str, int]] = set()

    for game_id, entry in findings.items():
        for phase in find_phases(entry.get("losses", []), window, threshold, hopeless):
            for move_number, *_ in phase["moves"]:
                inside.add((game_id, move_number))

    return inside


def verify_candidates(findings: dict, engine: chess.engine.SimpleEngine,
                      scan_threshold: int, depth: int,
                      extra: set[tuple[str, int]] | None = None) -> int:
    """
    Re-evaluate the scan's candidates at a greater depth, in place.

    Only positions whose scan loss reaches `scan_threshold` are re-checked, and only
    those not already verified at this depth or deeper. Two evaluations per candidate,
    which is why the expensive depth is affordable here but not in the scan.

    :return: the number of candidates verified by this call.
    """
    wanted = extra or set()
    pending = [(game_id, entry, hit)
               for game_id, entry in findings.items()
               for hit in entry["findings"]
               if (hit["scan"]["loss_cp"] >= scan_threshold
                   or (game_id, hit["move_number"]) in wanted)
               and hit.get("verified", {}).get("depth", 0) < depth]

    if not pending:
        return 0

    print(f"verifying {len(pending)} candidate(s) at depth {depth}")
    colors = {"white": chess.WHITE, "black": chess.BLACK}

    for number, (game_id, entry, hit) in enumerate(pending, start=1):
        chess960 = entry.get("chess960", False)
        hit["verified"] = evaluate_move(engine, hit["fen_before"], hit["uci"],
                                        colors[entry["color"]], depth, chess960)
        save_findings(findings)
        scanned, verified = hit["scan"]["loss_cp"], hit["verified"]["loss_cp"]
        print(f"  [{number}/{len(pending)}] {game_id} move {hit['move_number']} "
              f"{hit['move']}: scan {scanned} -> verified {verified} cp")

    return len(pending)


def write_report(findings: dict, threshold: int, scan_threshold: int,
                 window: int = DEFAULT_WINDOW_MOVES,
                 phase_threshold: int = DEFAULT_PHASE_THRESHOLD_CP,
                 hopeless: int = DEFAULT_HOPELESS_CP) -> int:
    """
    Regenerate the human-readable blunder report and return the number of blunders.

    Only *verified* losses count, so a candidate that shrinks below `threshold` at the
    greater depth is correctly dropped. Unverified candidates are listed separately
    rather than silently ignored.
    """
    confirmed, unverified = [], []
    for game_id, entry in findings.items():
        for hit in entry["findings"]:
            if hit["scan"]["loss_cp"] < scan_threshold:
                continue
            if "verified" not in hit:
                unverified.append((game_id, hit))
            elif hit["verified"]["loss_cp"] >= threshold:
                confirmed.append((game_id, entry, hit))

    confirmed.sort(reverse=True, key=lambda row: row[2]["verified"]["loss_cp"])

    lines = ["# myChess blunders found on lichess",
             "",
             f"Generated by `tools/lichess-blunder-scan.py`. Candidates are moves losing at least "
             f"**{scan_threshold} cp** in the scan; a candidate counts as a blunder when the "
             f"deeper verification confirms a loss of at least **{threshold} cp**.",
             "",
             f"**{len(confirmed)} single-move blunders in "
             f"{len({row[0] for row in confirmed})} games**, out of {len(findings)} analysed. "
             f"Losing phases are listed first: {window} consecutive own moves losing at least "
             f"{phase_threshold} cp together.",
             ""]

    phase_rows = []
    for game_id, entry in findings.items():
        for phase in find_phases(entry.get("losses", []), window, phase_threshold, hopeless):
            verified_total = 0
            detail = []
            first_fen = ""
            for move_number, scan_loss, *_ in phase["moves"]:
                hit = next((h for h in entry["findings"] if h["move_number"] == move_number), None)
                loss = hit["verified"]["loss_cp"] if hit and "verified" in hit else scan_loss
                verified_total += loss
                if loss >= RECORD_FLOOR_CP:
                    detail.append(f"{move_number}.{hit['move'] if hit else '?'} ({fmt_loss(loss)})")
                    # The position the slide starts from — the one to paste into a board.
                    if not first_fen and hit:
                        first_fen = hit["fen_before"]
            if verified_total >= phase_threshold:
                phase_rows.append((verified_total, game_id, entry, phase, detail, first_fen))

    phase_rows.sort(reverse=True, key=lambda row: row[0])

    if phase_rows:
        lines += [f"## Losing phases — {window} consecutive own moves, at least "
                  f"{phase_threshold} cp lost",
                  "",
                  "The interesting failure mode: no single move crosses the blunder threshold, "
                  "yet a won game is gone in three. Summed over myChess's own moves only, so "
                  "gifts from the opponent do not offset our mistakes.",
                  "",
                  f"Phases starting below -{hopeless} cp are skipped (losing more from a lost "
                  f"position is not the mistake worth studying), and so are those starting at or "
                  f"above +{CLAMP_CP / 100:.0f} (a forced win leaves nothing to improve on). "
                  f"Starting merely a pawn or two ahead is the interesting case, not a filtered one.",
                  "",
                  "| Total loss | At start | Game | Moves | Contributing moves | Color "
                  "| FEN before the first of them |",
                  "|---:|---:|---|---|---|---|---|"]
        for total, game_id, entry, phase, detail, first_fen in phase_rows:
            lines.append(f"| **{fmt_loss(total)}** "
                         f"| {fmt_cp(phase.get('eval_at_start_cp', 0))} "
                         f"| [{game_id}](https://lichess.org/{game_id}) "
                         f"| {phase['from_move']}–{phase['to_move']} "
                         f"| {', '.join(detail) or '—'} | {entry['color']} "
                         f"| `{first_fen}` |")
        lines.append("")

    if confirmed:
        lines += [f"## Single-move blunders — at least {threshold} cp in one move",
                  "",
                  "| Verified loss | Scan | Game | Move | Played | Before | After | Color "
                  "| FEN before the move |",
                  "|---:|---:|---|---:|---|---:|---:|---|---|"]
        for game_id, entry, hit in confirmed:
            ver = hit["verified"]
            lines.append(
                f"| **{fmt_loss(ver['loss_cp'])}** | {fmt_loss(hit['scan']['loss_cp'])} "
                f"| [{game_id}](https://lichess.org/{game_id}) | {hit['move_number']} "
                f"| `{hit['move']}` | {fmt_cp(ver['eval_before_cp'])} "
                f"| {fmt_cp(ver['eval_after_cp'])} | {entry['color']} "
                f"| `{hit['fen_before']}` |")
        lines.append("")

    if unverified:
        lines += [f"## {len(unverified)} candidate(s) not yet verified",
                  "",
                  "Run the verify phase to resolve these; they are neither confirmed nor dismissed.",
                  ""]
        for game_id, hit in sorted(unverified, key=lambda row: -row[1]["scan"]["loss_cp"]):
            lines.append(f"- {game_id} move {hit['move_number']} `{hit['move']}` "
                         f"— scan loss {fmt_loss(hit['scan']['loss_cp'])}")
        lines.append("")

    lines += ["## Reading these numbers",
              "",
              "The *scan* column is the loss measured at the shallow depth, the *verified* column "
              "at the deeper one. Expect the verified figure to be **larger**: a shallow search "
              "does not yet see the consequence of a mistake and therefore scores the position "
              "after it too kindly.",
              "",
              f"All losses of at least {RECORD_FLOOR_CP} cp are kept in `findings.json`, so both "
              "thresholds can be changed afterwards — `--report-only` re-filters without any new "
              "analysis, as long as the candidates in question were verified.",
              ""]

    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    REPORT_FILE.write_text("\n".join(lines))

    return len(confirmed)


def copy_blunder_games(findings: dict, threshold: int) -> int:
    """Copy every game with a confirmed blunder into `blunders/`, named by move number."""
    copied = 0
    for game_id, entry in findings.items():
        hits = [hit for hit in entry["findings"]
                if hit.get("verified", {}).get("loss_cp", 0) >= threshold]
        date = entry.get("date", "unknown")
        source = dated_path(GAMES_DIR, date, f"{game_id}.pgn")
        if not hits or not source.is_file():
            continue

        worst = max(hits, key=lambda hit: hit["verified"]["loss_cp"])
        target = dated_path(BLUNDERS_DIR, date, f"{game_id}-move{worst['move_number']}.pgn")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text())
        copied += 1

    return copied


def parse_args() -> argparse.Namespace:
    """Define and parse the command-line options."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--user", default=DEFAULT_USER, help="lichess account (default: %(default)s)")
    parser.add_argument("--stockfish", default=DEFAULT_STOCKFISH, help="path to the Stockfish binary")

    parser.add_argument("--scan-depth", type=int, default=DEFAULT_SCAN_DEPTH,
                        help="phase 2 search depth, cheap and wide (default: %(default)s)")
    parser.add_argument("--scan-threshold", type=int, default=DEFAULT_SCAN_THRESHOLD_CP,
                        help="centipawn loss making a move a candidate (default: %(default)s)")
    parser.add_argument("--verify-depth", type=int, default=DEFAULT_VERIFY_DEPTH,
                        help="phase 3 search depth, applied to candidates only (default: %(default)s)")
    parser.add_argument("--threshold", type=int, default=DEFAULT_THRESHOLD_CP,
                        help="verified centipawn loss counting as a single-move blunder "
                             "(default: %(default)s)")
    parser.add_argument("--window", type=int, default=DEFAULT_WINDOW_MOVES,
                        help="consecutive own moves forming a phase (default: %(default)s)")
    parser.add_argument("--phase-threshold", type=int, default=DEFAULT_PHASE_THRESHOLD_CP,
                        help="summed centipawn loss over the window counting as a losing phase "
                             "(default: %(default)s)")
    parser.add_argument("--hopeless", type=int, default=DEFAULT_HOPELESS_CP,
                        help="skip phases starting with myChess worse than this many centipawns "
                             "(default: %(default)s)")

    parser.add_argument("--fetch-only", action="store_true", help="download only")
    parser.add_argument("--scan-only", action="store_true", help="download and scan, do not verify")
    parser.add_argument("--verify-only", action="store_true", help="verify stored candidates only")
    parser.add_argument("--report-only", action="store_true", help="regenerate the report only")
    parser.add_argument("--rescan", action="store_true", help="scan every stored game again")

    return parser.parse_args()


def run_scan(args: argparse.Namespace, state: dict, findings: dict) -> None:
    """Run the scan phase over every game not yet scanned."""
    done = set() if args.rescan else set(state["scanned_ids"])
    todo = sorted(path for path in GAMES_DIR.rglob("*.pgn") if path.stem not in done)
    if not todo:
        print("nothing new to scan")
        return

    print(f"scanning {len(todo)} game(s) at depth {args.scan_depth}")
    engine = chess.engine.SimpleEngine.popen_uci(args.stockfish)
    try:
        for number, path in enumerate(todo, start=1):
            try:
                game_id, color, hits, losses, chess960 = scan_game(
                    path, engine, args.scan_depth, args.user)
            except ValueError as skipped:
                print(f"  [{number}/{len(todo)}] skipped — {skipped}")
                state["scanned_ids"] = sorted(set(state["scanned_ids"]) | {path.stem})
                continue
            except (chess.engine.EngineError, chess.engine.EngineTerminatedError) as broken:
                # A broken UCI dialogue leaves the protocol unusable and the next analyse
                # call would wait forever, so the engine is replaced rather than reused.
                # The game is left unscanned so a later run retries it.
                print(f"  [{number}/{len(todo)}] {path.stem}: engine error, restarting — {broken}")
                try:
                    engine.close()
                except Exception:
                    # The engine is already broken; closing it is best-effort.
                    pass
                engine = chess.engine.SimpleEngine.popen_uci(args.stockfish)
                continue

            candidates = [hit for hit in hits if hit["scan"]["loss_cp"] >= args.scan_threshold]
            findings[game_id] = {"color": "white" if color == chess.WHITE else "black",
                                 "chess960": chess960,
                                 "date": game_date(path),
                                 "findings": hits,
                                 "losses": losses}

            state["scanned_ids"] = sorted(set(state["scanned_ids"]) | {path.stem})
            save_json(STATE_FILE, state)
            save_findings(findings)

            marker = f"  <-- {len(candidates)} candidate(s)" if candidates else ""
            variant = " [960]" if chess960 else ""
            print(f"  [{number}/{len(todo)}] {game_id}{variant}{marker}")
    finally:
        engine.quit()


def main() -> None:
    """Run the requested phases in order."""
    args = parse_args()
    findings = load_findings()
    state = load_json(STATE_FILE, {"last_game_ms": 0, "known_ids": [], "scanned_ids": []})
    state.setdefault("scanned_ids", state.pop("analysed_ids", []))

    if args.report_only:
        count = write_report(findings, args.threshold, args.scan_threshold,
                             args.window, args.phase_threshold, args.hopeless)
        print(f"{count} blunder(s); report: {REPORT_FILE.relative_to(REPO_ROOT)}")
        return

    if not args.verify_only:
        new_ids = fetch_new_games(args.user, state)
        save_json(STATE_FILE, state)
        print(f"fetched {len(new_ids)} new game(s); {len(state['known_ids'])} stored in total")

        if args.fetch_only:
            return

        run_scan(args, state, findings)

        if args.scan_only:
            write_report(findings, args.threshold, args.scan_threshold,
                         args.window, args.phase_threshold, args.hopeless)
            print("scan complete; candidates are listed as unverified in the report")
            return

    # Moves inside a detected losing phase are verified too, even when no single one of
    # them reaches --scan-threshold: the phase total is what matters there.
    in_phases = phase_move_numbers(findings, args.window, args.phase_threshold, args.hopeless)

    engine = chess.engine.SimpleEngine.popen_uci(args.stockfish)
    try:
        verify_candidates(findings, engine, args.scan_threshold, args.verify_depth, in_phases)
    finally:
        engine.quit()
    save_findings(findings)

    count = write_report(findings, args.threshold, args.scan_threshold,
                         args.window, args.phase_threshold, args.hopeless)
    copied = copy_blunder_games(findings, args.threshold)
    print(f"{count} blunder(s) in {copied} game(s). Report: {REPORT_FILE.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
