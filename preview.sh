#!/usr/bin/env bash
# Renders layout previews (PNG) with plain Java. Requires ./build-core.sh to have run first.
set -euo pipefail
cd "$(dirname "$0")"
if command -v javac >/dev/null 2>&1; then JAVAC=(javac); else JAVAC=(java -m jdk.compiler/com.sun.tools.javac.Main -source 21 -target 21 -Xlint:-options); fi
mkdir -p build/preview-classes
"${JAVAC[@]}" -cp build/classes:build/test-classes -d build/preview-classes core/src/test/java/dev/kingbrezz/featherfps/core/ui/PreviewMain.java
java -Djava.awt.headless=true -cp build/classes:build/test-classes:build/preview-classes dev.kingbrezz.featherfps.core.ui.PreviewMain "${1:-docs/preview}"
