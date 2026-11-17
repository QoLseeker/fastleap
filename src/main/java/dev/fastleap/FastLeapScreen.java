package dev.fastleap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

final class FastLeapScreen extends Screen {
	private static String editing; // class whose targets the buttons edit
	FastLeapScreen() {
		super(Component.literal("Fast Leap"));
	}

	private static Component toggleText() {
		return Component.literal("Fast Leap: " + (Config.cfg.enabled ? "ON" : "OFF"));
	}

	private static Component stageText(FastLeap.Stage s) {
		return Component.literal(s.label + ": " + Config.cfg.get(editing, s));
	}

	private static Component delayText() {
		return Component.literal("Click delay: " + Config.cfg.clickDelayTicks + " ticks");
	}

	private static Component cooldownText() {
		return Component.literal("Cooldown: " + Config.cfg.cooldownTicks + " ticks");
	}

	@Override
	protected void init() {
		if (editing == null) {
			String me = FastLeap.myClass(Minecraft.getInstance());
			editing = me != null ? me : Config.CLASSES[0];
		}
		int w = 200, x = width / 2 - w / 2, y = Math.max(8, height / 2 - 140);

		addRenderableWidget(Button.builder(toggleText(), b -> {
			Config.cfg.enabled = !Config.cfg.enabled;
			b.setMessage(toggleText());
		}).bounds(x, y, w, 20).build());

		y += 24;
		Button current = Button.builder(Component.literal("Current stage: " + FastLeap.stage.label), b -> {})
				.bounds(x, y, w, 20).build();
		current.active = false;
		addRenderableWidget(current);

		y += 24;
		addRenderableWidget(Button.builder(Component.literal("Editing for: " + editing), b -> {
			editing = Config.CLASSES[(Arrays.asList(Config.CLASSES).indexOf(editing) + 1) % Config.CLASSES.length];
			rebuildWidgets();
		}).bounds(x, y, w, 20).build());

		for (FastLeap.Stage s : FastLeap.Stage.values()) {
			y += 24;
			addRenderableWidget(Button.builder(stageText(s), b -> {
				Config.cfg.cycle(editing, s);
				b.setMessage(stageText(s));
			}).bounds(x, y, w, 20).build());
		}

		y += 24;
		addRenderableWidget(Button.builder(delayText(), b -> {
			Config.cfg.clickDelayTicks = Config.cfg.clickDelayTicks % 10 + 1;
			b.setMessage(delayText());
		}).bounds(x, y, w, 20).build());

		y += 24;
		addRenderableWidget(Button.builder(cooldownText(), b -> {
			Config.cfg.cooldownTicks = (Config.cfg.cooldownTicks + 5) % 45;
			b.setMessage(cooldownText());
		}).bounds(x, y, w, 20).build());

		y += 30;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, w, 20).build());
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
