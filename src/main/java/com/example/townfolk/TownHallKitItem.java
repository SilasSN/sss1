package com.example.townfolk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class TownHallKitItem extends Item {
    public TownHallKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        BlockPos home = context.getClickedPos().above();
        if (!canBuild(level, home)) {
            player.sendOverlayMessage(Component.literal("Town Hall needs a clear 9x9 area, 5 blocks tall."));
            return InteractionResult.FAIL;
        }
        build(level, home);
        TownManager.initialize(level, home, (net.minecraft.server.level.ServerPlayer) player);
        for (String role : TownManager.ROLES) {
            TownManager.spawn(level, home, role);
        }
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        player.sendSystemMessage(Component.literal("Town Hall founded! Lumberjack, Miner, Farmer and Guard hired."));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static boolean canBuild(ServerLevel level, BlockPos home) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    BlockPos p = home.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) return false;
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.canBeReplaced()) return false;
                }
            }
        }
        return true;
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void build(ServerLevel level, BlockPos home) {
        // 9x9 village hall, oak floor, columns, windows, pitched-look flat roof.
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                place(level, home.offset(x, 0, z), Blocks.OAK_PLANKS);
                place(level, home.offset(x, 4, z), Blocks.SPRUCE_PLANKS);
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.abs(x) != 4 && Math.abs(z) != 4) continue;
                boolean pillar = Math.abs(x) == 4 && Math.abs(z) == 4;
                for (int y = 1; y <= 3; y++) {
                    // Doorway on north side: two-block opening.
                    if (z == -4 && x == 0 && y <= 2) continue;
                    Block wall = pillar ? Blocks.OAK_LOG : Blocks.OAK_PLANKS;
                    if (y == 2 && ((x == 0 && Math.abs(z) == 4) || (z == 0 && Math.abs(x) == 4))) {
                        wall = Blocks.GLASS;
                    }
                    place(level, home.offset(x, y, z), wall);
                }
            }
        }
        place(level, home.offset(2, 1, 2), Blocks.CHEST);
        place(level, home.offset(0, 1, 2), Blocks.BELL);
        place(level, home.offset(-2, 1, 2), Blocks.CRAFTING_TABLE);
    }

}
