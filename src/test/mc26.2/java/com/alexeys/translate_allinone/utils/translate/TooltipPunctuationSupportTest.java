package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TooltipPunctuationSupportTest {
    @Test void removesDashBeforeExplicitPositiveStatWithoutChangingNegativeValues() {
        assertEquals("裂隙时间: +110s", TooltipPunctuationSupport.clean("Rift Time: +110s", "裂隙时间: -+110s"));
        assertEquals("<s0>裂隙时间: </s0><s1>+{d1}s</s1>", TooltipPunctuationSupport.clean(
                "Rift Time: +110s", "<s0>裂隙时间: -</s0><s1>+{d1}s</s1>"));
        assertEquals("裂隙时间: -110s", TooltipPunctuationSupport.clean("Rift Time: -110s", "裂隙时间: -110s"));
    }
    @Test
    void removesSeparatedDashClustersAndSpaceSeparatedEnglishBullets() {
        assertEquals("制作Stews!", TooltipPunctuationSupport.clean("craft it into Stews!", "制作- -Stews!"));
        assertEquals("notes!", TooltipPunctuationSupport.clean("read my notes!", "- -notes!"));
        assertEquals("craftStews", TooltipPunctuationSupport.clean("craft Stews", "craft- -Stews"));
        assertEquals("I notes", TooltipPunctuationSupport.clean("read my notes", "I -notes"));
        assertEquals("<s0>制作</s0><s1>Stews</s1>", TooltipPunctuationSupport.clean("craft Stews",
                "<s0>制作- </s0><s1>-Stews</s1>"));
        assertEquals("-5", TooltipPunctuationSupport.clean("5", "-5"));
        assertEquals("right-click", TooltipPunctuationSupport.clean("click", "right-click"));
    }
    @Test
    void priceUnitDashIsNotMistakenForANegativeNumber() {
        assertEquals("<s0>{d1} 金币 </s0><s1>(每个 {d2})</s1>", TooltipPunctuationSupport.clean(
                "{d1} Coins (each {d2})", "<s0>{d1} -金币 </s0><s1>(每个 {d2})</s1>"));
        assertEquals("192.0 Coins", TooltipPunctuationSupport.clean("192.0 Coins", "192.0 -Coins"));
        assertEquals("3-D", TooltipPunctuationSupport.clean("Three dimensional", "3-D"));
    }
    @Test
    void removesObservedSackDashesWithoutChangingAnchors() {
        String original = "Holds various items obtained from Foraging! Items you pickup go directly into your sacks.";
        assertEquals("存放从{accent0.begin}采集{accent0.end}中获得的各种物品！你拾取的物品会直接进入你的袋子。",
                TooltipPunctuationSupport.clean(original,
                        "-存放从{accent0.begin}采集{accent0.end}中获得的各种物品！-你-拾取的物品会直接进入你的袋子。"));
        assertEquals("<s0>多个袋子容量叠加！</s0>", TooltipPunctuationSupport.clean(
                "Multiple sacks sum their capacity!", "<s0>-多个袋子容量叠加！</s0>"));
        assertEquals("§7自动熔炼", TooltipPunctuationSupport.clean("Automatically smelts", "§7-自动熔炼"));
        assertEquals("<s0></s0><s1>自动熔炼</s1>", TooltipPunctuationSupport.clean(
                "Automatically smelts", "<s0>-</s0><s1>自动熔炼</s1>"));
    }

    @Test
    void preservesRealPunctuationNumbersAndProtectedMarkers() {
        for (String value : new String[]{"减少 -5 点", "减少 -{d1} 点", "减少 -<s0>{value0}</s0> 点",
                "1-5级", "右键 right-click", "{some-token} 中文", "-- 分隔线"}) {
            assertEquals(value, TooltipPunctuationSupport.clean("Plain source", value), value);
        }
        for (String original : new String[]{"- Item", "Damage -5", "Right-click", "• Item", "A—B"}) {
            assertEquals("-物品", TooltipPunctuationSupport.clean(original, "-物品"), original);
        }
        assertEquals("多个袋子容量叠加！", TooltipPunctuationSupport.clean(
                "Multiple sacks sum their capacity!", "多个袋子容量叠加！"));
    }

    @Test
    void cachedLineRenderingAlsoCleansOldResults() {
        var source = net.minecraft.network.chat.Component.literal("Multiple sacks sum their capacity!");
        var prepared = TooltipTemplateRuntime.prepareTemplate(source, false);
        var result = TooltipTemplateRuntime.renderComponentTemplateTranslation(prepared, "<s0>-多个袋子容量叠加！</s0>");
        assertNotNull(result);
        assertEquals("多个袋子容量叠加！", result.getString());
    }
}
