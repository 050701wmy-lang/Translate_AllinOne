package com.alexeys.translate_allinone.utils.translate;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Verify against the actual Minecraft dependency, not mocks of its rendering signatures. */
class GenericTranslationInjectionTargetsTest {
    @Test
    void widgetTooltipHasPersistentCacheAndRefreshableComponentEntry() throws IOException {
        var tooltip = read("net/minecraft/client/gui/components/Tooltip");
        assertTrue(tooltip.fields.stream().anyMatch(f -> f.name.equals("message")
                && f.desc.equals("Lnet/minecraft/network/chat/Component;")));
        assertTrue(tooltip.fields.stream().anyMatch(f -> f.name.equals("cachedTooltip")));
        assertTrue(tooltip.methods.stream().anyMatch(m -> m.name.equals("toCharSequence")
                && m.desc.equals("(Lnet/minecraft/client/Minecraft;)Ljava/util/List;")));
        assertTrue(tooltip.methods.stream().anyMatch(m -> m.name.equals("splitTooltip")
                && m.desc.equals("(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/chat/Component;)Ljava/util/List;")));
    }
    private ClassNode read(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    @Test
    void hudStillReadsSourcesDuringRendering() throws IOException {
        var hud = read("net/minecraft/client/gui/Hud");
        Set<String> found = new HashSet<>();
        for (var method : hud.methods) {
            if (!Set.of("extractTitle", "extractOverlayMessage").contains(method.name)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(hud.name)
                        && field.desc.equals("Lnet/minecraft/network/chat/Component;")) {
                    found.add(method.name + "/" + field.name);
                }
            }
        }
        assertTrue(found.containsAll(Set.of("extractTitle/title", "extractTitle/subtitle", "extractOverlayMessage/overlayMessageString")));
    }

    @Test
    void tabWrapsBothHeaderAndFooterAndBossNameIsAvailableBeforeLayout() throws IOException {
        var tab = read("net/minecraft/client/gui/components/PlayerTabOverlay");
        int splits = 0;
        for (var method : tab.methods) {
            if (!method.name.equals("extractRenderState")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/Font")
                        && call.name.equals("split") && call.desc.equals("(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;")) splits++;
            }
        }
        assertEquals(2, splits);
        assertTrue(tab.methods.stream().anyMatch(m -> m.name.equals("getNameForDisplay")
                && m.desc.equals("(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;")));
        var boss = read("net/minecraft/client/gui/components/BossHealthOverlay");
        boolean nameCall = false;
        for (var method : boss.methods) {
            if (!method.name.equals("extractRenderState")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/components/LerpingBossEvent")
                        && call.name.equals("getName") && call.desc.equals("()Lnet/minecraft/network/chat/Component;")) nameCall = true;
            }
        }
        assertTrue(nameCall);
    }

    @Test
    void hoverAndContainerBoundariesExistWithExactDescriptors() throws IOException {
        assertTrue(read("net/minecraft/client/gui/GuiGraphicsExtractor").methods.stream().anyMatch(m ->
                m.name.equals("setTooltipForNextFrame") && m.desc.equals("(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V")));
        assertTrue(read("net/minecraft/client/gui/GuiGraphicsExtractor").methods.stream().anyMatch(m ->
                m.name.equals("setTooltipForNextFrame") && m.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V")));
        assertTrue(read("net/minecraft/client/gui/GuiGraphicsExtractor").methods.stream().anyMatch(m ->
                m.name.equals("componentHoverEffect") && m.desc.equals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V")));
        assertTrue(read("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen").methods.stream().anyMatch(m ->
                m.name.equals("extractTooltip") && m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")));
    }
}
