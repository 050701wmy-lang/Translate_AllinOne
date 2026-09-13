package com.alexeys.translate_allinone.registration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class ConfigMigrationSupport {
    static boolean migrateHypixelSettings(JsonElement rawConfig,
            com.alexeys.translate_allinone.utils.config.ModConfig config) {
        if (rawConfig == null || !rawConfig.isJsonObject() || config == null) return false;
        JsonObject root = rawConfig.getAsJsonObject();
        boolean changed = false;
        if (config.hypixelUi == null) config.hypixelUi = new com.alexeys.translate_allinone.utils.config.pojos.HypixelUiConfig();
        if (!root.has("hypixelUi") && config.chatTranslate != null && config.chatTranslate.output != null) {
            config.hypixelUi.target_language = config.chatTranslate.output.target_language;
            changed = true;
        }
        JsonObject provider = root.has("providerManager") && root.get("providerManager").isJsonObject()
                ? root.getAsJsonObject("providerManager") : new JsonObject();
        JsonObject routes = provider.has("routes") && provider.get("routes").isJsonObject()
                ? provider.getAsJsonObject("routes") : new JsonObject();
        if (!routes.has("hypixel") && config.providerManager != null && config.providerManager.routes != null) {
            var selected = config.providerManager.routes;
            selected.hypixel = selected.chat_output != null && !selected.chat_output.isBlank()
                    ? selected.chat_output : selected.other_translations != null && !selected.other_translations.isBlank()
                        ? selected.other_translations : selected.scoreboard;
            changed = true;
        }
        config.hypixelUi.normalize();
        return changed;
    }

    private ConfigMigrationSupport() {
    }

    static boolean hasDeprecatedWynnItemCompatibilityConfig(JsonElement rawConfig) {
        JsonObject itemTranslateObject = getItemTranslateObject(rawConfig);
        if (itemTranslateObject != null && itemTranslateObject.has("wynn_item_compatibility")) {
            return true;
        }
        JsonObject wynnCraftObject = getWynnCraftObject(rawConfig);
        return wynnCraftObject != null && wynnCraftObject.has("wynn_item_compatibility");
    }

    private static JsonObject getWynnCraftObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonElement wynnCraftElement = rawConfig.getAsJsonObject().get("wynnCraft");
        if (wynnCraftElement == null || !wynnCraftElement.isJsonObject()) {
            return null;
        }
        return wynnCraftElement.getAsJsonObject();
    }

    static JsonObject getItemTranslateObject(JsonElement rawConfig) {
        if (rawConfig == null || !rawConfig.isJsonObject()) {
            return null;
        }
        JsonObject root = rawConfig.getAsJsonObject();
        JsonElement itemTranslateElement = root.get("itemTranslate");
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            itemTranslateElement = root.get("itemTranslateConfig");
        }
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            itemTranslateElement = root.get("ItemTranslateConfig");
        }
        if (itemTranslateElement == null || !itemTranslateElement.isJsonObject()) {
            return null;
        }
        return itemTranslateElement.getAsJsonObject();
    }
}
