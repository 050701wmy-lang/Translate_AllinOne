package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TooltipSharedSentenceTest {
    @TempDir Path directory;

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private WynncraftPlaceholderDictionary dictionary(String entries) throws Exception {
        Path file = directory.resolve("skyblock_items_zh.json");
        Files.writeString(file, entries);
        return new WynncraftPlaceholderDictionary(file, "test");
    }

    @Test
    void sackNamesDescriptionsAndRarityShareTermsAcrossSkillsAndWrapping() throws Exception {
        var dictionary = dictionary("{}");
        for (var term : Map.of("Mining", "采矿", "Foraging", "采集", "Husbandry", "畜牧").entrySet()) {
            String source = "Holds various items obtained from " + term.getKey()
                    + "! Items you pickup go directly into your sacks.";
            String expected = "存放从" + term.getValue() + "中获得的各种物品！你拾取的物品会直接进入袋子。";
            assertEquals(expected, dictionary.findTranslation(source));
            assertEquals(expected, dictionary.findTranslation(source.replace(" from ", " from\n")
                    .replace(" directly ", " directly\n")));
            assertEquals("初级" + term.getValue() + "袋",
                    dictionary.findTranslation("Beginner " + term.getKey() + " Sack"));
        }
        assertEquals("普通袋", dictionary.findTranslation("COMMON SACK"));
        assertEquals("稀有袋", dictionary.findTranslation("RARE SACK"));
        assertEquals("容量：每种物品640", dictionary.findTranslation("Capacity: 640 of each item"));
        assertEquals("容量：每种物品20,160", dictionary.findTranslation("Capacity: 20,160 of each item"));
        assertEquals("多个袋子的容量可叠加！", dictionary.findTranslation("Multiple sacks sum their capacity!"));
    }

    @Test
    void glossarySlotsFollowUserOverridesAndReloadWithoutReusingOldWording() throws Exception {
        var dictionary = dictionary("{\"Mining\":\"§6矿业\",\"COMMON\":\"§f§l一般\"}");
        assertEquals("初级矿业袋", dictionary.findTranslation("Beginner Mining Sack"));
        assertEquals("存放从矿业中获得的各种物品！", dictionary.findTranslation("Holds various items obtained from Mining!"));
        assertEquals("一般袋", dictionary.findTranslation("COMMON SACK"));
        Files.writeString(directory.resolve("skyblock_items_zh.json"),
                "{\"Mining\":\"采矿\",\"Beginner Mining Sack\":\"自定义矿袋\"}");
        dictionary.load();
        assertEquals("自定义矿袋", dictionary.findTranslation("Beginner Mining Sack"));
        assertEquals("存放从采矿中获得的各种物品！", dictionary.findTranslation("Holds various items obtained from Mining!"));
    }

    @Test
    void unknownTermsFallThroughAndOrdinaryNamesRemainVerbatim() throws Exception {
        var dictionary = dictionary("{\"Owner: {name}\":\"所有者：{name}\",\"Mining\":\"采矿\"}");
        assertNull(dictionary.findTranslation("Beginner Unknown Skill Sack"));
        assertNull(dictionary.findTranslation("Holds various items obtained from Unknown Skill!"));
        assertEquals("所有者：Mining", dictionary.findTranslation("Owner: Mining"));
    }

    @Test
    void wrappedLegacyParagraphUsesFullSentenceLookupAndRetainsSkillColor() throws Exception {
        var dictionary = dictionary("{}");
        var prepared = List.of(
                "§7Holds various items obtained from",
                "§6Mining§7! Items you pickup go directly",
                "§7into your sacks.").stream()
                .map(text -> TooltipTemplateRuntime.prepareTemplate(Component.literal(text), true)).toList();
        var block = new TooltipRoutePlanner.TooltipParagraphBlock(1, 4, 1, 3,
                prepared, TooltipTemplateRuntime.prepareParagraphTemplate(prepared));
        var lookup = TooltipParagraphSupport.lookupAcceptedLocalDictionaryTranslation(block, text -> {
            String translated = dictionary.findTranslation(text);
            return new WynnSharedDictionaryService.LookupResult(translated, "test", WynnSharedDictionaryService.MatchType.PATTERN);
        });
        assertNotNull(lookup);
        assertEquals("存放从采矿中获得的各种物品！你拾取的物品会直接进入袋子。", lookup.translation());
        var rendered = TooltipDictionaryStyleSupport.render(
                prepared.stream().map(TooltipTemplateRuntime::renderOriginalPreparedLine).toList(),
                lookup.translation(), Style.EMPTY.withColor(ChatFormatting.GRAY), dictionary::findTranslation);
        assertEquals(Style.EMPTY.withColor(ChatFormatting.GOLD).getColor(), colorAt(rendered, 3));
    }

    @Test
    void sentenceRenderingKeepsEachSkillsOriginalHighlightAndBodyColor() throws Exception {
        var dictionary = dictionary("{}");
        Style body = Style.EMPTY.withColor(0xAAC0CE);
        for (var entry : Map.of("Mining", ChatFormatting.GOLD, "Foraging", ChatFormatting.GREEN).entrySet()) {
            var lines = List.<Component>of(
                    Component.literal("Holds various items obtained from").setStyle(body),
                    Component.literal(entry.getKey()).withStyle(entry.getValue())
                            .append(Component.literal("! Items you pickup go directly").setStyle(body)),
                    Component.literal("into your sacks.").setStyle(body));
            String source = String.join(" ", lines.stream().map(Component::getString).toList());
            String translated = dictionary.findTranslation(source);
            Component result = TooltipDictionaryStyleSupport.render(lines, translated, body, dictionary::findTranslation);
            assertEquals(translated, result.getString());
            String translatedSkill = dictionary.findTranslation(entry.getKey());
            assertEquals(Style.EMPTY.withColor(entry.getValue()).getColor(), colorAt(result, translated.indexOf(translatedSkill)));
            assertEquals(body.getColor(), colorAt(result, 0));
            assertEquals(body.getColor(), colorAt(result, translated.indexOf("中获得")));
        }
    }

    @Test
    void capacityRenderingPreservesNumberHighlightAndDoesNotGuessAmbiguousAccents() throws Exception {
        var dictionary = dictionary("{}");
        Style body = Style.EMPTY.withColor(ChatFormatting.GRAY);
        Component source = Component.literal("Capacity: ").setStyle(body)
                .append(Component.literal("640 of each item").withStyle(ChatFormatting.YELLOW));
        String translated = dictionary.findTranslation(source.getString());
        Component result = TooltipDictionaryStyleSupport.render(List.of(source), translated, body, dictionary::findTranslation);
        assertEquals("容量：每种物品640", result.getString());
        assertEquals(Style.EMPTY.withColor(ChatFormatting.YELLOW).getColor(), colorAt(result, 3));
        assertEquals(body.getColor(), colorAt(result, 0));
        Component ambiguous = TooltipDictionaryStyleSupport.render(
                List.of(Component.literal("Mining").withStyle(ChatFormatting.GOLD)),
                "采矿与采矿", body, dictionary::findTranslation);
        assertEquals(body.getColor(), colorAt(ambiguous, 0));
        Component numberSubstring = TooltipDictionaryStyleSupport.render(
                List.of(Component.literal("1").withStyle(ChatFormatting.YELLOW)), "192", body, text -> null);
        assertEquals(body.getColor(), colorAt(numberSubstring, 0));
    }

    private static net.minecraft.network.chat.TextColor colorAt(Component component, int position) {
        int[] offset = {0};
        return component.visit((style, text) -> {
            int start = offset[0];
            offset[0] += text.length();
            return position >= start && position < offset[0] ? Optional.ofNullable(style.getColor()) : Optional.empty();
        }, Style.EMPTY).orElse(null);
    }

    @Test
    void beeStatAndFortuneKeepLiveColorsAndSeparateIconFontDespiteDictionaryFormatting() throws Exception {
        var dictionary = dictionary("{}");
        var font = new net.minecraft.network.chat.FontDescription.Resource(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("skyblock", "icons"));
        for (var stat : Map.of("Intelligence", ChatFormatting.AQUA, "Strength", ChatFormatting.RED,
                "Defense", ChatFormatting.GREEN).entrySet()) {
            Style accent = Style.EMPTY.withColor(stat.getValue());
            Component source = Component.literal("+1.65").setStyle(accent)
                    .append(Component.literal("\uE001").setStyle(accent.withFont(font)))
                    .append(Component.literal(" " + stat.getKey()).setStyle(accent));
            String term = dictionary.findTranslation(stat.getKey()).replaceAll("§[0-9a-f]", "");
            Component result = TooltipDictionaryStyleSupport.renderSourceAligned(List.of(source),
                    "§a+1.65 §7" + term, dictionary::findTranslation);
            assertNotNull(result);
            assertEquals("+1.65\uE001 " + term, result.getString());
            assertEquals(accent.getColor(), colorAt(result, 0));
            assertEquals(accent.getColor(), colorAt(result, result.getString().indexOf(term)));
            assertTrue(result.visit((style, text) -> text.contains("\uE001")
                    ? Optional.of(style.getFont().equals(font)) : Optional.empty(), Style.EMPTY).orElse(false));
        }
        for (String term : List.of("Farming Fortune", "Foraging Fortune", "Mining Fortune")) {
            Component source = Component.literal("☘ " + term).withStyle(ChatFormatting.GOLD);
            Component result = TooltipDictionaryStyleSupport.renderSourceAligned(List.of(source),
                    dictionary.findTranslation(term), dictionary::findTranslation);
            assertNotNull(result);
            assertTrue(result.getString().startsWith("☘ "));
            assertEquals(source.getStyle().getColor(), colorAt(result, 2));
        }
    }

    @Test
    void sourceAlignmentRejectsChangedValuesAndKeepsSentenceTranslation() throws Exception {
        var dictionary = dictionary("{}");
        assertNull(TooltipDictionaryStyleSupport.renderSourceAligned(
                List.of(Component.literal("+1.65 Intelligence")), "+1.52智力", dictionary::findTranslation));
        assertNull(TooltipDictionaryStyleSupport.renderSourceAligned(
                List.of(Component.literal("−1.65 Intelligence")), "1.65智力", dictionary::findTranslation));
        Style gold = Style.EMPTY.withColor(ChatFormatting.GOLD);
        Style body = Style.EMPTY.withColor(ChatFormatting.GRAY);
        var lines = List.<Component>of(Component.literal("☘ Farming Fortune").setStyle(gold)
                .append(Component.literal(" increases your").setStyle(body)),
                Component.literal("chance of dropping multiple crops").setStyle(body),
                Component.literal("when farming!").setStyle(body));
        String translated = dictionary.findTranslation("Farming Fortune increases your chance of dropping multiple crops when farming!");
        Component result = TooltipDictionaryStyleSupport.render(lines, translated, body, dictionary::findTranslation);
        assertEquals("☘ " + translated, result.getString());
        assertEquals(gold.getColor(), colorAt(result, 2));
        assertEquals(body.getColor(), colorAt(result, result.getString().indexOf("可提高")));
        var font = new net.minecraft.network.chat.FontDescription.Resource(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("skyblock", "icons"));
        Component separateIcon = Component.literal("\uE001").setStyle(gold.withFont(font))
                .append(Component.literal(" Farming Fortune increases your chance").setStyle(gold));
        Component existingIcon = TooltipDictionaryStyleSupport.render(List.of(separateIcon),
                "\uE001 " + translated, body, dictionary::findTranslation);
        assertEquals("\uE001 " + translated, existingIcon.getString());
        assertEquals(font, existingIcon.visit((style, text) -> text.contains("\uE001")
                ? Optional.of(style.getFont()) : Optional.empty(), Style.EMPTY).orElse(null));
    }
}
