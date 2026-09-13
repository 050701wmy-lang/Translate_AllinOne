package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import com.alexeys.translate_allinone.utils.config.pojos.HypixelUiConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelRoutingTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void npcKeepsHypixelModelAfterSharedTextCanonicalization() {
        var payload = ChatOutputTranslateManager.prepareTranslationPayload(Component.literal("[NPC] Kat: Hello!"));
        var original = ChatOutputTranslateManager.prepareSharedNpcDocument(payload);
        var unit = original.units().getFirst();
        var plan = SharedHudTranslationSupport.plan(unit.sourceText(), text -> null);
        var shared = SharedHudTranslationSupport.document(original, unit, plan, "hypixel");
        assertEquals(ProviderRouteResolver.Route.HYPIXEL, ComponentTranslationRuntimeCore.providerRoute(shared));
        var scoreboard = ComponentTranslationRuntime.prepare(Component.literal("Hello!"), ComponentTranslationRoute.SCOREBOARD, "test", "1");
        assertEquals(ProviderRouteResolver.Route.SCOREBOARD, ComponentTranslationRuntimeCore.providerRoute(scoreboard));
        var ui = ComponentTranslationRuntime.prepare(Component.literal("Storage"), ComponentTranslationRoute.SKYBLOCK_UI, "test", "1");
        assertEquals(ProviderRouteResolver.Route.HYPIXEL, ComponentTranslationRuntimeCore.providerRoute(ui));
        var generic = ComponentTranslationRuntime.prepare(Component.literal("Storage"), ComponentTranslationRoute.SCREEN_UI, "test", "1");
        assertEquals(ProviderRouteResolver.Route.OTHER_TRANSLATIONS, ComponentTranslationRuntimeCore.providerRoute(generic));
    }

    @Test
    void sharedHotkeyModesDoNotDependOnTheModUiToggle() {
        var config = new HypixelUiConfig();
        config.enabled = false; // NPC and server switches are independent.
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.HOLD_TO_TRANSLATE;
        assertFalse(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, true));
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.HOLD_TO_SEE_ORIGINAL;
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
        assertFalse(ComponentRenderTranslationSupport.shouldRenderTranslated(config, true));
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.DISABLED;
        assertTrue(ComponentRenderTranslationSupport.shouldRenderTranslated(config, false));
    }
}
