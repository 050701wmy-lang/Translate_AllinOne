package com.alexeys.translate_allinone.utils.translate;

import java.util.Set;
import net.minecraft.network.chat.Component;

final class ExternalScoreboardComponentTemplate {
    private ExternalScoreboardComponentTemplate() {
    }

    static Prepared prepare(Component source, Set<String> privateTokens) {
        return new Prepared(HudSentenceTemplate.prepare(source, HudGlyphProtection.tokens(source, privateTokens)));
    }

    record Prepared(HudSentenceTemplate dynamicTemplate) {
        Prepared {
            dynamicTemplate = dynamicTemplate == null
                    ? HudSentenceTemplate.prepare(Component.empty(), Set.of())
                    : dynamicTemplate;
        }

        Component templateComponent() {
            return dynamicTemplate.templateComponent();
        }

        Set<String> privatePlaceholders() {
            return dynamicTemplate.privatePlaceholders();
        }

        Component restore(Component translatedTemplate) {
            return dynamicTemplate.restore(translatedTemplate);
        }
    }
}
