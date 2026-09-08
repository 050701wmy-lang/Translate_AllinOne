package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelMessageTranslationSupportTest {
    @Test
    void separateSwitchesWorkWithGeneralChatDisabled() {
        var config = new com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig.ChatOutputTranslateConfig();
        config.enabled = false;
        config.auto_translate = false;
        config.skyblock_server_auto_translate = true;
        assertTrue(HypixelMessageTranslationSupport.shouldAutoTranslate(config, false, true));
        assertFalse(HypixelMessageTranslationSupport.shouldAutoTranslate(config, true, false));
        assertFalse(HypixelMessageTranslationSupport.shouldAutoTranslate(config, false, false));
        config.skyblock_server_auto_translate = false;
        config.skyblock_npc_auto_translate = true;
        assertTrue(HypixelMessageTranslationSupport.shouldAutoTranslate(config, true, false));
        assertFalse(HypixelMessageTranslationSupport.shouldAutoTranslate(config, false, true));
    }
    @Test
    void detectsHypixelSkyblockOnly() {
        assertTrue(HypixelMessageTranslationSupport.isSkyblock("mc.hypixel.net:25565", "§eSKYBLOCK"));
        assertTrue(HypixelMessageTranslationSupport.isSkyblock("MC.HYPIXEL.COM.CN", "§eSKYBLOCK"));
        assertTrue(HypixelMessageTranslationSupport.isSkyblock("mc.hypixel.com.cn:25565", "SKYBLOCK"));
        assertFalse(HypixelMessageTranslationSupport.isSkyblock("mc.hypixel.com.cn", "BED WARS"));
        assertFalse(HypixelMessageTranslationSupport.isSkyblock("hypixel.com.cn.example.org", "SKYBLOCK"));
        assertFalse(HypixelMessageTranslationSupport.isSkyblock("hypixel.net.example.org", "SKYBLOCK"));
        assertFalse(HypixelMessageTranslationSupport.isSkyblock("mc.hypixel.net", "BED WARS"));
        assertFalse(HypixelMessageTranslationSupport.isSkyblock("localhost", "SKYBLOCK"));
    }

    @Test
    void acceptsScreenshotRewardsAndNotifications() {
        for (String text : new String[]{
                "Since you've been away you earned 325 coins as interest in your personal bank account!",
                "SKILL LEVEL UP Combat III→IV", "REWARDS", "Warrior IV",
                "Deal 12→16% more damage to mobs.", "+0.5% ♣ Crit Chance", "+750 Coins",
                "+5 SkyBlock XP", "RARE DROP! Carrot (+6 ✯ Magic Find)",
                "COLLECTION UNLOCKED Gravel", "NEW AREA DISCOVERED!", "♟ Arachne's Burrow",
                "■ Home of Arachne.", "■ Tread carefully.",
                "BE CAREFUL! You're below the recommended Combat Level for this zone!",
                "The recommended level to enter Arachne's Burrow is 6. You are Combat Level 4."}) {
            assertTrue(HypixelMessageTranslationSupport.isServerText(Component.literal(text)), text);
        }
    }

    @Test
    void excludesPlayersNpcAndTechnicalLines() {
        for (String text : new String[]{"[MVP+] Alex: SKILL LEVEL UP", "Alex: RARE DROP!",
                "Party > [VIP] Alex: hello", "From Alex: hello", "<Alex> hello",
                "[NPC] Rusty: Hello!", "[Skyblocker] Tip: hello", "Alex joined the lobby!",
                "Profile ID: ed96d019-83fb-4761-8fc3-fe7c6a21f10d", "Sending to server mini3BD...",
                "正在前往mini123DH……", "正在连接mini3AT...",
                "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "", "获得了金币"}) {
            assertFalse(HypixelMessageTranslationSupport.isServerText(Component.literal(text)), text);
        }
    }
}
