package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class HudGlyphProtectionTest {
    private Component location() {
        return Component.literal("\uE001 Graveyard").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("server", "hud"))));
    }

    private ComponentTranslationResponse reply(ComponentTranslationDocument document) {
        assertTrue(document.units().stream().anyMatch(unit -> unit.sourceText().contains("Graveyard")));
        assertTrue(document.units().stream().noneMatch(unit -> unit.sourceText().contains("\uE001")));
        return new ComponentTranslationResponse(document.protocol(), document.units().stream().collect(
                Collectors.toMap(unit -> unit.id(), unit -> unit.sourceText().replace("Graveyard", "墓地"))));
    }

    @Test
    void vanillaAndModScoreboardsTranslateLocationAndRestoreGlyph() {
        Component source = location();
        var vanilla = ScoreboardEntryTemplate.prepare(source, Component.empty(), false, true, Component.empty());
        assertEquals("\uE001 墓地", vanilla.renderTranslated(reply(vanilla.document())).getString());
        var external = ExternalScoreboardComponentTemplate.prepare(source, Set.of());
        var document = ExternalScoreboardTranslationSupport.prepareDocument(external.templateComponent(), external.privatePlaceholders());
        Component translated = external.restore(new ComponentTranslationApplier().apply(document, reply(document)));
        assertEquals("\uE001 墓地", translated.getString());
        assertEquals(source.getStyle(), translated.getStyle());
    }

    @Test
    void actionBarAllowsReadableLocationButSkipsPureGlyphs() {
        OtherTranslationsConfig config = new OtherTranslationsConfig();
        config.enabled = true;
        config.enabled_translate_action_bar = true;
        config.keybinding.mode = OtherTranslationsConfig.KeybindingMode.DISABLED;
        ComponentRenderTranslationSupport.setTranslationPipelineForTesting((source, route, context, version, cfg, refresh, tokens) -> {
            assertEquals(Set.of("\uE001"), tokens);
            var template = ComponentDynamicTemplate.prepare(source, tokens);
            var document = ComponentTranslationRuntime.prepare(template.templateComponent(), route, context, version);
            var translated = template.restore(new ComponentTranslationApplier().apply(document, reply(document)));
            return new ComponentRenderTranslationSupport.TranslationResult(source, translated, null,
                    ComponentTranslationRuntime.State.CACHE_HIT, false);
        });
        try {
            assertEquals("\uE001 墓地", GenericSurfaceTranslationSupport.translate(location(),
                    ComponentTranslationRoute.ACTION_BAR, "action-bar", config).getString());
            Component icons = Component.literal("\uE001\uE002");
            assertSame(icons, GenericSurfaceTranslationSupport.translate(icons,
                    ComponentTranslationRoute.ACTION_BAR, "action-bar", config));
        } finally {
            ComponentRenderTranslationSupport.setTranslationPipelineForTesting(null);
        }
    }
}
