#!/usr/bin/env bash
# ui.sh <art-section|all> <group> [preview opts...]: paint, quantize, restyle, preview
set -e
U="C:/Users/tamer/AppData/Local/Temp/claude/b45af14b2b7e04d4/afd203c5-91e3-4626-97fc-47a5e42d0b84/scratchpad/ui"
R="C:/Users/tamer/OneDrive/Desktop/zsv1/osrs server"
TOOL="$R/server zelus/.dev/cache-restore-tool"
K="$R/server zelus rev241/tools/rev241-migration/assets/karn_ui"
CP="$(tr -d '\r\n' < "$TOOL/cp_with_jna.txt")"
cd "$U"
mkdir -p kcls
javac -cp "$CP" -d kcls "$K"/KarnArt*.java "$K/KarnIcons.java" "$K/MakeKarnUi2.java" "$K"/Restyle*.java "$TOOL/src/BuildBondsInterface.java" 2>&1 | grep -v "^Note" || true
sec="$1"; grp="$2"; shift 2
java -cp "kcls;$CP" MakeKarnUi2 "$K" "$([ "$sec" = all ] && echo "" || echo "$sec")"
python "$K/quantize.py" "$K" >/dev/null
java -cp "kcls;$CP" RestyleKarnUi2 "$R/data zelus rev241/data/cache" "$K" "$grp" 2>&1 | grep -v SLF4J
java -cp "cls;$CP" IfPreview "$R/data zelus rev241/data/cache241" "$K/interface_$grp" "$grp" "out/new_$grp.png" "png=$K/png_q" "$@" 2>&1 | grep -v SLF4J || true
echo "preview out/new_$grp.png"
