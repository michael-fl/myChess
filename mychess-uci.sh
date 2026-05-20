#!/usr/bin/env bash
# Launches myChess in UCI mode for chess GUIs (Cute Chess, Banksia, ...).
#
# Build prerequisites: run `mvn package` once to compile and copy the runtime
# classpath into target/. After that, this script is a self-contained entry
# point and can be referenced as the engine command in the GUI's settings.

set -euo pipefail

# Resolve this script's directory regardless of how it was invoked.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The project targets JDK 25. Pin JAVA_HOME explicitly — ignore any inherited
# value, because Cute Chess (and most GUIs) inherit the shell environment and
# the user's default JAVA_HOME often points to an older JDK (e.g. IntelliJ's
# bundled JBR). Adjust if your Corretto / Temurin / Oracle install lives
# elsewhere.
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home

# Change to the project root so OpeningDB can find db/openings.db at its
# default relative path. Cute Chess sets its own working directory per
# engine configuration, but doing this here makes the script work the same
# whether invoked from a GUI or directly from a shell.
cd "$DIR"

# Persistent stderr log for diagnostics across game sessions. stderr is also
# piped back to the GUI (Cute Chess shows it in its engine debug window) via
# `tee >&2`, so we don't lose the live view. stdout (= UCI protocol) is left
# alone — the GUI consumes it directly.
LOG="$DIR/mychess-stderr.log"
printf '\n--- %s myChess UCI started (pid %d) ---\n' "$(date '+%Y-%m-%d %H:%M:%S')" $$ >> "$LOG"

exec "$JAVA_HOME/bin/java" \
    -cp "target/classes:target/dependency/*" \
    org.michaelfl.mychess.MyChessMain uci \
    2> >(tee -a "$LOG" >&2)
