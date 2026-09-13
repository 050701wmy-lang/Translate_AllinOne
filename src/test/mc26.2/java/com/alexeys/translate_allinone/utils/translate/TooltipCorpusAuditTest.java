package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline audit: set TAIO_AUDIT_ITEMS to a Skyblocker items.min.json. No provider calls. */
class TooltipCorpusAuditTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void classifyRepositoryTextThroughProductionEligibilityRules() throws Exception {
        String path = System.getenv("TAIO_AUDIT_ITEMS");
        JsonArray items = path == null ? JsonParser.parseString("""
                [{"components":{"minecraft:custom_name":{"text":"Rogue Sword"},
                "minecraft:lore":[{"text":"§8Cooldown: §a5s"},{"text":""}]}}]
                """).getAsJsonArray() : JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonArray();
        var config = new ItemTranslateConfig();
        config.enabled_translate_item_custom_name = true;
        config.enabled_translate_item_lore = true;
        var counts = new TreeMap<String, Integer>();
        var missed = new TreeSet<String>();
        int lines = 0;
        for (var item : items) {
            var components = item.getAsJsonObject().getAsJsonObject("components");
            List<String> text = new ArrayList<>();
            if (components.has("minecraft:custom_name"))
                text.add(components.getAsJsonObject("minecraft:custom_name").get("text").getAsString());
            if (components.has("minecraft:lore")) for (var line : components.getAsJsonArray("minecraft:lore"))
                text.add(line.getAsJsonObject().get("text").getAsString());
            for (int i = 0; i < text.size(); i++) {
                String source = text.get(i);
                var decision = TooltipTextMatcherSupport.evaluateTooltipLine(Component.literal(source), i == 0, config, true);
                counts.merge(decision.kind().name(), 1, Integer::sum);
                lines++;
                if (!decision.shouldTranslate() && source.replaceAll("§[0-9a-fk-or]", "").matches("(?s).*[A-Za-z]{2}.*"))
                    missed.add(decision.kind() + " | " + source);
            }
        }
        assertTrue(lines > 0);
        Path report = Path.of("build/reports/translation-audit/item-eligibility.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "Items: " + items.size() + "\nLines: " + lines + "\nDecisions: " + counts
                + "\nEnglish-bearing rejected lines (unique): " + missed.size() + "\n" + String.join("\n", missed)
                + "\nScope: raw item repository eligibility only; does not simulate other mods, network replies or final game rendering.\n");
        assertTrue(missed.isEmpty(), "Raw English item text bypassed translation; inspect " + report);
    }
}
