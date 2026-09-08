package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import java.util.List;

/** Defer ordinary item translation until mods have finished decorating the tooltip. */
public final class FinalItemTooltipTranslationSupport {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private FinalItemTooltipTranslationSupport() {
    }

    public static boolean ownsTooltip() {
        return DEPTH.get() > 0
                && !GenericSurfaceTranslationSupport.isRenderingHover()
                && !TooltipTranslationContext.isInWynnmodTooltipRender()
                && !TooltipTranslationContext.isInWynntilsItemStatTooltipRender()
                && !TooltipTranslationContext.isInWynntilsQuestTooltipRender()
                && !TooltipTranslationContext.isInReiTooltipRender();
    }

    public static void render(Runnable render) {
        DEPTH.set(DEPTH.get() + 1);
        try (var ignored = UiTranslationScope.enterInternal()) {
            render.run();
        } finally {
            int depth = DEPTH.get() - 1;
            if (depth == 0) DEPTH.remove();
            else DEPTH.set(depth);
        }
    }

    public static List<Component> translate(List<Component> source) {
        return TooltipTranslationSupport.buildTranslatedTooltipResult(source, "item-tooltip-final").translatedTooltip();
    }
}
