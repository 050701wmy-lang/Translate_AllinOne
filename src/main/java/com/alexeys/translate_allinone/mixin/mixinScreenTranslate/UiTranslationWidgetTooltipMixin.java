package com.alexeys.translate_allinone.mixin.mixinScreenTranslate;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;

@Mixin(Tooltip.class)
public abstract class UiTranslationWidgetTooltipMixin {
    @Shadow @Final private Component message;

    @WrapMethod(method = "toCharSequence(Lnet/minecraft/client/Minecraft;)Ljava/util/List;")
    private List<FormattedCharSequence> translate_allinone$refreshWidgetTooltip(Minecraft client,
                                                                              Operation<List<FormattedCharSequence>> original) {
        // Widget tooltips cache their first split indefinitely. Re-query the original
        // Component each frame so async completion and original/translated toggles work.
        Component visible = UiTranslationRuntime.translateComponentInCurrentScreen(message, UiTextRole.TOOLTIP);
        List<FormattedCharSequence> lines = UiTranslationRuntime.withoutNestedTranslation(() ->
                visible.equals(message) ? original.call(client) : Tooltip.splitTooltip(client, visible));
        lines.forEach(UiTranslationRuntime::markFormattedSequenceHandled);
        return lines;
    }
}
