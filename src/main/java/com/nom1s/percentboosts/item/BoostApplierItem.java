package com.nom1s.percentboosts.item;

import com.nom1s.percentboosts.component.ModComponents;
import com.nom1s.percentboosts.client.BoostScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BoostApplierItem extends Item {
    public BoostApplierItem(Properties props) { super(props); }

    public static int balance(ItemStack stack) {
        return stack.getOrDefault(ModComponents.APPLIER_BALANCE.get(), 0);
    }

    public static void setBalance(ItemStack stack, int balance) {
        stack.set(ModComponents.APPLIER_BALANCE.get(), Math.max(0, balance));
        // Custom name also helps players see the balance in their inventory.
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Boost Applier (" + balance(stack) + "%)"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack applier = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (other.getItem() instanceof PercentageItem) {
            if (!level.isClientSide()) {
                int points = PercentageItem.value(other);
                setBalance(applier, Math.min(1_000_000, balance(applier) + points));
                if (!player.getAbilities().instabuild) other.shrink(1);
                player.sendOverlayMessage(Component.literal("Absorbed " + points + "%. Balance: " + balance(applier)));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            BoostScreen.open(applier, !other.isEmpty());
        }
        return InteractionResult.SUCCESS;
    }
}
