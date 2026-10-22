package com.flanks255.simplylight;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldDataConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Headless-friendly client harness: creates a flat creative world, opens a crafting table for each
 * showcase recipe, fills the grid from data-pack JSON, and saves PNG screenshots.
 */
@Environment(EnvType.CLIENT)
public final class SimplyLightCraftingScreenshotClient {
    private static final String WORLD_NAME = "crafting_screenshots";
    private static final List<String> SHOWCASE_RECIPES = List.of(
            "illuminant_block",
            "illuminant_block_on",
            "illuminant_red_block_dyed",
            "illuminant_slab",
            "illuminant_panel_dyed",
            "wall_lamp_dyed",
            "bulb",
            "rodlamp",
            "edge_light",
            "lamp_post"
    );

    private static int tickCounter;
    private static Phase phase = Phase.BOOTSTRAP_WORLD;
    private static int recipeIndex;
    private static int waitTicks;
    private static BlockPos tablePos = BlockPos.ZERO;
    private static boolean screenshotWritePending;

    private enum Phase {
        BOOTSTRAP_WORLD,
        WAIT_IN_WORLD,
        PREP_TABLE,
        OPEN_TABLE,
        FILL_GRID,
        WAIT_RESULT,
        SCREENSHOT,
        NEXT,
        DONE
    }

    private SimplyLightCraftingScreenshotClient() {
    }

    public static void register() {
        if (!Boolean.getBoolean("simplylight.craftingScreenshots")) {
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> tickCounter = 0);
        ClientTickEvents.END_CLIENT_TICK.register(SimplyLightCraftingScreenshotClient::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null && phase != Phase.BOOTSTRAP_WORLD) {
            return;
        }
        tickCounter++;
        if (phase == Phase.BOOTSTRAP_WORLD) {
            if (tickCounter < 40) {
                return;
            }
            bootstrapWorld(client);
            phase = Phase.WAIT_IN_WORLD;
            waitTicks = 0;
            return;
        }

        if (phase == Phase.WAIT_IN_WORLD) {
            if (client.player == null) {
                return;
            }
            waitTicks++;
            if (waitTicks < 80) {
                return;
            }
            phase = Phase.PREP_TABLE;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        switch (phase) {
            case PREP_TABLE -> prepCraftingTable(client);
            case OPEN_TABLE -> openCraftingTable(client);
            case FILL_GRID -> fillCraftingGrid(client);
            case WAIT_RESULT -> waitForResult(client);
            case SCREENSHOT -> captureScreenshot(client);
            case NEXT -> advanceRecipe(client);
            case DONE -> client.stop();
            default -> {
            }
        }
    }

    private static void bootstrapWorld(Minecraft client) {
        Path worldDir = client.getLevelSource().getBaseDir().resolve(WORLD_NAME);
        if (Files.isDirectory(worldDir)) {
            client.createWorldOpenFlows().openWorld(WORLD_NAME, () -> SimplyLightCommon.LOGGER.info("Opened world {}", WORLD_NAME));
            return;
        }
        LevelSettings settings = new LevelSettings(
                WORLD_NAME,
                GameType.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(FeatureFlags.DEFAULT_FLAGS),
                WorldDataConfiguration.DEFAULT
        );
        client.createWorldOpenFlows().createFreshLevel(
                WORLD_NAME,
                settings,
                WorldOptions.defaultWithRandomSeed(),
                WorldPresets::createFlatWorldDimensions,
                null
        );
    }

    private static void prepCraftingTable(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            tablePos = player.blockPosition().offset(1, 0, 0);
            player.level().setBlockAndUpdate(tablePos.below(), Blocks.STONE.defaultBlockState());
            player.level().setBlockAndUpdate(tablePos, Blocks.CRAFTING_TABLE.defaultBlockState());
            player.getInventory().clearContent();
        });
        phase = Phase.OPEN_TABLE;
        waitTicks = 0;
    }

    private static void openCraftingTable(Minecraft client) {
        waitTicks++;
        if (waitTicks < 10) {
            return;
        }
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), tablePos);
            player.openMenu(new SimpleMenuProvider(
                    (syncId, inv, p) -> new CraftingMenu(syncId, inv, access),
                    Component.translatable("container.crafting")
            ));
        });
        phase = Phase.FILL_GRID;
        waitTicks = 0;
    }

    private static void fillCraftingGrid(Minecraft client) {
        waitTicks++;
        if (waitTicks < 15) {
            return;
        }
        if (!(client.player.containerMenu instanceof CraftingMenu)) {
            return;
        }
        JsonObject recipeJson = loadRecipeJson(SHOWCASE_RECIPES.get(recipeIndex));
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            AbstractContainerMenu menu = player.containerMenu;
            if (!(menu instanceof CraftingMenu craftingMenu)) {
                return;
            }
            var input = RecipeDataPackCrafting.craftingInputFromJson(recipeJson, server.registryAccess());
            List<Slot> grid = craftingMenu.getInputGridSlots();
            for (int i = 0; i < grid.size(); i++) {
                ItemStack stack = i < input.size() ? input.getItem(i) : ItemStack.EMPTY;
                grid.get(i).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            craftingMenu.slotsChanged(grid.getFirst().container);
        });
        phase = Phase.WAIT_RESULT;
        waitTicks = 0;
    }

    private static void waitForResult(Minecraft client) {
        waitTicks++;
        if (waitTicks < 10) {
            return;
        }
        if (client.player.containerMenu instanceof CraftingMenu craftingMenu) {
            ItemStack result = craftingMenu.getResultSlot().getItem();
            JsonObject recipeJson = loadRecipeJson(SHOWCASE_RECIPES.get(recipeIndex));
            Identifier expected = RecipeDataPackCrafting.resultIdFromJson(recipeJson.get("result"));
            if (!result.isEmpty() && Objects.equals(BuiltInRegistries.ITEM.getKey(result.getItem()), expected)) {
                phase = Phase.SCREENSHOT;
                waitTicks = 0;
            }
        }
        if (waitTicks > 200) {
            throw new IllegalStateException("Timed out waiting for crafting result for " + SHOWCASE_RECIPES.get(recipeIndex));
        }
    }

    private static void captureScreenshot(Minecraft client) {
        waitTicks++;
        if (waitTicks < 10) {
            return;
        }
        if (!(client.screen instanceof CraftingScreen)) {
            return;
        }
        if (screenshotWritePending) {
            return;
        }
        client.options.hideGui = false;
        Path dir = client.gameDirectory.toPath().resolve("screenshots").resolve("simplylight-crafting");
        String name = SHOWCASE_RECIPES.get(recipeIndex) + ".png";
        try {
            Files.createDirectories(dir);
            screenshotWritePending = true;
            Path target = dir.resolve(name);
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try {
                    image.writeToFile(target);
                    SimplyLightCommon.LOGGER.info("[SimplyLight screenshots] saved {}", target);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to write " + target, e);
                } finally {
                    image.close();
                    screenshotWritePending = false;
                    client.execute(() -> {
                        phase = Phase.NEXT;
                        waitTicks = 0;
                    });
                }
            });
        } catch (Exception e) {
            screenshotWritePending = false;
            throw new IllegalStateException("Failed to save screenshot for " + SHOWCASE_RECIPES.get(recipeIndex), e);
        }
    }

    private static void advanceRecipe(Minecraft client) {
        client.player.closeContainer();
        recipeIndex++;
        if (recipeIndex >= SHOWCASE_RECIPES.size()) {
            SimplyLightCommon.LOGGER.info("[SimplyLight screenshots] captured {} crafting table screenshots", SHOWCASE_RECIPES.size());
            phase = Phase.DONE;
            return;
        }
        phase = Phase.PREP_TABLE;
        waitTicks = 0;
    }

    private static JsonObject loadRecipeJson(String recipeName) {
        String path = "/data/simplylight/recipe/" + recipeName + ".json";
        try (var stream = SimplyLightCraftingScreenshotClient.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing recipe resource: " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + path, e);
        }
    }
}
