package com.nom1s.percentboosts.network;

import com.nom1s.percentboosts.PercentBoosts;
import com.nom1s.percentboosts.attribute.BoostData;
import com.nom1s.percentboosts.item.BoostApplierItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** All changes and balance checks are performed server-side; client cannot forge points. */
public record ApplyBoostPacket(String key, int amount, boolean reset, boolean forItem) implements CustomPacketPayload {
    public static final Type<ApplyBoostPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(PercentBoosts.MOD_ID, "apply_boost"));
    public static final StreamCodec<ByteBuf, ApplyBoostPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ApplyBoostPacket::key,
        ByteBufCodecs.VAR_INT, ApplyBoostPacket::amount,
        ByteBufCodecs.BOOL, ApplyBoostPacket::reset,
        ByteBufCodecs.BOOL, ApplyBoostPacket::forItem,
        ApplyBoostPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ApplyBoostPacket request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) handleOnServer(player, request);
    }

    private static void handleOnServer(ServerPlayer player, ApplyBoostPacket request) {
        // Only existing, registered attribute IDs are accepted (never arbitrary NBT keys).
        Holder<Attribute> holder = BoostData.AVAILABLE.get(request.key);
        if (holder == null || request.key.length() > 64) return;
        ItemStack applier = findApplier(player);
        if (applier.isEmpty()) return;
        ItemStack target = findTarget(player);
        if (request.forItem && (target.isEmpty() || target.getCount() != 1)) return;
        if (request.forItem && !holder.unwrapKey().map(k -> "minecraft".equals(k.identifier().getNamespace())).orElse(false)) {
            message(player, "Custom attributes can only be applied to players.");
            return;
        }
        if (request.reset()) {
            if (request.forItem) replaceItemModifier(target, holder, 0, true);
            else BoostData.set(player, request.key, 0);
            message(player, "Reset " + request.key);
            return;
        }
        // Avoid zero-value spam, malicious Integer.MIN_VALUE and pathological amounts.
        if (request.amount() == 0 || request.amount() < -5000 || request.amount() > 5000) return;
        int cost = Math.abs(request.amount());
        int balance = BoostApplierItem.balance(applier);
        if (cost > balance) {
            message(player, "Not enough percent points (have " + balance + ").");
            return;
        }
        if (request.forItem) replaceItemModifier(target, holder, request.amount() * 2, false);
        else BoostData.apply(player, request.key, request.amount());
        BoostApplierItem.setBalance(applier, balance - cost);
        message(player, "Applied " + (request.forItem ? request.amount() * 2 : request.amount())
            + "% " + request.key + "; remaining " + BoostApplierItem.balance(applier));
    }

    private static void message(ServerPlayer player, String s) {
        player.sendOverlayMessage(Component.literal(s));
    }

    private static ItemStack findApplier(ServerPlayer player) {
        ItemStack a = player.getMainHandItem();
        if (a.getItem() instanceof BoostApplierItem) return a;
        ItemStack b = player.getOffhandItem();
        return b.getItem() instanceof BoostApplierItem ? b : ItemStack.EMPTY;
    }

    private static ItemStack findTarget(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BoostApplierItem) return player.getOffhandItem();
        if (player.getOffhandItem().getItem() instanceof BoostApplierItem) return player.getMainHandItem();
        return ItemStack.EMPTY;
    }

    private static void replaceItemModifier(ItemStack target, Holder<Attribute> attribute, int pct, boolean reset) {
        Identifier id = Identifier.fromNamespaceAndPath(PercentBoosts.MOD_ID, "boost_item_"
            + attribute.unwrapKey().orElseThrow().identifier().getPath());
        ItemAttributeModifiers before = target.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        int previous = 0;
        for (ItemAttributeModifiers.Entry e : before.modifiers()) {
            if (e.modifier().id().equals(id)) previous = (int) Math.round(e.modifier().amount() * 100.0);
            else builder.add(e.attribute(), e.modifier(), e.slot(), e.display());
        }
        int combined = reset ? 0 : Math.max(-98, Math.min(10000, previous + pct));
        if (combined != 0) {
            builder.add(attribute, new AttributeModifier(id, combined / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.ANY);
        }
        target.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }
}
