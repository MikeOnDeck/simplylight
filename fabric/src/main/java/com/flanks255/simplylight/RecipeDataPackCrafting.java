package com.flanks255.simplylight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds crafting grids from Simply Light recipe JSON (same layout as data pack files). */
public final class RecipeDataPackCrafting {
    private RecipeDataPackCrafting() {
    }

    public static CraftingInput craftingInputFromJson(JsonObject json, HolderLookup.Provider registries) {
        String type = json.get("type").getAsString();
        if ("minecraft:crafting_shaped".equals(type)) {
            return buildShapedInput(json, registries);
        }
        if ("minecraft:crafting_shapeless".equals(type)) {
            return buildShapelessInput(json, registries);
        }
        throw new IllegalStateException("Unsupported recipe type: " + type);
    }

    public static List<ItemStack> ingredientStacksFromJson(JsonObject json, HolderLookup.Provider registries) {
        CraftingInput input = craftingInputFromJson(json, registries);
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return stacks;
    }

    private static CraftingInput buildShapedInput(JsonObject json, HolderLookup.Provider registries) {
        JsonObject key = json.getAsJsonObject("key");
        JsonArray pattern = json.getAsJsonArray("pattern");
        int height = pattern.size();
        int width = 0;
        for (JsonElement line : pattern) {
            width = Math.max(width, line.getAsString().length());
        }

        List<ItemStack> stacks = new ArrayList<>(width * height);
        for (int row = 0; row < height; row++) {
            String line = pattern.get(row).getAsString();
            for (int col = 0; col < width; col++) {
                char symbol = col < line.length() ? line.charAt(col) : ' ';
                if (symbol == ' ') {
                    stacks.add(ItemStack.EMPTY);
                } else {
                    String ingredientId = key.get(String.valueOf(symbol)).getAsString();
                    stacks.add(resolveIngredient(ingredientId, registries));
                }
            }
        }
        return CraftingInput.of(width, height, stacks);
    }

    private static CraftingInput buildShapelessInput(JsonObject json, HolderLookup.Provider registries) {
        JsonArray ingredients = json.getAsJsonArray("ingredients");
        List<ItemStack> stacks = new ArrayList<>();
        for (JsonElement element : ingredients) {
            stacks.add(resolveIngredient(element.getAsString(), registries));
        }
        int count = stacks.size();
        int width = Math.min(3, count);
        int height = (count + width - 1) / width;
        while (stacks.size() < width * height) {
            stacks.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(width, height, stacks);
    }

    public static ItemStack resolveIngredient(String ingredientId, HolderLookup.Provider registries) {
        Ingredient ingredient;
        if (ingredientId.startsWith("#")) {
            Identifier tagId = Identifier.parse(ingredientId.substring(1));
            var tag = registries.lookupOrThrow(Registries.ITEM)
                    .get(TagKey.create(Registries.ITEM, tagId))
                    .orElseThrow(() -> new IllegalStateException("Unknown item tag: #" + tagId));
            ingredient = Ingredient.of(tag);
        } else {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(ingredientId));
            if (item == null) {
                throw new IllegalStateException("Unknown item ingredient: " + ingredientId);
            }
            ingredient = Ingredient.of(item);
        }

        Optional<Holder<Item>> first = ingredient.items().findFirst();
        if (first.isEmpty()) {
            throw new IllegalStateException("Ingredient has no matching items: " + ingredientId);
        }
        return new ItemStack(first.get().value());
    }

    public static Identifier resultIdFromJson(JsonElement result) {
        if (result.isJsonObject()) {
            JsonObject obj = result.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : obj.get("item").getAsString();
            return Identifier.parse(id);
        }
        return Identifier.parse(result.getAsString());
    }

    public static int resultCountFromJson(JsonElement result) {
        if (result.isJsonObject() && result.getAsJsonObject().has("count")) {
            return result.getAsJsonObject().get("count").getAsInt();
        }
        return 1;
    }
}
