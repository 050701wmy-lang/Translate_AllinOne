package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.translate.GenericSurfaceTranslationSupport;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiGraphicsExtractor.class)
public abstract class HoverEventTranslationMixin {
    @WrapMethod(method = "componentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V")
    private void translate_allinone$hover(Font font, Style style, int x, int y, Operation<Void> original) {
        if (!GenericSurfaceTranslationSupport.ownsHover(style)) {
            original.call(font, style, x, y);
            return;
        }
        // Tooltip scheduling happens here: keep UI collectors and item translation out of this document.
        GenericSurfaceTranslationSupport.renderHover(() ->
                original.call(font, GenericSurfaceTranslationSupport.hoverStyle(style), x, y));
    }
}
