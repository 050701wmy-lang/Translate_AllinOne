package com.alexeys.translate_allinone.utils.translate;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ChatLineUpdateOrderingTest {
    @SuppressWarnings("unchecked")
    private static <T> Map<UUID, T> state(String name) throws Exception {
        var field = ChatOutputTranslateManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(null);
    }

    private static GuiMessage line(String text) {
        return new GuiMessage(0, Component.literal(text), null, null, null);
    }

    @Test
    void queuedFinalUsesLatestAnimationAndStreamingLine() throws Exception {
        Map<UUID, GuiMessage> lines = state("activeTranslationLines");
        Map<UUID, Long> generations = state("translationGenerations");
        UUID id = UUID.randomUUID();
        List<Runnable> queue = new ArrayList<>();
        List<GuiMessage> seen = new ArrayList<>();
        GuiMessage animated = line("animated");
        GuiMessage streamed = line("streamed");
        try {
            lines.put(id, line("initial"));
            generations.put(id, 1L);
            ChatOutputTranslateManager.dispatchChatLineUpdate(id, 1L, queue::add, current -> {
                seen.add(current);
                lines.put(id, streamed);
            });
            ChatOutputTranslateManager.dispatchChatLineUpdate(id, 1L, queue::add, current -> {
                seen.add(current);
                lines.remove(id);
                generations.remove(id);
            });
            ChatOutputTranslateManager.dispatchChatLineUpdate(id, 1L, queue::add,
                    current -> fail("Late preview must not overwrite final text"));
            lines.put(id, animated);
            queue.forEach(Runnable::run);
            assertEquals(List.of(animated, streamed), seen);
            assertFalse(generations.containsKey(id));
        } finally {
            lines.remove(id);
            generations.remove(id);
        }
    }

    @Test
    void queuedOldGenerationCannotFinishNewRequest() throws Exception {
        Map<UUID, GuiMessage> lines = state("activeTranslationLines");
        Map<UUID, Long> generations = state("translationGenerations");
        UUID id = UUID.randomUUID();
        List<Runnable> queue = new ArrayList<>();
        try {
            lines.put(id, line("initial"));
            generations.put(id, 1L);
            ChatOutputTranslateManager.dispatchChatLineUpdate(id, 1L, queue::add,
                    current -> fail("Stale generation must be ignored"));
            generations.put(id, 2L);
            queue.forEach(Runnable::run);
            assertEquals(2L, generations.get(id));
        } finally {
            lines.remove(id);
            generations.remove(id);
        }
    }
}
