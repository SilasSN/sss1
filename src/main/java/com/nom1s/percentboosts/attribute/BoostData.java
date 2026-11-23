package com.nom1s.percentboosts.attribute;

import com.mojang.serialization.Codec;
import com.nom1s.percentboosts.PercentBoosts;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server-authoritative permanent boost storage (data attachment survives player death). */
public final class BoostData {
    private BoostData() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, PercentBoosts.MOD_ID);
    public static final Supplier<AttachmentType<Map<String, Integer>>> BOOSTS = ATTACHMENTS.register(
        "player_boosts", () -> AttachmentType.<Map<String, Integer>>builder((Supplier<Map<String, Integer>>) HashMap::new)
            .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("values"))
            .copyOnDeath().build());

    public static final Map<String, Holder<Attribute>> AVAILABLE = new LinkedHashMap<>();
    static {
        // Same base attributes found in the uploaded version. Names are stable, non-localized IDs.
        AVAILABLE.put("max_health", Attributes.MAX_HEALTH);
        AVAILABLE.put("attack_damage", Attributes.ATTACK_DAMAGE);
        AVAILABLE.put("attack_speed", Attributes.ATTACK_SPEED);
        AVAILABLE.put("attack_knockback", Attributes.ATTACK_KNOCKBACK);
        AVAILABLE.put("knockback_resistance", Attributes.KNOCKBACK_RESISTANCE);
        AVAILABLE.put("armor", Attributes.ARMOR);
        AVAILABLE.put("armor_toughness", Attributes.ARMOR_TOUGHNESS);
        AVAILABLE.put("luck", Attributes.LUCK);
        AVAILABLE.put("movement_speed", Attributes.MOVEMENT_SPEED);
        AVAILABLE.put("flying_speed", Attributes.FLYING_SPEED);
        AVAILABLE.put("sneaking_speed", Attributes.SNEAKING_SPEED);
        AVAILABLE.put("jump_strength", Attributes.JUMP_STRENGTH);
        AVAILABLE.put("step_height", Attributes.STEP_HEIGHT);
        AVAILABLE.put("gravity", Attributes.GRAVITY);
        AVAILABLE.put("safe_fall_distance", Attributes.SAFE_FALL_DISTANCE);
        AVAILABLE.put("fall_damage_multiplier", Attributes.FALL_DAMAGE_MULTIPLIER);
        AVAILABLE.put("movement_efficiency", Attributes.MOVEMENT_EFFICIENCY);
        AVAILABLE.put("water_movement_efficiency", Attributes.WATER_MOVEMENT_EFFICIENCY);
        AVAILABLE.put("block_break_speed", Attributes.BLOCK_BREAK_SPEED);
        AVAILABLE.put("mining_efficiency", Attributes.MINING_EFFICIENCY);
        AVAILABLE.put("submerged_mining_speed", Attributes.SUBMERGED_MINING_SPEED);
        AVAILABLE.put("block_interaction_range", Attributes.BLOCK_INTERACTION_RANGE);
        AVAILABLE.put("entity_interaction_range", Attributes.ENTITY_INTERACTION_RANGE);
        AVAILABLE.put("scale", Attributes.SCALE);
        AVAILABLE.put("burning_time", Attributes.BURNING_TIME);
        AVAILABLE.put("explosion_knockback_resistance", Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
        AVAILABLE.put("sweeping_damage_ratio", Attributes.SWEEPING_DAMAGE_RATIO);
        AVAILABLE.put("max_absorption", Attributes.MAX_ABSORPTION);
        AVAILABLE.put("oxygen_bonus", Attributes.OXYGEN_BONUS);
        AVAILABLE.put("crit_damage", ModAttributes.CRIT_DAMAGE);
        AVAILABLE.put("xp_multiplier", ModAttributes.XP_MULTIPLIER);
        AVAILABLE.put("item_use_speed", ModAttributes.ITEM_USE_SPEED);
        AVAILABLE.put("projectile_speed", ModAttributes.PROJECTILE_SPEED);
        AVAILABLE.put("projectile_damage", ModAttributes.PROJECTILE_DAMAGE);
        AVAILABLE.put("damage_undead", ModAttributes.DAMAGE_UNDEAD);
        AVAILABLE.put("damage_bosses", ModAttributes.DAMAGE_BOSSES);
        AVAILABLE.put("enchant_cost", ModAttributes.ENCHANT_COST);
        AVAILABLE.put("repair_cost", ModAttributes.REPAIR_COST);
    }

    public static int get(Player player, String key) {
        return player.getData(BOOSTS).getOrDefault(key, 0);
    }

    public static void apply(Player player, String key, int delta) {
        if (!AVAILABLE.containsKey(key)) return;
        int next = Math.max(-99, Math.min(5000, get(player, key) + delta));
        set(player, key, next);
    }

    public static void set(Player player, String key, int amount) {
        if (player.level().isClientSide() || !AVAILABLE.containsKey(key)) return;
        Map<String, Integer> values = new HashMap<>(player.getData(BOOSTS));
        if (amount == 0) values.remove(key);
        else values.put(key, amount);
        player.setData(BOOSTS, values);
        reapply(player, key);
    }

    public static void reapplyAll(Player player) {
        if (player.level().isClientSide()) return;
        for (String key : AVAILABLE.keySet()) reapply(player, key);
    }

    public static void reapply(Player player, String key) {
        Holder<Attribute> attr = AVAILABLE.get(key);
        if (attr == null) return;
        AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) return;
        Identifier id = Identifier.fromNamespaceAndPath(PercentBoosts.MOD_ID, "boost_" + key);
        instance.removeModifier(id);
        int percentage = get(player, key);
        if (percentage != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, percentage / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
