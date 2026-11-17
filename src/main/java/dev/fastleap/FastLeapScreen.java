package dev.fastleap;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class FastLeapScreen extends Screen {
	FastLeapScreen() {
		super(Component.literal("Fast Leap"));
	}

	private static Component toggleText() {
		return Component.literal("Fast Leap: " + (Config.cfg.enabled ? "ON" : "OFF"));
	}

	private static Component stageText(FastLeap.Stage s) {
		return Component.literal(s.label + ": " + Config.cfg.get(s));
	}

	@Override
	protected void init() {
		int w = 200, x = width / 2 - w / 2, y = Math.max(8, height / 2 - 110);

		addRenderableWidget(Button.builder(toggleText(), b -> {
			Config.cfg.enabled = !Config.cfg.enabled;
			b.setMessage(toggleText());
		}).bounds(x, y, w, 20).build());

		y += 24;
		Button current = Button.builder(Component.literal("Current stage: " + FastLeap.stage.label), b -> {})
				.bounds(x, y, w, 20).build();
		current.active = false;
		addRenderableWidget(current);

		for (FastLeap.Stage s : FastLeap.Stage.values()) {
			y += 24;
			addRenderableWidget(Button.builder(stageText(s), b -> {
				Config.cfg.cycle(s);
				b.setMessage(stageText(s));
			}).bounds(x, y, w, 20).build());
		}

		y += 30;
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y, w, 20).build());
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
