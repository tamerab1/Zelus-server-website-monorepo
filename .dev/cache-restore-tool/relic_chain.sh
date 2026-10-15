#!/bin/bash
# Creates npc 30572 "Golden Relic" (gold ToA Obelisk 11698) step by step. Usage: relic_chain.sh <cachePath>
C="$1"; J="java -cp fatjar;out_perkers"
$J CopyNpcVerbatimBatch apply "$C" 11698:30572 2>&1 | grep -v SLF4J | tail -1 && \
$J UpdateDefsInPlace apply "$C" npcname "30572=Golden Relic" 2>&1 | grep -v SLF4J | tail -1 && \
$J EditNpcRecolorInPlace apply "$C" 30572 142,6060,152,7094,154,7096,156,7098,8105,7111,8107,7113,8109,7115,8121,7127,8123,7129,61598,7100,62633,7111 2>&1 | grep -v SLF4J | tail -1 && \
$J SetNpcActionsInPlace apply "$C" 30572 "-,Attack,-,-,-" 2>&1 | grep -v SLF4J | tail -1 && \
$J EditNpcInPlace "$C" 30572 160 160 2>&1 | grep -v SLF4J | tail -1 && \
$J ListConfigIds "$C" npc 30570 91780 2>/dev/null && \
$J LookupNpcNames lookup "$C" 30572 2>/dev/null | head -1 | cut -c1-220 && \
$J DumpItemColors "$C" npc 30572 2>/dev/null | grep -i recolor
