package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.translate.FinalItemTooltipTranslationSupport;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTranslationBoundaryMixin {
    @WrapMethod(method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")
    private void translate_allinone$itemTooltip(GuiGraphicsExtractor graphics, int x, int y, Operation<Void> original) {
        FinalItemTooltipTranslationSupport.render(() -> original.call(graphics, x, y));
    }
}
