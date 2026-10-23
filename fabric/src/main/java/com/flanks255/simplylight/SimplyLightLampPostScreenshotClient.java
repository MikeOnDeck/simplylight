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
 * Headless-friendly harness: flat grass pad with open sky, lamp post scenes, side-view PNGs.
 */
@Environment(EnvType.CLIENT)
public final class SimplyLightLampPostScreenshotClient {
    private static final String WORLD_NAME = "lamp_post_screenshots_v2";
    /** Grass block Y; support (slab/full block) sits here; column bottom is one block above. */
    private static final int FLOOR_Y = 80;
    private static final BlockPos COLUMN_BOTTOM = new BlockPos(0, FLOOR_Y + 1, 0);
    private static final int PAD_RADIUS = 8;

    private enum SceneKind {
        STONE_BOTTOM_SLAB,
        OAK_BOTTOM_SLAB,
        FULL_BLOCK,
        TOP_SLAB,
        BREAK_SLAB_UNDER,
        BREAK_TOP,
        TOP_EMITS_LIGHT
    }

    private record Capture(SceneKind scene, String fileName, boolean closeUp) {}

    private static final List<Capture> CAPTURES = List.of(
            new Capture(SceneKind.STONE_BOTTOM_SLAB, "column-stone-bottom-slab", false),
            new Capture(SceneKind.STONE_BOTTOM_SLAB, "column-stone-bottom-slab-close", true),
            new Capture(SceneKind.OAK_BOTTOM_SLAB, "column-oak-bottom-slab", false),
            new Capture(SceneKind.OAK_BOTTOM_SLAB, "column-oak-bottom-slab-close", true),
            new Capture(SceneKind.FULL_BLOCK, "column-full-block", false),
            new Capture(SceneKind.TOP_SLAB, "column-top-slab", false),
            new Capture(SceneKind.BREAK_SLAB_UNDER, "column-break-slab-under", false),
            new Capture(SceneKind.BREAK_TOP, "column-break-top-removes-all", false),
            new Capture(SceneKind.TOP_EMITS_LIGHT, "column-top-emits-light", false)
    );

    private static int tickCounter;
    private static Phase phase = Phase.BOOTSTRAP_WORLD;
    private static int captureIndex;
    private static SceneKind builtScene;
    private static int waitTicks;
    private static boolean screenshotWritePending;
    private static boolean padPrepared;

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
            var server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    preparePad(level);
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.teleportTo(level, 0.5, FLOOR_Y + 1.0, 6.5, java.util.Set.of(), 180, 0, true);
                });
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
            case NEXT -> advanceCapture(client);
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

    private static void preparePad(ServerLevel level) {
        BlockPos floorOrigin = new BlockPos(0, FLOOR_Y, 0);
        for (int dx = -PAD_RADIUS; dx <= PAD_RADIUS; dx++) {
            for (int dz = -PAD_RADIUS; dz <= PAD_RADIUS; dz++) {
                BlockPos floor = floorOrigin.offset(dx, 0, dz);
                level.setBlock(floor, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int dy = 1; dy <= 5; dy++) {
                    level.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        padPrepared = true;
    }

    private static void resetColumnFootprint(ServerLevel level) {
        BlockPos support = COLUMN_BOTTOM.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = COLUMN_BOTTOM.offset(dx, dy, dz);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(support, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
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

    private static void buildSceneKind(ServerLevel level, SceneKind scene) {
        BlockPos support = COLUMN_BOTTOM.below();
        resetColumnFootprint(level);
        switch (scene) {
            case STONE_BOTTOM_SLAB -> {
                level.setBlockAndUpdate(support, Blocks.STONE_SLAB.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
            }
            case OAK_BOTTOM_SLAB -> {
                level.setBlockAndUpdate(support, Blocks.OAK_SLAB.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
            }
            case FULL_BLOCK -> {
                level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
            }
            case TOP_SLAB -> {
                level.setBlockAndUpdate(support, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                placeColumn(level, COLUMN_BOTTOM);
            }
            case BREAK_SLAB_UNDER -> {
                level.setBlockAndUpdate(support, Blocks.STONE_SLAB.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
                level.setBlockAndUpdate(support, Blocks.GRASS_BLOCK.defaultBlockState());
            }
            case BREAK_TOP -> {
                level.setBlockAndUpdate(support, Blocks.STONE_SLAB.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
                level.destroyBlock(COLUMN_BOTTOM.above(2), false);
            }
            case TOP_EMITS_LIGHT -> {
                level.setBlockAndUpdate(support, Blocks.STONE_SLAB.defaultBlockState());
                placeColumn(level, COLUMN_BOTTOM);
            }
        }
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
        Capture capture = CAPTURES.get(captureIndex);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (!padPrepared) {
                preparePad(level);
            }
            SceneKind needed = capture.scene();
            if (needed != builtScene) {
                buildSceneKind(level, needed);
                builtScene = needed;
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
            Capture capture = CAPTURES.get(captureIndex);
            double distance = capture.closeUp() ? 2.25 : 3.75;
            double lookUp = capture.closeUp() ? 0.45 : 1.1;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getChunk(COLUMN_BOTTOM.getX() >> 4, COLUMN_BOTTOM.getZ() >> 4);
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                double camX = COLUMN_BOTTOM.getX() + 0.5;
                double camZ = COLUMN_BOTTOM.getZ() + distance + 0.5;
                double camY = FLOOR_Y + 1.0;
                player.teleportTo(level, camX, camY, camZ, java.util.Set.of(), 180, 0, true);
                Vec3 target = Vec3.atCenterOf(COLUMN_BOTTOM).add(0, lookUp, 0);
                aimAt(player, target);
                player.setYHeadRot(player.getYRot());
            });
            return;
        }
        if (waitTicks < 60) {
            return;
        }
        Capture capture = CAPTURES.get(captureIndex);
        if (client.player != null) {
            double distance = capture.closeUp() ? 2.25 : 3.75;
            double lookUp = capture.closeUp() ? 0.45 : 1.1;
            double camX = COLUMN_BOTTOM.getX() + 0.5;
            double camZ = COLUMN_BOTTOM.getZ() + distance + 0.5;
            double camY = FLOOR_Y + 1.0;
            Vec3 target = Vec3.atCenterOf(COLUMN_BOTTOM).add(0, lookUp, 0);
            client.player.setPos(camX, camY, camZ);
            client.player.setYRot(180);
            aimAtClient(client.player, target);
        }
        client.options.hideGui = true;
        phase = Phase.CAPTURE;
        waitTicks = 0;
    }

    private static void captureScreenshot(Minecraft client) {
        waitTicks++;
        if (waitTicks < 40) {
            return;
        }
        if (screenshotWritePending) {
            return;
        }
        Path dir = client.gameDirectory.toPath().resolve("screenshots").resolve("simplylight-lamp-post");
        String name = CAPTURES.get(captureIndex).fileName() + ".png";
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

    private static void aimAtClient(net.minecraft.client.player.LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        Vec3 diff = target.subtract(eye);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float xRot = (float) (-(Math.atan2(diff.y, horizontal) * (180.0 / Math.PI)));
        float yRot = (float) (Math.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0f;
        player.setXRot(xRot);
        player.setYRot(yRot);
    }

    private static void advanceCapture(Minecraft client) {
        captureIndex++;
        if (captureIndex >= CAPTURES.size()) {
            SimplyLightCommon.LOGGER.info("[SimplyLight lamp post screenshots] captured {} images", CAPTURES.size());
            phase = Phase.DONE;
            return;
        }
        Capture next = CAPTURES.get(captureIndex);
        Capture prev = CAPTURES.get(captureIndex - 1);
        if (next.scene() != prev.scene()) {
            phase = Phase.BUILD_SCENE;
        } else {
            phase = Phase.POSITION_CAMERA;
        }
        waitTicks = 0;
    }
}
