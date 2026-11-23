package com.nom1s.percentboosts.component;

import com.mojang.serialization.Codec;
import com.nom1s.percentboosts.PercentBoosts;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModComponents {
    private ModComponents() {}

    public static final DeferredRegister.DataComponents REGISTRY =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PercentBoosts.MOD_ID);

    // Same IDs as the original mod. This helps keep percent and applier stack data readable.
    public static final Supplier<DataComponentType<Integer>> PERCENTAGE_VALUE = REGISTRY.registerComponentType(
        "percentage_value", builder -> builder.persistent(Codec.INT));
    public static final Supplier<DataComponentType<Integer>> APPLIER_BALANCE = REGISTRY.registerComponentType(
        "applier_balance", builder -> builder.persistent(Codec.INT));
}
