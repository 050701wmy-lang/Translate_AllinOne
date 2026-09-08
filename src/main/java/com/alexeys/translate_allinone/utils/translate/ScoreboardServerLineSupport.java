package com.alexeys.translate_allinone.utils.translate;

import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Keep the scoreboard's real-world date and instance identifier verbatim. */
final class ScoreboardServerLineSupport {
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_LINE = Pattern.compile(
            "\\s*\\d{2}/\\d{2}/\\d{2,4}\\s+[A-Za-z]+[0-9][A-Za-z0-9]*\\s*");

    private ScoreboardServerLineSupport() {}

    static boolean isServerLine(Component component) {
        return component != null && SERVER_LINE.matcher(
                FORMATTING.matcher(component.getString()).replaceAll("")).matches();
    }
}
