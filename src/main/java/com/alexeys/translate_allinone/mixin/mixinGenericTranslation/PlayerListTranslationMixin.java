package com.alexeys.translate_allinone.mixin.mixinGenericTranslation;

import com.alexeys.translate_allinone.utils.componentjson.ComponentTranslationRoute;
import com.alexeys.translate_allinone.utils.translate.GenericSurfaceTranslationSupport;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListTranslationMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"), require = 2)
    private List<FormattedCharSequence> translate_allinone$headerFooter(Font font, FormattedText source, int width) {
        return font.split(source instanceof Component component
                ? GenericSurfaceTranslationSupport.translate(component, ComponentTranslationRoute.PLAYER_LIST, "player-list/header-footer")
                : source, width);
    }

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void translate_allinone$displayName(PlayerInfo player, CallbackInfoReturnable<Component> cir) {
        // Never translate the account-name fallback, which is also used to identify players.
        if (player.getTabListDisplayName() != null) {
            cir.setReturnValue(GenericSurfaceTranslationSupport.playerDisplayName(cir.getReturnValue()));
        }
    }
}
