package com.nom1s.percentboosts.attribute;

import com.nom1s.percentboosts.PercentBoosts;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom attributes reconstructed from the 1.21.1 bytecode, same IDs and base values. */
public final class ModAttributes {
    private ModAttributes() {}
    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, PercentBoosts.MOD_ID);

    private static DeferredHolder<Attribute, Attribute> attribute(String id, double base, double min, double max) {
        return ATTRIBUTES.register(id, () ->
            new RangedAttribute("attribute.percentboosts." + id, base, min, max).setSyncable(true));
    }

    public static final DeferredHolder<Attribute, Attribute> CRIT_DAMAGE = attribute("crit_damage", 1.5, 1, 10);
    public static final DeferredHolder<Attribute, Attribute> XP_MULTIPLIER = attribute("xp_multiplier", 1, 0, 20);
    public static final DeferredHolder<Attribute, Attribute> ITEM_USE_SPEED = attribute("item_use_speed", 1, .1, 5);
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_SPEED = attribute("projectile_speed", 1, .1, 5);
    public static final DeferredHolder<Attribute, Attribute> PROJECTILE_DAMAGE = attribute("projectile_damage", 1, 0, 10);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_UNDEAD = attribute("damage_undead", 1, 0, 10);
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BOSSES = attribute("damage_bosses", 1, 0, 10);
    public static final DeferredHolder<Attribute, Attribute> ENCHANT_COST = attribute("enchant_cost", 1, .1, 5);
    public static final DeferredHolder<Attribute, Attribute> REPAIR_COST = attribute("repair_cost", 1, .1, 5);

    public static final java.util.List<Holder<Attribute>> ALL = java.util.List.of(
        CRIT_DAMAGE, XP_MULTIPLIER, ITEM_USE_SPEED, PROJECTILE_SPEED, PROJECTILE_DAMAGE,
        DAMAGE_UNDEAD, DAMAGE_BOSSES, ENCHANT_COST, REPAIR_COST);
}
