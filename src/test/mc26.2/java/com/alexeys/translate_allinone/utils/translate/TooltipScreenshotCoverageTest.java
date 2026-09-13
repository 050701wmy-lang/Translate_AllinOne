package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TooltipScreenshotCoverageTest {
    @Test void cooldownUsesProductionTooltipPlanAndDictionaryService() throws Exception {
        var configField = com.alexeys.translate_allinone.registration.ConfigManager.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object oldConfig = configField.get(null);
        var registeredField = com.alexeys.translate_allinone.registration.ConfigManager.class.getDeclaredField("registered");
        registeredField.setAccessible(true);
        boolean oldRegistered = registeredField.getBoolean(null);
        var service = WynnSharedDictionaryService.getInstance();
        var dictionaryField = WynnSharedDictionaryService.class.getDeclaredField("itemSkillDictionary");
        dictionaryField.setAccessible(true);
        Object oldDictionary = dictionaryField.get(service);
        try {
            var config = new com.alexeys.translate_allinone.utils.config.ModConfig();
            config.itemTranslate.enabled = true;
            config.itemTranslate.enabled_translate_item_custom_name = true;
            config.itemTranslate.enabled_translate_item_lore = true;
            config.dictionary.enabled = true;
            config.dictionary.item_skill_enabled = true;
            config.dictionary.item_skill_dictionary_files = new ArrayList<>(List.of("skyblock_items_zh.json", "wynncraft_items_zh.json"));
            configField.set(null, config);
            registeredField.setBoolean(null, true);
            dictionaryField.set(service, dictionary());
            for (String value : List.of("Cooldown: 5s", "Cooldown:5s", "§8Cooldown: §a5s")) {
                var result = TooltipTranslationSupport.processTooltipLines(
                        List.of(Component.literal(""), Component.literal(value).withStyle(ChatFormatting.DARK_GRAY)),
                        config.itemTranslate, false, false, "cooldown-regression");
                assertEquals("冷却时间：5秒", result.translatedLines().getLast().getString().replace(" ", ""), value
                        + " lookup=" + service.lookupItemLine("Cooldown: 5s")
                        + " accepted=" + TooltipTemplateRuntime.describeLocalDictionaryLookup(Component.literal(value))
                        + " plan=" + TooltipRoutePlanner.planTooltip(List.of(Component.literal(value)), config.itemTranslate, false).segments());
            }
        } finally {
            dictionaryField.set(service, oldDictionary);
            configField.set(null, oldConfig);
            registeredField.setBoolean(null, oldRegistered);
        }
    }
    @Test void cooldownReachesDictionaryThroughTheActualTooltipFilter() throws Exception {
        var dictionary = dictionary();
        var config = new ItemTranslateConfig();
        config.enabled_translate_item_lore = true;
        for (String text : List.of("Cooldown:5s", "Cooldown: 5s", "§8Cooldown: §a5s")) {
            Component source = Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
            var decision = TooltipTextMatcherSupport.evaluateTooltipLine(source, false, config);
            assertTrue(decision.shouldTranslate(), text + ": " + decision.reason());
            String translation = dictionary.findTranslation(TooltipTemplateRuntime.normalizeLocalDictionaryLookupSourceText(text));
            assertNotNull(translation, text);
            Component rendered = TooltipTemplateRuntime.renderLocalDictionaryTranslation(
                    TooltipTemplateRuntime.prepareTemplate(source, true), translation);
            assertEquals("冷却时间：5秒", rendered.getString().replace(" ", ""));
        }
        assertFalse(TooltipTextMatcherSupport.evaluateTooltipLine(Component.literal("minecraft:stone"), false, config).shouldTranslate());
    }
    @Test void coloredLegacyStatLabelsAndCompactPricesRepairTheirOldEnglishCache() throws Exception {
        var dictionary = dictionary();
        for (var sample : Map.of(
                "§8Cooldown: §a5s", "冷却时间",
                "§7Hearts: §c+1", "心数",
                "§7Gemstones: §8[\uE001] [\uE002]", "宝石槽",
                "§eNPC Sell Price:§338,000Coins", "NPC出售价格",
                "§cRequires §aHard Stone Collection V§c.", "硬石收集").entrySet()) {
            String original = sample.getKey();
            String cached = "<s0>-" + original.replaceAll("§[0-9a-fk-or]", "") + "</s0>";
            String repaired = SharedHudTranslationSupport.repairTooltipResidue(original, cached, dictionary::findTranslation);
            repaired = TooltipPunctuationSupport.clean(original, repaired);
            assertTrue(repaired.contains(sample.getValue()), repaired);
            assertFalse(repaired.contains("-"), repaired);
            if (original.contains("\uE001")) assertTrue(repaired.contains("[\uE001] [\uE002]"));
            if (original.contains("38,000")) assertTrue(repaired.contains("38,000金币"), repaired);
        }
        assertEquals("约格护腿", dictionary.findTranslation("Yog Leggings"));
        assertEquals("裂隙时间：+110秒", dictionary.findTranslation("Rift Time: +110s"));
        assertEquals("需要硬石收集 V。", dictionary.findTranslation("Requires Hard Stone Collection V."));
        assertEquals("Alex123Coins", SharedHudTranslationSupport.repairTooltipResidue(
                "Alex123Coins", "Alex123Coins", dictionary::findTranslation));
    }

    @Test void localDictionaryRenderingAlsoCleansInjectedStatDash() {
        var prepared = TooltipTemplateRuntime.prepareTemplate(Component.literal("Hearts: +1"), true);
        assertEquals("心数：+1", TooltipTemplateRuntime.renderLocalDictionaryTranslation(prepared, "-心数：+1").getString());
    }
    @TempDir Path directory;
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private WynncraftPlaceholderDictionary dictionary() throws Exception {
        Path file = directory.resolve("skyblock_items_zh.json");
        Files.writeString(file, "{}");
        return new WynncraftPlaceholderDictionary(file, "test");
    }
    private List<TooltipRoutePlanner.TooltipLineCandidate> candidates(String... lines) {
        ItemTranslateConfig config = new ItemTranslateConfig();
        config.enabled = true;
        config.enabled_translate_item_lore = true;
        var result = new ArrayList<TooltipRoutePlanner.TooltipLineCandidate>();
        for (String line : lines) {
            Component component = Component.literal(line);
            result.add(new TooltipRoutePlanner.TooltipLineCandidate(result.size(), component, false,
                    TooltipTextMatcherSupport.evaluateTooltipLine(component, false, config)));
        }
        return result;
    }
    @Test void wrappedDictionarySentencesBeatShortLineHeuristicsButRespectSeparators() throws Exception {
        var dictionary = dictionary();
        for (var lines : List.of(
                candidates("\uE001 Farming Fortune increases your", "chance of dropping multiple crops", "when farming!"),
                candidates("☘ Farming Fortune increases your", "chance of dropping multiple crops", "when farming!"),
                candidates("Grants a +2% chance to double", "shards from a Reptile Fusion."))) {
            assertEquals(lines.size(), TooltipRoutePlanner.findDictionaryParagraphEnd(lines, 0,
                    text -> dictionary.findTranslation(text) != null));
        }
        assertEquals(1, TooltipRoutePlanner.findDictionaryParagraphEnd(candidates(
                "Grants a +2% chance to double", "", "shards from a Reptile Fusion."), 0,
                text -> dictionary.findTranslation(text) != null));
        assertEquals(1, TooltipRoutePlanner.findDictionaryParagraphEnd(candidates(
                "Unknown item description", "COMMON SWORD"), 0, text -> dictionary.findTranslation(text) != null));
    }
    @Test void rarityCooldownEmptySlotsAndShardDescriptionsTranslateWithoutChangingIds() throws Exception {
        var dictionary = dictionary();
        assertEquals("普通剑", dictionary.findTranslation("COMMON SWORD"));
        assertEquals("稀有战斗碎片 (ID R45)", dictionary.findTranslation("RARE COMBAT SHARD (ID R45)"));
        assertEquals("无", dictionary.findTranslation("NONE"));
        assertEquals("冷却时间：5秒", dictionary.findTranslation("Cooldown: 5s"));
        assertEquals("使爬行动物融合获得的碎片翻倍的概率提高2%。",
                dictionary.findTranslation("Grants a +2% chance to double shards from a Reptile Fusion."));
        assertEquals("这件专属节日装饰品可放置在你的岛屿上或用于交易。", dictionary.findTranslation(
                "This exclusive holiday decoration can be placed on your island or traded."));
        assertEquals("这专属的假日装饰品", SharedHudTranslationSupport.repairTooltipResidue(
                "This exclusive Holiday cosmetic", "This专属的假日装饰品", dictionary::findTranslation));
    }
    @Test void priceRowsRetainTheirNumberColorWithPunctuationAndThousandsSeparators() throws Exception {
        var dictionary = dictionary();
        for (String label : List.of("Lowest BIN Price:", "3 Day Avg. Price:", "Est. Item Value:",
                "Bazaar Buy Price:", "Bazaar Sell Price:")) {
            Component source = Component.literal(label).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("132,388.7 Coins").withStyle(ChatFormatting.DARK_AQUA));
            String translated = dictionary.findTranslation(source.getString());
            assertNotNull(translated, label);
            Component rendered = TooltipDictionaryStyleSupport.renderSourceAligned(List.of(source), translated,
                    dictionary::findTranslation);
            assertNotNull(rendered, label);
            assertTrue(rendered.getString().contains("132,388.7"));
            assertFalse(rendered.getString().contains("Coins"));
            assertEquals(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA).getColor(),
                    rendered.visit((style, text) -> text.contains("132,388.7")
                            ? Optional.of(style.getColor()) : Optional.empty(), Style.EMPTY).orElse(null));
        }
    }

    @Test void matchedLocalParagraphActuallyRendersWithIconsAndLiteralNumbers() throws Exception {
        var dictionary = dictionary();
        for (var texts : List.of(
                List.of("§6\uE051 Farming Fortune §7increases your", "§7chance of dropping multiple crops", "§7when farming!"),
                List.of("☘ Farming Fortune increases your", "chance of dropping multiple crops", "when farming!"),
                List.of("Grants a +2% chance to double", "shards from a Reptile Fusion."))) {
            var prepared = texts.stream().map(text -> TooltipTemplateRuntime.prepareTemplate(
                    Component.literal(text).withStyle(ChatFormatting.GRAY), true)).toList();
            var block = new TooltipRoutePlanner.TooltipParagraphBlock(1, texts.size() + 1, 1, texts.size(),
                    prepared, TooltipTemplateRuntime.prepareParagraphTemplate(prepared));
            String translated = dictionary.findTranslation(TooltipParagraphSupport.buildParagraphLocalDictionaryLookupSource(block));
            assertNotNull(translated);
            var rendered = TooltipParagraphSupport.renderTranslatedParagraphBlock(block, translated,
                    new ItemTranslateConfig(), false, "test", true);
            assertNotNull(rendered, "Dictionary hit must survive the real paragraph renderer: " + texts);
            String expected = texts.getFirst().contains("\uE051") ? "\uE051 " + translated : translated;
            assertEquals(expected, String.join("", rendered.stream().map(line -> line.translatedLine().getString()).toList()));
            // AI responses must still carry the original protected values and glyph anchors.
            if (!block.paragraphTemplate().templateValues().isEmpty() || !block.paragraphTemplate().glyphValues().isEmpty()) {
                assertFalse(TooltipParagraphSupport.renderComponentParagraphTranslationResult(block, translated,
                        new ItemTranslateConfig()).accepted());
            }
        }
    }

    @Test void observedPartialCachesAreRejectedByTheActualLineRenderer() {
        for (var pair : Map.of("This item can be reforged!", "<s0>这 item can be reforged!</s0>",
                "NPC Sell Price:", "<s0>NPC Sell 价格:</s0>",
                "Cooldown: 5s", "<s0>-Cooldown: {d1}s</s0>").entrySet()) {
            var prepared = TooltipTemplateRuntime.prepareTemplate(Component.literal(pair.getKey()), true);
            assertNull(TooltipTemplateRuntime.renderComponentTemplateTranslation(prepared, pair.getValue()), pair.getKey());
            assertTrue(SharedHudTranslationSupport.hasIncompleteTooltipProse(pair.getKey(), pair.getValue(), "Chinese"));
        }
        assertFalse(SharedHudTranslationSupport.hasIncompleteTooltipProse("Talk to Kat (ID R45)", "与Kat交谈 (ID R45)", "Chinese"));
        assertFalse(SharedHudTranslationSupport.hasIncompleteTooltipProse("Cooldown: 5s", "Cooldown: 5s", "English"));
        var source = TooltipTemplateRuntime.prepareTemplate(Component.literal("This item can be reforged!"), true);
        assertEquals("此物品可以重铸！", TooltipTemplateRuntime.renderComponentTemplateTranslation(source,
                "<s0>此物品可以重铸！</s0>").getString());
    }

    @Test void liveItemNamesAndLoreGetCompleteDictionaryTranslations() throws Exception {
        var dictionary = dictionary();
        for (var pair : Map.of("§8This item can be reforged!", "此物品可以重铸！",
                "§fRogue Sword", "流氓剑", "§fRookie Farming Axe", "新手农耕斧",
                "§fRookie Hoe", "新手锄", "§8Cooldown: §a5s", "冷却时间：5秒",
                "NPC Sell    Price:", "NPC出售价格：").entrySet()) {
            assertEquals(pair.getValue(), dictionary.findTranslation(
                    TooltipTemplateRuntime.normalizeLocalDictionaryLookupSourceText(pair.getKey())));
        }
    }

    @Test void realFishyTreatAndWitchIngredientLoreRenderAsCompleteParagraphs() throws Exception {
        var dictionary = dictionary();
        for (var texts : List.of(
                List.of("§7Bring to §dAlcina§7 in the §aHub§7 to craft it", "§7into §dStews§7!"),
                List.of("§7Can be earned during the §9Year of", "§9the Seal §7and can be spent at §bLukas",
                        "§bthe Aquarist's Shop§7!"))) {
            var prepared = texts.stream().map(text -> TooltipTemplateRuntime.prepareTemplate(Component.literal(text), true)).toList();
            var block = new TooltipRoutePlanner.TooltipParagraphBlock(1, texts.size() + 1, 1, texts.size(),
                    prepared, TooltipTemplateRuntime.prepareParagraphTemplate(prepared));
            String translated = dictionary.findTranslation(TooltipParagraphSupport.buildParagraphLocalDictionaryLookupSource(block));
            assertNotNull(translated);
            var rendered = TooltipParagraphSupport.renderTranslatedParagraphBlock(block, translated,
                    new ItemTranslateConfig(), false, "test", true);
            assertNotNull(rendered);
            assertEquals(translated, String.join("", rendered.stream().map(line -> line.translatedLine().getString()).toList()));
            assertFalse(translated.matches(".*[A-Za-z-].*"));
        }
        var source = com.alexeys.translate_allinone.utils.text.StylePreserver.fromLegacyText("§8Cooldown: §a5s");
        var rendered = TooltipDictionaryStyleSupport.renderSourceAligned(List.of(source),
                dictionary.findTranslation("Cooldown: 5s"), dictionary::findTranslation);
        assertNotNull(rendered);
        assertEquals("冷却时间： 5秒", rendered.getString());
        assertNull(dictionary.findTranslation("notesXYZ"));
    }
}
