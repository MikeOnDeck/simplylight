package com.flanks255.simplylight;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class SimplyLightRuntimeChecks {
    public static final String VERIFY_LOG_PREFIX = "[SimplyLight verification]";

    private SimplyLightRuntimeChecks() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(SimplyLightRuntimeChecks::onServerStarted);
    }

    private static void onServerStarted(MinecraftServer server) {
        long recipeCount = server.getRecipeManager()
                .getRecipes()
                .stream()
                .map(RecipeHolder::id)
                .filter(id -> SimplyLightCommon.MODID.equals(id.identifier().getNamespace()))
                .count();

        CreativeModeTabs.tryRebuildTabContents(
                server.overworld().enabledFeatures(),
                false,
                server.registryAccess()
        );

        ResourceKey<CreativeModeTab> tabKey = SimplyLightCommon.LIGHTS_TAB;
        boolean tabRegistered = BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(tabKey);
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tabKey);
        int tabItemCount = tab != null ? tab.getDisplayItems().size() : 0;
        boolean tabShouldDisplay = tab != null && tab.shouldDisplay();

        SimplyLightCommon.LOGGER.info(
                "{} simplylight recipes loaded: {}",
                VERIFY_LOG_PREFIX,
                recipeCount
        );
        SimplyLightCommon.LOGGER.info(
                "{} creative tab {} registered: {}, shouldDisplay: {}, display item stacks: {}",
                VERIFY_LOG_PREFIX,
                tabKey.identifier(),
                tabRegistered,
                tabShouldDisplay,
                tabItemCount
        );

        boolean recipeVerify = Boolean.getBoolean("simplylight.recipeVerify");
        boolean smokeTest = Boolean.getBoolean("simplylight.smokeTest");

        if (recipeVerify || smokeTest) {
            SimplyLightRecipeVerifier.verify(server);
        }

        if (smokeTest) {
            if (recipeCount < 230 || !tabRegistered || !tabShouldDisplay || tabItemCount < 100) {
                throw new IllegalStateException(
                        "Smoke test failed: recipes=" + recipeCount + ", tabRegistered=" + tabRegistered
                                + ", shouldDisplay=" + tabShouldDisplay + ", tabItems=" + tabItemCount
                );
            }
            SimplyLightCommon.LOGGER.info("{} smoke test complete, stopping server", VERIFY_LOG_PREFIX);
            server.halt(false);
        }

        if (recipeVerify && !smokeTest) {
            SimplyLightCommon.LOGGER.info("{} recipe verification complete, stopping server", VERIFY_LOG_PREFIX);
            server.halt(false);
        }
    }
}
