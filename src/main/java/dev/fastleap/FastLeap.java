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
		BLOOD_RUSH("Blood Rush"), MAXOR("Maxor"), STORM("Storm"), PRE_GOLDOR("Pre-Goldor"),
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
	private static int ticks;
	private static long lastLeap = -100_000, opened = -1; // ms, see now()
	private static String want; // class to leap to, fixed when the leap starts
	private static boolean hadCrystal;
	private static int autoAt = -1;

	private static long now() {
		return System.nanoTime() / 1_000_000;
	}

	// Slider position (0..1) -> ms. Squared so the low end, where you tune, gets most of the travel.
	static int scale(double t, int min, int max, int step) {
		t = Math.max(0, Math.min(1, t));
		int v = (int) Math.round((min + (max - min) * t * t) / step) * step;
		return Math.max(min, Math.min(max, v));
	}

	static String myClass(Minecraft mc) {
		return mc.player == null ? null : teammateClasses(mc).get(mc.player.getName().getString());
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.parse("fastleap:keys"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fastleap.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

		ClientTickEvents.START_CLIENT_TICK.register(FastLeap::startTick);
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
	// shortcut: Pre-Goldor starts on Storm's death line ("...stood no chance"), wording from memory and unverified; fix it here if the stage never switches
	static Stage stageFor(String s) {
		return s.startsWith("[BOSS] Maxor:") ? Stage.MAXOR
				: s.startsWith("[BOSS] Storm:") ? (s.contains("stood no chance") ? Stage.PRE_GOLDOR : Stage.STORM)
				: s.startsWith("[BOSS] Goldor:") ? Stage.GOLDOR
				: s.startsWith("[BOSS] Necron:") ? Stage.NECRON
				: s.startsWith("[BOSS] Wither King:") ? Stage.DRAGONS
				: null;
	}

	private static void onMessage(String s) {
		Stage next = stageFor(s);
		if (next != null && next.ordinal() > stage.ordinal()) stage = next;
	}

	// Runs before vanilla reads the mouse this tick, so when a leap starts we can swallow the click: no swing, no attack, no block hit.
	private static void startTick(Minecraft mc) {
		boolean down = mc.options.keyAttack.isDown();
		if (down && !wasDown && startLeap(mc, stage.name())) {
			while (mc.options.keyAttack.consumeClick()) { /* drain */ }
			mc.options.keyAttack.setDown(false); // else vanilla's "held" handling starts breaking the block you aim at
		}
		wasDown = down;
	}

	private static void tick(Minecraft mc) {
		ticks++;
		if (mc.level != level) { // new world / dungeon run
			level = mc.level;
			stage = Stage.BLOOD_RUSH;
			pending = 0;
			autoAt = -1;
		}
		while (menuKey.consumeClick()) mc.gui.setScreen(new FastLeapScreen());

		crystalLeap(mc);

		if (pending > 0 && (tryClick(mc) || --pending == 0)) pending = 0; // shortcut: no feedback when the target is dead/absent, the menu just stays open
	}

	// Picking up a crystal in the Maxor stage switches to the leap item, then leaps one tick later so the server knows the new slot.
	private static void crystalLeap(Minecraft mc) {
		if (mc.player == null) return;
		boolean has = Config.cfg.crystalLeap && stage == Stage.MAXOR && hasCrystal(mc);
		if (has && !hadCrystal) {
			int slot = leapSlot(mc);
			if (slot >= 0) {
				mc.player.getInventory().setSelectedSlot(slot);
				autoAt = ticks + 2;
			}
		}
		hadCrystal = has;
		if (autoAt >= 0 && ticks >= autoAt) {
			autoAt = -1;
			startLeap(mc, Config.CRYSTAL);
		}
	}

	private static boolean hasCrystal(Minecraft mc) {
		var inv = mc.player.getInventory();
		for (int i = 0; i < 36; i++) if (inv.getItem(i).getHoverName().getString().contains(Config.cfg.crystalItem)) return true;
		return false;
	}

	private static int leapSlot(Minecraft mc) {
		for (int i = 0; i < 9; i++) if (isLeapItem(mc.player.getInventory().getItem(i).getHoverName().getString())) return i;
		return -1;
	}

	private static boolean isLeapItem(String name) {
		return name.contains("Spirit Leap") || name.contains("Infinileap");
	}

	// true if a leap was started
	private static boolean startLeap(Minecraft mc, String key) {
		if (!Config.cfg.enabled || mc.player == null || mc.gameMode == null || mc.gui.screen() != null || pending > 0
				|| now() - lastLeap < Config.cfg.cooldownMs) return false; // pending: ignore click-spam while a leap is in flight
		if (!isLeapItem(mc.player.getMainHandItem().getHoverName().getString())) return false;
		String target = Config.cfg.get(myClass(mc), key);
		if (target.equals(Config.OFF)) return false; // vanilla click goes through untouched
		want = target;

		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // opens the leap menu
		pending = WAIT_TICKS + Config.cfg.clickDelayMs / 50; // the delay only starts once the menu is open
		lastLeap = now();
		opened = -1;
		return true;
	}

	private static boolean isLeapMenu(AbstractContainerScreen<?> s) {
		String t = s.getTitle().getString();
		return t.equals("Spirit Leap") || t.equals("Teleport to Player");
	}

	private static boolean tryClick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return false;
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !isLeapMenu(s)) return false;

		if (opened < 0) opened = now(); // delay counts from when the menu actually shows up, so ping doesn't eat it
		if (now() - opened < Config.cfg.clickDelayMs) return false; // shortcut: checked once per tick, so the real delay rounds up to the next 50 ms
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
