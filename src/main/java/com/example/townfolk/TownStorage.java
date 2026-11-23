package com.example.townfolk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One logical town warehouse made from real, player-accessible inventories:
 * one vanilla chest and up to nine vanilla barrels placed inside the hall.
 * Each store adds 27 slots. Workers pay, feed and deposit across ALL stores.
 * No virtual item production, no custom client menu, no destructive resize.
 */
public final class TownStorage {
    private TownStorage() {}

    public static final int MAX_STORAGE_LEVEL = 10;
    public static final int SLOTS_PER_TIER = 27;

    /** Storage levels 2..10 are backed by these nine visible barrel blocks. */
    private static final int[][] BARREL_LOCATIONS = {
            {-3, 1, 3}, {-2, 1, 3}, {-1, 1, 3},
            { 0, 1, 3}, { 1, 1, 3}, { 2, 1, 3},
            { 3, 1, 3}, {-3, 1, 2}, { 3, 1, 2}
    };

    public static int level(ServerLevel world, BlockPos home) {
        return Math.max(1, Math.min(MAX_STORAGE_LEVEL,
                TownManager.data(world, home).getIntOr("tf_storage_level", 1)));
    }

    public static int capacitySlots(ServerLevel world, BlockPos home) {
        return SLOTS_PER_TIER * level(world, home);
    }

    public static BlockPos nextBarrelPosition(BlockPos home, int currentStorageLevel) {
        if (currentStorageLevel < 1 || currentStorageLevel >= MAX_STORAGE_LEVEL) return null;
        int[] offset = BARREL_LOCATIONS[currentStorageLevel - 1];
        return home.offset(offset[0], offset[1], offset[2]);
    }

    public static boolean canExpand(ServerLevel world, BlockPos home) {
        if (!TownManager.isHall(world, home)) return false;
        int current = level(world, home);
        if (current >= MAX_STORAGE_LEVEL || current >= TownManager.level(world, home)) return false;
        BlockPos next = nextBarrelPosition(home, current);
        return next != null && world.isLoaded(next) && world.getBlockState(next).isAir();
    }

    public static TownManager.Cost upgradeCost(int currentStorageLevel) {
        return new TownManager.Cost(Items.OAK_PLANKS, 32 * currentStorageLevel,
                Items.IRON_INGOT, 8 * currentStorageLevel,
                Items.EMERALD, 4 * currentStorageLevel);
    }

    /** Returns only actually existing block inventories, never creates replacements. */
    public static List<Container> containers(ServerLevel world, BlockPos home) {
        List<Container> result = new ArrayList<>();
        BlockPos mainPos = TownManager.chestPos(home);
        if (!world.isLoaded(mainPos) || !world.getBlockState(mainPos).is(Blocks.CHEST)) {
            return result;
        }
        if (!(world.getBlockEntity(mainPos) instanceof Container main)) return result;
        result.add(main);
        int tiers = level(world, home);
        for (int i = 1; i < tiers; i++) {
            int[] offset = BARREL_LOCATIONS[i - 1];
            BlockPos p = home.offset(offset[0], offset[1], offset[2]);
            if (!world.isLoaded(p) || !world.getBlockState(p).is(Blocks.BARREL)) continue;
            if (world.getBlockEntity(p) instanceof Container barrel) result.add(barrel);
        }
        return result;
    }

    public static int accessibleSlots(ServerLevel world, BlockPos home) {
        int total = 0;
        for (Container c : containers(world, home)) total += c.getContainerSize();
        return total;
    }

    public static boolean hasCost(ServerLevel world, BlockPos home, TownManager.Cost cost) {
        List<Container> stores = containers(world, home);
        if (stores.isEmpty()) return false;
        for (Map.Entry<Item, Integer> need : requirements(cost).entrySet()) {
            int found = 0;
            for (Container store : stores) {
                for (int slot = 0; slot < store.getContainerSize(); slot++) {
                    ItemStack stack = store.getItem(slot);
                    if (stack.is(need.getKey())) found += stack.getCount();
                }
            }
            if (found < need.getValue()) return false;
        }
        return true;
    }

    /** Atomic on Minecraft's server thread: validate all prices, then deduct. */
    public static boolean charge(ServerLevel world, BlockPos home, TownManager.Cost cost) {
        if (!hasCost(world, home, cost)) return false;
        for (Map.Entry<Item, Integer> need : requirements(cost).entrySet()) {
            int remaining = need.getValue();
            for (Container store : containers(world, home)) {
                for (int slot = 0; slot < store.getContainerSize() && remaining > 0; slot++) {
                    ItemStack stack = store.getItem(slot);
                    if (!stack.is(need.getKey())) continue;
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                    if (stack.isEmpty()) store.setItem(slot, ItemStack.EMPTY);
                }
                store.setChanged();
                if (remaining == 0) break;
            }
        }
        return true;
    }

    private static Map<Item, Integer> requirements(TownManager.Cost cost) {
        Map<Item, Integer> required = new LinkedHashMap<>();
        add(required, cost.first(), cost.firstCount());
        add(required, cost.second(), cost.secondCount());
        add(required, cost.third(), cost.thirdCount());
        return required;
    }

    private static void add(Map<Item, Integer> map, Item item, int amount) {
        if (item != Items.AIR && amount > 0) map.merge(item, amount, Integer::sum);
    }

    /** Mutates the input item stack; any overflow stays in it to be dropped by the worker. */
    public static void insert(ServerLevel world, BlockPos home, ItemStack stack) {
        if (stack.isEmpty()) return;
        List<Container> stores = containers(world, home);
        // First complete partially filled stacks across the entire warehouse.
        for (Container store : stores) {
            for (int slot = 0; slot < store.getContainerSize() && !stack.isEmpty(); slot++) {
                ItemStack existing = store.getItem(slot);
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                int amount = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                if (amount > 0) {
                    existing.grow(amount);
                    stack.shrink(amount);
                    store.setChanged();
                }
            }
        }
        // Then allocate into empty slots, in order, without overwriting player items.
        for (Container store : stores) {
            for (int slot = 0; slot < store.getContainerSize() && !stack.isEmpty(); slot++) {
                if (!store.getItem(slot).isEmpty()) continue;
                int amount = Math.min(stack.getCount(), stack.getMaxStackSize());
                store.setItem(slot, stack.copyWithCount(amount));
                stack.shrink(amount);
            }
            store.setChanged();
        }
    }

    /** Check supplies without consuming them (farm planning uses this on the server thread). */
    public static boolean hasItem(ServerLevel world, BlockPos home, Item wanted) {
        for (Container store : containers(world, home)) {
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                if (store.getItem(slot).is(wanted)) return true;
            }
        }
        return false;
    }

    /** Consume exactly one item from real physical warehouse inventories (for sapling replanting). */
    public static boolean consumeItem(ServerLevel world, BlockPos home, Item wanted) {
        for (Container store : containers(world, home)) {
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                ItemStack stack = store.getItem(slot);
                if (!stack.is(wanted)) continue;
                stack.shrink(1);
                if (stack.isEmpty()) store.setItem(slot, ItemStack.EMPTY);
                store.setChanged();
                return true;
            }
        }
        return false;
    }

    public static boolean consumeFood(ServerLevel world, BlockPos home) {
        for (Container store : containers(world, home)) {
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                ItemStack stack = store.getItem(slot);
                if (!isFood(stack)) continue;
                stack.shrink(1);
                if (stack.isEmpty()) store.setItem(slot, ItemStack.EMPTY);
                store.setChanged();
                return true;
            }
        }
        return false;
    }

    public static int countFood(ServerLevel world, BlockPos home) {
        int total = 0;
        for (Container store : containers(world, home)) {
            for (int slot = 0; slot < store.getContainerSize(); slot++) {
                ItemStack stack = store.getItem(slot);
                if (isFood(stack)) total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean isFood(ItemStack stack) {
        return stack.is(Items.BREAD) || stack.is(Items.CARROT) || stack.is(Items.POTATO)
                || stack.is(Items.BAKED_POTATO) || stack.is(Items.BEETROOT)
                || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)
                || stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_MUTTON);
    }

    /** Places a real, accessible new barrel and records the new storage tier. */
    public static boolean expand(ServerLevel world, BlockPos home) {
        if (!canExpand(world, home)) return false;
        int current = level(world, home);
        TownManager.Cost price = upgradeCost(current);
        if (!hasCost(world, home, price)) return false;
        BlockPos destination = nextBarrelPosition(home, current);
        if (destination == null || !world.setBlock(destination, Blocks.BARREL.defaultBlockState(), 3)) {
            return false;
        }
        // No intervening tick can withdraw resources between the check and deduction.
        if (!charge(world, home, price)) {
            world.removeBlock(destination, false); // Do not give a free barrel if pre-payment fails.
            return false;
        }
        TownManager.data(world, home).putInt("tf_storage_level", current + 1);
        TownManager.save(world, home);
        return true;
    }
}
