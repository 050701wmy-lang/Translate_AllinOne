package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.text.StylePreserver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.util.function.Function;

/** Reattach unambiguous source accents after a plain dictionary sentence is translated. */
final class TooltipDictionaryStyleSupport {
    private static final java.util.regex.Pattern WORDS = java.util.regex.Pattern.compile("[A-Za-z]+(?:[ '’-][A-Za-z]+)*");
    private static final java.util.regex.Pattern FOLLOWING_TERM = java.util.regex.Pattern.compile("^\\s*([A-Za-z]+(?: +[A-Za-z]+)*)");
    private TooltipDictionaryStyleSupport() {}

    /** Prefer the original runs when glossary substitution accounts for the complete translation.
     * Dictionary colors describe one context only; the live tooltip also owns icon fonts and accents.
     */
    static Component renderSourceAligned(List<Component> sources, String translation,
                                         Function<String, String> lookup) {
        var result = Component.empty();
        for (Component source : sources) {
            source.visit((style, text) -> {
                if (!text.isBlank() && text.codePoints().noneMatch(TooltipDictionaryStyleSupport::isIcon)) {
                    String direct = lookup.apply(text.strip());
                    if (direct != null && !direct.isBlank()) {
                        int start = text.indexOf(text.strip());
                        result.append(Component.literal(text.substring(0, start)
                                + direct.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                                + text.substring(start + text.strip().length()))
                                .setStyle(StylePreserver.sanitizeStyleForComparison(style, true)));
                        return Optional.empty();
                    }
                }
                var words = WORDS.matcher(text);
                int offset = 0;
                while (words.find()) {
                    result.append(Component.literal(text.substring(offset, words.start())).setStyle(style));
                    var plan = SharedHudTranslationSupport.plan(words.group(), lookup);
                    String translated = plan.restore(plan.exact() == null ? plan.text() : plan.exact());
                    result.append(Component.literal(translated)
                            .setStyle(StylePreserver.sanitizeStyleForComparison(style, true)));
                    offset = words.end();
                }
                result.append(Component.literal(text.substring(offset)).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
        }
        return comparable(result.getString()).equals(comparable(translation)) ? result : null;
    }

    private static String comparable(String text) {
        StringBuilder result = new StringBuilder();
        text.replaceAll("§[0-9a-fk-orA-FK-OR]", "").codePoints().forEach(cp -> {
            // Keep signs, decimal separators and punctuation: only layout/icon differences are allowed.
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
                    && !isIcon(cp)) result.appendCodePoint(cp);
        });
        return result.toString();
    }

    static Component render(List<Component> sources, String translation, Style body,
                            Function<String, String> lookup) {
        Map<Integer, List<Component>> icons = new TreeMap<>();
        Map<Integer, Style> existingIconStyles = new HashMap<>();
        Style[] styles = new Style[translation.length()];
        Arrays.fill(styles, body);
        // An icon immediately before a known term belongs to that term, even in reordered prose.
        var sourceText = Component.empty();
        for (Component source : sources) sourceText.append(source).append(" ");
        String plain = sourceText.getString();
        int[] sourceOffset = {0};
        sourceText.visit((style, text) -> {
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                int length = Character.charCount(cp);
                if (isIcon(cp)) {
                    String glyph = new String(Character.toChars(cp));
                    boolean anchored = false;
                    int existing = translation.indexOf(glyph);
                    if (existing >= 0 && translation.indexOf(glyph, existing + glyph.length()) < 0) {
                        for (int j = existing; j < existing + glyph.length(); j++) existingIconStyles.put(j, style);
                    }
                    var following = FOLLOWING_TERM.matcher(plain.substring(sourceOffset[0] + i + length));
                    if (following.find()) {
                        String term = following.group(1);
                        String target = lookup.apply(term);
                        while (target == null && term.contains(" ")) {
                            term = term.substring(0, term.lastIndexOf(' '));
                            target = lookup.apply(term);
                        }
                        if (target != null) {
                            target = target.replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip();
                            int anchor = translation.indexOf(target);
                            if (!target.isEmpty() && anchor >= 0 && translation.indexOf(target, anchor + target.length()) < 0) {
                                anchored = true;
                                if (!translation.contains(glyph)) icons.computeIfAbsent(anchor, key -> new ArrayList<>())
                                        .add(Component.literal(glyph + " ").setStyle(style));
                                Arrays.fill(styles, anchor, anchor + target.length(),
                                        StylePreserver.sanitizeStyleForComparison(style, true));
                            }
                        }
                    }
                    // A leading decoration remains a prefix even when no glossary term supplies an anchor.
                    if (!anchored && !translation.contains(glyph)
                            && plain.substring(0, sourceOffset[0] + i).codePoints()
                            .allMatch(value -> Character.isWhitespace(value) || isIcon(value))) {
                        icons.computeIfAbsent(0, key -> new ArrayList<>())
                                .add(Component.literal(glyph + " ").setStyle(style));
                    }
                }
                i += length;
            }
            sourceOffset[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        for (Component source : sources) {
            source.visit((style, text) -> {
                Style visual = StylePreserver.sanitizeStyleForComparison(style, true);
                if (visual.equals(body)) return Optional.empty();
                String word = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip();
                String target = lookup.apply(word);
                if (target == null) {
                    word = word.replaceAll("^[\\s\\p{P}]+|[\\s\\p{P}]+$", "");
                    target = lookup.apply(word);
                }
                if (target == null) target = word; // Numbers and unchanged names can retain their accent too.
                target = target.replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip();
                if (target.isEmpty()) return Optional.empty();
                int start = translation.indexOf(target);
                // Ambiguous/repeated text should keep the body style instead of coloring the wrong occurrence.
                if (start >= 0 && translation.indexOf(target, start + target.length()) < 0
                        && !(start > 0 && asciiWord(translation.charAt(start - 1)) && asciiWord(target.charAt(0)))
                        && !(start + target.length() < translation.length()
                            && asciiWord(translation.charAt(start + target.length()))
                            && asciiWord(target.charAt(target.length() - 1)))) {
                    Arrays.fill(styles, start, start + target.length(), visual);
                }
                return Optional.empty();
            }, Style.EMPTY);
        }
        existingIconStyles.forEach((position, style) -> styles[position] = style);
        var result = Component.empty();
        for (int start = 0; start < translation.length();) {
            if (icons.containsKey(start)) icons.get(start).forEach(result::append);
            int end = start + 1;
            while (end < translation.length() && !icons.containsKey(end) && styles[end].equals(styles[start])) end++;
            result.append(Component.literal(translation.substring(start, end)).setStyle(styles[start]));
            start = end;
        }
        return result;
    }

    private static boolean asciiWord(char value) {
        return value >= '0' && value <= '9' || value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static boolean isIcon(int cp) {
        int type = Character.getType(cp);
        return cp > 127 && (type == Character.PRIVATE_USE || type == Character.UNASSIGNED
                || type == Character.OTHER_SYMBOL);
    }
}
