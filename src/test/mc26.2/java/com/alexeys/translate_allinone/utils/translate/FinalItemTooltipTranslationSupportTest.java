package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FinalItemTooltipTranslationSupportTest {
    @Test
    void defersEarlyMirrorOnlyInsideItemRenderingAndRestoresScopeOnFailure() {
        assertFalse(FinalItemTooltipTranslationSupport.ownsTooltip());
        assertThrows(IllegalStateException.class, () -> FinalItemTooltipTranslationSupport.render(() -> {
            assertTrue(FinalItemTooltipTranslationSupport.ownsTooltip());
            assertTrue(UiTranslationScope.isInternal());
            FinalItemTooltipTranslationSupport.render(() -> assertTrue(FinalItemTooltipTranslationSupport.ownsTooltip()));
            assertTrue(FinalItemTooltipTranslationSupport.ownsTooltip());
            throw new IllegalStateException("render failure");
        }));
        assertFalse(FinalItemTooltipTranslationSupport.ownsTooltip());
        assertFalse(UiTranslationScope.isInternal());
    }

    @Test
    void dedicatedTooltipOwnersKeepTheirExistingPipeline() {
        FinalItemTooltipTranslationSupport.render(() -> {
            TooltipTranslationContext.pushReiTooltipRender();
            try {
                assertFalse(FinalItemTooltipTranslationSupport.ownsTooltip());
            } finally {
                TooltipTranslationContext.popReiTooltipRender();
            }
            GenericSurfaceTranslationSupport.renderHover(() -> assertFalse(FinalItemTooltipTranslationSupport.ownsTooltip()));
            assertTrue(FinalItemTooltipTranslationSupport.ownsTooltip());
        });
    }

    @Test
    void screenshotRowsAreEligibleForItemLoreTranslationWithStylesAndNumbersIntact() {
        ItemTranslateConfig config = new ItemTranslateConfig();
        config.enabled = true;
        config.enabled_translate_item_lore = true;
        List<Component> rows = List.of(
                price("NPC Sell Price:", "3.0 Coins"),
                price("Lowest BIN Price:", "175.0 Coins"),
                price("3 Day Avg. Price:", "3.0 Coins"),
                price("Est. Item Value:", "3.0 Coins"),
                Component.literal("Obtained:").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("June 20, 2025").withStyle(ChatFormatting.RED)),
                Component.literal("Museum (Farming):").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("✖ Not Donated").withStyle(ChatFormatting.RED))
        );
        for (Component row : rows) {
            String original = row.getString();
            assertTrue(TooltipTextMatcherSupport.evaluateTooltipLine(row, false, config).shouldTranslate(), original);
            var prepared = TooltipTemplateRuntime.prepareTemplate(row, false);
            assertFalse(prepared.translationTemplateKey().isBlank(), original);
            assertEquals(original, row.getString());
            config.enabled_translate_item_lore = false;
            assertFalse(TooltipTextMatcherSupport.evaluateTooltipLine(row, false, config).shouldTranslate());
            config.enabled_translate_item_lore = true;
        }
    }

    private Component price(String label, String value) {
        return Component.literal(label).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(value).withStyle(ChatFormatting.DARK_AQUA));
    }
}
