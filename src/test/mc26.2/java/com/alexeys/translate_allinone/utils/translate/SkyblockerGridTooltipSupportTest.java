package com.alexeys.translate_allinone.utils.translate;

import de.hysky.skyblocker.utils.render.text.GridComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SkyblockerGridTooltipSupportTest {
    @Test
    void obtainedDateUsesStrictLocalConversionBeforeProviderOrCache() {
        var config = new com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig();
        config.target_language = "Chinese";
        Component date = Component.literal("February 8, 2025").withStyle(ChatFormatting.RED);
        Component translated = SkyblockerGridTooltipSupport.translateColumn(date, config);
        assertEquals("2025年2月8日", translated.getString());
        assertEquals(date.getStyle(), translated.getStyle());
        assertEquals("February 8, 2025", date.getString());
        assertEquals("2024年2月29日", SkyblockerGridTooltipSupport.translateColumn(
                Component.literal("February 29, 2024"), config).getString());
        Component invalid = Component.literal("February 29, 2025");
        assertSame(invalid, SkyblockerGridTooltipSupport.translateColumn(invalid, config));
        assertEquals("2025年12月31日", SkyblockerGridTooltipSupport.translateColumn(
                Component.literal("December 31, 2025"), config).getString());
    }

    @Test
    void dateLocalizationHonorsLanguageAndNestedStyle() {
        Component source = Component.empty().append(Component.literal(" June 20, 2025 ").withStyle(ChatFormatting.RED));
        for (String language : List.of("Chinese", "zh-CN", "zh_TW", "简体中文", "Traditional Chinese")) {
            Component translated = TooltipDateTranslationSupport.translate(source, language);
            assertEquals(" 2025年6月20日 ", translated.getString());
            assertEquals(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.RED), translated.getStyle());
        }
        assertSame(source, TooltipDateTranslationSupport.translate(source, "English"));
        assertNull(TooltipDateTranslationSupport.translate(Component.literal("175.0 Coins"), "Chinese"));
    }
    @Test
    void translatesInvisibleGridPayloadWithoutSerializingOrFlatteningColumns() {
        Map<String, String> examples = Map.of(
                "NPC Sell Price:", "NPC 出售价格：", "Lowest BIN Price:", "最低一口价：",
                "3 Day Avg. Price:", "三日均价：", "Est. Item Value:", "物品估值：",
                "Obtained:", "获取日期：", "Museum (Farming):", "博物馆（耕作）：");
        for (var entry : examples.entrySet()) {
            Component left = Component.literal(entry.getKey()).withStyle(ChatFormatting.GOLD);
            Component right = Component.literal("175.0 Coins").withStyle(ChatFormatting.DARK_AQUA);
            Component source = grid(left, right);
            assertEquals("", source.getString());
            assertThrows(UnsupportedOperationException.class, () -> source.getContents().codec());
            Component translated = SkyblockerGridTooltipSupport.translateRow(source, column ->
                    column == left ? Component.literal(entry.getValue()).setStyle(column.getStyle()) : column);
            var contents = assertInstanceOf(GridComponent.Contents.class, translated.getContents());
            assertEquals("prices", contents.group());
            assertEquals(2, contents.components().size());
            assertEquals(entry.getValue(), contents.components().getFirst().getString());
            assertEquals(left.getStyle(), contents.components().getFirst().getStyle());
            assertSame(right, contents.components().get(1));
            assertSame(left, ((GridComponent.Contents) source.getContents()).components().getFirst());
        }
    }

    @Test
    void leavesPendingFailedAndOrdinaryRowsUntouched() {
        Component source = grid(Component.literal("Obtained:"), Component.literal("June 20, 2025"));
        assertSame(source, SkyblockerGridTooltipSupport.translateRow(source, column -> column));
        assertSame(source, SkyblockerGridTooltipSupport.translateRow(source, column -> null));
        assertSame(source, SkyblockerGridTooltipSupport.translateRow(source, column -> { throw new IllegalStateException(); }));
        Component ordinary = Component.literal("ordinary lore");
        assertSame(ordinary, SkyblockerGridTooltipSupport.translateRow(ordinary, column -> { fail(); return column; }));
    }

    @Test
    void currentPriceAndMuseumStatusComeFromCurrentGridNotPreviousFrame() {
        Component first = grid(Component.literal("Museum (Farming):"), Component.literal("✖ Not Donated"));
        Component next = grid(Component.literal("Museum (Farming):"), Component.literal("✔ Donated"));
        var translated = SkyblockerGridTooltipSupport.translateRow(first, column ->
                Component.literal(column.getString().replace("Not Donated", "未捐赠")));
        assertEquals("✖ 未捐赠", ((GridComponent.Contents) translated.getContents()).components().get(1).getString());
        assertSame(next, SkyblockerGridTooltipSupport.translateRow(next, column -> column));
    }

    private Component grid(Component left, Component right) {
        return MutableComponent.create(new GridComponent.Contents("prices", List.of(left, right)));
    }
}
