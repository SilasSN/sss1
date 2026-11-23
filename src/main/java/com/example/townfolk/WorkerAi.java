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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real-world worker simulation. Trees are claimed and cut log-by-log as ONE job.
 * Quarry mining uses actual loot tables and provides persistent production totals.
 * Workers use a path retry -> return-to-hall -> safe teleport fallback.
 */
public final class WorkerAi {
    private WorkerAi() {}
    private static final int JOB_RADIUS = 22;
    private static final int HALL_SAFE_RADIUS = 6;
    private static final int TREE_MAX_LOGS = 192;
    private static final int TREE_MAX_HEIGHT = 36;
    private static final int STALL_RETRY_TICKS = 120;
    private static final int STALL_RETURN_TICKS = 240;
    private static final int RETURN_TELEPORT_TICKS = 240;

    private record Target(BlockPos block, BlockPos stand, int acquiredAt) {}
    private record TreeCandidate(BlockPos root, BlockPos stand, double distance) {}
    private static final class TreeJob {
        final BlockPos root;
        final BlockPos stand;
        final List<BlockPos> logs;
        final Block logBlock;
        int nextIndex;
        TreeJob(BlockPos root, BlockPos stand, List<BlockPos> logs, Block logBlock) {
            this.root = root;
            this.stand = stand;
            this.logs = logs;
            this.logBlock = logBlock;
        }
    }
    private static final class Motion {
        double x, y, z;
        int idleTicks;
        int returningTicks;
        boolean returning;
        Motion(Villager v) { remember(v); }
        void remember(Villager v) { x = v.getX(); y = v.getY(); z = v.getZ(); }
        boolean moved(Villager v) {
            double dx = v.getX() - x, dy = v.getY() - y, dz = v.getZ() - z;
            remember(v);
            return dx * dx + dy * dy + dz * dz >= 0.09;
        }
    }

    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final Map<UUID, TreeJob> TREE_JOBS = new HashMap<>();
    private static final Map<UUID, Motion> MOTIONS = new HashMap<>();

    public static void forget(Villager villager) {
        UUID id = villager.getUUID();
        TARGETS.remove(id);
        TREE_JOBS.remove(id);
        MOTIONS.remove(id);
    }

    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager worker)) return;
        if (!(worker.level() instanceof ServerLevel level)) return;
        CompoundTag data = worker.getPersistentData();
        String role = data.getStringOr("townfolk_role", "");
        if (!TownManager.validRole(role) || !worker.isAlive() || worker.isBaby()) return;
        if (worker.tickCount % 20 != 0) return;
        BlockPos home = new BlockPos(data.getIntOr("townfolk_x", 0),
                data.getIntOr("townfolk_y", 0), data.getIntOr("townfolk_z", 0));
        if (!TownManager.isHall(level, home)) return;
        updateHunger(level, home, data);

        Motion motion = MOTIONS.computeIfAbsent(worker.getUUID(), ignored -> new Motion(worker));
        if (motion.returning || worker.distanceToSqr(home.getX() + .5, home.getY() + 1,
                home.getZ() + .5) > 30 * 30) {
            if (!motion.returning) startReturn(worker, motion);
            returnToHall(level, worker, home, motion);
            return;
        }

        int workerLevel = Math.max(1, Math.min(10, data.getIntOr("tf_worker_level", 1)));
        int hunger = Math.max(0, Math.min(100, data.getIntOr("tf_food", 100)));
        if (role.equals("guard")) {
            guard(level, worker, home, workerLevel, hunger, motion);
            return;
        }
        if (hunger <= 0) { motion.idleTicks = 0; return; }
        if (role.equals("lumberjack")) {
            treeWork(level, worker, home, workerLevel, hunger, motion);
        } else if (role.equals("miner")) {
            blockWork(level, worker, home, role, workerLevel, hunger, motion);
        } else if (role.equals("farmer")) {
            farmerWork(level, worker, home, workerLevel, hunger, motion);
        }
    }

    private static void updateHunger(ServerLevel level, BlockPos home, CompoundTag data) {
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

    /** Used both by the automatic rescue system and the owner's /townfolk rescue command. */
    public static boolean rescueWorker(ServerLevel level, Villager worker, BlockPos home) {
        BlockPos safe = safeHome(level, home);
        if (safe == null) return false; // Never teleport into solid blocks or unloaded chunks.
        worker.getNavigation().stop();
        worker.setPos(safe.getX() + .5, safe.getY(), safe.getZ() + .5);
        forget(worker);
        worker.getPersistentData().putLong("tf_next_work_tick", level.getGameTime() + 80);
        addMetric(level, worker, home, "rescues", 1);
        return true;
    }

    private static BlockPos safeHome(ServerLevel level, BlockPos home) {
        // Locations inside the hall are tried before the entrance and nearby terrain.
        BlockPos[] spots = {home.offset(0,1,-2), home.offset(-1,1,-2), home.offset(1,1,-2),
                home.offset(0,1,-3), home.offset(0,1,-5), home.offset(-1,1,-5),
                home.offset(1,1,-5), home.offset(0,1,-6)};
        for (BlockPos p : spots) if (isStandingSpot(level, p)
                && level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty()) return p;
        return null;
    }

    private static void startReturn(Villager worker, Motion motion) {
        TARGETS.remove(worker.getUUID());
        TREE_JOBS.remove(worker.getUUID());
        motion.returning = true;
        motion.returningTicks = 0;
        motion.idleTicks = 0;
        motion.remember(worker);
        worker.getNavigation().stop();
    }

    private static void returnToHall(ServerLevel level, Villager worker, BlockPos home, Motion motion) {
        BlockPos safe = safeHome(level, home);
        if (safe == null) return;
        if (worker.distanceToSqr(safe.getX()+.5, safe.getY(), safe.getZ()+.5) < 4 * 4) {
            motion.returning = false;
            motion.returningTicks = 0;
            motion.idleTicks = 0;
            return;
        }
        if (motion.moved(worker)) motion.returningTicks = 0;
        else motion.returningTicks += 20;
        worker.getNavigation().moveTo(safe.getX()+.5, safe.getY(), safe.getZ()+.5, 1.1);
        if (motion.returningTicks >= RETURN_TELEPORT_TICKS) {
            // The worker tried to walk home for 12 stationary seconds.
            rescueWorker(level, worker, home);
        }
    }

    /** Called while a worker is far from an assigned worksite. Returns true if recovery started. */
    private static boolean checkRoute(ServerLevel level, Villager worker, BlockPos home,
                                      BlockPos goal, Motion motion) {
        if (worker.distanceToSqr(goal.getX()+.5, goal.getY(), goal.getZ()+.5) <= 3.4 * 3.4) {
            motion.idleTicks = 0;
            motion.remember(worker);
            return false;
        }
        if (motion.moved(worker)) motion.idleTicks = 0;
        else motion.idleTicks += 20;
        if (motion.idleTicks >= STALL_RETURN_TICKS) {
            startReturn(worker, motion);
            addMetric(level, worker, home, "stuck_returns", 1);
            returnToHall(level, worker, home, motion);
            return true;
        }
        if (motion.idleTicks == STALL_RETRY_TICKS) {
            worker.getNavigation().stop(); // Clear a stale/partial path before retry.
        }
        return false;
    }

    private static void guard(ServerLevel level, Villager worker, BlockPos home,
                              int workerLevel, int hunger, Motion motion) {
        List<Monster> targets = level.getEntitiesOfClass(Monster.class,
                worker.getBoundingBox().inflate(12),
                mob -> mob.isAlive() && mob.distanceToSqr(home.getX()+.5, home.getY()+1,
                        home.getZ()+.5) <= JOB_RADIUS * JOB_RADIUS);
        Monster closest = null;
        double distance = Double.MAX_VALUE;
        for (Monster mob : targets) {
            double d = worker.distanceToSqr(mob);
            if (d < distance) { distance = d; closest = mob; }
        }
        if (closest != null) {
            if (distance > 7 && checkRoute(level, worker, home, closest.blockPosition(), motion)) return;
            worker.getNavigation().moveTo(closest, 1.25);
            if (distance < 7) {
                worker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                float damage = 4.0f + .65f * (workerLevel - 1);
                if (hunger <= 0) damage *= .5f;
                closest.hurtServer(level, level.damageSources().mobAttack(worker), damage);
            }
        } else {
            motion.idleTicks = 0;
            if (worker.distanceToSqr(home.getX(), home.getY()+1, home.getZ()) > 14*14) {
                BlockPos anchor = home.offset(0, 1, -3);
                if (!checkRoute(level, worker, home, anchor, motion)) {
                    worker.getNavigation().moveTo(anchor.getX()+.5, anchor.getY(), anchor.getZ()+.5, .95);
                }
            }
        }
    }

    private static void treeWork(ServerLevel level, Villager worker, BlockPos home,
                                 int workerLevel, int hunger, Motion motion) {
        UUID id = worker.getUUID();
        TreeJob job = TREE_JOBS.get(id);
        if (job == null) {
            if (worker.tickCount % 100 != 0) return;
            job = findTreeTask(level, worker, home, workerLevel);
            if (job == null) return;
            TREE_JOBS.put(id, job); // Claim entire tree so nearby lumberjacks choose another.
            motion.idleTicks = 0;
            motion.remember(worker);
        }
        if (worker.distanceToSqr(job.stand.getX()+.5, job.stand.getY(),
                job.stand.getZ()+.5) > 3.4*3.4) {
            if (checkRoute(level, worker, home, job.stand, motion)) return;
            worker.getNavigation().moveTo(job.stand.getX()+.5, job.stand.getY(),
                    job.stand.getZ()+.5, 1.0);
            return;
        }
        motion.idleTicks = 0;
        long next = worker.getPersistentData().getLongOr("tf_next_work_tick", 0L);
        if (level.getGameTime() < next) return;
        // Finish this tree before scanning for another one. One log per work cooldown.
        boolean chopped = false;
        while (job.nextIndex < job.logs.size()) {
            BlockPos p = job.logs.get(job.nextIndex++);
            if (!level.isLoaded(p) || !level.getBlockState(p).getBlock().equals(job.logBlock)
                    || !canWork(level, p, home, "lumberjack")) continue;
            chopped = collect(level, worker, home, "lumberjack", p);
            if (chopped) break;
        }
        if (job.nextIndex >= job.logs.size()) {
            TREE_JOBS.remove(id);
            // Replant only if a real sapling is already present in the connected warehouse.
            attemptReplant(level, home, job);
            addMetric(level, worker, home, "trees_felled", 1);
        }
        if (chopped) setCooldown(level, worker, workerLevel, hunger);
    }

    private static TreeJob findTreeTask(ServerLevel level, Villager worker,
                                        BlockPos home, int workerLevel) {
        int searchRadius = Math.min(JOB_RADIUS, 14 + workerLevel);
        BlockPos center = worker.blockPosition();
        List<TreeCandidate> roots = new ArrayList<>();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                if (dx*dx + dz*dz > searchRadius*searchRadius) continue;
                for (int dy = -5; dy <= 7; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!level.isLoaded(p) || !canWork(level, p, home, "lumberjack")
                            || !isNaturalSoil(level, p.below())) continue;
                    BlockPos stand = findStand(level, p, center);
                    if (stand == null) continue;
                    double distance = worker.distanceToSqr(stand.getX()+.5, stand.getY(), stand.getZ()+.5);
                    roots.add(new TreeCandidate(p.immutable(), stand.immutable(), distance));
                }
            }
        }
        roots.sort(Comparator.comparingDouble(TreeCandidate::distance));
        Set<BlockPos> claimed = new HashSet<>();
        for (TreeJob existing : TREE_JOBS.values()) claimed.add(existing.root);
        for (int i = 0; i < Math.min(roots.size(), 32); i++) {
            TreeCandidate c = roots.get(i);
            if (claimed.contains(c.root)) continue;
            TreeJob result = inspectTree(level, home, c.root, c.stand);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Only natural trees: root on soil, a connected trunk, and a real leaf canopy.
     * Matching log block type + 5-block spread prevents chaining into other species.
     * Limit the job to 192 logs and within the Town Hall's protected working area.
     */
    private static TreeJob inspectTree(ServerLevel level, BlockPos home, BlockPos root, BlockPos stand) {
        BlockState initial = level.getBlockState(root);
        if (!initial.is(BlockTags.LOGS) || !isNaturalSoil(level, root.below())) return null;
        Block block = initial.getBlock();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        pending.add(root);
        seen.add(root);
        while (!pending.isEmpty()) {
            BlockPos p = pending.removeFirst();
            if (Math.abs(p.getX()-root.getX()) > 5 || Math.abs(p.getZ()-root.getZ()) > 5
                    || p.getY() < root.getY() || p.getY() > root.getY()+TREE_MAX_HEIGHT) continue;
            if (!level.isLoaded(p) || level.getBlockState(p).getBlock() != block) continue;
            if (!canWork(level, p, home, "lumberjack")) return null;
            logs.add(p.immutable());
            if (logs.size() > TREE_MAX_LOGS) return null;
            for (Direction direction : Direction.values()) {
                BlockPos other = p.relative(direction);
                if (seen.add(other)) pending.add(other);
            }
        }
        if (logs.size() < 3 || !hasCanopy(level, logs)) return null;
        logs.sort(Comparator.comparingInt(BlockPos::getY)
                .thenComparingInt(p -> Math.abs(p.getX()-root.getX()) + Math.abs(p.getZ()-root.getZ())));
        return new TreeJob(root.immutable(), stand.immutable(), logs, block);
    }

    private static boolean hasCanopy(ServerLevel level, List<BlockPos> logs) {
        int maxY = logs.stream().mapToInt(BlockPos::getY).max().orElse(Integer.MIN_VALUE);
        for (BlockPos log : logs) {
            if (log.getY() < maxY - 3) continue;
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
                for (int dy=-1; dy<=2; dy++) {
                    BlockPos leaf = log.offset(dx,dy,dz);
                    if (level.isLoaded(leaf) && level.getBlockState(leaf).is(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isNaturalSoil(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState b = level.getBlockState(p);
        return b.is(Blocks.GRASS_BLOCK) || b.is(Blocks.DIRT) || b.is(Blocks.PODZOL)
                || b.is(Blocks.COARSE_DIRT) || b.is(Blocks.ROOTED_DIRT)
                || b.is(Blocks.MOSS_BLOCK) || b.is(Blocks.MYCELIUM) || b.is(Blocks.MUD);
    }

    private static void attemptReplant(ServerLevel level, BlockPos home, TreeJob job) {
        if (!level.isLoaded(job.root) || !level.getBlockState(job.root).isAir()
                || !isNaturalSoil(level, job.root.below())) return;
        String blockName = BuiltInRegistries.BLOCK.getKey(job.logBlock).getPath();
        Item sapling = switch (blockName) {
            case "oak_log" -> Items.OAK_SAPLING;
            case "spruce_log" -> Items.SPRUCE_SAPLING;
            case "birch_log" -> Items.BIRCH_SAPLING;
            case "jungle_log" -> Items.JUNGLE_SAPLING;
            case "acacia_log" -> Items.ACACIA_SAPLING;
            case "cherry_log" -> Items.CHERRY_SAPLING;
            // Dark oak requires four saplings; mangroves require propagules.
            default -> Items.AIR;
        };
        Block saplingBlock = switch (blockName) {
            case "oak_log" -> Blocks.OAK_SAPLING;
            case "spruce_log" -> Blocks.SPRUCE_SAPLING;
            case "birch_log" -> Blocks.BIRCH_SAPLING;
            case "jungle_log" -> Blocks.JUNGLE_SAPLING;
            case "acacia_log" -> Blocks.ACACIA_SAPLING;
            case "cherry_log" -> Blocks.CHERRY_SAPLING;
            default -> Blocks.AIR;
        };
        if (sapling == Items.AIR || !TownStorage.consumeItem(level, home, sapling)) return;
        level.setBlock(job.root, saplingBlock.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Build, sow and harvest one dedicated 5x5 field, with mature crops prioritized. */
    private static void farmerWork(ServerLevel level, Villager worker, BlockPos home,
                                   int workerLevel, int hunger, Motion motion) {
        BlockPos field = TownFarm.locate(level, home, worker);
        if (field == null) {
            // Existing player-created crop fields still work when there is no level plot.
            blockWork(level, worker, home, "farmer", workerLevel, hunger, motion);
            return;
        }
        TownFarm.Task task = TownFarm.nextTask(level, home, field, worker.blockPosition());
        if (task == null) {
            // While waiting for seeds, water or growth, help with existing crops.
            blockWork(level, worker, home, "farmer", workerLevel, hunger, motion);
            return;
        }
        BlockPos goal = task.kind() == TownFarm.TaskType.WATER ||
                task.kind() == TownFarm.TaskType.TILL ? task.pos().above() : task.pos();
        BlockPos stand = findStand(level, goal, worker.blockPosition());
        if (stand == null) {
            // Do not teleport or alter unreachable farmland.
            motion.idleTicks = 0;
            return;
        }
        if (worker.distanceToSqr(stand.getX()+.5, stand.getY(), stand.getZ()+.5) > 3.4*3.4) {
            if (checkRoute(level, worker, home, stand, motion)) return;
            worker.getNavigation().moveTo(stand.getX()+.5, stand.getY(), stand.getZ()+.5, 1.0);
            return;
        }
        motion.idleTicks = 0;
        long next = worker.getPersistentData().getLongOr("tf_next_work_tick", 0L);
        if (level.getGameTime() >= next && TownFarm.perform(level, worker, home, task)) {
            setCooldown(level, worker, workerLevel, hunger);
        }
    }

    private static void blockWork(ServerLevel level, Villager worker, BlockPos home,
                                  String role, int workerLevel, int hunger, Motion motion) {
        UUID id = worker.getUUID();
        Target target = TARGETS.get(id);
        if (target != null && (worker.tickCount - target.acquiredAt > 360
                || !level.isLoaded(target.block) || !canWork(level, target.block, home, role)
                || !isStandingSpot(level, target.stand))) {
            TARGETS.remove(id);
            target = null;
        }
        if (target == null) {
            if (worker.tickCount % 100 != 0) return;
            target = findBlockTask(level, worker, home, role, workerLevel);
            if (target == null) return;
            TARGETS.put(id, target);
            motion.idleTicks = 0;
            motion.remember(worker);
        }
        if (worker.distanceToSqr(target.stand.getX()+.5, target.stand.getY(),
                target.stand.getZ()+.5) > 3.4*3.4) {
            if (checkRoute(level, worker, home, target.stand, motion)) return;
            worker.getNavigation().moveTo(target.stand.getX()+.5, target.stand.getY(),
                    target.stand.getZ()+.5, 1.0);
            return;
        }
        motion.idleTicks = 0;
        if (level.getGameTime() < worker.getPersistentData().getLongOr("tf_next_work_tick", 0L)) return;
        collect(level, worker, home, role, target.block);
        TARGETS.remove(id);
        setCooldown(level, worker, workerLevel, hunger);
    }

    private static Target findBlockTask(ServerLevel level, Villager worker, BlockPos home,
                                        String role, int workerLevel) {
        Target nearest = null;
        double best = Double.MAX_VALUE;
        BlockPos center = worker.blockPosition();
        int radius = Math.min(JOB_RADIUS, 14 + workerLevel);
        for (int dx=-radius; dx<=radius; dx++) for (int dz=-radius; dz<=radius; dz++) {
            if (dx*dx + dz*dz > radius*radius) continue;
            for (int dy=-5; dy<=7; dy++) {
                BlockPos p = center.offset(dx,dy,dz);
                if (!level.isLoaded(p) || !canWork(level, p, home, role)) continue;
                BlockPos stand = findStand(level, p, center);
                if (stand == null) continue;
                double d = worker.distanceToSqr(stand.getX()+.5, stand.getY(), stand.getZ()+.5);
                if (d < best) { best = d; nearest = new Target(p.immutable(), stand.immutable(), worker.tickCount); }
            }
        }
        return nearest;
    }

    private static void setCooldown(ServerLevel level, Villager worker, int levelValue, int hunger) {
        int cooldown = Math.max(20, 110 - (levelValue-1)*10);
        if (hunger <= 30) cooldown += 80;
        worker.getPersistentData().putLong("tf_next_work_tick", level.getGameTime()+cooldown);
    }

    private static boolean canWork(ServerLevel level, BlockPos pos, BlockPos home, String role) {
        int minY = home.getY()-8;
        int maxY = home.getY() + (role.equals("lumberjack") ? 42 : 10);
        if (pos.getY() < minY || pos.getY() > maxY) return false;
        if (Math.abs(pos.getX()-home.getX()) > JOB_RADIUS || Math.abs(pos.getZ()-home.getZ()) > JOB_RADIUS) return false;
        if (Math.abs(pos.getX()-home.getX()) <= HALL_SAFE_RADIUS
                && Math.abs(pos.getZ()-home.getZ()) <= HALL_SAFE_RADIUS) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity()) return false;
        if (role.equals("lumberjack")) return state.is(BlockTags.LOGS);
        if (role.equals("farmer")) return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        if (role.equals("miner")) {
            String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (name.equals("stone") || name.equals("deepslate") || name.equals("andesite")
                    || name.equals("diorite") || name.equals("granite") || name.equals("tuff")
                    || name.endsWith("_ore") || name.equals("ancient_debris")) return true;
            return (name.equals("dirt") || name.equals("grass_block")
                    || name.equals("coarse_dirt") || name.equals("gravel"))
                    && pos.getX() >= home.getX()+8 && pos.getX() <= home.getX()+14
                    && pos.getZ() >= home.getZ()+8 && pos.getZ() <= home.getZ()+14
                    && pos.getY() >= home.getY()-6 && pos.getY() <= home.getY()+1;
        }
        return false;
    }

    private static boolean isStandingSpot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) return false;
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        // Crops have no solid collision and a farmer can walk over planted farmland.
        return (feet.isAir() || feet.getBlock() instanceof CropBlock)
                && head.isAir() && (floor.isSolid() || floor.is(Blocks.FARMLAND));
    }

    private static BlockPos findStand(ServerLevel level, BlockPos block, BlockPos from) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos near = block.relative(direction);
            for (int dy=-1; dy<=2; dy++) {
                BlockPos stand = near.offset(0,dy,0);
                if (!isStandingSpot(level, stand)) continue;
                double d = stand.distSqr(from);
                if (d < distance) { distance = d; best = stand; }
            }
        }
        return best;
    }

    /** Mines with the NPC's actual tool and uses Minecraft block loot tables. */
    static boolean collect(ServerLevel level, Villager worker, BlockPos home, String role, BlockPos pos) {
        if (!level.isLoaded(pos) || !canWork(level, pos, home, role)) return false;
        BlockState state = level.getBlockState(pos);
        ItemStack tool = worker.getItemBySlot(EquipmentSlot.MAINHAND).copy();
        if (tool.isEmpty()) tool = new ItemStack(role.equals("lumberjack") ? Items.IRON_AXE
                : role.equals("miner") ? Items.IRON_PICKAXE : Items.IRON_HOE);
        // Ancient debris needs a diamond-tier pickaxe; never destroy blocks without their loot.
        if (role.equals("miner") && state.is(Blocks.ANCIENT_DEBRIS)
                && !tool.is(Items.DIAMOND_PICKAXE) && !tool.is(Items.NETHERITE_PICKAXE)) return false;
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, tool);
        boolean isCrop = role.equals("farmer") && state.getBlock() instanceof CropBlock;
        ItemStack seed = ItemStack.EMPTY;
        if (isCrop) {
            seed = switch (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()) {
                case "wheat" -> new ItemStack(Items.WHEAT_SEEDS);
                case "beetroots" -> new ItemStack(Items.BEETROOT_SEEDS);
                case "carrots" -> new ItemStack(Items.CARROT);
                case "potatoes" -> new ItemStack(Items.POTATO);
                default -> ItemStack.EMPTY;
            };
        }
        boolean replanted = false;
        if (!seed.isEmpty()) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && ItemStack.isSameItem(drop, seed)) {
                    drop.shrink(1);
                    replanted = true;
                    break;
                }
            }
        }
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return false;
        level.levelEvent(2001, pos, Block.getId(state));
        if (isCrop && replanted && state.getBlock() instanceof CropBlock crop) {
            level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        }
        int delivered = 0;
        int overflow = 0;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            int generated = drop.getCount();
            TownStorage.insert(level, home, drop); // Actual items, no synthetic resource creation.
            delivered += generated - drop.getCount();
            if (!drop.isEmpty()) {
                overflow += drop.getCount();
                Block.popResource(level, pos, drop);
            }
        }
        String kind = role.equals("lumberjack") ? "wood" : role.equals("miner") ? "mined" : "harvested";
        addMetric(level, worker, home, kind + "_blocks", 1);
        addMetric(level, worker, home, "deposited_items", delivered);
        addMetric(level, worker, home, "overflow_items", overflow);
        worker.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        return true;
    }

    static void recordMetric(ServerLevel level, Villager worker, BlockPos home, String key, int increment) {
        addMetric(level, worker, home, key, increment);
    }

    private static void addMetric(ServerLevel level, Villager worker, BlockPos home, String key, int increment) {
        if (increment <= 0) return;
        CompoundTag workerData = worker.getPersistentData();
        workerData.putInt("tf_stat_"+key, workerData.getIntOr("tf_stat_"+key, 0)+increment);
        CompoundTag hall = TownManager.data(level, home);
        hall.putInt("tf_stat_"+key, hall.getIntOr("tf_stat_"+key, 0)+increment);
        TownManager.save(level, home);
    }
}
