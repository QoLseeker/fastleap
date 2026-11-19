package dev.fastleap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// Dark click-gui look (Odin / NoamAddons style): one panel, accent header, rows with a value or a toggle switch on the right.
final class FastLeapScreen extends Screen {
	private static final int PANEL = 0xF0141418, ROW = 0xFF1E1E26, ROW_HOVER = 0xFF2B2B38, ACCENT = 0xFF7C6CFF,
			TEXT = 0xFFE8E8F0, DIM = 0xFF8A8A99, RED = 0xFFFF5566, GREEN = 0xFF55E08A;
	private static final int ROW_H = 16, STEP = 18, LEFT_W = 150, RIGHT_W = 170, PAD = 8;

	private static String editing; // class whose targets the right column edits

	FastLeapScreen() {
		super(Component.literal("Fast Leap"));
	}

	private record Section(int x, int y, String title) {}

	private static final class Panel extends AbstractWidget {
		final List<Section> sections = new ArrayList<>();

		Panel(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty());
			active = false;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			int x = getX(), y = getY();
			g.fillGradient(x, y, x + width, y + height, PANEL, PANEL);
			g.fillGradient(x, y, x + width, y + 20, ACCENT, ACCENT);
			g.text(font, "Fast Leap", x + PAD, y + 6, 0xFFFFFFFF, true);
			String st = "Stage: " + FastLeap.stage.label;
			g.text(font, st, x + width - PAD - font.width(st), y + 6, 0xFFFFFFFF, true);
			for (Section s : sections) g.text(font, s.title.toUpperCase(), s.x, s.y, DIM, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	// A label with either a toggle switch (toggle != null) or a text value on the right; clicking runs onPress.
	private static final class Row extends Button {
		final String label;
		final BooleanSupplier toggle;
		final Supplier<String> value;
		final BooleanSupplier current; // marks the row of the stage we are in

		Row(int x, int y, int w, String label, BooleanSupplier toggle, Supplier<String> value, BooleanSupplier current, Runnable onPress) {
			super(x, y, w, ROW_H, Component.empty(), b -> onPress.run(), DEFAULT_NARRATION);
			this.label = label;
			this.toggle = toggle;
			this.value = value;
			this.current = current;
		}

		@Override
		public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			int x = getX(), y = getY(), ty = y + (height - 8) / 2;
			int bg = isHovered() ? ROW_HOVER : ROW;
			g.fillGradient(x, y, x + width, y + height, bg, bg);
			if (current != null && current.getAsBoolean()) g.fillGradient(x, y, x + 2, y + height, ACCENT, ACCENT);
			g.text(font, label, x + 6, ty, TEXT, false);
			if (toggle != null) {
				boolean on = toggle.getAsBoolean();
				int sx = x + width - 26, sy = y + 4;
				int track = on ? GREEN : 0xFF45454F;
				g.fillGradient(sx, sy, sx + 20, sy + 8, track, track);
				int kx = on ? sx + 12 : sx;
				g.fillGradient(kx, sy - 1, kx + 8, sy + 9, 0xFFFFFFFF, 0xFFFFFFFF);
			} else {
				String v = value.get();
				g.text(font, v, x + width - 6 - font.width(v), ty, v.equals(Config.OFF) ? RED : ACCENT, false);
			}
		}
	}

	// Draggable ms slider; the track is squared (see FastLeap.scale) so short delays are easy to dial in.
	private static final class Slider extends AbstractWidget {
		final String label;
		final int min, max;
		final IntSupplier get;
		final IntConsumer set;

		Slider(int x, int y, int w, String label, int min, int max, IntSupplier get, IntConsumer set) {
			super(x, y, w, ROW_H, Component.empty());
			this.label = label;
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
		}

		private void setFrom(double mouseX) {
			set.accept(FastLeap.scale((mouseX - getX() - 6) / (width - 12), min, max, 10));
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean doubleClick) {
			setFrom(e.x());
		}

		@Override
		public void onDrag(MouseButtonEvent e, double dx, double dy) {
			setFrom(e.x());
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			var font = Minecraft.getInstance().font;
			int x = getX(), y = getY(), ty = y + 2;
			int bg = isHovered() ? ROW_HOVER : ROW;
			g.fillGradient(x, y, x + width, y + height, bg, bg);
			g.text(font, label, x + 6, ty, TEXT, false);
			String v = get.getAsInt() + " ms";
			g.text(font, v, x + width - 6 - font.width(v), ty, ACCENT, false);
			int tx = x + 6, tw = width - 12, ky = y + height - 3;
			g.fillGradient(tx, ky, tx + tw, ky + 2, 0xFF45454F, 0xFF45454F);
			int kx = tx + (int) (Math.sqrt((double) (get.getAsInt() - min) / (max - min)) * tw);
			g.fillGradient(tx, ky, kx, ky + 2, ACCENT, ACCENT);
			g.fillGradient(kx - 1, ky - 2, kx + 2, ky + 4, 0xFFFFFFFF, 0xFFFFFFFF);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {}
	}

	@Override
	protected void init() {
		if (editing == null) {
			String me = FastLeap.myClass(Minecraft.getInstance());
			editing = me != null ? me : Config.CLASSES[0];
		}
		Config c = Config.cfg;
		int rightRows = 1 + FastLeap.Stage.values().length + 1; // class selector + stages + crystal
		int bodyH = 12 + rightRows * STEP;
		int w = PAD + LEFT_W + PAD + RIGHT_W + PAD, h = 20 + PAD + bodyH + PAD / 2 + ROW_H + PAD;
		int px = width / 2 - w / 2, py = Math.max(4, height / 2 - h / 2);
		int lx = px + PAD, rx = lx + LEFT_W + PAD, top = py + 20 + PAD;

		Panel panel = new Panel(px, py, w, h);
		addRenderableWidget(panel);

		// left column: general + timing
		int y = top;
		panel.sections.add(new Section(lx, y, "General"));
		y += 12;
		addRenderableWidget(new Row(lx, y, LEFT_W, "Enabled", () -> c.enabled, null, null, () -> c.enabled = !c.enabled));
		y += STEP;
		addRenderableWidget(new Row(lx, y, LEFT_W, "Crystal auto-leap", () -> c.crystalLeap, null, null, () -> c.crystalLeap = !c.crystalLeap));
		y += STEP + 6;
		panel.sections.add(new Section(lx, y, "Timing"));
		y += 12;
		addRenderableWidget(new Slider(lx, y, LEFT_W, "Click delay", Config.MIN_DELAY_MS, Config.MAX_MS, () -> c.clickDelayMs, v -> c.clickDelayMs = v));
		y += STEP;
		addRenderableWidget(new Slider(lx, y, LEFT_W, "Cooldown", 0, Config.MAX_MS, () -> c.cooldownMs, v -> c.cooldownMs = v));

		// right column: per-class targets, "Off" leaves the click alone for that stage
		y = top;
		panel.sections.add(new Section(rx, y, "Targets"));
		y += 12;
		addRenderableWidget(new Row(rx, y, RIGHT_W, "Editing for", null, () -> editing, null,
				() -> editing = Config.CLASSES[(Arrays.asList(Config.CLASSES).indexOf(editing) + 1) % Config.CLASSES.length]));
		for (FastLeap.Stage s : FastLeap.Stage.values()) {
			y += STEP;
			addRenderableWidget(new Row(rx, y, RIGHT_W, s.label, null, () -> c.get(editing, s.name()), () -> FastLeap.stage == s,
					() -> c.cycle(editing, s.name())));
		}
		y += STEP;
		addRenderableWidget(new Row(rx, y, RIGHT_W, "Crystal", null, () -> c.get(editing, Config.CRYSTAL), null,
				() -> c.cycle(editing, Config.CRYSTAL)));

		y = top + bodyH + PAD / 2;
		addRenderableWidget(new Row(lx, y, w - 2 * PAD, "Done", null, () -> "", null, this::onClose));
	}

	@Override
	public void onClose() {
		Config.save();
		super.onClose();
	}
}
