package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.cache.component.ComponentCacheModule;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyblockModUiSupportTest {
    @Test
    void storageUsesDedicatedRouteWithGenericTranslationDisabled() {
        ModConfig config = new ModConfig();
        config.otherTranslations.enabled = false;
        config.otherTranslations.enabled_screen_translation = false;
        config.hypixelUi.enabled = true;
        Component source = Component.literal("Storage");
        ComponentTranslationRoute[] actualRoute = {null};
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((original, route, context, version, settings, refresh, tokens) -> {
            actualRoute[0] = route;
            assertTrue(refresh, "SkyBlock widget tooltips must honor the shared refresh binding");
            assertSame(config.hypixelUi, settings);
            return new ComponentRenderTranslationSupport.TranslationResult(original, Component.literal("仓库"),
                    null, ComponentTranslationRuntime.State.CACHE_HIT, false);
        });
        try {
            assertEquals("仓库", SkyblockModUiSupport.translateTooltip(source,
                    SkyblockModUiSupport.selectConfig(config, true)).getString());
            assertEquals(ComponentTranslationRoute.SKYBLOCK_UI, actualRoute[0]);
            config.hypixelUi.enabled = false;
            config.otherTranslations.enabled = true;
            config.otherTranslations.enabled_screen_translation = true;
            assertSame(source, SkyblockModUiSupport.translateTooltip(source,
                    SkyblockModUiSupport.selectConfig(config, true)));
            assertSame(config.otherTranslations, SkyblockModUiSupport.selectConfig(config, false));
        } finally {
            ComponentRenderTranslationSupport.setTranslationPipelineForTesting(null);
        }
        assertNotEquals(ComponentCacheModule.forRoute(ComponentTranslationRoute.SCREEN_UI),
                ComponentCacheModule.forRoute(ComponentTranslationRoute.SKYBLOCK_UI));
    }

    @Test
    void onlyKnownModOwnersAreIndependent() {
        assertTrue(SkyblockModUiSupport.ownsClass("de.hysky.skyblocker.skyblock.quicknav.QuickNavButton"));
        assertTrue(SkyblockModUiSupport.ownsClass("at.hannibal2.skyhanni.features.inventory.TestScreen"));
        assertFalse(SkyblockModUiSupport.ownsClass("net.minecraft.client.gui.screens.inventory.InventoryScreen"));
        assertFalse(SkyblockModUiSupport.ownsClass("me.shedaniel.rei.Screen"));
        assertFalse(SkyblockModUiSupport.ownsClass("de.hysky.skyblockerOther.Screen"));
    }
}
