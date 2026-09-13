package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceTextCompletionTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void bossAndSplitScoreboardGoalHaveIdenticalWordingAndRetainTheirColors() {
        Component boss = Component.literal("Objective: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("Talk to Gerald.").withStyle(ChatFormatting.YELLOW));
        Component sidebar = Component.literal("Talk to ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Geral").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("d").withStyle(ChatFormatting.BLUE));
        Component translatedBoss = SurfaceTextCompletion.objective(boss, "Chinese");
        Component translatedSidebar = SurfaceTextCompletion.objective(sidebar, "Chinese");
        assertEquals("目标：与Gerald交谈。", translatedBoss.getString());
        assertEquals(translatedBoss.getString().substring(3), translatedSidebar.getString());
        assertColor(translatedSidebar, "Gerald", ChatFormatting.BLUE);
        assertColor(translatedBoss, "Gerald", ChatFormatting.YELLOW);
        assertNull(SurfaceTextCompletion.objective(sidebar, "English"));
        assertNull(SurfaceTextCompletion.objective(Component.literal("Someone said Talk to Gerald"), "Chinese"));
    }

    @Test void finalCooldownCorrectionDoesNotDependOnCacheAndPreservesValueStyle() {
        for (String label : List.of("Cooldown:", "Cooldown: ", "Cooldown:\u00a0")) {
            Component source = Component.literal(label).withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("5s").withStyle(ChatFormatting.GREEN));
            Component result = SurfaceTextCompletion.cooldown(source, "Chinese");
            assertEquals("冷却时间：5秒", result.getString());
            assertColor(result, "5", ChatFormatting.GREEN);
            assertColor(result, "冷却时间", ChatFormatting.DARK_GRAY);
        }
        assertEquals("冷却时间：5秒", SurfaceTextCompletion.cooldown(Component.literal("§8Cooldown: §a5s"), "Chinese").getString());
        Component original = Component.literal("Cooldown:5s");
        assertSame(original, SurfaceTextCompletion.cooldown(original, "English"));
        Component prose = Component.literal("Cooldown:5s after equipping");
        assertSame(prose, SurfaceTextCompletion.cooldown(prose, "Chinese"));
    }

    private static void assertColor(Component source, String fragment, ChatFormatting color) {
        assertTrue(source.visit((style, text) -> text.contains(fragment)
                ? Optional.of(Objects.equals(style.getColor(), Style.EMPTY.withColor(color).getColor()))
                : Optional.empty(), Style.EMPTY).orElse(false));
    }
}
