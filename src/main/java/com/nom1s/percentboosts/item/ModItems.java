package com.nom1s.percentboosts.item;

import com.nom1s.percentboosts.PercentBoosts;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PercentBoosts.MOD_ID);
    public static final DeferredItem<PercentageItem> PERCENTAGE = ITEMS.registerItem(
        "percentage", PercentageItem::new, props -> props.stacksTo(64));
    public static final DeferredItem<BoostApplierItem> BOOST_APPLIER = ITEMS.registerItem(
        "boost_applier", BoostApplierItem::new, props -> props.stacksTo(1));

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PercentBoosts.MOD_ID);
    public static final Supplier<CreativeModeTab> TAB = TABS.register("percentboosts_tab", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.percentboosts_tab"))
            .icon(() -> new ItemStack(BOOST_APPLIER.get()))
            .displayItems((parameters, output) -> {
                output.accept(BOOST_APPLIER.get());
                output.accept(PERCENTAGE.get());
            }).build());
}
