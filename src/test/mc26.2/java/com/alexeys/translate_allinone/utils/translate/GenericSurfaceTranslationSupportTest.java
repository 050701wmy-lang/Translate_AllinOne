package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.component.ComponentCacheModule;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRuntime;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GenericSurfaceTranslationSupportTest {
    @AfterEach
    void reset() {
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting(null);
        TranslationFeatureGate.update(true);
    }

    private OtherTranslationsConfig config() {
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        config.enabled = true;
        config.enabled_translate_titles = true;
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.DISABLED;
        return config;
    }

    @Test
    void fallsBackWhilePendingThenUsesTranslationWithoutMutatingSource() {
        OtherTranslationsConfig config = config();
        Component source = Component.literal("Quest Complete").withStyle(ChatFormatting.GOLD);
        Component translated = Component.literal("任务完成").setStyle(source.getStyle());
        AtomicInteger requests = new AtomicInteger();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((text, route, context, version, cfg, refresh, tokens) -> {
            assertEquals(ComponentTranslationRoute.TITLE, route);
            assertEquals("title", context);
            assertTrue(refresh);
            boolean ready = requests.getAndIncrement() > 0;
            return new ComponentRenderTranslationSupport.TranslationResult(text, ready ? translated : text, null,
                    ready ? ComponentTranslationRuntime.State.CACHE_HIT : ComponentTranslationRuntime.State.PENDING, !ready);
        });
        assertSame(source, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.TITLE, "title", config));
        assertSame(translated, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.TITLE, "title", config));
        assertEquals("Quest Complete", source.getString());
        assertEquals(source.getStyle(), translated.getStyle());
    }

    @Test
    void gatesRequestsForDisabledFeaturesOriginalModeAndDecorativeOrNumericText() {
        OtherTranslationsConfig config = config();
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((a, b, c, d, e, f, g) -> {
            fail("An excluded surface must never enter the translation pipeline");
            return null;
        });
        for (String text : List.of("", "42", "/warp hub", "\uE001\uE002")) {
            Component source = Component.literal(text);
            assertSame(source, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.TITLE, "title", config));
        }
        Component source = Component.literal("Quest Complete");
        assertSame(source, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.BOSS_BAR, "boss", config));
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.HOLD_TO_TRANSLATE;
        assertSame(source, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.TITLE, "title", config));
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.DISABLED;
        TranslationFeatureGate.update(false);
        assertSame(source, GenericSurfaceTranslationSupport.translate(source, ComponentTranslationRoute.TITLE, "title", config));
    }

    @Test
    void originalChatHoverAndNonTextHoverRemainUntouched() {
        Style source = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                ChatOutputOriginalHoverStyle.markComponent(Component.literal("Original English"))));
        assertSame(source, GenericSurfaceTranslationSupport.hoverStyle(source));
        assertSame(Style.EMPTY, GenericSurfaceTranslationSupport.hoverStyle(Style.EMPTY));
        assertNull(GenericSurfaceTranslationSupport.hoverStyle(null));
    }

    @Test
    void nestedHoverScopeAlwaysRestoresItemAndUiBoundaries() {
        assertThrows(IllegalStateException.class, () -> GenericSurfaceTranslationSupport.renderHover(() -> {
            assertTrue(GenericSurfaceTranslationSupport.isRenderingHover());
            assertTrue(UiTranslationScope.isInternal());
            GenericSurfaceTranslationSupport.renderHover(() -> assertTrue(GenericSurfaceTranslationSupport.isRenderingHover()));
            assertTrue(GenericSurfaceTranslationSupport.isRenderingHover());
            throw new IllegalStateException("render failure");
        }));
        assertFalse(GenericSurfaceTranslationSupport.isRenderingHover());
        assertFalse(UiTranslationScope.isInternal());
    }

    @Test
    void genericSurfacesHaveSeparateStoresAndDoNotOwnSpecializedRoutes() {
        var files = new HashSet<String>();
        for (var route : List.of(ComponentTranslationRoute.PLAYER_LIST, ComponentTranslationRoute.BOSS_BAR,
                ComponentTranslationRoute.TITLE, ComponentTranslationRoute.ACTION_BAR, ComponentTranslationRoute.HOVER_TEXT,
                ComponentTranslationRoute.SCREEN_UI)) {
            var module = ComponentCacheModule.forRoute(route);
            assertTrue(files.add(module.fileName()));
            assertFalse(module.owns(ComponentTranslationRoute.TOOLTIP_STRUCTURED));
            assertFalse(module.owns(ComponentTranslationRoute.SCOREBOARD));
            assertFalse(module.owns(ComponentTranslationRoute.BOOK_PAGE));
            assertEquals(route, ComponentTranslationRoute.fromWireName(route.wireName()));
        }
    }
}
