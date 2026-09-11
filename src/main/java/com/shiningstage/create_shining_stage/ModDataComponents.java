package com.shiningstage.create_shining_stage;

import java.util.function.Supplier;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateShiningStage.MOD_ID);

    /** Microphone a speaker item was bound to by right-clicking it. Consumed by SpeakerBlock.setPlacedBy. */
    public static final Supplier<DataComponentType<GlobalPos>> BOUND_MIC =
        COMPONENTS.register("bound_mic", () -> DataComponentType.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC)
            .networkSynchronized(GlobalPos.STREAM_CODEC)
            .build());
}
