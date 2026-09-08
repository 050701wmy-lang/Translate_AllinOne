package com.alexeys.translate_allinone.utils.componentjson;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PrivateTokenCountingTest {
    private static final String SLOT = "{taio.private.a}";

    @Test
    void oneHudIconMatchesTwoRulesButCountsOnce() {
        var policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.ACTION_BAR).withPrivateTokens(Set.of(SLOT));
        assertEquals(Map.of(SLOT, 1, "§7", 1, "§b", 1), policy.protectedTokenMultiset("§7" + SLOT + " §bRuins"));
        assertEquals(Map.of(SLOT, 2), policy.protectedTokenMultiset(SLOT + " " + SLOT));
    }

    @Test
    void realActionBarResponsePassesValidationButMissingIconStillFails() {
        String source = "§7" + SLOT + " §bRuins";
        var policy = ComponentTranslationPolicy.forRoute(ComponentTranslationRoute.ACTION_BAR).withPrivateTokens(Set.of(SLOT));
        JsonObject json = new JsonObject();
        json.addProperty("text", source);
        var document = new ComponentTranslationDocument(ComponentTranslationDocument.PROTOCOL, policy.version(),
                policy.route(), json, List.of(new ComponentTextUnit("u0", "/text", source,
                policy.protectedTokenMultiset(source), "action-bar")), policy.semanticSettings());
        var validator = new ComponentTranslationValidator();
        assertDoesNotThrow(() -> validator.validate(document, new ComponentTranslationResponse(document.protocol(),
                Map.of("u0", "§7" + SLOT + " §b遗迹"))));
        assertThrows(ComponentJsonException.class, () -> validator.validate(document,
                new ComponentTranslationResponse(document.protocol(), Map.of("u0", "§7 §b遗迹"))));
    }
}
