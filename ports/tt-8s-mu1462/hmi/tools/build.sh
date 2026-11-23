#!/bin/bash
# Builds tt-zoom.jar from ../src against your own converted HMI jar.
#   compile (javac --release 8) -> PatchZoom (patch stock classes, rewrite to class version 46)
#   -> VerifyZoom (ASM verifier) -> checklib.py (only java.* calls the car's library has)
#   -> deterministic jar.
# Nothing from the firmware is stored in this repository; point these at your own copies:
#   STOCK_JAR  lsd.jxe converted to a jar (jxe2jar), e.g. mu1462-stock.jar
#   ASM_DIR    folder with asm-9.7.jar, asm-tree-9.7.jar, asm-analysis-9.7.jar, asm-util-9.7.jar
#   JAVA_LIB   folder with the car's java/* class library extracted as .class files
#   JDK_BIN    (optional) JDK 11+ bin folder
# usage: STOCK_JAR=... ASM_DIR=... JAVA_LIB=... bash build.sh <tag> [out dir]
set -euo pipefail
TAG=${1:?version tag, e.g. 19}
: "${STOCK_JAR:?set STOCK_JAR}" "${ASM_DIR:?set ASM_DIR}" "${JAVA_LIB:?set JAVA_LIB}"
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$HERE/../src
OUT=${2:-$HERE/../out/v$TAG}
J=${JDK_BIN:+$JDK_BIN/}
if command -v cygpath >/dev/null 2>&1; then w() { cygpath -w "$1"; }; SEP=';'; else w() { echo "$1"; }; SEP=':'; fi
ASM="$(w $ASM_DIR/asm-9.7.jar)$SEP$(w $ASM_DIR/asm-tree-9.7.jar)$SEP$(w $ASM_DIR/asm-analysis-9.7.jar)$SEP$(w $ASM_DIR/asm-util-9.7.jar)"
B=$(mktemp -d)
mkdir -p "$B/gate" "$B/out" "$B/tools" "$OUT"
SRCS=$(cd "$SRC" && find . -name '*.java' | sed 's|^\./||')
(cd "$SRC" && "${J}javac" -nowarn --release 8 -encoding UTF-8 -cp "$(w $STOCK_JAR)" -d "$(w $B/gate)" $SRCS)
"${J}javac" -nowarn -cp "$ASM" -d "$(w $B/tools)" "$(w $HERE/PatchZoom.java)" "$(w $HERE/VerifyZoom.java)"
"${J}java" -cp "$(w $B/tools)$SEP$ASM" PatchZoom "$(w $STOCK_JAR)" "$(w $B/gate/local/tt/ZoomGate.class)" "$(w $B/out)"
"${J}java" -cp "$(w $B/tools)$SEP$ASM" VerifyZoom "$(w $STOCK_JAR)" "$(w $B/out)"
OURS=$(cd "$B/out" && find local de/audi/tghu/navi/app/cluster/TtRgi.class -name '*.class' | sed 's|\.class$||')
python3 -I "$(w $HERE/checklib.py)" "$(w $B/out)" "$(w $JAVA_LIB)" $OURS
python3 -I "$(w $HERE/mkjar.py)" "$(w $B/out)" "$(w $OUT/tt-zoom.jar)"
rm -rf "$B"
echo "built $OUT/tt-zoom.jar (contains patched stock classes: keep it out of git)"
