package dev.sbkit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.stream.Collectors;

public final class Util {
    private Util() {}

    public static long now() { return System.nanoTime() / 1_000_000; }

    public static String strip(String s) { return s.replaceAll("§.", ""); }

    public static String lower(String s) { return strip(s).toLowerCase(Locale.ROOT); }

    public static String name(ItemStack s) { return s.getHoverName().getString(); }

    public static String heldName(Minecraft mc) { return name(mc.player.getMainHandItem()); }

    /** SkyBlock item id (e.g. RAGNAROCK_AXE) from the item's custom data, or "". Survives reforges and renames. */
    public static String skyblockId(ItemStack s) {
        return s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("id").orElse("");
    }

    /** All lore lines joined with newlines, formatting stripped. */
    public static String lore(ItemStack s) {
        return s.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).styledLines().stream()
            .map(Component::getString).collect(Collectors.joining("\n"));
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
    }
}
