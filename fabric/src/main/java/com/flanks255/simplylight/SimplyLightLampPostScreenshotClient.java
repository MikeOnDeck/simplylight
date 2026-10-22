package com.flanks255.simplylight;

import com.flanks255.simplylight.blocks.LampPost;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.flag.FeatureFlags;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Headless-friendly harness: builds each lamp-post acceptance scene in a flat world and saves PNGs.
 */
@Environment(EnvType.CLIENT)
public final class SimplyLightLampPostScreenshotClient {
    private static final String WORLD_NAME = "lamp_post_screenshots";
    private static BlockPos sceneAnchor = BlockPos.ZERO;
    private static final List<String> SCENARIOS = List.of(
            "column-stone-bottom-slab",
            "column-oak-bottom-slab",
            "column-full-block",
            "column-top-slab",
            "column-break-slab-under",
            "column-break-top-removes-all",
            "column-top-emits-light"
    );

    private static int tickCounter;
    private static Phase phase = Phase.BOOTSTRAP_WORLD;
    private static int scenarioIndex;
    private static int waitTicks;
    private static boolean screenshotWritePending;

    private enum Phase {
        BOOTSTRAP_WORLD,
        WAIT_IN_WORLD,
        BUILD_SCENE,
        POSITION_CAMERA,
        CAPTURE,
        NEXT,
        DONE
    }

    private SimplyLightLampPostScreenshotClient() {
    }

    public static void register() {
        if (!Boolean.getBoolean("simplylight.lampPostScreenshots")) {
            return;
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> tickCounter = 0);
        ClientTickEvents.END_CLIENT_TICK.register(SimplyLightLampPostScreenshotClient::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
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
            phase = Phase.BUILD_SCENE;
            waitTicks = 0;
        }

        if (client.player == null || client.level == null) {
            return;
        }

        switch (phase) {
            case BUILD_SCENE -> buildScene(client);
            case POSITION_CAMERA -> positionCamera(client);
            case CAPTURE -> captureScreenshot(client);
            case NEXT -> advanceScenario(client);
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

    private static void clearColumnArea(ServerLevel level) {
        BlockPos slabPos = sceneAnchor.below();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 4; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(slabPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void placeColumn(ServerLevel level, BlockPos bottomPos) {
        LampPost post = SLBlocks.LAMP_POST.get();
        boolean lowered = LampPost.shouldBeLowered(level, bottomPos.below());
        var state = post.defaultBlockState()
                .setValue(LampPost.POSITION, LampPost.Position.BOTTOM)
                .setValue(LampPost.LOWERED, lowered);
        level.setBlock(bottomPos, state, 3);
        post.setPlacedBy(level, bottomPos, state, null, ItemStack.EMPTY);
    }

    private static void buildScene(Minecraft client) {
        waitTicks++;
        if (waitTicks < 10) {
            return;
        }
        if (waitTicks > 10) {
            return;
        }
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            if (scenarioIndex == 0) {
                sceneAnchor = player.blockPosition();
            }
            clearColumnArea(level);
            String scenario = SCENARIOS.get(scenarioIndex);
            BlockPos slabPos = sceneAnchor.below();
            BlockPos bottom = sceneAnchor;
            switch (scenario) {
                case "column-stone-bottom-slab" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.STONE_SLAB.defaultBlockState());
                    placeColumn(level, bottom);
                }
                case "column-oak-bottom-slab" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.OAK_SLAB.defaultBlockState());
                    placeColumn(level, bottom);
                }
                case "column-full-block" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.STONE.defaultBlockState());
                    placeColumn(level, bottom);
                }
                case "column-top-slab" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                    placeColumn(level, bottom);
                }
                case "column-break-slab-under" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.STONE_SLAB.defaultBlockState());
                    placeColumn(level, bottom);
                    level.setBlockAndUpdate(slabPos, Blocks.AIR.defaultBlockState());
                }
                case "column-break-top-removes-all" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.STONE_SLAB.defaultBlockState());
                    placeColumn(level, bottom);
                    level.destroyBlock(bottom.above(2), false);
                }
                case "column-top-emits-light" -> {
                    level.setBlockAndUpdate(slabPos, Blocks.STONE_SLAB.defaultBlockState());
                    placeColumn(level, bottom);
                }
                default -> throw new IllegalStateException("Unknown scenario " + scenario);
            }
        });
        phase = Phase.POSITION_CAMERA;
        waitTicks = 0;
    }

    private static void positionCamera(Minecraft client) {
        waitTicks++;
        if (waitTicks < 10) {
            return;
        }
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (waitTicks == 10) {
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getChunk(sceneAnchor.getX() >> 4, sceneAnchor.getZ() >> 4);
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                String scenario = SCENARIOS.get(scenarioIndex);
                Vec3 columnCenter = Vec3.atCenterOf(sceneAnchor).add(0, 1.5, 0);
                player.teleportTo(level, sceneAnchor.getX() + 2.5, sceneAnchor.getY() + 0.1, sceneAnchor.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
                aimAt(player, columnCenter);
                player.setYHeadRot(player.getYRot());
            });
            return;
        }
        if (waitTicks < 80) {
            return;
        }
        client.options.hideGui = true;
        phase = Phase.CAPTURE;
        waitTicks = 0;
    }

    private static void captureScreenshot(Minecraft client) {
        waitTicks++;
        if (waitTicks < 30) {
            return;
        }
        if (screenshotWritePending) {
            return;
        }
        Path dir = client.gameDirectory.toPath().resolve("screenshots").resolve("simplylight-lamp-post");
        String name = SCENARIOS.get(scenarioIndex) + ".png";
        try {
            Files.createDirectories(dir);
            screenshotWritePending = true;
            Path target = dir.resolve(name);
            Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                try {
                    image.writeToFile(target);
                    SimplyLightCommon.LOGGER.info("[SimplyLight lamp post screenshots] saved {}", target);
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
            throw new IllegalStateException("Failed to save screenshot for " + name, e);
        }
    }

    private static void aimAt(ServerPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        Vec3 diff = target.subtract(eye);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float xRot = (float) (-(Math.atan2(diff.y, horizontal) * (180.0 / Math.PI)));
        float yRot = (float) (Math.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0f;
        player.setXRot(xRot);
        player.setYRot(yRot);
    }

    private static void advanceScenario(Minecraft client) {
        scenarioIndex++;
        if (scenarioIndex >= SCENARIOS.size()) {
            SimplyLightCommon.LOGGER.info("[SimplyLight lamp post screenshots] captured {} scenes", SCENARIOS.size());
            phase = Phase.DONE;
            return;
        }
        phase = Phase.BUILD_SCENE;
        waitTicks = 0;
    }
}
