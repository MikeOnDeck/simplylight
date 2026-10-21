#!/usr/bin/env python3
"""
Verify that every registered item has an item definition JSON that points to a valid model.
"""
import json
import sys
import zipfile
import re
from pathlib import Path
from typing import Set, Dict, List

def extract_registered_items_from_java(java_content: str) -> Set[str]:
    """Extract item IDs from SLBlocks.java registration patterns."""
    items = set()
    
    # Pattern: new SLBlockReg<>("item_name", ...
    pattern = r'new SLBlockReg<>\("([^"]+)"'
    for match in re.finditer(pattern, java_content):
        items.add(match.group(1))
    
    return items

def validate_item_definitions_in_jar(jar_path: str, expected_items: Set[str]) -> tuple:
    """Validate all item definitions exist and point to valid models."""
    errors = []
    warnings = []
    
    with zipfile.ZipFile(jar_path, 'r') as jar:
        # Get all files in the jar
        jar_files = set(jar.namelist())
        
        # Check each expected item has an item definition
        for item_id in sorted(expected_items):
            item_def_path = f"assets/simplylight/items/{item_id}.json"
            
            if item_def_path not in jar_files:
                errors.append(f"Missing item definition: {item_def_path}")
                continue
            
            # Parse the item definition
            try:
                with jar.open(item_def_path) as f:
                    item_def = json.load(f)
                
                # Check if it has a model reference
                if 'model' not in item_def:
                    errors.append(f"{item_def_path}: missing 'model' field")
                    continue
                
                model_ref = item_def['model']
                if isinstance(model_ref, dict) and 'model' in model_ref:
                    model_path = model_ref['model']
                elif isinstance(model_ref, str):
                    model_path = model_ref
                else:
                    errors.append(f"{item_def_path}: invalid model reference: {model_ref}")
                    continue
                
                # Convert model reference to file path
                # e.g. "simplylight:block/rodlamp" -> "assets/simplylight/models/block/rodlamp.json"
                if ':' in model_path:
                    namespace, path = model_path.split(':', 1)
                    model_file = f"assets/{namespace}/models/{path}.json"
                else:
                    model_file = f"assets/simplylight/models/{model_path}.json"
                
                # Check if the model file exists
                if model_file not in jar_files:
                    errors.append(f"{item_def_path}: references non-existent model: {model_file}")
                else:
                    # Try to parse the model to ensure it's valid JSON
                    try:
                        with jar.open(model_file) as f:
                            json.load(f)
                    except json.JSONDecodeError as e:
                        errors.append(f"{model_file}: invalid JSON - {e}")
                        
            except json.JSONDecodeError as e:
                errors.append(f"{item_def_path}: invalid JSON - {e}")
            except Exception as e:
                errors.append(f"{item_def_path}: error reading - {e}")
    
    return errors, warnings

if __name__ == '__main__':
    # Read SLBlocks.java to get registered items
    slblocks_path = 'common/src/main/java/com/flanks255/simplylight/SLBlocks.java'
    if not Path(slblocks_path).exists():
        print(f"ERROR: {slblocks_path} not found")
        sys.exit(1)
    
    with open(slblocks_path, 'r') as f:
        java_content = f.read()
    
    registered_items = extract_registered_items_from_java(java_content)
    print(f"Found {len(registered_items)} registered items in {slblocks_path}")
    
    # Check the jar
    jar_path = 'fabric/build/libs/simplylight-fabric-1.21.11-1.5.4+1.21.11-fabric.jar'
    if not Path(jar_path).exists():
        print(f"ERROR: Jar not found at {jar_path}")
        sys.exit(1)
    
    print("=== Validating item definitions ===")
    errors, warnings = validate_item_definitions_in_jar(jar_path, registered_items)
    
    if warnings:
        print(f"\n⚠ {len(warnings)} warnings:")
        for warning in warnings:
            print(f"  - {warning}")
    
    if errors:
        print(f"\n❌ {len(errors)} errors:")
        for error in errors:
            print(f"  - {error}")
        sys.exit(1)
    
    print(f"\n✓ All {len(registered_items)} registered items have valid item definitions with existing models!")
