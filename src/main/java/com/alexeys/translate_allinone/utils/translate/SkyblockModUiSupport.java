package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import net.minecraft.network.chat.Component;

public final class SkyblockModUiSupport {
    private SkyblockModUiSupport() {}

    static boolean ownsClass(String name) {
        return name != null && (name.startsWith("de.hysky.skyblocker.")
                || name.startsWith("at.hannibal2.skyhanni.")
                || name.startsWith("me.owdding.skyocean.")
                || name.startsWith("me.owdding.customscoreboard."));
    }

    public static boolean isOwnedCreation() {
        return StackWalker.getInstance().walk(frames -> frames
                .anyMatch(frame -> ownsClass(frame.getClassName())));
    }

    public static boolean ownsScreen() {
        var adapter = UiTranslationScope.adapter();
        return adapter != null && ownsClass(adapter.screenId());
    }

    static OtherTranslationsConfig selectConfig(ModConfig config, boolean owned) {
        return config == null ? null : owned ? config.hypixelUi : config.otherTranslations;
    }

    public static Component translateTooltip(Component source) {
        return translateTooltip(source, selectConfig(Translate_AllinOne.getConfig(), true));
    }

    static Component translateTooltip(Component source, OtherTranslationsConfig config) {
        if (config == null || !config.enabled || !ComponentRenderTranslationSupport.shouldRenderTranslated(config)
                || !UiTextFilter.evaluate(source.getString(), UiTextRole.TOOLTIP, false).eligible()) return source;
        return ComponentRenderTranslationSupport.translate(source, ComponentTranslationRoute.SKYBLOCK_UI,
                "skyblock-mod/widget-tooltip", "skyblock-ui-v1", config, true,
                HudGlyphProtection.tokens(source, java.util.Set.of())).displayed();
    }
}
