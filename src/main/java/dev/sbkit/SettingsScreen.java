package dev.sbkit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * One bar per feature, in the fastleap 1.0.0 look. Left click toggles the feature, right click expands its
 * settings underneath (one at a time, so it always fits on screen).
 */
final class SettingsScreen extends Screen {
    private static final int PANEL = -267119592, ROW = -14803418, ROW_HOVER = -13948104, ACCENT = -8622849,
        TEXT = -1513232, RED = -43674, GREEN = -11149174, TRACK = -12237489;
    private static final int ROW_H = 16, STEP = 18, PAD = 8, W = 230, INDENT = 6;

    /** The expanded feature. Static so the screen reopens the way you left it. */
    private static Feature open;

    SettingsScreen() { super(Component.literal("SB Kit")); }

    private static void rebuild() { Minecraft.getInstance().gui.setScreen(new SettingsScreen()); }

    @Override
    protected void init() {
        // "Enabled" is the bar's own switch, so it is not repeated in the list.
        List<Setting<?>> rows = open == null ? List.of()
            : open.settings.stream().filter(s -> s != open.enabled && s.visible.getAsBoolean()).toList();
        int body = (Features.ALL.size() + rows.size() - 1) * STEP + ROW_H;
        int panelH = 28 + body + PAD;
        int px = (width - W) / 2, py = Math.max(4, height / 2 - panelH / 2), y = py + 28;
        addRenderableWidget(new Panel(px, py, W, panelH,
            () -> open == null ? "right-click for settings" : open.status()));

        for (Feature f : Features.ALL) {
            Row bar = addRenderableWidget(new Row(px + PAD, y, W - 2 * PAD, f.name, f::on, null,
                () -> f == open, () -> f.enabled.set(!f.on())));
            bar.onRight = () -> { open = open == f ? null : f; rebuild(); };
            y += STEP;
            if (f != open) continue;
            for (Setting<?> s : rows) { addRow(s, px + PAD + INDENT, y, W - 2 * PAD - INDENT); y += STEP; }
        }
    }

    private void addRow(Setting<?> s, int x, int y, int w) {
        if (s instanceof Setting.Bool v) {
            addRenderableWidget(new Row(x, y, w, v.label, v::get, null, null, () -> v.set(!v.get())));
        } else if (s instanceof Setting.Choice v) {
            // Cycling can change which rows are visible (e.g. "Editing for"), so rebuild.
            addRenderableWidget(new Row(x, y, w, v.label, null, v::get, v.highlight, () -> { v.cycle(); rebuild(); }));
        } else if (s instanceof Setting.Int v) {
            addRenderableWidget(new Slider(x, y, w, v));
        } else if (s instanceof Setting.Text v) {
            addRenderableWidget(new Row(x, y, 58, v.label, null, () -> "", null, () -> {}));
            EditBox box = new EditBox(Minecraft.getInstance().font, x + 60, y, w - 60, ROW_H, Component.literal(v.label));
            box.setMaxLength(40);
            box.setValue(v.get());
            box.setResponder(v::set);
            addRenderableWidget(box);
        }
    }

    @Override
    public void removed() { Config.save(Features.ALL); }

    // ---- widgets (look ported from fastleap 1.0.0) ----------------------------------------------------------

    private static final class Panel extends AbstractWidget {
        private final Supplier<String> status;

        Panel(int x, int y, int w, int h, Supplier<String> status) {
            super(x, y, w, h, Component.empty());
            this.status = status;
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY();
            g.fillGradient(x, y, x + width, y + height, PANEL, PANEL);
            g.fillGradient(x, y, x + width, y + 20, ACCENT, ACCENT);
            g.text(font, "SB Kit", x + 8, y + 6, -1, true);
            String s = status.get();
            g.text(font, s, x + width - 8 - font.width(s), y + 6, -1, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** A flat row: label on the left, then an on/off switch or a value on the right. Left click = action. */
    private static final class Row extends Button {
        private final String label;
        private final BooleanSupplier toggle, current;
        private final Supplier<String> value;
        /** Optional right-click action. */
        Runnable onRight;

        Row(int x, int y, int w, String label, BooleanSupplier toggle, Supplier<String> value, BooleanSupplier current, Runnable action) {
            super(x, y, w, ROW_H, Component.empty(), b -> action.run(), DEFAULT_NARRATION);
            this.label = label;
            this.toggle = toggle;
            this.value = value;
            this.current = current;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
            if (onRight != null && e.button() == 1 && isMouseOver(e.x(), e.y())) {
                onRight.run();
                return true;
            }
            return super.mouseClicked(e, doubleClick);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), ty = y + (height - 8) / 2;
            int bg = isHovered() ? ROW_HOVER : ROW;
            g.fillGradient(x, y, x + width, y + height, bg, bg);
            if (current != null && current.getAsBoolean()) g.fillGradient(x, y, x + 2, y + height, ACCENT, ACCENT);
            g.text(font, label, x + 6, ty, TEXT, false);

            if (toggle != null) {
                boolean on = toggle.getAsBoolean();
                int tx = x + width - 26, tyy = y + 4, c = on ? GREEN : TRACK;
                g.fillGradient(tx, tyy, tx + 20, tyy + 8, c, c);
                int kx = on ? tx + 12 : tx;
                g.fillGradient(kx, tyy - 1, kx + 8, tyy + 9, -1, -1);
            } else {
                String v = value.get();
                g.text(font, v, x + width - 6 - font.width(v), ty, v.equals("Off") ? RED : ACCENT, false);
            }
        }
    }

    /** Drag slider with the squared curve from the old mod. */
    private static final class Slider extends AbstractWidget {
        private final Setting.Int setting;

        Slider(int x, int y, int w, Setting.Int setting) {
            super(x, y, w, ROW_H, Component.empty());
            this.setting = setting;
        }

        private void setFrom(double mouseX) { setting.set(setting.fromFraction((mouseX - getX() - 6) / (width - 12))); }

        @Override public void onClick(MouseButtonEvent e, boolean doubleClick) { setFrom(e.x()); }
        @Override public void onDrag(MouseButtonEvent e, double dx, double dy) { setFrom(e.x()); }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), ty = y + 2;
            int bg = isHovered() ? ROW_HOVER : ROW;
            g.fillGradient(x, y, x + width, y + height, bg, bg);
            g.text(font, setting.label, x + 6, ty, TEXT, false);
            String v = String.valueOf(setting.get());
            g.text(font, v, x + width - 6 - font.width(v), ty, ACCENT, false);

            int tx = x + 6, tw = width - 12, trackY = y + height - 3;
            g.fillGradient(tx, trackY, tx + tw, trackY + 2, TRACK, TRACK);
            int fx = tx + (int) (Math.sqrt((setting.get() - setting.min) / (double) (setting.max - setting.min)) * tw);
            g.fillGradient(tx, trackY, fx, trackY + 2, ACCENT, ACCENT);
            g.fillGradient(fx - 1, trackY - 2, fx + 2, trackY + 4, -1, -1);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }
}
