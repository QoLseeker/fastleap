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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Two jobs, both for M7: left click with a leap item during Blood Rush, and an auto leap after you pick up a Maxor crystal.
public final class FastLeap implements ClientModInitializer {

	// Blood Rush is everything before the boss. Maxor starts on his first line; Later on any other F7 boss line (not "The Watcher", he speaks in the blood room).
	public enum Stage {
		BLOOD_RUSH("Blood Rush"), MAXOR("Maxor"), LATER("Later");

		final String label;

		Stage(String label) { this.label = label; }
	}

	// The name is the word right before "(Class ...)", so ranks ("[MVP+]"), level tags and trailing symbols don't matter.
	static final Pattern TAB = Pattern.compile("(\\w{1,16})[^\\w(]*\\((Archer|Berserk|Healer|Mage|Tank)\\b");
	// The pickup line NoammAddons' Maxor crystal feature uses, plus an optional rank tag and "You" for your own pickup.
	static final Pattern PICKUP = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w+) picked up an Energy Crystal!");
	private static final Pattern LATER = Pattern.compile("^\\[BOSS] (Storm|Goldor|Necron|Wither King):");
	private static final Pattern HEAD_NAME = Pattern.compile("(?:\\[.+?] )?(\\w{1,16})");
	private static final int WAIT_TICKS = 40; // how long we wait for the leap menu to fill
	private static final long NONE = Long.MIN_VALUE, INFINILEAP_COOLDOWN_MS = 2000;

	static Stage stage = Stage.BLOOD_RUSH;

	private static KeyMapping menuKey;
	private static Object level;
	private static int pending;
	private static boolean wasDown;
	private static long lastLeap = NONE / 2, opened = NONE, autoAt = NONE; // ms, see now()
	private static String want; // class to leap to, fixed when the leap starts

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

	// shortcut: stage only moves forward on chat lines, so a reconnect mid-boss starts at Blood Rush until the next boss line
	static Stage stageFor(String s) {
		return s.startsWith("[BOSS] Maxor:") ? Stage.MAXOR : LATER.matcher(s).find() ? Stage.LATER : null;
	}

	// Local chat line, only with "Debug messages" on: says why a feature did or didn't fire.
	private static void debug(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (Config.cfg.debug && mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7[FastLeap] " + msg));
	}

	private static void onMessage(String s) {
		Stage next = stageFor(s);
		if (next != null && next.ordinal() > stage.ordinal()) {
			stage = next;
			debug("stage is now " + next.label);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) crystalPickup(mc, s);
	}

	// Own crystal pickup in Maxor's stage: switch to the leap item now, leap two ticks later so the server knows the new slot.
	private static void crystalPickup(Minecraft mc, String s) {
		Matcher m = PICKUP.matcher(s);
		if (!m.find()) {
			if (s.toLowerCase().contains("crystal")) debug("crystal line, not matched: " + s); // so we can see the real wording
			return;
		}
		String why = !Config.cfg.crystalLeap ? "Crystal auto-leap is off"
				: stage != Stage.MAXOR ? "stage is " + stage.label + ", not Maxor"
				: mc.gui.screen() != null ? "a screen is open (a leap menu included)"
				: !m.group(1).equalsIgnoreCase("You") && !m.group(1).equalsIgnoreCase(mc.player.getName().getString()) ? m.group(1) + " picked it up, not you"
				: null;
		if (why != null) {
			debug("crystal leap skipped: " + why);
			return;
		}
		boolean holding = isLeapItem(heldName(mc)); // keep the leap you already hold (Infinileap vs Spirit Leap have different cooldowns)
		int slot = holding ? -1 : leapSlot(mc);
		if ((!holding && slot < 0) || target(mc, Config.CRYSTAL) == null) {
			debug("crystal leap skipped: no leap item in your hotbar, or the Crystal target is Off");
			return;
		}
		if (!holding) mc.player.getInventory().setSelectedSlot(slot);
		autoAt = now() + 100;
		debug("crystal picked up, " + (holding ? "already holding a leap" : "switched to hotbar slot " + (slot + 1)) + ", leaping in 100 ms");
	}

	// Runs before vanilla reads the mouse this tick, so when a leap starts we can swallow the click: no swing, no attack, no block hit.
	private static void startTick(Minecraft mc) {
		boolean down = mc.options.keyAttack.isDown();
		String target = down && !wasDown && stage == Stage.BLOOD_RUSH ? leapTarget(mc, Config.BLOOD_RUSH) : null;
		if (target != null) { // also swallowed during cooldown / in-flight, else a spam click would swing
			startLeap(mc, target);
			while (mc.options.keyAttack.consumeClick()) { /* drain */ }
			mc.options.keyAttack.setDown(false); // else vanilla's "held" handling starts breaking the block you aim at
		}
		wasDown = down;
	}

	private static void tick(Minecraft mc) {
		if (mc.level != level) { // new world / dungeon run
			level = mc.level;
			stage = Stage.BLOOD_RUSH;
			pending = 0;
			autoAt = NONE;
		}
		while (menuKey.consumeClick()) mc.gui.setScreen(new FastLeapScreen());

		if (autoAt != NONE && now() >= autoAt) {
			autoAt = NONE;
			String t = leapTarget(mc, Config.CRYSTAL);
			if (t == null) debug("auto leap dropped: holding \"" + heldName(mc) + "\", screen " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
			startLeap(mc, t);
		}
		if (pending > 0 && (tryClick(mc) || --pending == 0)) pending = 0; // shortcut: no feedback when the target is dead/absent, the menu just stays open
	}

	private static int leapSlot(Minecraft mc) {
		for (int i = 0; i < 9; i++) if (isLeapItem(mc.player.getInventory().getItem(i).getHoverName().getString())) return i;
		return -1;
	}

	private static boolean isLeapItem(String name) {
		return name.contains("Spirit Leap") || name.contains("Infinileap");
	}

	// The class to leap to for this key, or null when the mod is off or the key is set to Off.
	private static String target(Minecraft mc, String key) {
		if (!Config.cfg.enabled || mc.player == null) return null;
		String t = Config.cfg.get(myClass(mc), key);
		return t.equals(Config.OFF) ? null : t;
	}

	// Same, but only when a leap item is in hand and no screen is open, i.e. when the click or the auto leap is really ours.
	private static String leapTarget(Minecraft mc, String key) {
		if (mc.player == null || mc.gui.screen() != null || !isLeapItem(heldName(mc))) return null;
		return target(mc, key);
	}

	private static String heldName(Minecraft mc) {
		return mc.player.getMainHandItem().getHoverName().getString();
	}

	// Infinileap can't be used again for 2 s, so never start a leap sooner than that with it.
	static long cooldownFor(String held, int userMs) {
		return held.contains("Infinileap") ? Math.max(INFINILEAP_COOLDOWN_MS, userMs) : userMs;
	}

	private static void startLeap(Minecraft mc, String target) {
		if (target == null) return;
		if (mc.gameMode == null || pending > 0 // pending: ignore click-spam while a leap is in flight
				|| now() - lastLeap < cooldownFor(heldName(mc), Config.cfg.cooldownMs)) {
			debug("leap not started: " + (pending > 0 ? "the last one is still in flight" : "cooldown"));
			return;
		}
		want = target;
		debug("leap started, target " + target);

		mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); // opens the leap menu
		pending = WAIT_TICKS + Config.cfg.clickDelayMs / 50; // the delay only starts once the menu is open
		lastLeap = now();
		opened = NONE;
	}

	private static boolean isLeapMenu(AbstractContainerScreen<?> s) {
		String t = s.getTitle().getString();
		return t.equals("Spirit Leap") || t.equals("Teleport to Player");
	}

	// true when this leap is over: we clicked, or the menu was open and is gone (you pressed Esc, or the server closed it)
	private static boolean tryClick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return false;
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || !isLeapMenu(s)) return opened != NONE;

		if (opened == NONE) opened = now(); // delay counts from when the menu actually shows up, so ping doesn't eat it
		if (now() - opened < Config.cfg.clickDelayMs) return false; // shortcut: checked once per tick, so the real delay rounds up to the next 50 ms
		Map<String, String> classes = teammateClasses(mc);
		var slots = s.getMenu().slots;
		for (int i = 0; i < slots.size() - 36; i++) { // last 36 slots are the player inventory
			ItemStack st = slots.get(i).getItem();
			if (!st.is(Items.PLAYER_HEAD)) continue;
			Matcher m = HEAD_NAME.matcher(st.getHoverName().getString().replaceAll("§.", ""));
			if (m.find() && want.equalsIgnoreCase(classes.get(m.group(1)))) {
				mc.gameMode.handleContainerInput(s.getMenu().containerId, i, 0, ContainerInput.PICKUP, mc.player); // the server closes the menu itself
				debug("clicked " + want);
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
