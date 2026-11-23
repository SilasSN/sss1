package com.nom1s.percentboosts.event;

import com.nom1s.percentboosts.attribute.BoostData;
import com.nom1s.percentboosts.attribute.ModAttributes;
import com.nom1s.percentboosts.item.PercentageItem;
import com.nom1s.percentboosts.logic.DropMath;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/** Direct reconstruction of gameplay rules from the submitted mod's bytecode. */
public final class GameplayEvents {
    private GameplayEvents() {}

    public static void onMobDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob instanceof Player || mob.level().isClientSide()
            || !(event.getSource().getEntity() instanceof Player)) return;

        var rng = mob.getRandom();
        if (rng.nextFloat() > .25f) return;
        double attack = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
            ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0;
        double armor = mob.getAttributes().hasAttribute(Attributes.ARMOR)
            ? mob.getAttributeValue(Attributes.ARMOR) : 0.0;
        double strength = DropMath.strength(mob.getMaxHealth(), attack, armor,
            !mob.getType().getCategory().isFriendly());
        // The RandomSource is not a java.util.random.RandomGenerator in every MC release.
        // The overload below uses exactly the same nextDouble/nextInt rules.
        double tier = rng.nextDouble();
        int pct;
        if (tier < .72) pct = 1 + rng.nextInt(10);
        else if (tier < .92) pct = 11 + rng.nextInt(20);
        else if (tier < .985) pct = 31 + rng.nextInt(40);
        else pct = 71 + rng.nextInt(30);
        if (strength >= 120) pct += 15 + rng.nextInt(20);
        else if (strength >= 60) pct += 5 + rng.nextInt(13);
        else if (strength >= 30) pct += rng.nextInt(8);
        pct = Math.max(1, Math.min(100, pct));
        if (mob.level() instanceof ServerLevel serverLevel) {
            mob.spawnAtLocation(serverLevel, PercentageItem.create(pct));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BoostData.reapplyAll(event.getEntity());
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BoostData.reapplyAll(event.getEntity());
    }

    private static double attribute(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder) {
        AttributeInstance instance = player.getAttribute(holder);
        return instance == null ? 1 : instance.getValue();
    }

    public static void onXp(PlayerXpEvent.XpChange event) {
        double multiplier = attribute(event.getEntity(), ModAttributes.XP_MULTIPLIER);
        if (multiplier != 1) event.setAmount(Math.max(0, (int) Math.round(event.getAmount() * multiplier)));
    }

    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker.level().isClientSide()) return;
        // Since Minecraft 26.3 no longer exposes AbstractArrow.getBaseDamage(),
        // apply the owner's arrow damage multiplier at the actual damage event.
        // This preserves vanilla arrow damage, bow enchantments and critical hits.
        if (event.getSource().getDirectEntity() instanceof AbstractArrow) {
            double projectileDamage = attribute(attacker, ModAttributes.PROJECTILE_DAMAGE);
            if (projectileDamage != 1) {
                event.setNewDamage((float) (event.getNewDamage() * projectileDamage));
            }
        }
        double crit = attribute(attacker, ModAttributes.CRIT_DAMAGE);
        if (crit > 1.5) {
            double chance = Math.min(.8, .25 + (crit - 1.5) * .4);
            if (attacker.getRandom().nextDouble() < chance)
                event.setNewDamage((float) (event.getNewDamage() * crit));
        }
        if (event.getEntity().typeHolder().is(EntityTypeTags.UNDEAD))
            event.setNewDamage((float) (event.getNewDamage() * attribute(attacker, ModAttributes.DAMAGE_UNDEAD)));
        LivingEntity target = event.getEntity();
        EntityType<?> type = target.getType();
        if (target.getMaxHealth() >= 100 || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WITHER
            || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN)
            event.setNewDamage((float) (event.getNewDamage() * attribute(attacker, ModAttributes.DAMAGE_BOSSES)));
    }

    public static void onItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        double speed = attribute(player, ModAttributes.ITEM_USE_SPEED);
        if (speed > 1 && event.getDuration() > 0)
            event.setDuration(Math.max(0, event.getDuration() - (int) Math.max(1, Math.round((speed - 1) * 3))));
    }

    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Projectile projectile)
            || !(projectile.getOwner() instanceof Player player)) return;
        double speed = attribute(player, ModAttributes.PROJECTILE_SPEED);
        if (speed != 1) entity.setDeltaMovement(entity.getDeltaMovement().scale(speed));
    }

    public static void onAnvil(AnvilUpdateEvent event) {
        if (event.getPlayer() == null || event.getXpCost() <= 0) return;
        Player player = event.getPlayer();
        boolean repair = !event.getLeft().isEmpty() && !event.getRight().isEmpty()
            && event.getLeft().isDamageableItem()
            && net.minecraft.world.item.ItemStack.isSameItem(event.getLeft(), event.getRight());
        double factor = attribute(player, repair ? ModAttributes.REPAIR_COST : ModAttributes.ENCHANT_COST);
        if (factor != 1) event.setXpCost((int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.round(event.getXpCost() * factor))));
    }
}
