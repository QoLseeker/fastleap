package dev.fastleap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FastLeap implements ClientModInitializer {

	public enum Stage {
		BLOOD_RUSH("Blood Rush"), MAXOR("Maxor"), STORM("Storm"),
		GOLDOR("Goldor"), NECRON("Necron"), DRAGONS("Dragons");

		final String label;

		Stage(String label) { this.label = label; }
	}

	// The name is the word right before "(Class ...)", so ranks ("[MVP+]"), level tags and trailing symbols don't matter.
	static final Pattern TAB = Pattern.compile("(\\w{1,16})[^\\w(]*\\((Archer|Berserk|Healer|Mage|Tank)\\b");
	private static final Pattern HEAD_NAME = Pattern.compile("(?:\\[.+?] )?(\\w{1,16})");
	private static final int WAIT_TICKS = 40; // how long we wait for the leap menu to fill

	static Stage stage = Stage.BLOOD_RUSH;

	private static KeyMapping menuKey;
	private static Object level;
	private static int pending;
	private static boolean wasDown;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.parse("fastleap:keys"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fastleap.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

		ClientTickEvents.END_CLIENT_TICK.register(FastLeap::tick);
		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> onMessage(msg.getString()));
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(
				ClientCommands.literal("fastleap").executes(c -> {
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> mc.gui.setScreen(new FastLeapScreen()));
					return 1;
				})));
	}

	// shortcut: stage only moves forward on boss chat lines; Dragons starts when the Wither King first speaks
	private static void onMessage(String s) {
		Stage next = s.startsWith("[BOSS] Maxor:") ? Stage.MAXOR
				: s.startsWith("[BOSS] Storm:") ? Stage.STORM
				: s.startsWith("[BOSS] Goldor:") ? Stage.GOLDOR
				: s.startsWith("[BOSS] Necron:") ? Stage.NECRON
				: s.startsWith("[BOSS] Wither King:") ? Stage.DRAGONS
				: null;
		if (next != null && next.ordinal() > stage.ordinal()) stage = next;
	}

	private static void tick(Minecraft mc) {
		if (mc.level != level) { // new world / dungeon run
			level = mc.level;
			stage = Stage.BLOOD_RUSH;
			pending = 0;
		}
		while (menuKey.consumeClick()) mc.gui.setScreen(new FastLeapScreen());

		// shortcut: the left click still reaches vanilla (can hit a mob you aim at); cancel it with a mixin if that matters
		boolean down = mc.options.keyAttack.isDown();
		if (down && !wasDown) startLeap(mc);
		wasDown = down;

		if (pending > 0 && (tryClick(mc) || --pending == 0)) pending = 0; // shortcut: no feedback when the target is dead/absent, the menu just stays open
	}

	private static void startLeap(Minecraft mc) {
		if (!Config.cfg.enabled || mc.player == null || mc.gameMode == null || mc.gui.screen() != null || pending > 0) return; // pending: ignore click-spam while a leap is in flight
		String n = mc.player.getMainHandItem().getHoverName().getString();
		if (!n.contains("Spirit Leap") && !n.contains("Infinileap")) return;

		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // opens the leap menu
		pending = WAIT_TICKS;
	}

	private static boolean isLeapMenu(AbstractContainerScreen<?> s) {
		String t = s.getTitle().getString();
		return t.equals("Spirit Leap") || t.equals("Teleport to Player");
	}

	private static boolean tryClick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return false;
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !isLeapMenu(s)) return false;

		String want = Config.cfg.get(stage);
		Map<String, String> classes = teammateClasses(mc);
		var slots = s.getMenu().slots;
		for (int i = 0; i < slots.size() - 36; i++) { // last 36 slots are the player inventory
			ItemStack st = slots.get(i).getItem();
			if (!st.is(Items.PLAYER_HEAD)) continue;
			Matcher m = HEAD_NAME.matcher(st.getHoverName().getString().replaceAll("§.", ""));
			if (m.find() && want.equalsIgnoreCase(classes.get(m.group(1)))) {
				mc.gameMode.handleContainerInput(s.getMenu().containerId, i, 0, ContainerInput.PICKUP, mc.player); // the server closes the menu itself
				return true;
			}
		}
		return false; // not there yet (menu still filling) or that class is dead / absent
	}

	private static Map<String, String> teammateClasses(Minecraft mc) {
		Map<String, String> out = new HashMap<>();
		if (mc.getConnection() == null) return out;
		for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
			if (info.getTabListDisplayName() == null) continue;
			Matcher m = TAB.matcher(info.getTabListDisplayName().getString());
			if (m.find()) out.put(m.group(1), m.group(2));
		}
		return out;
	}
}
