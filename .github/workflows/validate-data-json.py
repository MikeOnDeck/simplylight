#!/usr/bin/env python3
import json
import sys
import zipfile
from pathlib import Path

def validate_json_files_in_jar(jar_path):
    """Validate all JSON files in the jar are parseable."""
    errors = []
    recipe_count = 0
    loot_table_count = 0
    tag_count = 0
    
    with zipfile.ZipFile(jar_path, 'r') as jar:
        for file_info in jar.namelist():
            if file_info.endswith('.json') and file_info.startswith('data/'):
                try:
                    with jar.open(file_info) as f:
                        data = json.load(f)
                        
                        # Count file types
                        if '/recipe/' in file_info:
                            recipe_count += 1
                            # Verify recipe has new format
                            if 'result' in data:
                                result = data['result']
                                if isinstance(result, dict):
                                    if 'item' in result and 'id' not in result:
                                        errors.append(f"Recipe {file_info} uses old format (result.item instead of result.id)")
                        elif '/loot_table/' in file_info:
                            loot_table_count += 1
                        elif '/tags/' in file_info:
                            tag_count += 1
                            
                except json.JSONDecodeError as e:
                    errors.append(f"Invalid JSON in {file_info}: {e}")
                except Exception as e:
                    errors.append(f"Error reading {file_info}: {e}")
    
    return errors, recipe_count, loot_table_count, tag_count

if __name__ == '__main__':
    jar_path = 'fabric/build/libs/simplylight-fabric-1.21.11-1.5.4+1.21.11-fabric.jar'
    
    if not Path(jar_path).exists():
        print(f"ERROR: Jar not found at {jar_path}")
        sys.exit(1)
    
    print("=== Validating JSON files in Fabric jar ===")
    errors, recipes, loot_tables, tags = validate_json_files_in_jar(jar_path)
    
    print(f"Found {recipes} recipes")
    print(f"Found {loot_tables} loot tables")
    print(f"Found {tags} tags")
    
    if errors:
        print("\n❌ Validation FAILED with the following errors:")
        for error in errors:
            print(f"  - {error}")
        sys.exit(1)
    
    if recipes < 230:
        print(f"\n❌ ERROR: Expected at least 230 recipes, found {recipes}")
        sys.exit(1)
    
    if loot_tables < 160:
        print(f"\n❌ ERROR: Expected at least 160 loot tables, found {loot_tables}")
        sys.exit(1)
    
    if tags < 10:
        print(f"\n❌ ERROR: Expected at least 10 tags, found {tags}")
        sys.exit(1)
    
    print("\n✓ All JSON files are valid!")
    print(f"✓ Recipes: {recipes} (1.21.2+ format)")
    print(f"✓ Loot tables: {loot_tables}")
    print(f"✓ Tags: {tags}")
