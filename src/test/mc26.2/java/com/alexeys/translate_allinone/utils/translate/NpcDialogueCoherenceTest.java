package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.cache.component.ComponentCacheModule;
import com.alexeys.translate_allinone.utils.componentjson.*;
import com.alexeys.translate_allinone.utils.config.ProviderRouteResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NpcDialogueCoherenceTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void realGwynnieMessageTravelsAsOneSentenceAndRestoresNameAndFishingColor() {
        Component message = Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Gwynnie").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(": Before I can get ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Fishing").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(", I have to make sure I understand the fish themselves.").withStyle(ChatFormatting.WHITE));
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(message, Set.of());
        var original = ChatOutputTranslateManager.prepareSharedNpcDocument(prepared);
        var unit = original.units().getFirst();
        assertEquals(List.of(unit.sourceText()), SharedHudTranslationSupport.pieces(original, unit.sourceText()));
        var plan = SharedHudTranslationSupport.planFor(original, unit.sourceText(), Map.of("Fishing", "钓鱼")::get);
        var shared = SharedHudTranslationSupport.document(original, unit, plan, "hypixel");
        assertEquals(ComponentTranslationRoute.CHAT_OUTPUT, shared.route());
        assertEquals(ComponentCacheModule.NPC_DIALOGUE, ComponentCacheModule.forRoute(shared.route()));
        assertEquals(ProviderRouteResolver.Route.HYPIXEL, ComponentTranslationRuntimeCore.providerRoute(shared));
        assertEquals(unit.protectedTokens(), shared.units().getFirst().protectedTokens());
        String reply = unit.sourceText().replace("Before I can get ", "在开始")
                .replace("Fishing", "钓鱼")
                .replace(", I have to make sure I understand the fish themselves.", "之前，我得先弄清鱼类本身。");
        var response = new ComponentTranslationResponse(shared.protocol(), Map.of(unit.id(), reply));
        new ComponentTranslationValidator().validate(shared, response);
        Component translated = ChatOutputTranslateManager.rebuildTranslatedText(reply, prepared);
        assertEquals("[NPC] Gwynnie: 在开始钓鱼之前，我得先弄清鱼类本身。", translated.getString());
        assertTrue(translated.visit((style, text) -> text.contains("钓鱼") ? Optional.of(
                style.getColor().equals(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GREEN).getColor()))
                : Optional.empty(), net.minecraft.network.chat.Style.EMPTY).orElse(false));
    }

    @Test void npcRebuildRemovesInjectedDashesAcrossStyles() {
        Component message = Component.literal("[NPC] Gwynnie: Here, read my ")
                .append(Component.literal("notes").withStyle(ChatFormatting.AQUA)).append("!");
        var prepared = ChatOutputTranslateManager.prepareTranslationPayload(message, Set.of());
        String reply = prepared.textToTranslate().replace("Here, read my ", "来，读读我的- ")
                .replace("notes", "-笔记");
        assertEquals("[NPC] Gwynnie: 来，读读我的笔记!",
                ChatOutputTranslateManager.rebuildTranslatedText(reply, prepared).getString());
    }

    @Test void observedNpcEnglishResidueFailsCompletionCheck() {
        for (var entry : Map.of(
                "This part of the hub is a popular area for fishing because of the Fishing Outpost!",
                "-This part of the hub is 是钓鱼的热门地区，因为钓鱼前哨站！",
                "If you follow the river that flows under the bridge downstream, you'll find your way to it.",
                "如果在-you follow the river that flows桥下下游，你会找到通往它的路。",
                "Before I can get Fishing, I have to make sure I understand the fish themselves.",
                "在我获得之前钓鱼，我必须制作-sure I understand the fish themselves。",
                "There are 4 Fishing stats you need to know about.", "有4-Fishing stats你需要了解的。",
                "Here, read my notes!", "来，读读我的-notes！").entrySet()) {
            assertTrue(SharedHudTranslationSupport.hasIncompleteTooltipProse(entry.getKey(), entry.getValue(), "Chinese"));
        }
    }
}
