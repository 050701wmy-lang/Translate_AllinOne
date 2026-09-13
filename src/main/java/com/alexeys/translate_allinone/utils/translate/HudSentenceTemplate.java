package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.*;
import com.alexeys.translate_allinone.utils.text.StylePreserver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.Map;
import java.util.Set;

/** Component boundaries describe rendering, not word or sentence boundaries. */
final class HudSentenceTemplate {
    private final ComponentDynamicTemplate values;
    private final String source;
    private final Map<Integer, Style> styles;

    private HudSentenceTemplate(Component original, Set<String> privateTokens) {
        var extraction = ChatOutputFormatSupport.extract(original);
        source = extraction.markedText;
        styles = extraction.styleMap;
        values = ComponentDynamicTemplate.prepare(Component.literal(source), privateTokens);
    }

    static HudSentenceTemplate prepare(Component original, Set<String> privateTokens) {
        return new HudSentenceTemplate(original, privateTokens);
    }

    Component templateComponent() { return values.templateComponent(); }
    Set<String> privatePlaceholders() { return values.privatePlaceholders(); }

    ComponentTranslationDocument document(ComponentTranslationRoute route) {
        return ComponentTranslationRuntime.prepare(templateComponent(),
                ComponentTranslationPolicy.forRoute(route).withContext("hud-sentence")
                        .withPrivateTokens(privatePlaceholders())
                        .withSemanticSetting("styled_sentence", "v1"));
    }

    Component restore(Component translated) {
        String tagged = values.restore(translated).getString();
        return StylePreserver.reapplyStylesFromTags(
                ChatOutputFormatSupport.restoreSentence(source, tagged), styles, false);
    }
}
