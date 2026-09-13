package com.alexeys.translate_allinone.utils.translate;

import com.alexeys.translate_allinone.utils.AnimationManager;
import com.alexeys.translate_allinone.utils.config.pojos.ChatTranslateConfig;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.UUID;
import com.alexeys.translate_allinone.Translate_AllinOne;
import com.alexeys.translate_allinone.utils.MessageUtils;
import com.alexeys.translate_allinone.utils.config.pojos.HypixelUiConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.alexeys.translate_allinone.utils.input.KeybindingManager;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;

public final class HypixelMessageTranslationSupport {
    private static final LinkedHashMap<UUID, Boolean> RECENT_MESSAGES = new LinkedHashMap<>();
    private static Boolean lastShowTranslated;
    private static boolean refreshHeld;

    private static HypixelUiConfig config() {
        var config = Translate_AllinOne.getConfig();
        return config == null ? null : config.hypixelUi;
    }

    public static String targetLanguage() {
        var config = config();
        return config == null || config.target_language == null || config.target_language.isBlank()
                ? OtherTranslationsConfig.DEFAULT_TARGET_LANGUAGE : config.target_language;
    }

    public static boolean shouldShowTranslated() {
        return ComponentRenderTranslationSupport.shouldRenderTranslated(config());
    }

    public static void trackMessage(UUID id, boolean npc) {
        RECENT_MESSAGES.put(id, npc);
        while (RECENT_MESSAGES.size() > 32) RECENT_MESSAGES.remove(RECENT_MESSAGES.keySet().iterator().next());
    }

    public static void resetSession() {
        RECENT_MESSAGES.clear();
        lastShowTranslated = null;
        refreshHeld = false;
    }

    static void onTranslationCompleted(UUID id) {
        if (shouldHideTranslation(id)) ChatOutputTranslateManager.restoreOriginal(id);
    }

    private static boolean shouldHideTranslation(UUID id) {
        if (!RECENT_MESSAGES.containsKey(id)) return false;
        var config = config();
        return config != null && config.keybinding != null
                && config.keybinding.mode != OtherTranslationsConfig.KeybindingMode.DISABLED
                && !shouldShowTranslated();
    }

    static Component pendingDisplay(UUID id, Component translated) {
        var original = MessageUtils.getTrackedMessage(id);
        return original != null && shouldHideTranslation(id)
                ? ChatOutputTranslateManager.buildOriginalMessageWithToggle(id, original) : translated;
    }

    static void tickHotkeys() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || !TranslationFeatureGate.isEnabled()) {
            resetSession();
            return;
        }
        if (client.gui.screen() instanceof com.alexeys.translate_allinone.gui.ModConfigScreen) return;
        var config = config();
        if (config == null || config.keybinding == null) return;
        var output = Translate_AllinOne.getConfig().chatTranslate.output;
        RECENT_MESSAGES.entrySet().removeIf(entry -> MessageUtils.getTrackedMessage(entry.getKey()) == null
                || (entry.getValue() ? !output.skyblock_npc_auto_translate : !output.skyblock_server_auto_translate));
        boolean show = shouldShowTranslated();
        boolean refresh = KeybindingManager.isPressed(config.keybinding.refreshBinding);
        if (refresh && !refreshHeld && !RECENT_MESSAGES.isEmpty()) {
            UUID latest = null;
            for (UUID id : RECENT_MESSAGES.keySet()) latest = id;
            ChatOutputTranslateManager.forceRefreshTranslation(latest);
        }
        refreshHeld = refresh;
        if (lastShowTranslated == null || lastShowTranslated != show) {
            boolean useHotkey = config.keybinding.mode != OtherTranslationsConfig.KeybindingMode.DISABLED
                    || Boolean.FALSE.equals(lastShowTranslated);
            lastShowTranslated = show;
            if (useHotkey) for (UUID id : RECENT_MESSAGES.keySet()) {
                var tracked = MessageUtils.getTrackedChatMessage(id);
                if (!show) ChatOutputTranslateManager.showPendingOriginal(id);
                if (tracked == null || tracked.showingTranslated() == show) continue;
                if (show) ChatOutputTranslateManager.translate(id, tracked.originalMessage());
                else ChatOutputTranslateManager.restoreOriginal(id);
            }
        }
    }

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
