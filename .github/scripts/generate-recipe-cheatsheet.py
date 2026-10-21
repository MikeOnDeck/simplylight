#!/usr/bin/env python3
"""Generate a short markdown cheat sheet for showcase Simply Light recipes."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RECIPE_DIR = ROOT / "fabric/src/main/resources/data/simplylight/recipe"

SHOWCASE = [
    "illuminant_block",
    "illuminant_block_on",
    "illuminant_red_block_dyed",
    "illuminant_slab",
    "illuminant_panel_dyed",
    "wall_lamp_dyed",
    "bulb",
    "rodlamp",
    "edge_light",
    "lamp_post",
]


def format_recipe(name: str) -> str:
    data = json.loads((RECIPE_DIR / f"{name}.json").read_text(encoding="utf-8"))
    result = data["result"]
    if isinstance(result, dict):
        out = result.get("id") or result.get("item")
        count = result.get("count", 1)
    else:
        out, count = result, 1
    out_short = out.split(":")[-1]

    if data["type"] == "minecraft:crafting_shapeless":
        ings = ", ".join(data["ingredients"])
        return f"- **{out_short}** x{count}: shapeless — {ings}"

    key = data["key"]
    pattern = data["pattern"]
    legend = ", ".join(f"`{k}`={v}" for k, v in sorted(key.items()))
    shape = " / ".join(f"`{row}`" for row in pattern)
    return f"- **{out_short}** x{count}: shaped {shape} ({legend})"


def main() -> None:
    print("## Recipe cheat sheet (showcase)")
    print()
    for name in SHOWCASE:
        print(format_recipe(name))
    print()
    print(f"_Full data pack: {len(list(RECIPE_DIR.glob('*.json')))} recipe JSON files under `data/simplylight/recipe/`._")


if __name__ == "__main__":
    main()
