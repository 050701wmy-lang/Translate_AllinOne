package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic complete phrases at surface boundaries; never translate individual name fragments. */
public final class SurfaceTextCompletion {
    private static final Pattern TALK = Pattern.compile("^(?:(?:Objective|目标)\\s*[:：]\\s*)?Talk to\\s+([\\p{L}\\p{N}_'’ .-]+?)[.!]?\\s*$");
    private static final Pattern COOLDOWN = Pattern.compile("^(\\s*)Cooldown\\s*:\\s*(\\d+(?:\\.\\d+)?)s\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    private SurfaceTextCompletion() {}

    private static boolean chinese(String language) {
        String value = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return value.startsWith("zh") || value.contains("chinese") || value.contains("中文");
    }

    static Component objective(Component source, String language) {
        if (source == null || !chinese(language)) return null;
        Component normalized = ChatOutputFormatSupport.normalize(source);
        String text = normalized.getString();
        var match = TALK.matcher(text);
        if (!match.matches()) return null;
        int taskStart = text.indexOf("Talk to");
        Style taskStyle = styleAt(normalized, taskStart);
        var result = Component.empty();
        if (taskStart > 0) result.append(Component.literal("目标：").setStyle(styleAt(normalized, 0)));
        result.append(Component.literal("与").setStyle(taskStyle));
        result.append(slice(normalized, match.start(1), match.end(1)));
        result.append(Component.literal("交谈。").setStyle(taskStyle));
        return result;
    }

    static Component cooldown(Component source, String language) {
        if (source == null || !chinese(language)) return source;
        Component normalized = ChatOutputFormatSupport.normalize(source);
        var match = COOLDOWN.matcher(normalized.getString());
        if (!match.matches()) return source;
        return Component.literal(match.group(1) + "冷却时间：").setStyle(styleAt(normalized, match.end(1)))
                .append(slice(normalized, match.start(2), match.end(2)))
                .append(Component.literal("秒").setStyle(styleAt(normalized, match.end(2))));
    }

    public static List<Component> completeTooltips(List<Component> lines) {
        if (lines == null || !TranslationFeatureGate.isEnabled()) return lines;
        var config = Translate_AllinOne.getConfig().itemTranslate;
        if (config == null || !config.enabled || !config.enabled_translate_item_lore || !chinese(config.target_language)) return lines;
        if (config.keybinding != null && TooltipTranslationSupport.shouldShowOriginal(config.keybinding.mode,
                KeybindingManager.isPressed(config.keybinding.binding))) return lines;
        List<Component> result = null;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line == null || !line.getString().toLowerCase(Locale.ROOT).contains("cooldown")) continue;
            Component completed = cooldown(line, config.target_language);
            if (completed != line) {
                if (result == null) result = new ArrayList<>(lines);
                result.set(i, completed);
            }
        }
        return result == null ? lines : result;
    }

    private static Style styleAt(Component source, int index) {
        int[] offset = {0};
        return source.visit((style, text) -> {
            int start = offset[0]; offset[0] += text.length();
            return start <= index && index < offset[0] ? Optional.of(style) : Optional.empty();
        }, Style.EMPTY).orElse(Style.EMPTY);
    }

    private static Component slice(Component source, int start, int end) {
        var result = Component.empty();
        int[] offset = {0};
        source.visit((style, text) -> {
            int left = Math.max(0, start - offset[0]);
            int right = Math.min(text.length(), end - offset[0]);
            if (right > left) result.append(Component.literal(text.substring(left, right)).setStyle(style));
            offset[0] += text.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
