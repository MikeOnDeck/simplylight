#!/usr/bin/env python3
"""Generate lowered lamp post models and blockstate variants for Fabric resources."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "fabric/src/main/resources/assets/simplylight/models/block"
BLOCKSTATE_DIR = ROOT / "fabric/src/main/resources/assets/simplylight/blockstates"

SHIFT = 8

COLOR_SUFFIXES = [
    "",
    "_black",
    "_blue",
    "_brown",
    "_cyan",
    "_gray",
    "_green",
    "_light_blue",
    "_light_gray",
    "_lime",
    "_magenta",
    "_orange",
    "_pink",
    "_purple",
    "_red",
    "_yellow",
]


def shift_element_y(element: dict, delta: int) -> dict:
    element = copy.deepcopy(element)
    for key in ("from", "to"):
        if key in element:
            element[key] = [element[key][0], element[key][1] + delta, element[key][2]]
    return element


def make_lowered_model(source_name: str, dest_name: str) -> None:
    source = MODEL_DIR / f"{source_name}.json"
    data = json.loads(source.read_text())
    if "elements" in data:
        data["elements"] = [shift_element_y(el, -SHIFT) for el in data["elements"]]
        dest = MODEL_DIR / f"{dest_name}.json"
        dest.write_text(json.dumps(data, indent="\t") + "\n")
        return
    # Texture override child model: parent lowered base and keep textures
    parent = data.get("parent", "")
    if parent.endswith(source_name.split("_", 1)[-1] if False else ""):
        pass
    base = source_name.replace("_red", "").replace("_black", "")
    if "parent" in data:
        parent_path = data["parent"]
        if parent_path.endswith(source_name):
            data["parent"] = parent_path.replace(source_name, dest_name.replace("_lowered", "_lowered"))
        elif parent_path.endswith("/post_top"):
            data["parent"] = "simplylight:block/post_top_lowered"
        else:
            data["parent"] = parent_path.replace(source_name, dest_name.replace("_lowered", ""))
            if "post_top" in source_name and "_lowered" not in data["parent"]:
                data["parent"] = "simplylight:block/post_top_lowered"
    dest = MODEL_DIR / f"{dest_name}.json"
    dest.write_text(json.dumps(data, indent=2) + "\n")


def generate_lowered_block_models() -> None:
    for name in ("post_base", "post_mid", "post_top"):
        src = MODEL_DIR / f"{name}.json"
        data = json.loads(src.read_text())
        data["elements"] = [shift_element_y(el, -SHIFT) for el in data["elements"]]
        (MODEL_DIR / f"{name}_lowered.json").write_text(json.dumps(data, indent="\t") + "\n")

    for suffix in COLOR_SUFFIXES:
        if suffix == "":
            continue
        colored = MODEL_DIR / f"post_top{suffix}.json"
        if not colored.exists():
            continue
        data = json.loads(colored.read_text())
        data["parent"] = "simplylight:block/post_top_lowered"
        (MODEL_DIR / f"post_top{suffix}_lowered.json").write_text(json.dumps(data, indent=2) + "\n")


def blockstate_for(suffix: str) -> dict:
    top_model = "post_top" if suffix == "" else f"post_top{suffix}"
    variants = {}
    for position, base in (("bottom", "post_base"), ("middle", "post_mid"), ("top", top_model)):
        for lowered in (False, True):
            key = f"position={position},lowered={str(lowered).lower()}"
            model = f"simplylight:block/{base}" + ("_lowered" if lowered else "")
            if position == "top" and lowered and suffix != "":
                model = f"simplylight:block/{top_model}_lowered"
            variants[key] = {"model": model}
    return {"variants": variants}


def generate_blockstates() -> None:
    for suffix in COLOR_SUFFIXES:
        name = "lamp_post" if suffix == "" else f"lamp_post{suffix}"
        path = BLOCKSTATE_DIR / f"{name}.json"
        path.write_text(json.dumps(blockstate_for(suffix), indent=2) + "\n")


def main() -> None:
    generate_lowered_block_models()
    generate_blockstates()
    print("Generated lowered lamp post models and blockstates")


if __name__ == "__main__":
    main()
