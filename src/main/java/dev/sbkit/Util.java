package dev.sbkit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** Every tab-list line, formatting stripped. */
    public static List<String> tabLines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (mc.getConnection() == null) return out;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getTabListDisplayName() != null) out.add(info.getTabListDisplayName().getString());
        }
        return out;
    }

    /** Player name -> dungeon class, read from the tab list. */
    public static Map<String, String> teammateClasses(Minecraft mc) {
        Map<String, String> out = new HashMap<>();
        for (String line : tabLines(mc)) {
            String[] m = Dungeon.member(line);
            if (m != null) out.put(m[0], m[1]);
        }
        return out;
    }

    public static String myClass(Minecraft mc) {
        return mc.player == null ? null : teammateClasses(mc).get(mc.player.getName().getString());
    }

    /** Dungeon team score from the tab list, or -1 when no line shows one. */
    public static int dungeonScore(Minecraft mc) {
        for (String line : tabLines(mc)) {
            int s = Dungeon.score(line);
            if (s >= 0) return s;
        }
        return -1;
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
    }
}
