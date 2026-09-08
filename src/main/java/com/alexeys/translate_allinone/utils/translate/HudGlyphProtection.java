package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Keep resource-pack icons local while allowing adjacent readable HUD text to translate. */
public final class HudGlyphProtection {
    private static final Pattern GLYPHS = Pattern.compile("[\\p{Co}]+");

    private HudGlyphProtection() { }

    public static String readableText(Component source) {
        return source == null ? "" : GLYPHS.matcher(source.getString()).replaceAll("");
    }

    public static Set<String> tokens(Component source, Set<String> existing) {
        Set<String> result = new LinkedHashSet<>(existing);
        if (source != null) {
            var glyphs = GLYPHS.matcher(source.getString());
            while (glyphs.find()) {
                // Individual code points also cover icons split across Component boundaries.
                glyphs.group().codePoints().forEach(cp -> result.add(new String(Character.toChars(cp))));
            }
        }
        return Set.copyOf(result);
    }
}
