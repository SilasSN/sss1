package com.example.townfolk;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(Townfolk.ID)
public final class Townfolk {
    public static final String ID = "townfolk";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredItem<TownHallKitItem> TOWN_HALL_KIT =
            ITEMS.registerItem("town_hall_kit", TownHallKitItem::new, p -> p.stacksTo(1));
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final Supplier<CreativeModeTab> TAB = TABS.register("townfolk_tab", () ->
            CreativeModeTab.builder()
                    .title(Component.literal("Townfolk"))
                    .icon(() -> new ItemStack(TOWN_HALL_KIT.get()))
                    .displayItems((parameters, output) -> output.accept(TOWN_HALL_KIT.get()))
                    .build());

    public Townfolk(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        NeoForge.EVENT_BUS.addListener(WorkerAi::onTick);
        NeoForge.EVENT_BUS.addListener(TownController::onBell);
        NeoForge.EVENT_BUS.addListener(TownController::onDeath);
        NeoForge.EVENT_BUS.addListener(TownController::register);
    }
}
