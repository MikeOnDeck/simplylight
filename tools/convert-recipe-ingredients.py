#!/usr/bin/env python3
"""Convert pre-1.21.2 recipe ingredient objects to 1.21.2+ string form."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def convert_ingredient(value: Any) -> Any:
    if isinstance(value, str):
        return value
    if not isinstance(value, dict):
        return value
    if "item" in value and len(value) == 1:
        return value["item"]
    if "tag" in value and len(value) == 1:
        tag = value["tag"]
        if isinstance(tag, str) and not tag.startswith("#"):
            return f"#{tag}"
        return tag
    if "id" in value and len(value) == 1:
        # Some packs use {"id": "minecraft:stone"} for ingredients
        return value["id"]
    return value


def convert_recipe(data: dict[str, Any]) -> bool:
    changed = False

    key = data.get("key")
    if isinstance(key, dict):
        for slot, ingredient in list(key.items()):
            new = convert_ingredient(ingredient)
            if new != ingredient:
                key[slot] = new
                changed = True

    ingredients = data.get("ingredients")
    if isinstance(ingredients, list):
        for i, ingredient in enumerate(ingredients):
            new = convert_ingredient(ingredient)
            if new != ingredient:
                ingredients[i] = new
                changed = True

    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "recipe_dir",
        type=Path,
        nargs="?",
        default=Path("fabric/src/main/resources/data/simplylight/recipe"),
    )
    args = parser.parse_args()
    recipe_dir: Path = args.recipe_dir

    if not recipe_dir.is_dir():
        raise SystemExit(f"Recipe directory not found: {recipe_dir}")

    changed_files = 0
    for path in sorted(recipe_dir.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        if convert_recipe(data):
            with path.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
            changed_files += 1

    print(f"Updated {changed_files} recipe file(s) in {recipe_dir}")


if __name__ == "__main__":
    main()
