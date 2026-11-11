#!/usr/bin/env bash
# Compiles and tests the loader-independent core with a plain JDK (no Gradle, no network).
set -euo pipefail
cd "$(dirname "$0")"
if command -v javac >/dev/null 2>&1; then
  JAVAC=(javac --release 21 -Xlint:all)
else
  # JRE-only fallback (jdk.compiler module without ct.sym): --release is unavailable
  JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:all,-options)
fi
rm -rf build && mkdir -p build/classes build/test-classes
"${JAVAC[@]}" -d build/classes $(find core/src/main -name '*.java')
"${JAVAC[@]}" -cp build/classes -d build/test-classes $(find core/src/test -name '*.java' ! -name 'PreviewMain.java')
status=0
java -cp build/classes:build/test-classes dev.kingbrezz.featherfps.core.CoreTests || status=1
java -cp build/classes:build/test-classes dev.kingbrezz.featherfps.core.ui.UiTests || status=1
exit $status
