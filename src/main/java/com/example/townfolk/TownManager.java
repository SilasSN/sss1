package com.example.townfolk;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Town metadata lives in the real town bell's saved NeoForge NBT. */
public final class TownManager {
    private TownManager() {}
    public static final int MAX_LEVEL = 10;
    public static final int BASE_CAPACITY = 4;
    public static final int MAX_WORKER_LEVEL = 10;
    public static final String[] ROLES = {"lumberjack", "miner", "farmer", "guard"};

    public record Cost(Item first, int firstCount, Item second, int secondCount, Item third, int thirdCount) {
        public String display() {
            return firstCount + " " + name(first) + ", " + secondCount + " " + name(second)
                    + (thirdCount > 0 ? ", " + thirdCount + " " + name(third) : "");
        }
        private static String name(Item item) { return item.getName(new ItemStack(item)).getString(); }
    }

    public static boolean validRole(String role) {
        for (String r : ROLES) if (r.equals(role)) return true;
        return false;
    }

    public static BlockPos bellPos(BlockPos home) { return home.offset(0, 1, 2); }
    public static BlockPos chestPos(BlockPos home) { return home.offset(2, 1, 2); }

    public static boolean isHall(ServerLevel level, BlockPos home) {
        BlockPos pos = bellPos(home);
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.BELL)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getPersistentData().getBooleanOr("tf_hall", false);
    }

    public static CompoundTag data(ServerLevel level, BlockPos home) {
        BlockEntity bell = level.getBlockEntity(bellPos(home));
        return bell == null ? new CompoundTag() : bell.getPersistentData();
    }

    public static void save(ServerLevel level, BlockPos home) {
        BlockEntity be = level.getBlockEntity(bellPos(home));
        if (be != null) be.setChanged();
    }

    public static void initialize(ServerLevel level, BlockPos home, ServerPlayer player) {
        BlockEntity be = level.getBlockEntity(bellPos(home));
        if (be == null) return;
        CompoundTag tag = be.getPersistentData();
        tag.putBoolean("tf_hall", true);
        tag.putString("tf_owner", player.getUUID().toString());
        tag.putInt("tf_hall_level", 1);
        tag.putInt("tf_storage_level", 1);
        tag.putInt("tf_worker_count", 0);
        be.setChanged();
    }

    public static int level(ServerLevel level, BlockPos home) {
        return Math.max(1, Math.min(MAX_LEVEL, data(level, home).getIntOr("tf_hall_level", 1)));
    }

    public static int capacity(ServerLevel level, BlockPos home) {
        return BASE_CAPACITY * level(level, home);
    }

    public static int count(ServerLevel level, BlockPos home) {
        return Math.max(0, data(level, home).getIntOr("tf_worker_count", 0));
    }

    public static boolean isOwner(ServerLevel level, BlockPos home, ServerPlayer player) {
        return player.getUUID().toString().equals(data(level, home).getStringOr("tf_owner", ""));
    }

    public static BlockPos nearest(ServerLevel level, BlockPos near) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        // Towns do not force-load chunks. Only bells in a loaded area can be used.
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                if (x*x + z*z > 32*32) continue;
                for (int y = -8; y <= 8; y++) {
                    BlockPos possible = near.offset(x, y, z);
                    if (!level.isLoaded(possible) || !level.getBlockState(possible).is(Blocks.BELL)) continue;
                    BlockPos home = possible.offset(0, -1, -2);
                    if (!isHall(level, home)) continue;
                    double d = near.distSqr(possible);
                    if (d < distance) { best = home.immutable(); distance = d; }
                }
            }
        }
        return best;
    }

    public static Container chest(ServerLevel level, BlockPos home) {
        return level.getBlockEntity(chestPos(home)) instanceof Container c ? c : null;
    }

    public static List<Villager> loadedWorkers(ServerLevel level, BlockPos home) {
        AABB bounds = new AABB(home).inflate(48, 12, 48);
        return level.getEntitiesOfClass(Villager.class, bounds, v -> {
            CompoundTag tag = v.getPersistentData();
            return v.isAlive() && validRole(tag.getStringOr("townfolk_role", ""))
                    && tag.getIntOr("townfolk_x", Integer.MIN_VALUE) == home.getX()
                    && tag.getIntOr("townfolk_y", Integer.MIN_VALUE) == home.getY()
                    && tag.getIntOr("townfolk_z", Integer.MIN_VALUE) == home.getZ();
        });
    }

    public static Cost hiringCost(String role) {
        return switch (role) {
            case "lumberjack" -> new Cost(Items.OAK_LOG, 32, Items.EMERALD, 8, Items.AIR, 0);
            case "miner" -> new Cost(Items.COBBLESTONE, 32, Items.EMERALD, 12, Items.AIR, 0);
            case "farmer" -> new Cost(Items.WHEAT, 32, Items.EMERALD, 6, Items.AIR, 0);
            case "guard" -> new Cost(Items.IRON_INGOT, 16, Items.EMERALD, 16, Items.AIR, 0);
            default -> new Cost(Items.AIR, 0, Items.AIR, 0, Items.AIR, 0);
        };
    }

    public static Cost workerUpgradeCost(String role, int currentLevel) {
        int emeralds = 4 * currentLevel;
        int resources = 8 * currentLevel;
        Item resource = switch (role) {
            case "lumberjack" -> Items.OAK_LOG;
            case "miner" -> Items.COBBLESTONE;
            case "farmer" -> Items.WHEAT;
            default -> Items.IRON_INGOT;
        };
        return new Cost(resource, resources, Items.EMERALD, emeralds, Items.AIR, 0);
    }

    public static Cost hallUpgradeCost(int currentLevel) {
        return new Cost(Items.EMERALD, 8 * currentLevel, Items.OAK_LOG,
                16 * currentLevel, Items.COBBLESTONE, 16 * currentLevel);
    }

    public static boolean canSpawn(ServerLevel level, BlockPos home) {
        BlockPos spawn = home.offset(0, 1, -1);
        for (int attempt = 0; attempt < 5; attempt++) {
            BlockPos pos = spawn.offset(attempt - 2, 0, 0);
            if (level.isLoaded(pos) && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()) return true;
        }
        return false;
    }

    public static boolean spawn(ServerLevel level, BlockPos home, String role) {
        if (!validRole(role) || !isHall(level, home)) return false;
        if (count(level, home) >= capacity(level, home)) return false;
        // Spawn just outside north entrance, with height clearance to prevent suffocation.
        BlockPos spawn = home.offset(0, 1, -1);
        for (int attempt = 0; attempt < 5; attempt++) {
            BlockPos pos = spawn.offset(attempt - 2, 0, 0);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()
                    || !level.getBlockState(pos.above()).isAir()) continue;
            Villager villager = EntityTypes.VILLAGER.spawn(level, pos, EntitySpawnReason.COMMAND);
            if (villager == null) continue;
            CompoundTag tag = villager.getPersistentData();
            tag.putString("townfolk_role", role);
            tag.putInt("townfolk_x", home.getX());
            tag.putInt("townfolk_y", home.getY());
            tag.putInt("townfolk_z", home.getZ());
            tag.putInt("tf_worker_level", 1);
            tag.putInt("tf_food", 100);
            tag.putLong("tf_last_food_tick", level.getGameTime());
            setEquipmentAndName(villager, role, 1);
            CompoundTag hall = data(level, home);
            hall.putInt("tf_worker_count", count(level, home) + 1);
            save(level, home);
            return true;
        }
        return false;
    }

    public static void setEquipmentAndName(Villager v, String role, int level) {
        String name = switch (role) {
            case "lumberjack" -> "Lumberjack";
            case "miner" -> "Miner";
            case "farmer" -> "Farmer";
            default -> "Town Guard";
        };
        v.setCustomName(Component.literal(name + " Lv." + level));
        v.setCustomNameVisible(true);
        Item tool = switch (role) {
            case "lumberjack" -> level >= 6 ? Items.DIAMOND_AXE : Items.IRON_AXE;
            case "miner" -> level >= 6 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE;
            case "farmer" -> level >= 6 ? Items.DIAMOND_HOE : Items.IRON_HOE;
            default -> level >= 6 ? Items.DIAMOND_SWORD : Items.IRON_SWORD;
        };
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tool));
    }
}
