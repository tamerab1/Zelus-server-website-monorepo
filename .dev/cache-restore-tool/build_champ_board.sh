#!/bin/bash
# Pit Championship Board reskin (2026-10-15): new model 60132 (frame, crest, banners, painted face), sprites 24532-24535,
# textures 210-213 (appended above the max), object 44930's model list -> [60132] in both slots.
# Usage: build_champ_board.sh verify|apply <cacheDir> <classesDir> <classpath> <previewDir>
set -euo pipefail
java -cp "$3;$4" BuildChampionshipBoard "$1" "$2" "$5" 2>&1 | grep -v SLF4J
