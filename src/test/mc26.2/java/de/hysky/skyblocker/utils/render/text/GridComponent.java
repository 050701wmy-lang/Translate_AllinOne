package de.hysky.skyblocker.utils.render.text;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import java.util.List;

/** Test-only model of the external public record API: no text visitor and no serialization. */
public final class GridComponent {
    public record Contents(String group, List<Component> components) implements ComponentContents {
        @Override
        public MapCodec<? extends ComponentContents> codec() {
            throw new UnsupportedOperationException("Grid payloads are render-only");
        }
    }
}
