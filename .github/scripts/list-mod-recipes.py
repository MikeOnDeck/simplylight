#!/usr/bin/env python3
"""List Simply Light recipes and registered items; flag items with no crafting recipe."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECIPE_DIR = ROOT / "fabric/src/main/resources/data/simplylight/recipe"
SLBLOCKS = ROOT / "common/src/main/java/com/flanks255/simplylight/SLBlocks.java"


def registered_item_ids() -> set[str]:
    text = SLBLOCKS.read_text(encoding="utf-8")
    names = set(re.findall(r'makeItemProperties\("([^"]+)"\)', text))
    colors = [
        "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    ]
    for color in colors:
        names.add(f"illuminant_{color}_block")
        names.add(f"illuminant_{color}_block_on")
        names.add(f"illuminant_panel_{color}")
        names.add(f"illuminant_slab_{color}")
        names.add(f"rodlamp_{color}")
        names.add(f"lightbulb_{color}")
        names.add(f"wall_lamp_{color}")
        names.add(f"lamp_post_{color}")
        names.add(f"edge_light_{color}")
        names.add(f"edge_light_top_{color}")
    return names


def recipe_results() -> dict[str, Path]:
    results: dict[str, Path] = {}
    for path in sorted(RECIPE_DIR.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        result = data.get("result")
        if isinstance(result, dict):
            item_id = result.get("id") or result.get("item")
        else:
            item_id = result
        if not item_id:
            continue
        short = item_id.split(":")[-1]
        results[short] = path
    return results


def main() -> int:
    items = sorted(registered_item_ids())
    results = recipe_results()
    missing = [i for i in items if i not in results]

    print(f"Registered items (block items): {len(items)}")
    print(f"Recipe JSON files: {len(list(RECIPE_DIR.glob('*.json')))}")
    print(f"Unique recipe outputs: {len(results)}")
    if missing:
        print("Items without a crafting recipe:")
        for name in missing:
            print(f"  - {name}")
        return 1
    print("All registered items have at least one crafting recipe.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
