package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;

public final class HypixelMessageTranslationSupport {
    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:(?:Party|Guild|Officer|Co-op)\\s*(?:>|:)\\s*|(?:From|To)\\s+))?(?:\\[[^]\\r\\n]+]\\s*)*[A-Za-z0-9_]{1,16}(?:\\s*\\[[^]\\r\\n]+])?\\s*:");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern TECHNICAL = Pattern.compile(
            "(?i)^(?:Profile ID:|Sending to server |Connecting to |You are playing on profile:|You are playing on:|正在前往|正在连接|正在連接|正在傳送|正在传送)");

    private HypixelMessageTranslationSupport() {}

    public static boolean isSkyblock() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.getCurrentServer() == null) return false;
        var objective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        return isSkyblock(client.getCurrentServer().ip,
                objective == null ? "" : objective.getDisplayName().getString());
    }

    static boolean isSkyblock(String address, String title) {
        String host = address.strip().toLowerCase(Locale.ROOT).split(":", 2)[0];
        return (host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || host.equals("hypixel.com.cn") || host.endsWith(".hypixel.com.cn"))
                && AnimationManager.stripFormatting(title).toUpperCase(Locale.ROOT).contains("SKYBLOCK");
    }

    public static boolean isServerMessage(Component message) {
        return isSkyblock() && isServerText(message);
    }

    public static boolean shouldAutoTranslate(ChatTranslateConfig.ChatOutputTranslateConfig config,
                                               boolean npcMessage, boolean serverMessage) {
        return (config.skyblock_npc_auto_translate && npcMessage)
                || (config.skyblock_server_auto_translate && serverMessage);
    }

    static boolean isServerText(Component message) {
        if (message == null || ChatOutputTranslateManager.isSkyblockNpcMessage(message)) return false;
        String text = AnimationManager.stripFormatting(message.getString()).strip();
        if (!LETTER.matcher(text).find() || TECHNICAL.matcher(text).find()) return false;
        if (text.startsWith("[") || text.startsWith("<") || PLAYER_CHAT.matcher(text).find()) return false;
        return !text.matches("(?is).*(?:joined the lobby|joined the game|left the game|joined SkyBlock).*" );
    }
}
