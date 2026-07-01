#!/usr/bin/env bash
# Dev-mode launcher for myChess in UCI mode — sibling of mychess-uci.sh.
#
# Loads classes directly from target/classes and target/dependency, so any
# `mvn compile` (no need for `mvn package`) is picked up the next time a
# GUI like Cute Chess starts a new game.
#
# Use this script as the engine command in the GUI while you are actively
# editing code. The sibling mychess-uci.sh keeps loading from test/* and
# is reserved for long-running cutechess-cli matches that must not be
# disturbed by intermediate rebuilds. The two scripts intentionally share
# no state: separate stderr logs, separate classpaths.

set -euo pipefail

# Resolve this script's directory regardless of how it was invoked.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Pin JDK 25 — the GUI's inherited JAVA_HOME is often an older JBR / JDK.
export JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-25.jdk/Contents/Home

# Change to project root so OpeningDB resolves db/openings.db relatively.
cd "$DIR"

# Separate log file from the snapshot launcher, so a SPRT match using
# mychess-uci.sh and an interactive GUI session using this script do not
# fight over the same file.
LOG="$DIR/mychess-stderr-dev.log"
printf '\n--- %s myChess UCI (dev) started (pid %d) ---\n' "$(date '+%Y-%m-%d %H:%M:%S')" $$ >> "$LOG"

exec "$JAVA_HOME/bin/java" \
    -cp "target/classes:target/dependency/*" \
    org.michaelfl.mychess.MyChessMain uci \
    2> >(tee -a "$LOG" >&2)
