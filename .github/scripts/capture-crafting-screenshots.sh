#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

OUT_DIR="${ROOT}/fabric/runs/client-crafting-screenshots/screenshots/simplylight-crafting"
mkdir -p "$(dirname "$OUT_DIR")"

echo "Running headless Fabric client crafting screenshot harness (xvfb)..."
xvfb-run -a -s "-screen 0 1280x720x24" \
  ./gradlew :fabric:runClientCraftingScreenshots --no-daemon

if [[ ! -d "$OUT_DIR" ]]; then
  echo "ERROR: Screenshot directory not created: $OUT_DIR"
  exit 1
fi

COUNT=$(find "$OUT_DIR" -name '*.png' | wc -l)
if [[ "$COUNT" -lt 10 ]]; then
  echo "ERROR: Expected at least 10 PNG screenshots, found $COUNT"
  exit 1
fi

echo "✓ Captured $COUNT crafting table screenshots in $OUT_DIR"
