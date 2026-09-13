package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded local observations, not a claim that every Latin name is untranslated. */
public final class TooltipBoundaryAudit {
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]{2,}");
    private static final Set<String> SEEN = new HashSet<>();
    private static final int LIMIT = 128;
    private TooltipBoundaryAudit() {}

    public static synchronized void observe(List<Component> source, List<Component> visible, boolean itemOwner) {
        if (SEEN.size() >= LIMIT || visible == null || !TranslationFeatureGate.isEnabled()) return;
        var config = Translate_AllinOne.getConfig().itemTranslate;
        if (config == null || !config.enabled) return;
        try {
            for (int i = 0; i < visible.size() && SEEN.size() < LIMIT; i++) {
                Component line = visible.get(i);
                if (line == null || !LATIN.matcher(line.getString()).find()) continue;
                String output = preview(line.getString());
                String types = types(line);
                String key = itemOwner + "|" + types + "|" + output;
                if (!SEEN.add(key)) continue;
                // Paragraph wrapping can change indices. Log the input as a separate snapshot,
                // never pretend that input row i necessarily corresponds to output row i.
                Translate_AllinOne.LOGGER.info("[translation-audit] tooltip-final itemOwner={} outputRow={} types={} text={} inputSnapshot={}",
                        itemOwner, i, types, output, source == null ? "null" : source.stream()
                                .limit(48).map(value -> value == null ? "null" : types(value) + ":" + preview(value.getString()))
                                .toList());
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never interrupt rendering, including foreign Component implementations.
        }
    }

    private static String types(Component line) {
        Set<String> types = new TreeSet<>();
        types.add(line.getContents().getClass().getName());
        line.getSiblings().stream().limit(16).forEach(child -> types.add(child.getContents().getClass().getName()));
        return types.toString();
    }

    private static String preview(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().limit(240).forEach(cp -> {
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT)
                result.append(String.format("\\u%04X", cp));
            else result.appendCodePoint(cp);
        });
        return result.toString();
    }
}
