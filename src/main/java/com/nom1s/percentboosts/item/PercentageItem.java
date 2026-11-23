package com.nom1s.percentboosts.item;

import com.nom1s.percentboosts.component.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class PercentageItem extends Item {
    public PercentageItem(Properties properties) { super(properties); }

    public static ItemStack create(int value) {
        ItemStack stack = new ItemStack(ModItems.PERCENTAGE.get());
        int percent = Math.max(1, Math.min(100, value));
        stack.set(ModComponents.PERCENTAGE_VALUE.get(), percent);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("" + percent + "% Percent"));
        return stack;
    }

    public static int value(ItemStack stack) {
        return stack.getOrDefault(ModComponents.PERCENTAGE_VALUE.get(), 1);
    }
}
