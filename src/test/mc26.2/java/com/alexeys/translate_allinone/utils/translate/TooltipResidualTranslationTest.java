package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import com.alexeys.translate_allinone.utils.cache.CacheStats;
import static org.junit.jupiter.api.Assertions.*;

class TooltipResidualTranslationTest {
    @TempDir Path directory;

    @Test
    void bundledAdditionsFillExistingDictionaryAndPreserveUserOverrides() throws Exception {
        Path selected = directory.resolve("skyblock_items_zh.json");
        Files.writeString(selected, "{\"COMMON\":\"自定义普通\"}");
        var dictionary = new WynncraftPlaceholderDictionary(selected, "test");
        dictionary.load();
        assertEquals("自定义普通", dictionary.lookupTranslation("COMMON").translation());
        assertEquals("金币", dictionary.lookupTranslation("Coins").translation());
        assertEquals("拾取", dictionary.lookupTranslation("pickup").translation());
        assertEquals("{\"COMMON\":\"自定义普通\"}", Files.readString(selected));
        Path custom = directory.resolve("custom.json");
        Files.writeString(custom, "{\"COMMON\":\"普通\"}");
        var customDictionary = new WynncraftPlaceholderDictionary(custom, "test");
        assertNull(customDictionary.lookupTranslation("Coins"));
    }

    @Test
    void oldMixedTranslationsAreRepairedWithoutChangingMarkersOrUnknownNames() {
        var glossary = Map.of("Coins", "金币", "pickup", "拾取", "COMMON", "普通");
        assertEquals("<s0>{d1} -金币</s0>", SharedHudTranslationSupport.repairTooltipResidue(
                "192.0 Coins", "<s0>{d1} -Coins</s0>", glossary::get));
        assertEquals("你拾取的物品直接进入袋子。", SharedHudTranslationSupport.repairTooltipResidue(
                "Items you pickup go directly into your sacks.", "你pickup的物品直接进入袋子。", glossary::get));
        assertEquals("<s0>普通</s0>", SharedHudTranslationSupport.repairTooltipResidue(
                "COMMON", "<s0>COMMON</s0>", glossary::get));
        assertEquals("Roddy NPC pickup", SharedHudTranslationSupport.repairTooltipResidue(
                "Talk to Roddy NPC", "Roddy NPC pickup", glossary::get));
    }

    @Test
    void unfinishedOtherItemsDoNotKeepCurrentTooltipPending() {
        var done = new TooltipTranslationSupport.TooltipProcessingResult(List.of(Component.literal("普通")), 1, false, false);
        var pending = new TooltipTranslationSupport.TooltipProcessingResult(List.of(Component.literal("COMMON")), 1, true, false);
        var historicalStats = new CacheStats(3926, 3939);
        assertFalse(TooltipInternalLineSupport.shouldShowStatusLine(done, historicalStats));
        assertTrue(TooltipInternalLineSupport.shouldShowStatusLine(pending, historicalStats));
    }

    @Test
    void screenshotLabelsAndWrappedDescriptionUseBundledTerms() throws Exception {
        Path selected = directory.resolve("skyblock_items_zh.json");
        Files.writeString(selected, "{}");
        var dictionary = new WynncraftPlaceholderDictionary(selected, "test");
        for (var entry : Map.of("Seeds", "种子", "Bits", "比特", "Gems", "宝石",
                "COMMON FARMING TOOL", "普通农耕工具", "TURBO MINIONs!!!", "极速仆从！！！",
                "QUAD TAXEs!!!", "四倍税收！！！", "DOUBLE MOBs HP!!!", "怪物生命值翻倍！！！").entrySet()) {
            String result = dictionary.findTranslation(entry.getKey());
            assertNotNull(result, entry.getKey());
            assertEquals(entry.getValue(), result.replaceAll("§[0-9a-fk-or]", ""));
        }
        assertEquals("☘ 农耕时运可提高收获作物时获得额外掉落的概率！", dictionary.findTranslation(
                "☘ Farming Fortune increases your\nchance of dropping multiple crops\nwhen farming!"));
        assertEquals("-种子", SharedHudTranslationSupport.repairTooltipResidue(
                "Seeds", "-Seeds", dictionary::findTranslation));
        assertEquals("为你的宠物提供以下各项属性+2.6：",
                dictionary.findTranslation("Grants +2.6 of each to your pet:"));
    }
}
