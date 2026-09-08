package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ChatOutputFormatSupportTest {
    @Test
    void glyphSplitRunsHaveUniqueIdsAndSurviveBothTranslationPaths() {
        Component source = Component.literal("     §7§fGrants §a+§816→§a20§f §6\uE054 Foraging Fortune§f, which increases your")
                .withStyle(ChatFormatting.GOLD);
        var extracted = ChatOutputFormatSupport.extract(source);
        var numbers = com.alexeys.translate_allinone.utils.text.TemplateProcessor.extract(extracted.markedText);
        var glyphs = com.alexeys.translate_allinone.utils.text.TemplateProcessor.extractDecorativeGlyphTags(numbers.template());
        // This valid source previously failed its own validator before any model was involved.
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.restore(glyphs.template(), glyphs.template()));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source, java.util.Set.of());
        var tags = java.util.regex.Pattern.compile("<s(\\d+)>").matcher(prepared.textToTranslate());
        java.util.Set<String> ids = new java.util.HashSet<>();
        while (tags.find()) assertTrue(ids.add(tags.group(1)), prepared.textToTranslate());
        assertTrue(ids.size() > 1);
        assertEquals(source.getString(), ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate(), prepared).getString());
        String translated = prepared.textToTranslate().replace("Grants", "获得")
                .replace("Foraging Fortune", "伐木幸运").replace(", which increases your", "，提高你的");
        String expected = "     §7§f获得 §a+§816→§a20§f §6\uE054 伐木幸运§f，提高你的";
        assertEquals(expected, ChatOutputTranslateManager.rebuildTranslatedText(translated, prepared).getString());
        String json = ChatOutputFormatSupport.runRequest(prepared.textToTranslate()).replace("Grants", "获得")
                .replace("Foraging Fortune", "伐木幸运").replace(", which increases your", "，提高你的");
        assertEquals(expected, ChatOutputTranslateManager.rebuildTranslatedText(
                ChatOutputFormatSupport.runReply(prepared.textToTranslate(), json), prepared).getString());
    }

    @Test
    void privateGlyphInsideOneStyleDoesNotCreateDuplicateTranslationIds() {
        Component source = Component.literal("Gain \uE001 Luck and \uE002 Fortune").withStyle(ChatFormatting.GREEN);
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source, java.util.Set.of());
        String json = ChatOutputFormatSupport.runRequest(prepared.textToTranslate())
                .replace("Gain", "获得").replace("Luck and", "运气和").replace("Fortune", "幸运");
        Component translated = ChatOutputTranslateManager.rebuildTranslatedText(
                ChatOutputFormatSupport.runReply(prepared.textToTranslate(), json), prepared);
        assertEquals("获得 \uE001 运气和 \uE002 幸运", translated.getString());
        translated.visit((style, text) -> {
            if (!text.isEmpty()) assertEquals(Style.EMPTY.withColor(ChatFormatting.GREEN), style);
            return Optional.empty();
        }, Style.EMPTY);
    }
    @Test
    void protectsLegacyColoredChatSpeakerWithoutOnlinePlayerList() {
        Component source = Component.literal("§a[VIP] SealsAreGreat§f: Hello there!");
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source, java.util.Set.of());
        assertFalse(prepared.textToTranslate().contains("SealsAreGreat"));
        assertEquals("§a[VIP] SealsAreGreat§f: 你好！", ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate().replace("Hello there!", "你好！"), prepared).getString());
    }

    @Test
    void protectsOnlineNamesInJoinNoticesAndBodyBeforeNumericExtraction() {
        Component source = Component.literal("Alex123 joined the lobby! Say hello to Alex123 and s0.");
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source, java.util.Set.of("Alex123", "s0"));
        assertFalse(prepared.textToTranslate().contains("Alex123"));
        assertEquals("Alex123 加入了大厅！向 Alex123 和 s0 问好。", ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate().replace("joined the lobby! Say hello to", "加入了大厅！向")
                        .replace(" and ", " 和 ").replace(".", " 问好。"), prepared).getString());
    }

    @Test
    void keepsTabDisplayNameUntouched() {
        Component source = Component.literal("[VIP] Strong_Panda").withStyle(ChatFormatting.GREEN);
        assertSame(source, GenericSurfaceTranslationSupport.playerDisplayName(source));
    }
    @Test
    void numberedRepairRestoresStylesWithoutAskingModelForTags() {
        String source = "<s0>  ■ </s0><s1>Visit </s1><s2>Gold Mine</s2>";
        assertEquals("{\"1\":\"Visit \",\"2\":\"Gold Mine\"}", ChatOutputFormatSupport.runRequest(source));
        assertEquals("<s0>  ■ </s0><s1>前往 </s1><s2>金矿</s2>",
                ChatOutputFormatSupport.runReply(source, "{\"2\":\"金矿\",\"1\":\"前往\"}"));
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.runReply(source, "{\"1\":\"前往金矿\"}"));
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.runReply(source, "{\"1\":null,\"2\":\"金矿\"}"));
    }
    @Test
    void repairsBraceMarkerAndRestoresSeparateIcon() {
        Component source = Component.empty()
                .append(Component.literal("  ❖ ").withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                        .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("server", "icons")))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Original hover")))))
                .append(Component.literal("Gold Mine").withStyle(ChatFormatting.GOLD));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source);
        Component result = ChatOutputTranslateManager.rebuildTranslatedText("{s1} 金矿", prepared);
        assertEquals("  ❖ 金矿", result.getString());
        assertEquals(styles(source), styles(result));
    }

    @Test
    void restoresIndentAndBulletWithinTextRun() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(
                Component.literal("    ■ Talk to the Lazy Miner.").withStyle(ChatFormatting.WHITE));
        assertEquals("    ■ 与懒矿工交谈。", ChatOutputTranslateManager.rebuildTranslatedText(
                "<s0>与懒矿工交谈。</s0>", prepared).getString());
    }

    @Test
    void preservesBoldColorInsertionAndExplicitLines() {
        Component source = Component.empty()
                .append(Component.literal("  NEW AREA DISCOVERED!").withStyle(
                        Style.EMPTY.withColor(ChatFormatting.GOLD).withBold(true).withInsertion("original")))
                .append(Component.literal("\n    Visit the Blacksmith.").withStyle(ChatFormatting.WHITE));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source);
        Component result = ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate().replace("NEW AREA DISCOVERED!", "发现新区域！")
                        .replace("Visit the Blacksmith.", "拜访铁匠。"), prepared);
        assertEquals("  发现新区域！\n    拜访铁匠。", result.getString());
        // A standalone line break has no visible glyph; compare the styled visible runs.
        assertEquals(styles(source), styles(result));
    }

    @Test
    void preservesSpeakerPrefixInsideSameRun() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(
                Component.literal("[NPC] Maddox: Bring me the items.").withStyle(ChatFormatting.YELLOW));
        assertFalse(prepared.textToTranslate().contains("Maddox"));
        assertEquals("[NPC] Maddox: 把物品带给我。", ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate().replace("Bring me the items.", "把物品带给我。"), prepared).getString());
    }

    @Test
    void rejectsLostDuplicatedOrUnknownRuns() {
        String source = "<s0>Visit </s0><s1>Gold Mine</s1>";
        for (String reply : List.of("前往金矿", "<s0>前往</s0>",
                "<s0>前往</s0><s1>金矿</s1><s1>金矿</s1>", "<s9>前往金矿</s9>")) {
            assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                    () -> ChatOutputFormatSupport.restore(source, reply), reply);
        }
    }

    @Test
    void rejectsChangedNumbersAndPartialPlaceholders() {
        String source = "<s0>Mine {n1} coal.</s0>";
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.restore(source, "<s0>挖煤。</s0>"));
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.restore(source, "<s0>挖 {n1} 煤 {s</s0>"));
        assertEquals("<s0>挖 {n1} 煤。</s0>",
                ChatOutputFormatSupport.restore(source, "<s0>挖 {n1} 煤。</s0>"));
    }

    @Test
    void incompleteStreamKeepsOriginalStylesAndText() {
        Component source = Component.literal("    ■ Visit the Blacksmith.").withStyle(ChatFormatting.WHITE);
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source);
        Component preview = ChatOutputTranslateManager.rebuildStreamingPreview("<s0>拜访铁匠。</s", prepared);
        assertEquals(source.getString(), preview.getString());
        assertEquals(styles(source), styles(preview));
    }

    @Test
    void preservesPrivateUseSpacingAndNumberOnlyRuns() {
        Component source = Component.empty()
                .append(Component.literal("\uE000\uE001  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Progress ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("42").withStyle(ChatFormatting.GREEN));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source);
        Component result = ChatOutputTranslateManager.rebuildTranslatedText(
                prepared.textToTranslate().replace("Progress", "进度"), prepared);
        assertEquals("\uE000\uE001  进度 42", result.getString());
        assertEquals(styles(source), styles(result));
    }

    private static List<Style> styles(Component component) {
        List<Style> result = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isBlank()) result.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
