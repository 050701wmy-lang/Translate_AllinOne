package com.alexeys.translate_allinone.registration;

import com.alexeys.translate_allinone.utils.config.ModConfig;
import com.alexeys.translate_allinone.utils.config.pojos.OtherTranslationsConfig;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelSettingsMigrationTest {
    @Test
    void keepsOldUiLanguageAndSwitchesWhileAddingTheSharedRoute() {
        var config = new ModConfig();
        config.hypixelUi.target_language = "Japanese";
        config.hypixelUi.enabled = false;
        config.chatTranslate.output.skyblock_npc_auto_translate = true;
        config.providerManager.routes.chat_output = "chat::model";
        config.providerManager.routes.other_translations = "ui::model";
        assertTrue(ConfigMigrationSupport.migrateHypixelSettings(
                JsonParser.parseString("{\"hypixelUi\":{\"target_language\":\"Japanese\"}}"), config));
        assertEquals("Japanese", config.hypixelUi.target_language);
        assertFalse(config.hypixelUi.enabled);
        assertTrue(config.chatTranslate.output.skyblock_npc_auto_translate);
        assertEquals("chat::model", config.providerManager.routes.hypixel);
        assertEquals(OtherTranslationsConfig.KeybindingMode.DISABLED, config.hypixelUi.keybinding.mode);
        assertFalse(config.hypixelUi.keybinding.binding.isBound());
    }

    @Test
    void olderConfigsUseChatLanguageAndExistingRouteWithNoRepeatedMigration() {
        var config = new ModConfig();
        config.chatTranslate.output.target_language = "Korean";
        config.providerManager.routes.scoreboard = "scoreboard::model";
        ConfigMigrationSupport.migrateHypixelSettings(JsonParser.parseString("{}"), config);
        assertEquals("Korean", config.hypixelUi.target_language);
        assertEquals("scoreboard::model", config.providerManager.routes.hypixel);
        config.providerManager.routes.hypixel = "";
        assertFalse(ConfigMigrationSupport.migrateHypixelSettings(JsonParser.parseString(
                "{\"hypixelUi\":{},\"providerManager\":{\"routes\":{\"hypixel\":\"\"}}}"), config));
        assertEquals("", config.providerManager.routes.hypixel);
    }

    @Test
    void missingHotkeyFieldsNormalizeWithoutChangingExistingBindings() {
        var config = new ModConfig().hypixelUi;
        config.keybinding.binding.code = 77;
        config.keybinding.refreshBinding = null;
        config.target_language = "  Chinese ";
        config.normalize();
        assertEquals("Chinese", config.target_language);
        assertEquals(77, config.keybinding.binding.code);
        assertNotNull(config.keybinding.refreshBinding);
    }
}
