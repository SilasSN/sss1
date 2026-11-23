package com.example.townfolk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side jobs for real, loaded blocks only; no resource generation.
 * Uses vanilla Villagers, so no custom client entity renderer is required.
 */
public final class WorkerAi {
    private WorkerAi() {}
    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final int JOB_RADIUS = 22;
    private static final int HALL_SAFE_RADIUS = 6;
    private record Target(BlockPos block, BlockPos stand, int acquiredAt) {}

    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        CompoundTag data = villager.getPersistentData();
        String role = data.getStringOr("townfolk_role", "");
        if (role.isEmpty() || !villager.isAlive() || villager.isBaby()) return;
        if (villager.tickCount % 20 != 0) return;
        BlockPos home = new BlockPos(data.getIntOr("townfolk_x", 0),
                data.getIntOr("townfolk_y", 0), data.getIntOr("townfolk_z", 0));
        // Destroying the town controller bell stops work and feeding.
        if (!TownManager.isHall(level, home)) return;
        updateHunger(level, villager, home, data);
        if (villager.distanceToSqr(home.getX()+0.5, home.getY()+1, home.getZ()+0.5) > 30 * 30) {
            villager.getNavigation().moveTo(home.getX()+0.5, home.getY()+1, home.getZ()-2.5, 1.1);
            return;
        }
        int workerLevel = Math.max(1, Math.min(10, data.getIntOr("tf_worker_level", 1)));
        int hunger = Math.max(0, Math.min(100, data.getIntOr("tf_food", 100)));
        if (role.equals("guard")) {
            guard(level, villager, home, workerLevel, hunger);
            return;
        }
        if (!role.equals("lumberjack") && !role.equals("miner") && !role.equals("farmer")) return;
        if (hunger <= 0) return; // No food = no extraction or harvesting.
        long next = data.getLongOr("tf_next_work_tick", 0L);
        if (level.getGameTime() < next) return;
        work(level, villager, home, role, workerLevel, hunger);
    }

    /** Hunger is saved on each individual NPC and is fed from ANY warehouse tier. */
    private static void updateHunger(ServerLevel level, Villager villager, BlockPos home, CompoundTag data) {
        long now = level.getGameTime();
        long previous = data.getLongOr("tf_last_food_tick", now);
        if (previous > now) previous = now;
        if (now - previous < 1200) return;
        int periods = (int) Math.min(10L, (now - previous) / 1200L);
        int hunger = Math.max(0, data.getIntOr("tf_food", 100) - 10 * periods);
        if (hunger <= 50 && TownStorage.consumeFood(level, home)) hunger = Math.min(100, hunger + 70);
        data.putInt("tf_food", hunger);
        data.putLong("tf_last_food_tick", now);
    }

    private static void guard(ServerLevel level, Villager villager, BlockPos home, int workerLevel, int hunger) {
        List<Monster> targets = level.getEntitiesOfClass(Monster.class,
                villager.getBoundingBox().inflate(12.0),
                mob -> mob.isAlive() && mob.distanceToSqr(home.getX()+0.5, home.getY()+1,
                        home.getZ()+0.5) <= JOB_RADIUS * JOB_RADIUS);
        Monster closest = null;
        double best = Double.MAX_VALUE;
        for (Monster mob : targets) {
            double distance = villager.distanceToSqr(mob);
            if (distance < best) { best = distance; closest = mob; }
        }
        if (closest != null) {
            villager.getNavigation().moveTo(closest, 1.25);
            if (best < 7.0) {
                villager.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                // Guards attack only hostile Monster entities, not animals, villagers or players.
                float damage = 4.0f + 0.65f * (workerLevel - 1);
                if (hunger <= 0) damage *= 0.5f;
                closest.hurtServer(level, level.damageSources().mobAttack(villager), damage);
            }
        } else if (villager.distanceToSqr(home.getX(), home.getY()+1, home.getZ()) > 14*14) {
            villager.getNavigation().moveTo(home.getX()+0.5, home.getY()+1, home.getZ()-3.5, 0.95);
        }
    }

    private static void work(ServerLevel level, Villager villager, BlockPos home,
                             String role, int workerLevel, int hunger) {
        UUID id = villager.getUUID();
        Target target = TARGETS.get(id);
        if (target != null && (villager.tickCount - target.acquiredAt() > 240
                || !level.isLoaded(target.block())
                || !canWork(level, target.block(), home, role)
                || !isStandingSpot(level, target.stand()))) {
            TARGETS.remove(id);
            target = null;
        }
        if (target == null) {
            // Search only occasionally to keep server load predictable.
            if (villager.tickCount % 100 != 0) return;
            target = findTask(level, villager, home, role, workerLevel);
            if (target == null) return;
            target = new Target(target.block(), target.stand(), villager.tickCount);
            TARGETS.put(id, target);
        }
        if (villager.distanceToSqr(target.stand().getX()+0.5, target.stand().getY(),
                target.stand().getZ()+0.5) > 3.4 * 3.4) {
            villager.getNavigation().moveTo(target.stand().getX()+0.5, target.stand().getY(),
                    target.stand().getZ()+0.5, 1.0);
            return;
        }
        if (villager.distanceToSqr(target.block().getX()+0.5, target.block().getY()+0.5,
                target.block().getZ()+0.5) < 4.2 * 4.2) {
            collect(level, villager, home, role, target.block());
            TARGETS.remove(id);
            // Higher-level workers complete tasks faster. Hungry workers are slower.
            int cooldown = Math.max(20, 110 - (workerLevel - 1) * 10);
            if (hunger <= 30) cooldown += 80;
            villager.getPersistentData().putLong("tf_next_work_tick", level.getGameTime() + cooldown);
        }
    }

    private static Target findTask(ServerLevel level, Villager villager, BlockPos home,
                                   String role, int workerLevel) {
        Target nearest = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos searchCenter = villager.blockPosition();
        // Increasing worker level expands the actual horizontal search radius (15..22).
        int searchRadius = Math.min(JOB_RADIUS, 14 + workerLevel);
        // Sample loaded blocks, near worker and town, without loading new chunks.
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                // Use a cheap circle cutoff and avoid the protected hall footprint.
                if (dx*dx + dz*dz > searchRadius*searchRadius) continue;
                for (int dy = -5; dy <= 7; dy++) {
                    BlockPos p = searchCenter.offset(dx, dy, dz);
                    int fromTownX = p.getX() - home.getX();
                    int fromTownZ = p.getZ() - home.getZ();
                    if (fromTownX * fromTownX + fromTownZ * fromTownZ > searchRadius * searchRadius) continue;
                    if (!level.isLoaded(p) || !canWork(level, p, home, role)) continue;
                    BlockPos stand = findStand(level, p, villager.blockPosition());
                    if (stand == null) continue;
                    double distance = villager.distanceToSqr(stand.getX()+0.5, stand.getY(), stand.getZ()+0.5);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nearest = new Target(p.immutable(), stand.immutable(), villager.tickCount);
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean canWork(ServerLevel level, BlockPos pos, BlockPos home, String role) {
        if (pos.getY() < home.getY() - 8 || pos.getY() > home.getY() + 10) return false;
        if (Math.abs(pos.getX() - home.getX()) > JOB_RADIUS
                || Math.abs(pos.getZ() - home.getZ()) > JOB_RADIUS) return false;
        if (Math.abs(pos.getX() - home.getX()) <= HALL_SAFE_RADIUS
                && Math.abs(pos.getZ() - home.getZ()) <= HALL_SAFE_RADIUS) return false;
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || s.hasBlockEntity()) return false;
        if (role.equals("lumberjack")) return s.is(BlockTags.LOGS);
        if (role.equals("farmer")) return s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
        if (role.equals("miner")) {
            String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            // Stone and natural ores ONLY: do not mine utility blocks, containers or houses.
            return path.equals("stone") || path.equals("deepslate")
                    || path.equals("andesite") || path.equals("diorite")
                    || path.equals("granite") || path.equals("tuff")
                    || path.endsWith("_ore") || path.equals("ancient_debris")
                    // A small dedicated quarry area lets miners dig real topsoil to expose stone.
                    || ((path.equals("dirt") || path.equals("grass_block")
                        || path.equals("coarse_dirt") || path.equals("gravel"))
                        && pos.getX() >= home.getX()+8 && pos.getX() <= home.getX()+14
                        && pos.getZ() >= home.getZ()+8 && pos.getZ() <= home.getZ()+14
                        && pos.getY() >= home.getY()-6 && pos.getY() <= home.getY()+1);
        }
        return false;
    }

    private static boolean isStandingSpot(ServerLevel level, BlockPos p) {
        return level.isLoaded(p) && level.getBlockState(p).isAir()
                && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).isSolid();
    }

    private static BlockPos findStand(ServerLevel level, BlockPos p, BlockPos from) {
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = p.relative(direction);
            // Try level with target, one block below and above.
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos stand = adjacent.offset(0, dy, 0);
                if (!isStandingSpot(level, stand)) continue;
                double d = stand.distSqr(from);
                if (d < score) { score = d; best = stand; }
            }
        }
        return best;
    }

    private static void collect(ServerLevel level, Villager worker, BlockPos home, String role, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!canWork(level, pos, home, role)) return;
        ItemStack tool = worker.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        if (tool.isEmpty()) tool = new ItemStack(role.equals("lumberjack") ? Items.IRON_AXE
                : role.equals("miner") ? Items.IRON_PICKAXE : Items.IRON_HOE);
        List<ItemStack> loot = Block.getDrops(s, level, pos, level.getBlockEntity(pos), worker, tool);
        // Replant crops using an *actual seed item from the harvest* when possible.
        boolean replant = role.equals("farmer") && s.getBlock() instanceof CropBlock;
        ItemStack seed = ItemStack.EMPTY;
        if (replant) {
            String cropId = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            seed = switch (cropId) {
                case "wheat" -> new ItemStack(Items.WHEAT_SEEDS);
                case "beetroots" -> new ItemStack(Items.BEETROOT_SEEDS);
                case "carrots" -> new ItemStack(Items.CARROT);
                case "potatoes" -> new ItemStack(Items.POTATO);
                default -> ItemStack.EMPTY;
            };
        }
        // Capture a single seed from genuine block loot before storing the rest.
        boolean seedAvailable = false;
        if (!seed.isEmpty()) {
            for (ItemStack drop : loot) {
                if (!drop.isEmpty() && ItemStack.isSameItem(drop, seed)) {
                    drop.shrink(1);
                    seedAvailable = true;
                    break;
                }
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(s));
        if (replant && seedAvailable && s.getBlock() instanceof CropBlock crop) {
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        }
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) continue;
            TownStorage.insert(level, home, stack); // all upgraded storage inventories
            if (!stack.isEmpty()) Block.popResource(level, pos, stack); // overflow never disappears
        }
        worker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
    }
}
