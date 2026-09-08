package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.translate.GenericSurfaceTranslationSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarTranslationMixin {
    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
    private Component translate_allinone$name(LerpingBossEvent event, Operation<Component> original) {
        return GenericSurfaceTranslationSupport.translate(original.call(event), ComponentTranslationRoute.BOSS_BAR, "boss-bar/name");
    }
}
