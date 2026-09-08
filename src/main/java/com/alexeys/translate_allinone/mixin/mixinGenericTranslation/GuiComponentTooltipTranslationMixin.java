package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.translate.UiTextRole;
import com.alexeys.translate_allinone.utils.translate.FinalItemTooltipTranslationSupport;
import com.alexeys.translate_allinone.utils.translate.SkyblockerGridTooltipSupport;
import com.alexeys.translate_allinone.utils.translate.UiTranslationRuntime;
import com.alexeys.translate_allinone.utils.translate.UiTranslationScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;
import java.util.Optional;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiComponentTooltipTranslationMixin {
    @WrapMethod(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V")
    private void translate_allinone$components(Font font, List<Component> source, Optional<TooltipComponent> image,
                                             int x, int y, Identifier texture, Operation<Void> original) {
        // Collect Components before Font.split loses their structure and tooltip role.
        List<Component> visible = FinalItemTooltipTranslationSupport.ownsTooltip()
                ? FinalItemTooltipTranslationSupport.translate(source)
                : UiTranslationRuntime.translateComponents(source, UiTextRole.TOOLTIP);
        visible = SkyblockerGridTooltipSupport.translate(visible);
        try (var ignored = UiTranslationScope.enterInternal()) {
            original.call(font, visible, image, x, y, texture);
        }
    }

    @WrapMethod(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V")
    private void translate_allinone$itemBoundary(Font font, ItemStack stack, int x, int y, Operation<Void> original) {
        FinalItemTooltipTranslationSupport.render(() -> original.call(font, stack, x, y));
    }
}
