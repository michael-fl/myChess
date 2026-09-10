# The five foreign anchor engines, shared by every anchor measurement.
#
# Sourced rather than copied: the list lived verbatim in run-anchor-bracket.sh and
# run-king-line-gauntlet.sh, held together by a comment saying the two were identical.
# A rating that drifts between two copies silently changes what Ordo anchors against.
#
# Format: name|wrapper|protocol|CCRL rating (empty = free in Ordo)|engine options
#
# Kojiro is deliberately left free: with four fixed anchors the rating scale is already
# determined, and a fifth constraint would only fight the other four if its published
# rating disagrees with how it plays here.

ANCHOR_HASH_MB="${ANCHOR_HASH_MB:-256}"
BBC_HASH_MB="${BBC_HASH_MB:-128}"

ANCHORS="
TSCP|./engines/tscp-1.81-elo1609/tscp.sh|xboard|1609|
ZetaDva|./engines/ZetaDva-0402-unrated/zetadva.sh|xboard|1801|
Princhess|./engines/princhess-0.7.0-elo1985/princhess.sh|uci|1985|option.Hash=$ANCHOR_HASH_MB
BBC|./engines/BBC-1.1-elo2019/bbc.sh|uci|2019|option.Hash=$BBC_HASH_MB
Kojiro|./engines/Kojiro-0.1.4-elo1984/kojiro.sh|uci||option.Hash=$ANCHOR_HASH_MB
"

ANCHOR_COUNT=$(printf '%s\n' "$ANCHORS" | grep -c '|')
