package com.flanks255.simplylight;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads every data-pack recipe JSON for Simply Light, checks that the server registered it,
 * and simulates crafting with the exact pattern from the JSON file.
 */
public final class SimplyLightRecipeVerifier {
    private SimplyLightRecipeVerifier() {
    }

    public static void verify(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        HolderLookup.Provider registries = server.registryAccess();
        Level level = server.overworld();
        List<String> failures = new ArrayList<>();
        int tested = 0;
        int loaded = 0;

        Map<Identifier, Resource> recipeFiles = resources.listResources(
                "recipe",
                id -> SimplyLightCommon.MODID.equals(id.getNamespace()) && id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : recipeFiles.entrySet()) {
            Identifier resourceId = entry.getKey();
            String recipePath = resourceId.getPath();
            String recipeName = recipePath.substring(recipePath.lastIndexOf('/') + 1, recipePath.length() - 5);
            ResourceKey<Recipe<?>> recipeKey = ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(SimplyLightCommon.MODID, recipeName));

            Optional<RecipeHolder<?>> holder = server.getRecipeManager().byKey(recipeKey);
            if (holder.isEmpty()) {
                failures.add("Recipe not loaded by RecipeManager: " + recipeKey.identifier());
                continue;
            }
            loaded++;

            JsonObject json;
            try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                failures.add(recipeName + ": failed to read JSON: " + e.getMessage());
                continue;
            }

            Recipe<?> recipe = holder.get().value();
            if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }

            try {
                verifyCraftingRecipe(recipeName, json, craftingRecipe, registries, level, failures);
                tested++;
            } catch (Exception e) {
                failures.add(recipeName + ": " + e.getMessage());
            }
        }

        SimplyLightCommon.LOGGER.info(
                "{} recipe verification: {} data files, {} loaded in RecipeManager, {} crafting recipes exercised",
                SimplyLightRuntimeChecks.VERIFY_LOG_PREFIX,
                recipeFiles.size(),
                loaded,
                tested
        );

        if (!failures.isEmpty()) {
            failures.forEach(msg -> SimplyLightCommon.LOGGER.error("{} recipe failure: {}", SimplyLightRuntimeChecks.VERIFY_LOG_PREFIX, msg));
            throw new IllegalStateException(
                    "Recipe verification failed for " + failures.size() + " recipe(s); first: " + failures.getFirst()
            );
        }
    }

    private static void verifyCraftingRecipe(
            String recipeName,
            JsonObject json,
            CraftingRecipe recipe,
            HolderLookup.Provider registries,
            Level level,
            List<String> failures
    ) {
        CraftingInput input = RecipeDataPackCrafting.craftingInputFromJson(json, registries);
        if (!recipe.matches(input, level)) {
            failures.add(recipeName + ": recipe does not match ingredient grid built from JSON");
            return;
        }

        ItemStack crafted = recipe.assemble(input, registries);
        ExpectedResult expected = ExpectedResult.fromJson(json.get("result"));
        if (!expected.matches(crafted)) {
            failures.add(recipeName + ": expected " + expected + " but assembled " + stackDescription(crafted));
        }
    }

    private static String stackDescription(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + " x" + stack.getCount();
    }

    private record ExpectedResult(Identifier itemId, int count) {
        static ExpectedResult fromJson(JsonElement result) {
            return new ExpectedResult(
                    RecipeDataPackCrafting.resultIdFromJson(result),
                    RecipeDataPackCrafting.resultCountFromJson(result)
            );
        }

        boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            Identifier got = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return itemId.equals(got) && stack.getCount() == count;
        }

        @Override
        public String toString() {
            return itemId + " x" + count;
        }
    }
}
