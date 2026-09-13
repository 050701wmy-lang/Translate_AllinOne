package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedHudTranslationSupportTest {
    @Test
    void sackDescriptionAndPriceKeepReadableTermsWithoutInventingHardTokens() {
        for (String text : List.of("<s0>Items you pickup go directly into your sacks.</s0>",
                "<s0>Price: </s0><s1>{d1} Coins</s1>")) {
            var original = ComponentTranslationRuntime.prepare(Component.literal(text),
                    ComponentTranslationRoute.TOOLTIP_LINE, "tooltip", "1");
            var plan = SharedHudTranslationSupport.planFor(original, text, Map.of("Coins", "金币")::get);
            var shared = SharedHudTranslationSupport.document(original, original.units().getFirst(), plan, "hypixel");
            assertEquals(List.of(text), SharedHudTranslationSupport.pieces(original, text));
            assertEquals(original.units().getFirst().protectedTokens(), shared.units().getFirst().protectedTokens());
            assertFalse(shared.units().getFirst().sourceText().contains("{taio.term."));
            String translated = text.contains("Coins")
                    ? "<s0>价格：</s0><s1>{d1} 金币</s1>"
                    : "<s0>你拾取的物品会直接进入袋子。</s0>";
            new ComponentTranslationValidator().validate(shared, new ComponentTranslationResponse(shared.protocol(),
                    Map.of(shared.units().getFirst().id(), translated)));
        }
    }
    @Test
    void itemProseKeepsItsProviderAndSentenceAcrossColors() {
        String text = "<s0>Talk to </s0><s1>Roddy</s1><s0> in the </s0><s2>Backwater Bayou</s2>";
        var original = ComponentTranslationRuntime.prepare(Component.literal(text),
                ComponentTranslationRoute.TOOLTIP_LINE, "tooltip", "1");
        assertEquals(List.of(text), SharedHudTranslationSupport.pieces(original, text));
        var plan = SharedHudTranslationSupport.planFor(original, text,
                Map.of("Roddy", "罗迪", "Backwater Bayou", "死水河湾")::get);
        var shared = SharedHudTranslationSupport.document(original, original.units().getFirst(), plan, "hypixel");
        assertEquals(ComponentTranslationRoute.TOOLTIP_LINE, shared.route());
        assertTrue(shared.units().getFirst().sourceText().contains("Talk to"));
        assertTrue(shared.units().getFirst().context().contains("罗迪"));
        var noOp = SharedHudTranslationSupport.plan("Holds various items", s -> s);
        assertNull(noOp.exact());
        assertTrue(noOp.terms().isEmpty());
        assertTrue(SharedHudTranslationSupport.hasTranslatableWords(noOp.text()));
    }
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test
    void npcAndItemNamesSharePlainIdentityAcrossStyleTags() {
        var item = SharedHudTranslationSupport.pieces("<s0>Bee</s0>");
        var npc = SharedHudTranslationSupport.pieces("<s7>Bee</s7>");
        assertEquals(SharedHudTranslationSupport.document(
                        SharedHudTranslationSupport.plan(item.get(2), s -> null), "hypixel", Set.of()),
                SharedHudTranslationSupport.document(
                        SharedHudTranslationSupport.plan(npc.get(2), s -> null), "hypixel", Set.of()));
        assertEquals("Bee", item.get(2));
        var wynn = WynnDialogueTranslationSupport.sharedNpcDocument("npc::Bee");
        assertTrue(SharedHudTranslationSupport.supports(wynn));
        assertEquals("Bee", wynn.units().getFirst().sourceText());
        assertFalse(SharedHudTranslationSupport.hasTranslatableWords("<s7>"));
        assertFalse(SharedHudTranslationSupport.hasTranslatableWords("</s7>"));
        for (var route : List.of(ComponentTranslationRoute.ENTITY_NAME, ComponentTranslationRoute.TEXT_DISPLAY,
                ComponentTranslationRoute.TOOLTIP_LINE, ComponentTranslationRoute.TOOLTIP_STRUCTURED,
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH)) {
            assertTrue(SharedHudTranslationSupport.supports(ComponentTranslationRuntime.prepare(
                    Component.literal("Bee"), route, "test", "1")));
        }
    }

    @Test
    void npcDocumentRetainsPlayerAndStyleProtectionWithoutCapturingOrdinaryChat() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(
                Component.literal("[NPC] Kat: Hello Alex, your ")
                        .append(Component.literal("Bee").withStyle(ChatFormatting.GREEN)), Set.of("Alex"));
        var npc = ChatOutputTranslateManager.prepareSharedNpcDocument(prepared);
        assertTrue(SharedHudTranslationSupport.supports(npc));
        assertFalse(npc.units().toString().contains("Alex"));
        assertEquals("[NPC] Kat: Hello Alex, your Bee",
                ChatOutputTranslateManager.rebuildTranslatedText(prepared.textToTranslate(), prepared).getString());
        assertFalse(SharedHudTranslationSupport.supports(ComponentTranslationRuntime.prepare(
                Component.literal("Hello Bee"), ComponentTranslationRoute.CHAT_OUTPUT, "chat", "1")));
    }

    @Test
    void descriptionParagraphKeepsAnchorsAndWholeSentence() {
        String text = "Grants <s0>{d1} Foraging Fortune</s0>\nwhen equipped.";
        var doc = ComponentTranslationRuntime.prepare(Component.literal(text),
                ComponentTranslationRoute.TOOLTIP_PARAGRAPH, "test", "1");
        assertEquals(List.of(text), SharedHudTranslationSupport.pieces(doc, text));
        var plan = SharedHudTranslationSupport.plan(text,
                Map.of("s", "错误", "Foraging Fortune", "采集时运")::get);
        assertTrue(plan.text().contains("<s0>{d1} {taio.term.0}</s0>"));
        assertEquals(text.replace("Foraging Fortune", "采集时运"), plan.restore(plan.text()));
    }

    @Test
    void sharedDescriptionStillPassesInlineAnchorProtocol() {
        var original = ComponentTranslationBundle.createInlineAnchoredParagraph("test", "paragraph-v6",
                "Gain {value0} Foraging Fortune.", List.of("{value0}"), List.of()).cacheDocument();
        var unit = original.units().getFirst();
        var plan = SharedHudTranslationSupport.planFor(original, unit.sourceText(),
                Map.of("Foraging Fortune", "采集时运")::get);
        var shared = SharedHudTranslationSupport.document(original, unit, plan, "hypixel");
        assertEquals(ComponentTranslationRoute.TOOLTIP_PARAGRAPH, shared.route());
        assertEquals("Gain {value0} Foraging Fortune.", shared.units().getFirst().sourceText());
        assertTrue(shared.units().getFirst().context().contains("Foraging Fortune=采集时运"));
        assertEquals(original.units().getFirst().protectedTokens(), shared.units().getFirst().protectedTokens());
        var response = new ComponentTranslationResponse(shared.protocol(),
                Map.of(unit.id(), "获得 {value0} 采集时运。"));
        new ComponentTranslationValidator().validate(shared, response);
        var restored = new ComponentTranslationResponse(original.protocol(),
                Map.of(unit.id(), plan.restore(response.translations().get(unit.id()))));
        new ComponentTranslationValidator().validate(original, restored);
        assertEquals("获得 {value0} 采集时运。", restored.translations().get(unit.id()));
        assertEquals(SharedHudTranslationSupport.document(
                        SharedHudTranslationSupport.plan("Bee", s -> null), "hypixel", Set.of("<s0>", "{d1}")),
                SharedHudTranslationSupport.document(
                        SharedHudTranslationSupport.plan("Bee", s -> null), "hypixel", Set.of("<s9>")));
    }

    @Test
    void numericRunsDoNotBlockLabelsAndDictionaryColorsDoNotLeak() {
        for (String text : List.of("{d1}", "{d1}K", "{d1}/{d2}", "§6{d1}K", "{d1}h",
                "{taio.private.a}", "{taio.term.0}")) {
            assertFalse(SharedHudTranslationSupport.hasTranslatableWords(text), text);
        }
        for (String text : List.of("Bank: ", "-Bits: ", "-Gems: ", "Ends In: ")) {
            assertTrue(SharedHudTranslationSupport.hasTranslatableWords(text), text);
        }
        var source = Component.literal("Bank").withStyle(ChatFormatting.WHITE);
        var plan = SharedHudTranslationSupport.plan("Bank", Map.of("Bank", "§9银行")::get);
        var doc = ComponentTranslationRuntime.prepare(source, ComponentTranslationRoute.SCOREBOARD, "test", "1");
        Component translated = new ComponentTranslationApplier().apply(doc,
                new ComponentTranslationResponse(doc.protocol(), Map.of(doc.units().getFirst().id(), plan.restore(plan.exact()))));
        assertEquals("银行", translated.getString());
        assertEquals(source.getStyle(), translated.getStyle());
        var term = SharedHudTranslationSupport.plan("Bank: ", Map.of("Bank", "§9银行")::get);
        assertFalse(SharedHudTranslationSupport.hasTranslatableWords(term.text()));
        assertEquals("银行: ", term.restore(term.text()));
        assertEquals(List.of("", "§f", "Bank: ", "§6", "{d1}K"),
                SharedHudTranslationSupport.pieces("§fBank: §6{d1}K"));
    }
    @Test
    void bossAndScoreboardTaskUseSameDocumentWithoutTheirLayout() {
        String task = SharedHudTranslationSupport.pieces("Objective: Catch a fish!").get(2);
        var boss = SharedHudTranslationSupport.document(
                SharedHudTranslationSupport.plan(task, s -> null), "hypixel", Set.of());
        var scoreboard = SharedHudTranslationSupport.document(
                SharedHudTranslationSupport.plan(" Catch a fish! ", s -> null), "hypixel", Set.of());
        assertEquals(boss, scoreboard);
        assertNotEquals(boss, SharedHudTranslationSupport.document(
                SharedHudTranslationSupport.plan(task, s -> null), "other-server", Set.of()));
    }

    @Test
    void dictionaryWinsAndUpdatedTermsChangeCacheIdentity() {
        var exact = SharedHudTranslationSupport.plan(" Catch a fish! ",
                Map.of("Catch a fish!", "钓到一条鱼！")::get);
        assertEquals(" 钓到一条鱼！ ", exact.restore(exact.exact()));
        var first = SharedHudTranslationSupport.plan("Gain Foraging Fortune now!",
                Map.of("Foraging", "采集", "Foraging Fortune", "采集时运")::get);
        assertEquals("Gain {taio.term.0} now!", first.text());
        assertEquals("获得采集时运！", first.restore("获得{taio.term.0}！"));
        var second = SharedHudTranslationSupport.plan("Gain Foraging Fortune now!",
                Map.of("Foraging Fortune", "伐木时运")::get);
        assertNotEquals(SharedHudTranslationSupport.document(first, "server", Set.of()),
                SharedHudTranslationSupport.document(second, "server", Set.of()));
        var placeholder = SharedHudTranslationSupport.plan("Gain {d1} Fortune",
                Map.of("d", "错误", "Fortune", "时运")::get);
        assertTrue(placeholder.text().contains("{d1}"));
    }

    @Test
    void sharedTextRebuildKeepsEachSurfacesColorsAndLineBreaks() {
        for (Component source : List.of(
                Component.literal("Objective: ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("Catch a fish!").withStyle(ChatFormatting.YELLOW)),
                Component.literal("Objective\n").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("Catch a fish!").withStyle(ChatFormatting.YELLOW)))) {
            var doc = ComponentTranslationRuntime.prepare(source, ComponentTranslationRoute.BOSS_BAR,
                    "test", "1");
            Map<String, String> values = new LinkedHashMap<>();
            for (var unit : doc.units()) values.put(unit.id(), unit.sourceText()
                    .replace("Objective", "目标").replace("Catch a fish!", "钓到一条鱼！"));
            Component result = new ComponentTranslationApplier().apply(doc,
                    new ComponentTranslationResponse(doc.protocol(), values));
            assertEquals(source.getString().replace("Objective", "目标")
                    .replace("Catch a fish!", "钓到一条鱼！"), result.getString());
            assertEquals(source.getStyle(), result.getStyle());
            assertEquals(source.getSiblings().getFirst().getStyle(), result.getSiblings().getFirst().getStyle());
        }
    }
}
