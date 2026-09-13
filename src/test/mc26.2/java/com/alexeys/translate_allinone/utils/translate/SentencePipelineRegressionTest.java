package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class SentencePipelineRegressionTest {
    @Test void matchingHudSentencesShareCacheAcrossRenderingEntrypoints() {
        Component source = Component.literal("Collect ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Wheat").withStyle(ChatFormatting.GREEN));
        var template = HudSentenceTemplate.prepare(source, Set.of());
        List<ComponentTranslationDocument> documents = List.of(
                template.document(ComponentTranslationRoute.BOSS_BAR),
                template.document(ComponentTranslationRoute.SCOREBOARD),
                ExternalScoreboardTranslationSupport.prepareDocument(template.templateComponent(), Set.of()));
        var keys = new HashSet<String>();
        for (var doc : documents) {
            var unit = doc.units().getFirst();
            var shared = SharedHudTranslationSupport.document(doc, unit,
                    SharedHudTranslationSupport.planFor(doc, unit.sourceText(), s -> null), "hypixel");
            keys.add(ComponentTranslationCacheIdentity.create(shared, "Chinese").key());
        }
        assertEquals(1, keys.size(), "Surface layout must not create separate translations of the same styled sentence");
    }

    @Test void npcTranslationMaySplitAStyleToSurroundAColoredPhrase() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(Component.literal(
                "§e[NPC] §9Enid§f: This area is popular because of the §bFishing Outpost§f!"), Set.of());
        String source = prepared.textToTranslate();
        String reply = run(source, "This area", "这里有")
                + run(source, "Fishing Outpost", "钓鱼前哨站")
                + run(source, "This area", "，所以这一带很受欢迎")
                + run(source, "!", "！");
        Component translated = validateAndRestore(prepared, reply);
        assertEquals("[NPC] Enid: 这里有钓鱼前哨站，所以这一带很受欢迎！", translated.getString());
        assertColor(translated, "钓鱼前哨站", ChatFormatting.AQUA);
        assertColor(translated, "，所以这一带很受欢迎", ChatFormatting.WHITE);
    }

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void actualLegacyNpcDoesNotSendColorCodesAsProtectedProse() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(Component.literal(
                "§e[NPC] §9Enid§f: §fThis part of the hub is a popular area for fishing because of the §bFishing Outpost§f!"), Set.of());
        assertFalse(prepared.textToTranslate().contains("§"));
        assertFalse(prepared.textToTranslate().contains("Enid"));
        String reply = prepared.textToTranslate()
                .replace("This part of the hub is a popular area for fishing because of the ", "因为这里有")
                .replace("Fishing Outpost", "钓鱼前哨站").replace("!", "，这一带成了热门钓鱼区！");
        Component result = validateAndRestore(prepared, reply);
        assertEquals("[NPC] Enid: 因为这里有钓鱼前哨站，这一带成了热门钓鱼区！", result.getString());
        assertColor(result, "钓鱼前哨站", ChatFormatting.AQUA);
        assertColor(result, "Enid", ChatFormatting.BLUE);
    }

    @Test void actualTorrhusDialogueCanReorderColoredLocationsInChinese() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(Component.literal(
                "§e[NPC] §6Torrhus Canyon§f: If you want to check it out for yourself, you can access it from the §aLaunch Pad §fover in the §2Moonglade Marsh§f."), Set.of());
        String reply = run(prepared.textToTranslate(), "If you want", "如果想亲自去看看，你可以从")
                + run(prepared.textToTranslate(), "Moonglade Marsh", "月光沼泽")
                + run(prepared.textToTranslate(), "over in", "里的")
                + run(prepared.textToTranslate(), "Launch Pad", "发射台")
                + run(prepared.textToTranslate(), ".", "前往。");
        Component result = validateAndRestore(prepared, reply);
        assertEquals("[NPC] Torrhus Canyon: 如果想亲自去看看，你可以从月光沼泽里的发射台前往。", result.getString());
        assertColor(result, "月光沼泽", ChatFormatting.DARK_GREEN);
        assertColor(result, "发射台", ChatFormatting.GREEN);
    }

    @Test void darkGreenPrefixDoesNotBecomeAVisibleTwoAndExtraDashesAreRemoved() {
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(Component.literal(
                "§e[NPC] §6Torrhus Canyon§f: §2Galatea§f's second island - §6Torrhus Canyon§f, is here!"), Set.of());
        assertTrue(prepared.templateValues().isEmpty());
        String reply = prepared.textToTranslate().replace("'s second island - ", "的第二座岛屿 - ")
                .replace("Torrhus Canyon", "-Torrhus Canyon").replace(", is here!", "，-就在这里！");
        String result = validateAndRestore(prepared, reply).getString();
        assertEquals("[NPC] Torrhus Canyon: Galatea的第二座岛屿 - Torrhus Canyon，就在这里！", result);
    }

    @Test void numericPlaceholderMayMoveWithinTheSentenceButCannotDisappearOrDuplicate() {
        String source = "<s0>{d1} players </s0><s1>Fishing</s1>";
        String reply = "<s1>钓鱼</s1><s0>的玩家有{d1}人</s0>";
        assertEquals(reply, ChatOutputFormatSupport.restoreSentence(source, reply));
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.restoreSentence(source, reply.replace("{d1}", "")));
        assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                () -> ChatOutputFormatSupport.restoreSentence(source, reply.replace("{d1}", "{d1}{d1}")));
    }

    @Test void npcGlyphsBetweenStyleRunsRoundTripAndTranslateWithoutRejectingOriginal() {
        Component original = Component.literal("[NPC] Kat: Gain \uE001 Fishing \uE002!")
                .withStyle(ChatFormatting.GREEN);
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(original, Set.of());
        assertTrue(prepared.textToTranslate().contains("{g1}"));
        assertEquals(original.getString(), validateAndRestore(prepared, prepared.textToTranslate()).getString());
        Component result = validateAndRestore(prepared, prepared.textToTranslate()
                .replace("Gain", "获得").replace("Fishing", "钓鱼"));
        assertEquals("[NPC] Kat: 获得 \uE001 钓鱼 \uE002!", result.getString());
        assertColor(result, "\uE001", ChatFormatting.GREEN);
    }

    @Test void sentenceAllowsOnlyProtectedStandaloneTokensAndRejectsLostOrAddedOnes() {
        String source = "{g1}<s0>Read </s0>{c1}<s1>notes</s1>{g2}";
        String translated = "{g1}<s0>阅读</s0>{c1}<s1>笔记</s1>{g2}";
        assertEquals(translated, ChatOutputFormatSupport.restoreSentence(source, translated));
        for (String invalid : List.of(translated.replace("{g1}", ""), translated + "{g3}",
                translated + "{g2}", translated + "extra", translated.replace("{c1}", "{c"))) {
            assertThrows(ChatOutputFormatSupport.InvalidFormat.class,
                    () -> ChatOutputFormatSupport.restoreSentence(source, invalid));
        }
    }

    @Test void anotherModsNpcButtonIsNotTranslatedAsPartOfTheDialogue() {
        Component source = Component.literal("npctranslator.button ")
                .append(Component.literal("§e[NPC] §6Torrhus Canyon§f: Happy hunting!"));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(source, Set.of());
        assertFalse(prepared.textToTranslate().contains("npctranslator"));
        assertFalse(prepared.textToTranslate().contains("Torrhus"));
        assertEquals("npctranslator.button [NPC] Torrhus Canyon: 狩猎愉快！",
                validateAndRestore(prepared, prepared.textToTranslate().replace("Happy hunting!", "狩猎愉快！")).getString());
    }

    @Test void scoreboardWordSplitProducesTheSameSentenceAsUnsplitName() {
        Component full = Component.literal("Talk to Gerald").withStyle(ChatFormatting.YELLOW);
        var scoreboard = ScoreboardEntryTemplate.prepare(
                Component.literal("Talk to Geral").withStyle(ChatFormatting.YELLOW),
                Component.literal("d").withStyle(ChatFormatting.YELLOW), true, false, Component.empty());
        var complete = HudSentenceTemplate.prepare(full, Set.of()).document(ComponentTranslationRoute.SCOREBOARD);
        assertEquals(complete, scoreboard.document());
        assertEquals(1, scoreboard.document().units().size());
        var unit = scoreboard.document().units().getFirst();
        assertTrue(unit.sourceText().contains("Talk to Gerald"));
        var translated = scoreboard.renderTranslated(new ComponentTranslationResponse(complete.protocol(),
                Map.of(unit.id(), unit.sourceText().replace("Talk to Gerald", "与Gerald交谈。"))));
        assertEquals("与Gerald交谈。", translated.getString());
        assertColor(translated, "Gerald", ChatFormatting.YELLOW);
    }

    @Test void sharedDictionaryIsOnlyReferenceAndNeverMutatesSourceOrRenderedReply() {
        String source = "<s0>This item can be reforged!</s0>";
        var doc = ComponentTranslationRuntime.prepare(Component.literal(source), ComponentTranslationRoute.CHAT_OUTPUT, "test", "1");
        var plan = SharedHudTranslationSupport.planFor(doc, source,
                Map.of("This", "这", "item", "物品", "Gerald", "杰拉德")::get);
        assertEquals(source, plan.text());
        assertEquals("<s0>This = 示例，Gerald 保留。</s0>", plan.restore("<s0>This = 示例，Gerald 保留。</s0>"));
        assertFalse(plan.text().contains("{taio.term."));
    }

    private static Component validateAndRestore(ChatOutputTranslateManager.PreparedChatTranslation prepared, String reply) {
        var doc = ChatOutputTranslateManager.prepareSharedNpcDocument(prepared);
        var unit = doc.units().getFirst();
        var shared = SharedHudTranslationSupport.document(doc, unit,
                SharedHudTranslationSupport.planFor(doc, unit.sourceText(), s -> null), "hypixel");
        new ComponentTranslationValidator().validate(shared,
                new ComponentTranslationResponse(shared.protocol(), Map.of(unit.id(), reply)));
        return ChatOutputTranslateManager.rebuildTranslatedText(reply, prepared);
    }

    private static String run(String source, String fragment, String translated) {
        var matcher = Pattern.compile("<s(\\d+)>(.*?)</s\\1>", Pattern.DOTALL).matcher(source);
        while (matcher.find()) if (matcher.group(2).contains(fragment))
            return "<s" + matcher.group(1) + ">" + translated + "</s" + matcher.group(1) + ">";
        throw new AssertionError("No run containing " + fragment + " in " + source);
    }

    private static void assertColor(Component result, String fragment, ChatFormatting color) {
        assertTrue(result.visit((style, text) -> text.contains(fragment)
                        ? Optional.of(Objects.equals(Style.EMPTY.withColor(color).getColor(), style.getColor()))
                        : Optional.empty(), Style.EMPTY).orElse(false));
    }
}
