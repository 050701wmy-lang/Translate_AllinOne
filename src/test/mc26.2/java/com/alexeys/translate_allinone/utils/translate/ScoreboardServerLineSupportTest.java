package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.ScoreboardConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreboardServerLineSupportTest {
    @Test
    void preservesDateAndChangingInstanceIdsAcrossStyledSegments() {
        for (String id : new String[]{"m2CU", "m25BP", "mini123A", "mega12B"}) {
            Component source = Component.literal("09/06/26 ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
            assertTrue(ScoreboardServerLineSupport.isServerLine(source));
            var prepared = new ScoreboardEntryTemplate.Prepared(source, null, null);
            assertEquals(source, ScoreboardComponentTranslationSupport.resolve(prepared, new ScoreboardConfig()));
            assertTrue(ScoreboardComponentTranslationSupport.refreshIdentities(prepared, "Chinese").isEmpty());
            var result = ExternalScoreboardTranslationSupport.translate(source,
                    ExternalScoreboardTranslationSupport.Source.SKYHANNI, true);
            assertSame(source, result.component());
            assertFalse(result.pending());
        }
        assertTrue(ScoreboardServerLineSupport.isServerLine(Component.literal("§709/06/26 §8m2CU§r")));
    }

    @Test
    void leavesGameDatesLocationsAndObjectivesEligible() {
        for (String text : new String[]{"Late Autumn 10th", "12:30am", "Village",
                "Travel to the Spider's Den", "Purse: 26,482", "09/06/26 m2CU quest"}) {
            assertFalse(ScoreboardServerLineSupport.isServerLine(Component.literal(text)), text);
        }
    }
}
