package com.example.townfolk;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Comparator;
import java.util.List;

/** Commands + clickable chat action menu; no client-side custom screen/network code is needed. */
public final class TownController {
    private TownController() {}

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }

    private static void button(ServerPlayer player, String label, String command) {
        player.sendSystemMessage(Component.literal("  [ " + label + " ]")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand(command))));
    }

    public static void onBell(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos bell = event.getPos();
        if (!level.getBlockState(bell).is(Blocks.BELL)) return;
        BlockPos home = bell.offset(0, -1, -2);
        if (!TownManager.isHall(level, home)) return;
        // Prevent vanilla bell activation when using the controller bell.
        event.setCanceled(true);
        display(player, home);
    }

    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("townfolk")
                .then(Commands.literal("status").executes(c -> withHall(c, null, null)))
                .then(Commands.literal("food").executes(c -> withHall(c, "food", null)))
                .then(Commands.literal("storage").executes(c -> withHall(c, "storage", null)))
                .then(Commands.literal("storageupgrade").executes(c -> withHall(c, "storageupgrade", null)))
                .then(Commands.literal("workers").executes(c -> withHall(c, "workers", null)))
                .then(Commands.literal("hallupgrade").executes(c -> withHall(c, "hallupgrade", null)));
        for (String role : TownManager.ROLES) {
            root.then(Commands.literal("hire")
                    .then(Commands.literal(role).executes(c -> withHall(c, "hire", role))));
            root.then(Commands.literal("upgrade")
                    .then(Commands.literal(role).executes(c -> withHall(c, "upgrade", role))));
        }
        event.getDispatcher().register(root.executes(c -> withHall(c, null, null)));
    }

    private static int withHall(CommandContext<CommandSourceStack> context, String action, String role) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel world = player.level();
            BlockPos home = TownManager.nearest(world, player.blockPosition());
            if (home == null) {
                say(player, "No Town Hall within 32 blocks. Place a Town Hall Charter first.");
                return 0;
            }
            if (action == null) { display(player, home); return 1; }
            if (action.equals("workers")) { workers(player, home); return 1; }
            if (action.equals("food")) { food(player, home); return 1; }
            if (action.equals("storage")) { storage(player, home); return 1; }
            if (!TownManager.isOwner(world, home, player)) {
                say(player, "Only the Town Hall founder may hire or upgrade workers.");
                return 0;
            }
            return switch (action) {
                case "hallupgrade" -> hallUpgrade(player, home);
                case "storageupgrade" -> storageUpgrade(player, home);
                case "hire" -> hire(player, home, role);
                case "upgrade" -> upgrade(player, home, role);
                default -> 0;
            };
        } catch (Exception exception) {
            return 0;
        }
    }

    private static void display(ServerPlayer player, BlockPos home) {
        ServerLevel level = player.level();
        int hallLevel = TownManager.level(level, home);
        say(player, "====== Town Hall ======");
        say(player, "Hall level: " + hallLevel + "/10 | Workers: "
                + TownManager.count(level, home) + "/" + TownManager.capacity(level, home));
        say(player, "Warehouse: " + TownStorage.level(level, home) + "/10 ("
                + TownStorage.accessibleSlots(level, home) + "/"
                + TownStorage.capacitySlots(level, home) + " accessible/entitled slots).");
        say(player, "All payments use REAL materials from town chest + expanded barrels.");
        if (TownManager.isOwner(level, home, player)) {
            if (hallLevel < TownManager.MAX_LEVEL) {
                button(player, "Upgrade hall: " + TownManager.hallUpgradeCost(hallLevel).display(),
                        "/townfolk hallupgrade");
            }
            if (TownStorage.level(level, home) < TownStorage.MAX_STORAGE_LEVEL) {
                button(player, "Upgrade warehouse: "
                                + TownStorage.upgradeCost(TownStorage.level(level, home)).display(),
                        "/townfolk storageupgrade");
            }
            for (String role : TownManager.ROLES) {
                button(player, "Hire " + role + ": " + TownManager.hiringCost(role).display(),
                        "/townfolk hire " + role);
                button(player, "Upgrade nearest " + role, "/townfolk upgrade " + role);
            }
        } else {
            say(player, "You can view this town, but only its owner can manage it.");
        }
        button(player, "View workers", "/townfolk workers");
        button(player, "View food", "/townfolk food");
        button(player, "View warehouse", "/townfolk storage");
    }

    private static void workers(ServerPlayer player, BlockPos home) {
        List<Villager> workers = TownManager.loadedWorkers(player.level(), home);
        say(player, "Town workers loaded: " + workers.size() + " / capacity "
                + TownManager.capacity(player.level(), home));
        for (Villager v : workers) {
            CompoundTag tag = v.getPersistentData();
            say(player, " - " + tag.getStringOr("townfolk_role", "worker") + " lv."
                    + tag.getIntOr("tf_worker_level", 1)
                    + " | food: " + tag.getIntOr("tf_food", 100) + "/100");
        }
        say(player, "For individual upgrades, stand near the desired worker of its role.");
    }

    private static void food(ServerPlayer player, BlockPos home) {
        ServerLevel world = player.level();
        say(player, "Food in the WHOLE warehouse: " + TownStorage.countFood(world, home));
        say(player, "Put bread, carrots, potatoes, beetroot or cooked meat in any storage barrel/chest.");
        say(player, "Farmers harvest real crops and deliver their harvest to the warehouse.");
        say(player, "Workers consume one food item when hungry. No food = work stops;");
        say(player, "guards still defend the town, but deal less damage while starving.");
    }

    private static void storage(ServerPlayer player, BlockPos home) {
        ServerLevel world = player.level();
        int warehouse = TownStorage.level(world, home);
        say(player, "Warehouse level " + warehouse + "/10: "
                + TownStorage.accessibleSlots(world, home) + " currently accessible slots ("
                + TownStorage.capacitySlots(world, home) + " if intact).");
        say(player, "The first 27 slots are in the original CHEST. Additional 27-slot BARRELS");
        say(player, "appear INSIDE the Town Hall when you buy a warehouse upgrade.");
        say(player, "Workers use ALL connected inventories for deposits, food and payments.");
        if (TownManager.isOwner(world, home, player) && warehouse < TownStorage.MAX_STORAGE_LEVEL) {
            button(player, "Upgrade warehouse: " + TownStorage.upgradeCost(warehouse).display(),
                    "/townfolk storageupgrade");
        }
    }

    private static int storageUpgrade(ServerPlayer player, BlockPos home) {
        ServerLevel world = player.level();
        int current = TownStorage.level(world, home);
        if (current >= TownStorage.MAX_STORAGE_LEVEL) {
            say(player, "Warehouse is already at level 10: 270 slots."); return 0;
        }
        if (current >= TownManager.level(world, home)) {
            say(player, "First upgrade your Town Hall. Warehouse level may not exceed hall level.");
            return 0;
        }
        if (!TownStorage.canExpand(world, home)) {
            say(player, "Warehouse upgrade location is blocked; clear the next barrel space in the hall.");
            return 0;
        }
        TownManager.Cost price = TownStorage.upgradeCost(current);
        if (!TownStorage.hasCost(world, home, price)) {
            say(player, "Not enough materials across your warehouse. Need: " + price.display());
            return 0;
        }
        if (!TownStorage.expand(world, home)) {
            say(player, "Warehouse expansion failed. No upgrade was recorded."); return 0;
        }
        say(player, "Warehouse upgraded to level " + (current + 1) + "! Capacity: "
                + TownStorage.capacitySlots(world, home) + " slots. A new barrel was added in the hall.");
        return 1;
    }

    private static int hallUpgrade(ServerPlayer player, BlockPos home) {
        ServerLevel world = player.level();
        int current = TownManager.level(world, home);
        if (current >= TownManager.MAX_LEVEL) { say(player, "Hall is already at level 10."); return 0; }
        TownManager.Cost cost = TownManager.hallUpgradeCost(current);
        if (!TownStorage.charge(world, home, cost)) {
            say(player, "Not enough materials in the chest. Need: " + cost.display()); return 0;
        }
        TownManager.data(world, home).putInt("tf_hall_level", current + 1);
        TownManager.save(world, home);
        say(player, "Town Hall upgraded to level " + (current + 1)
                + "! Worker capacity: " + TownManager.capacity(world, home)
                + ". Warehouse may now be upgraded to level " + (current + 1) + ".");
        return 1;
    }

    private static int hire(ServerPlayer player, BlockPos home, String role) {
        ServerLevel world = player.level();
        if (TownManager.count(world, home) >= TownManager.capacity(world, home)) {
            say(player, "Your hall is full. Upgrade the Town Hall to unlock more worker slots."); return 0;
        }
        TownManager.Cost cost = TownManager.hiringCost(role);
        if (TownStorage.containers(world, home).isEmpty()) {
            say(player, "The Town Hall chest is missing."); return 0;
        }
        // Verify a safe spawn space before charging; a blocked hall must not take payment.
        if (!TownManager.canSpawn(world, home)) {
            say(player, "Clear room inside the Town Hall for the new worker."); return 0;
        }
        if (!TownStorage.charge(world, home, cost)) {
            say(player, "Not enough materials in the chest. Need: " + cost.display()); return 0;
        }
        if (!TownManager.spawn(world, home, role)) {
            // This is a rare failed-spawn condition, after preflight. The chest's charge was already made.
            say(player, "Could not spawn worker (charged materials). Contact the mod author with logs."); return 0;
        }
        say(player, "Hired " + role + "! " + TownManager.count(world, home)
                + "/" + TownManager.capacity(world, home) + " worker slots used.");
        return 1;
    }

    private static int upgrade(ServerPlayer player, BlockPos home, String role) {
        ServerLevel world = player.level();
        Villager target = TownManager.loadedWorkers(world, home).stream()
                .filter(v -> v.getPersistentData().getStringOr("townfolk_role", "").equals(role))
                .filter(v -> v.getPersistentData().getIntOr("tf_worker_level", 1) < TownManager.MAX_WORKER_LEVEL)
                .min(Comparator.comparingDouble(v -> player.distanceToSqr(v)))
                .orElse(null);
        if (target == null) { say(player, "No eligible " + role + " worker found."); return 0; }
        if (player.distanceToSqr(target) > 8 * 8) {
            say(player, "Stand within 8 blocks of the " + role + " you wish to upgrade."); return 0;
        }
        int current = target.getPersistentData().getIntOr("tf_worker_level", 1);
        TownManager.Cost cost = TownManager.workerUpgradeCost(role, current);
        if (!TownStorage.charge(world, home, cost)) {
            say(player, "Not enough materials in the chest. Need: " + cost.display()); return 0;
        }
        target.getPersistentData().putInt("tf_worker_level", current + 1);
        TownManager.setEquipmentAndName(target, role, current + 1);
        say(player, "Upgraded " + role + " to level " + (current + 1) + " / 10.");
        return 1;
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel world)) return;
        CompoundTag tag = villager.getPersistentData();
        if (!TownManager.validRole(tag.getStringOr("townfolk_role", ""))) return;
        BlockPos home = new BlockPos(tag.getIntOr("townfolk_x", 0),
                tag.getIntOr("townfolk_y", 0), tag.getIntOr("townfolk_z", 0));
        if (TownManager.isHall(world, home)) {
            TownManager.data(world, home).putInt("tf_worker_count", Math.max(0, TownManager.count(world, home) - 1));
            TownManager.save(world, home);
        }
    }
}
