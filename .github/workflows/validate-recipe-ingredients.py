#!/usr/bin/env python3
"""Fail if any Fabric recipe still uses pre-1.21.2 ingredient objects."""

import json
import sys
from pathlib import Path

RECIPE_DIR = Path("fabric/src/main/resources/data/simplylight/recipe")


def is_legacy_ingredient_object(value: object) -> bool:
    return isinstance(value, dict) and ("item" in value or "tag" in value)


def check_ingredient(value: object, path: str, errors: list[str]) -> None:
    if is_legacy_ingredient_object(value):
        errors.append(f"{path}: legacy ingredient object {value!r}")


def check_recipe(data: dict, file_path: Path, errors: list[str]) -> None:
    key = data.get("key")
    if isinstance(key, dict):
        for slot, ingredient in key.items():
            check_ingredient(ingredient, f"{file_path} key.{slot}", errors)

    ingredients = data.get("ingredients")
    if isinstance(ingredients, list):
        for i, ingredient in enumerate(ingredients):
            check_ingredient(ingredient, f"{file_path} ingredients[{i}]", errors)


def main() -> None:
    if not RECIPE_DIR.is_dir():
        print(f"ERROR: Missing recipe directory: {RECIPE_DIR}")
        sys.exit(1)

    errors: list[str] = []
    for path in sorted(RECIPE_DIR.glob("*.json")):
        with path.open(encoding="utf-8") as f:
            data = json.load(f)
        check_recipe(data, path, errors)

    if errors:
        print("Recipe ingredient validation FAILED:")
        for err in errors:
            print(f"  - {err}")
        sys.exit(1)

    count = len(list(RECIPE_DIR.glob("*.json")))
    print(f"✓ All {count} recipes use 1.21.2+ ingredient strings")


if __name__ == "__main__":
    main()
