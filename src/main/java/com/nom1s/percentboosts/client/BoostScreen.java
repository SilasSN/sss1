package com.nom1s.percentboosts.client;

import com.nom1s.percentboosts.attribute.BoostData;
import com.nom1s.percentboosts.item.BoostApplierItem;
import com.nom1s.percentboosts.network.ApplyBoostPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * New 26.3 screen reconstructed from the original menu's functionality.
 * Points and actual boosts are validated on the server (ApplyBoostPacket).
 */
public final class BoostScreen extends Screen {
    private static final int PER_PAGE = 6;
    private final ItemStack openedApplier;
    private final boolean forItem;
    private final int page;
    private final List<String> keys;
    private EditBox amountBox;

    private BoostScreen(ItemStack applier, boolean forItem, int page) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
            Component.literal("PercentBoosts"));
        this.openedApplier = applier;
        this.forItem = forItem;
        this.page = Math.max(0, page);
        this.keys = new ArrayList<>();
        BoostData.AVAILABLE.keySet().stream()
            .filter(key -> !forItem || !isCustom(key))
            .forEach(keys::add);
    }

    private static boolean isCustom(String key) {
        return switch (key) {
            case "crit_damage", "xp_multiplier", "item_use_speed",
                 "projectile_speed", "projectile_damage", "damage_undead",
                 "damage_bosses", "enchant_cost", "repair_cost" -> true;
            default -> false;
        };
    }

    public static void open(ItemStack applier, boolean forItem) {
        Minecraft.getInstance().setScreen(new BoostScreen(applier, forItem, 0));
    }

    private int amount() {
        if (amountBox == null) return 1;
        try {
            return Math.max(1, Math.min(1000, Integer.parseInt(amountBox.getValue().trim())));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void request(String key, int sign) {
        ClientPacketDistributor.sendToServer(new ApplyBoostPacket(key, sign * amount(), false, forItem));
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int top = Math.max(30, height / 2 - 103);
        amountBox = new EditBox(this.font, left + 236, top + 4, 70, 20,
            Component.literal("Amount of percent points"));
        amountBox.setValue("1");
        addRenderableWidget(amountBox);

        int start = page * PER_PAGE;
        int end = Math.min(keys.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            String key = keys.get(i);
            int y = top + 34 + (i - start) * 24;
            addRenderableWidget(Button.builder(Component.literal("+"), b -> request(key, 1))
                .bounds(left + 208, y, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal("-"), b -> request(key, -1))
                .bounds(left + 240, y, 28, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reset"),
                b -> ClientPacketDistributor.sendToServer(new ApplyBoostPacket(key, 0, true, forItem)))
                .bounds(left + 272, y, 42, 20).build());
        }
        int pages = Math.max(1, (keys.size() + PER_PAGE - 1) / PER_PAGE);
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("< Previous"),
            b -> navigate(page - 1)).bounds(left, top + 181, 87, 20).build());
        if (page + 1 < pages) addRenderableWidget(Button.builder(Component.literal("Next >"),
            b -> navigate(page + 1)).bounds(left + 227, top + 181, 87, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(left + 112, top + 181, 90, 20).build());
    }

    private void navigate(int targetPage) {
        Minecraft.getInstance().setScreen(new BoostScreen(openedApplier, forItem, targetPage));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - 155;
        int top = Math.max(30, height / 2 - 103);
        graphics.text(font, "PercentBoosts | " + (forItem ? "Item (2x effect)" : "Player"),
            left, top - 23, 0xFFFFFFFF, true);
        graphics.text(font, "Available: " + BoostApplierItem.balance(openedApplier)
            + "%     Spend per click:", left, top + 10, 0xFFFFFFFF, true);
        int start = page * PER_PAGE;
        int end = Math.min(keys.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            String readable = keys.get(i).replace('_', ' ');
            graphics.text(font, readable, left + 3,
                top + 40 + (i - start) * 24, 0xFFE9E9E9, false);
        }
        int pages = Math.max(1, (keys.size() + PER_PAGE - 1) / PER_PAGE);
        graphics.text(font, "Page " + (page + 1) + " / " + pages + "   |   Reset is free",
            left, top + 163, 0xFFB8C7D8, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
