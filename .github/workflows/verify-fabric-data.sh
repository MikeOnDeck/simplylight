#!/bin/bash
set -e

echo "=== Verifying Fabric jar data pack integrity ==="

JAR_PATH="fabric/build/libs/simplylight-fabric-1.21.11-1.5.4+1.21.11-fabric.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: Fabric jar not found at $JAR_PATH"
    exit 1
fi

# Create temp directory for extraction
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Extract data directory from jar
unzip -q "$JAR_PATH" "data/*" -d "$TEMP_DIR" 2>/dev/null || true

# Count recipes, loot tables, and tags
RECIPE_COUNT=$(find "$TEMP_DIR/data/simplylight/recipe" -name "*.json" 2>/dev/null | wc -l)
LOOT_TABLE_COUNT=$(find "$TEMP_DIR/data/simplylight/loot_table/blocks" -name "*.json" 2>/dev/null | wc -l)
TAG_COUNT=$(find "$TEMP_DIR/data" -path "*/tags/*" -name "*.json" 2>/dev/null | wc -l)

echo "Found $RECIPE_COUNT recipes"
echo "Found $LOOT_TABLE_COUNT loot tables"
echo "Found $TAG_COUNT tags"

# Verify minimum counts
if [ "$RECIPE_COUNT" -lt 230 ]; then
    echo "ERROR: Expected at least 230 recipes, found $RECIPE_COUNT"
    exit 1
fi

if [ "$LOOT_TABLE_COUNT" -lt 160 ]; then
    echo "ERROR: Expected at least 160 loot tables, found $LOOT_TABLE_COUNT"
    exit 1
fi

if [ "$TAG_COUNT" -lt 10 ]; then
    echo "ERROR: Expected at least 10 tags, found $TAG_COUNT"
    exit 1
fi

# Sample check: verify a specific recipe has the correct format
echo "Verifying recipe format (1.21.2+ with result.id)..."
SAMPLE_RECIPE="$TEMP_DIR/data/simplylight/recipe/illuminant_block.json"
if [ -f "$SAMPLE_RECIPE" ]; then
    if ! grep -q '"id".*"simplylight:illuminant_block"' "$SAMPLE_RECIPE"; then
        echo "ERROR: Sample recipe illuminant_block.json does not have correct id format"
        cat "$SAMPLE_RECIPE"
        exit 1
    fi
    if ! grep -q '"count".*4' "$SAMPLE_RECIPE"; then
        echo "ERROR: Sample recipe illuminant_block.json does not have correct count"
        exit 1
    fi
else
    echo "ERROR: Sample recipe illuminant_block.json not found"
    exit 1
fi

# Check that required tags exist
echo "Checking for required tags..."
REQUIRED_TAGS=(
    "data/simplylight/tags/item/any_lamp_on.json"
    "data/simplylight/tags/item/any_lamp_off.json"
    "data/minecraft/tags/block/mineable/pickaxe.json"
)

for tag in "${REQUIRED_TAGS[@]}"; do
    if [ ! -f "$TEMP_DIR/$tag" ]; then
        echo "ERROR: Required tag $tag not found in jar"
        exit 1
    fi
done

echo "✓ All data pack integrity checks passed!"
echo "✓ Recipes: $RECIPE_COUNT (verified 1.21.2+ format)"
echo "✓ Loot tables: $LOOT_TABLE_COUNT"
echo "✓ Tags: $TAG_COUNT"
