package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.pojos.ItemTranslateConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Soft compatibility: preserve Skyblocker's grid while translating its standard Component columns. */
public final class SkyblockerGridTooltipSupport {
    private static final ClassValue<Optional<Access>> ACCESS = new ClassValue<>() {
        @Override
        protected Optional<Access> computeValue(Class<?> type) {
            if (!type.getName().equals("de.hysky.skyblocker.utils.render.text.GridComponent$Contents")) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Access(type.getMethod("group"), type.getMethod("components"),
                        type.getConstructor(String.class, List.class)));
            } catch (ReflectiveOperationException error) {
                Translate_AllinOne.LOGGER.warn("Unsupported Skyblocker grid tooltip API; keeping original text", error);
                return Optional.empty();
            }
        }
    };

    private SkyblockerGridTooltipSupport() {
    }

    public static List<Component> translate(List<Component> lines) {
        if (lines == null || lines.isEmpty() || !TranslationFeatureGate.isEnabled()) return lines;
        if (lines.stream().noneMatch(row -> row != null && ACCESS.get(row.getContents().getClass()).isPresent())) return lines;
        ItemTranslateConfig config = Translate_AllinOne.getConfig().itemTranslate;
        if (config == null || !config.enabled || !config.enabled_translate_item_lore
                || (config.keybinding != null && config.keybinding.mode != null && TooltipTranslationSupport.shouldShowOriginal(
                config.keybinding.mode, KeybindingManager.isPressed(config.keybinding.binding)))) return lines;
        List<Component> result = new ArrayList<>(lines.size());
        for (Component row : lines) {
            result.add(translateRow(row, column -> translateColumn(column, config)));
        }
        return result;
    }

    static Component translateColumn(Component column, ItemTranslateConfig config) {
        Component date = TooltipDateTranslationSupport.translate(column, config.target_language);
        if (date != null) return date;
        if (!TooltipTextMatcherSupport.evaluateTooltipLine(column, false, config).shouldTranslate()) return column;
        var prepared = TooltipTemplateRuntime.prepareTemplate(column, false);
        if (TooltipTemplateRuntime.hasLocalDictionaryTranslation(column)) {
            return TooltipTemplateRuntime.translatePreparedTemplate(prepared).translatedLine();
        }
        var result = TooltipComponentTranslationSupport.translatePreparedLine(prepared,
                ComponentTranslationRoute.TOOLTIP_LINE, "tooltip:skyblocker:grid-column", "grid-column-v1", config);
        return result == null || result.pending() || result.translatedLine() == null ? column : result.translatedLine();
    }

    static Component translateRow(Component row, UnaryOperator<Component> translator) {
        if (row == null) return null;
        var access = ACCESS.get(row.getContents().getClass()).orElse(null);
        if (access == null) return row;
        try {
            Object group = access.group().invoke(row.getContents());
            Object value = access.columns().invoke(row.getContents());
            if (!(group instanceof String) || !(value instanceof List<?> columns)
                    || columns.stream().anyMatch(column -> !(column instanceof Component))) return row;
            List<Component> translated = new ArrayList<>(columns.size());
            boolean changed = false;
            for (Object valueColumn : columns) {
                Component column = (Component) valueColumn;
                Component visible = translator.apply(column);
                if (visible == null) visible = column;
                translated.add(visible);
                changed |= !visible.equals(column);
            }
            if (!changed) return row;
            // Do not serialize or flatten the unknown contents: its codec deliberately throws.
            var contents = (ComponentContents) access.constructor().newInstance(group, List.copyOf(translated));
            MutableComponent rebuilt = MutableComponent.create(contents).setStyle(row.getStyle());
            row.getSiblings().forEach(sibling -> rebuilt.append(sibling.copy()));
            return rebuilt;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return row;
        }
    }

    private record Access(Method group, Method columns, Constructor<?> constructor) {
    }
}
