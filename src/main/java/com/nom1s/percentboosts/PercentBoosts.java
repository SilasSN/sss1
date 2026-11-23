package com.nom1s.percentboosts;

import com.nom1s.percentboosts.attribute.BoostData;
import com.nom1s.percentboosts.attribute.ModAttributes;
import com.nom1s.percentboosts.component.ModComponents;
import com.nom1s.percentboosts.event.GameplayEvents;
import com.nom1s.percentboosts.item.ModItems;
import com.nom1s.percentboosts.network.ApplyBoostPacket;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(PercentBoosts.MOD_ID)
public final class PercentBoosts {
    public static final String MOD_ID = "percentboosts";

    public PercentBoosts(IEventBus modBus) {
        ModComponents.REGISTRY.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModAttributes.ATTRIBUTES.register(modBus);
        BoostData.ATTACHMENTS.register(modBus);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(GameplayEvents::onMobDeath);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onXp);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onItemTick);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onProjectileSpawn);
        NeoForge.EVENT_BUS.addListener(GameplayEvents::onAnvil);
    }

    private void registerAttributes(EntityAttributeModificationEvent event) {
        ModAttributes.ALL.forEach(attr -> event.add(EntityTypes.PLAYER, attr));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ApplyBoostPacket.TYPE,
            ApplyBoostPacket.STREAM_CODEC, ApplyBoostPacket::handle);
    }
}
