package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/** Render-only entry points; requests, dynamic tokens and persistence belong to AIO's pipeline. */
public final class GenericSurfaceTranslationSupport {
    private static final ThreadLocal<Integer> HOVER_DEPTH = ThreadLocal.withInitial(() -> 0);

    public static boolean isRenderingHover() {
        return HOVER_DEPTH.get() > 0;
    }

    public static void renderHover(Runnable render) {
        HOVER_DEPTH.set(HOVER_DEPTH.get() + 1);
        try (var ignored = UiTranslationScope.enterInternal()) {
            render.run();
        } finally {
            int depth = HOVER_DEPTH.get() - 1;
            if (depth == 0) HOVER_DEPTH.remove();
            else HOVER_DEPTH.set(depth);
        }
    }

    private GenericSurfaceTranslationSupport() {
    }

    public static Component translate(Component source, ComponentTranslationRoute route, String context) {
        return translate(source, route, context, ComponentRenderTranslationSupport.config());
    }

    static Component translate(Component source, ComponentTranslationRoute route, String context, OtherTranslationsConfig config) {
        if (source == null || !enabled(config, route)
                || !ComponentRenderTranslationSupport.shouldRenderTranslated(config)
                || !UiTextFilter.evaluate(HudGlyphProtection.readableText(source), UiTextRole.DESCRIPTION, false).eligible()) {
            return source;
        }
        return ComponentRenderTranslationSupport.translate(
                source, route, context, "generic-surface-v2", config, true,
                HudGlyphProtection.tokens(source, java.util.Set.of())
        ).displayed();
    }

    static boolean enabled(OtherTranslationsConfig config, ComponentTranslationRoute route) {
        if (config == null || !config.enabled || route == null) {
            return false;
        }
        return switch (route) {
            case PLAYER_LIST -> config.enabled_translate_player_list;
            case BOSS_BAR -> config.enabled_translate_boss_bars;
            case TITLE -> config.enabled_translate_titles;
            case ACTION_BAR -> config.enabled_translate_action_bar;
            case HOVER_TEXT -> config.enabled_translate_hover_text;
            default -> false;
        };
    }

    public static Component playerDisplayName(Component source) {
        // Player display names identify people, including rank and nickname decorations.
        return source;
    }

    public static Component actionBar(Component source) {
        return WynnDialogueOverlayController.getInstance().ownsOverlay()
                ? source : translate(source, ComponentTranslationRoute.ACTION_BAR, "action-bar");
    }

    public static boolean ownsHover(Style style) {
        return style != null && style.getHoverEvent() instanceof HoverEvent.ShowText
                && enabled(ComponentRenderTranslationSupport.config(), ComponentTranslationRoute.HOVER_TEXT);
    }

    public static Style hoverStyle(Style source) {
        if (source == null || !(source.getHoverEvent() instanceof HoverEvent.ShowText text)
                || ChatOutputOriginalHoverStyle.isMarked(source)
                || ChatOutputOriginalHoverStyle.isMarkedHoverEvent(source.getHoverEvent())) {
            return source;
        }
        Component visible = translate(text.value(), ComponentTranslationRoute.HOVER_TEXT, "hover-event/show-text");
        return visible == text.value() ? source : source.withHoverEvent(new HoverEvent.ShowText(visible));
    }
}
