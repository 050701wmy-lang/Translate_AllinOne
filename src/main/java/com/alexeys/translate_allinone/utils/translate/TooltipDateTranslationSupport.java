package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Dates are data, not prose: never let a model rearrange their numeric placeholders. */
final class TooltipDateTranslationSupport {
    private static final Pattern DATE = Pattern.compile("[A-Za-z]+ \\d{1,2}, \\d{4}");
    private static final DateTimeFormatter ENGLISH = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT);

    private TooltipDateTranslationSupport() { }

    // null means this column is not a standalone date and should use normal translation.
    static Component translate(Component source, String language) {
        String original = source.getString();
        String text = original.strip();
        if (!DATE.matcher(text).matches()) return null;
        LocalDate date;
        try {
            date = LocalDate.parse(text, ENGLISH);
        } catch (DateTimeParseException ignored) {
            return source;
        }
        String target = language == null ? "" : language.strip().toLowerCase(Locale.ROOT);
        boolean chinese = target.equals("zh") || target.startsWith("zh-") || target.startsWith("zh_")
                || target.contains("chinese") || target.contains("中文") || target.contains("汉语") || target.contains("漢語");
        if (!chinese) return source;
        Style[] dateStyle = {source.getStyle()};
        source.visit((style, part) -> {
            if (part.isBlank()) return Optional.empty();
            dateStyle[0] = style;
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        int start = original.indexOf(text);
        return Component.literal(original.substring(0, start) + date.getYear() + "年" + date.getMonthValue()
                + "月" + date.getDayOfMonth() + "日" + original.substring(start + text.length())).setStyle(dateStyle[0]);
    }
}
