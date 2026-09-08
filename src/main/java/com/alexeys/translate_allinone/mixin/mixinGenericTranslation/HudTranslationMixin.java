package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.translate.GenericSurfaceTranslationSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Hud.class)
public abstract class HudTranslationMixin {
    // Translate before both measurement and drawing without overwriting the server's source fields.
    @WrapOperation(method = "extractTitle", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/Hud;title:Lnet/minecraft/network/chat/Component;"))
    private Component translate_allinone$title(Hud hud, Operation<Component> original) {
        return GenericSurfaceTranslationSupport.translate(original.call(hud), ComponentTranslationRoute.TITLE, "title");
    }

    @WrapOperation(method = "extractTitle", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/Hud;subtitle:Lnet/minecraft/network/chat/Component;"))
    private Component translate_allinone$subtitle(Hud hud, Operation<Component> original) {
        return GenericSurfaceTranslationSupport.translate(original.call(hud), ComponentTranslationRoute.TITLE, "subtitle");
    }

    @WrapOperation(method = "extractOverlayMessage", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/Hud;overlayMessageString:Lnet/minecraft/network/chat/Component;"))
    private Component translate_allinone$actionBar(Hud hud, Operation<Component> original) {
        return GenericSurfaceTranslationSupport.actionBar(original.call(hud));
    }
}
