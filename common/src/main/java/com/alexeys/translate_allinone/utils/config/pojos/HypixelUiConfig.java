package com.alexeys.translate_allinone.utils.config.pojos;

public class HypixelUiConfig extends OtherTranslationsConfig {
    // Retain the serialized name for existing installations; language and keys now cover the Hypixel page.
    public HypixelUiConfig() {
        enabled_screen_translation = true;
        keybinding.mode = KeybindingMode.DISABLED;
    }

    public void normalize() {
        if (target_language == null || target_language.isBlank()) target_language = DEFAULT_TARGET_LANGUAGE;
        target_language = target_language.trim();
        if (keybinding == null) keybinding = new KeybindingConfig();
        if (keybinding.mode == null) keybinding.mode = KeybindingMode.DISABLED;
        if (keybinding.binding == null) keybinding.binding = new InputBindingConfig();
        if (keybinding.refreshBinding == null) keybinding.refreshBinding = new InputBindingConfig();
    }
}
