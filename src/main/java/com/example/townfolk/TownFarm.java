package com.example.townfolk;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Each farmer claims one separate 5x5 garden on real, flat existing soil.
 * The center is an actual water source paid for with one stored water bucket.
 * Farmers till and plant one block per work cooldown, then harvest vanilla crops.
 * No land is filled, no crops are spawned from thin air and no chunks are loaded.
 */
public final class TownFarm {
    private TownFarm() {}

    public static final int RADIUS = 2;

    public enum TaskType { WATER, TILL, PLANT, HARVEST }
    public record Task(BlockPos pos, TaskType kind, Item seed, Block crop) {}

    private record Seed(Item seed, Block crop) {}
    private static final Seed[] SEEDS = {
            new Seed(Items.WHEAT_SEEDS, Blocks.WHEAT),
            new Seed(Items.CARROT, Blocks.CARROTS),
            new Seed(Items.POTATO, Blocks.POTATOES),
            new Seed(Items.BEETROOT_SEEDS, Blocks.BEETROOTS)
    };

    /** Claim an intact, reasonably level patch; never build onto structures or existing trees. */
    public static BlockPos locate(ServerLevel level, BlockPos home, Villager farmer) {
        if (!TownManager.isHall(level, home)) return null;
        CompoundTag data = farmer.getPersistentData();
        if (data.getBooleanOr("tf_farm_claimed", false)) {
            return new BlockPos(data.getIntOr("tf_farm_x", 0),
                    data.getIntOr("tf_farm_y", home.getY() - 1),
                    data.getIntOr("tf_farm_z", 0));
        }
        // Every role farmer receives its own plot. Existing workers' saved claims
        // are checked before selecting an unoccupied candidate.
        List<BlockPos> occupied = new ArrayList<>();
        for (Villager v : TownManager.loadedWorkers(level, home)) {
            if (v.getUUID().equals(farmer.getUUID())) continue;
            CompoundTag other = v.getPersistentData();
            if (!other.getBooleanOr("tf_farm_claimed", false)) continue;
            occupied.add(new BlockPos(other.getIntOr("tf_farm_x", 0),
                    other.getIntOr("tf_farm_y", home.getY()-1),
                    other.getIntOr("tf_farm_z", 0)));
        }
        int[][] centers = { {-13, 0}, {-13, -9}, {-13, 9}, {13, -9},
                {13, 0}, {0, 13}, {0, -13}, {-9, 13}, {9, -13},
                {-19, -7}, {-19, 7}, {19, -7}, {19, 7}, {-7, -19},
                {7, -19}, {-7, 19}, {7, 19}, {-19, 0}, {19, 0} };
        int groundY = home.getY() - 1;
        for (int[] offset : centers) {
            BlockPos center = new BlockPos(home.getX() + offset[0], groundY,
                    home.getZ() + offset[1]);
            boolean overlaps = occupied.stream().anyMatch(other ->
                    Math.abs(other.getX()-center.getX()) <= RADIUS*2
                            && Math.abs(other.getZ()-center.getZ()) <= RADIUS*2);
            if (overlaps || !canClaim(level, home, center)) continue;
            data.putBoolean("tf_farm_claimed", true);
            data.putInt("tf_farm_x", center.getX());
            data.putInt("tf_farm_y", center.getY());
            data.putInt("tf_farm_z", center.getZ());
            return center;
        }
        return null;
    }

    private static boolean canClaim(ServerLevel level, BlockPos home, BlockPos center) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                BlockPos ground = center.offset(x, 0, z);
                if (!level.isLoaded(ground) || !level.isLoaded(ground.above())
                        || !level.isLoaded(ground.above(2))) return false;
                if (Math.abs(ground.getX()-home.getX()) <= 6
                        && Math.abs(ground.getZ()-home.getZ()) <= 6) return false;
                BlockState soil = level.getBlockState(ground);
                BlockState crop = level.getBlockState(ground.above());
                if (x == 0 && z == 0) {
                    if (!isConvertibleSoil(soil) && !soil.is(Blocks.WATER)) return false;
                    if (!crop.isAir()) return false;
                } else {
                    if (!isConvertibleSoil(soil)) return false;
                    if (!crop.isAir() && !(crop.getBlock() instanceof CropBlock)) return false;
                }
                if (!level.getBlockState(ground.above(2)).isAir()) return false;
            }
        }
        return true;
    }

    private static boolean isConvertibleSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.FARMLAND);
    }

    /** Determine what productive operation is possible; only uses actual world blocks/items. */
    public static Task nextTask(ServerLevel level, BlockPos home, BlockPos center, BlockPos from) {
        if (!level.isLoaded(center)) return null;
        BlockState centerState = level.getBlockState(center);
        if (!centerState.is(Blocks.WATER)) {
            if (isConvertibleSoil(centerState) && level.getBlockState(center.above()).isAir()
                    && TownStorage.hasItem(level, home, Items.WATER_BUCKET)) {
                return new Task(center, TaskType.WATER, Items.AIR, Blocks.AIR);
            }
            return null;
        }
        List<Task> harvests = new ArrayList<>();
        List<Task> tilling = new ArrayList<>();
        List<Task> planting = new ArrayList<>();
        Seed seed = availableSeed(level, home);
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos ground = center.offset(dx, 0, dz);
                BlockPos plant = ground.above();
                if (!level.isLoaded(ground) || !level.isLoaded(plant)) continue;
                BlockState soil = level.getBlockState(ground);
                BlockState above = level.getBlockState(plant);
                if (soil.is(Blocks.FARMLAND)) {
                    if (above.getBlock() instanceof CropBlock crop && crop.isMaxAge(above)) {
                        harvests.add(new Task(plant, TaskType.HARVEST, Items.AIR, Blocks.AIR));
                    } else if (above.isAir() && seed != null) {
                        planting.add(new Task(plant, TaskType.PLANT, seed.seed(), seed.crop()));
                    }
                } else if (seed != null && (soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK))
                        && above.isAir()) {
                    tilling.add(new Task(ground, TaskType.TILL, Items.AIR, Blocks.AIR));
                }
            }
        }
        Comparator<Task> distance = Comparator.comparingDouble(t -> t.pos().distSqr(from));
        // Food/ripe crops first, then ensure the field is tilled and planted.
        if (!harvests.isEmpty()) return harvests.stream().min(distance).orElse(null);
        if (!tilling.isEmpty()) return tilling.stream().min(distance).orElse(null);
        return planting.stream().min(distance).orElse(null);
    }

    private static Seed availableSeed(ServerLevel level, BlockPos home) {
        for (Seed seed : SEEDS) if (TownStorage.hasItem(level, home, seed.seed())) return seed;
        return null;
    }

    /** Perform one job near the field, checking world state again to avoid dupes. */
    public static boolean perform(ServerLevel level, Villager worker, BlockPos home, Task task) {
        BlockPos pos = task.pos();
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above())) return false;
        switch (task.kind()) {
            case WATER -> {
                BlockState old = level.getBlockState(pos);
                if (!isConvertibleSoil(old) || !level.getBlockState(pos.above()).isAir()
                        || !TownStorage.hasItem(level, home, Items.WATER_BUCKET)) return false;
                if (!level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL)) return false;
                if (!TownStorage.consumeItem(level, home, Items.WATER_BUCKET)) {
                    level.setBlock(pos, old, Block.UPDATE_ALL);
                    return false;
                }
                ItemStack bucket = new ItemStack(Items.BUCKET);
                TownStorage.insert(level, home, bucket);
                if (!bucket.isEmpty()) Block.popResource(level, pos.above(), bucket);
                WorkerAi.recordMetric(level, worker, home, "farm_water_sources", 1);
                return true;
            }
            case TILL -> {
                if (!level.getBlockState(pos.above()).isAir()) return false;
                BlockState ground = level.getBlockState(pos);
                if (!ground.is(Blocks.GRASS_BLOCK) && !ground.is(Blocks.DIRT)) return false;
                if (!level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL)) return false;
                WorkerAi.recordMetric(level, worker, home, "farm_tilled", 1);
                return true;
            }
            case PLANT -> {
                if (!level.getBlockState(pos.below()).is(Blocks.FARMLAND)
                        || !level.getBlockState(pos).isAir()
                        || !TownStorage.consumeItem(level, home, task.seed())) return false;
                if (!level.setBlock(pos, task.crop().defaultBlockState(), Block.UPDATE_ALL)) {
                    ItemStack returned = new ItemStack(task.seed());
                    TownStorage.insert(level, home, returned);
                    if (!returned.isEmpty()) Block.popResource(level, pos, returned);
                    return false;
                }
                WorkerAi.recordMetric(level, worker, home, "farm_planted", 1);
                return true;
            }
            case HARVEST -> {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)
                        || !level.getBlockState(pos.below()).is(Blocks.FARMLAND)) return false;
                return WorkerAi.collect(level, worker, home, "farmer", pos);
            }
        }
        return false;
    }
}
