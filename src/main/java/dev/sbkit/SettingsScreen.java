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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The fastleap 1.0.0 look (same colors, sizes and drawing), built from any feature's settings.
 * feature == null shows the list of features. Bool -> General, Int -> Timing, Choice -> Targets, Text -> Text.
 */
final class SettingsScreen extends Screen {
    private static final int PANEL = -267119592, ROW = -14803418, ROW_HOVER = -13948104, ACCENT = -8622849,
        TEXT = -1513232, DIM = -7697767, RED = -43674, GREEN = -11149174, TRACK = -12237489;
    private static final int ROW_H = 16, STEP = 18, PAD = 8, LEFT_W = 150, RIGHT_W = 170, PANEL_W = 344;

    private record Section(int x, int y, String title) {}

    private final Feature feature;

    SettingsScreen(Feature feature) {
        super(Component.literal(feature == null ? "SB Kit" : feature.name));
        this.feature = feature;
    }

    private static void reopen(Feature f) { Minecraft.getInstance().gui.setScreen(new SettingsScreen(f)); }

    @Override
    protected void init() {
        if (feature == null) { initList(); return; }

        List<Setting<?>> shown = feature.settings.stream().filter(s -> s.visible.getAsBoolean()).toList();
        List<Setting<?>> general = ofType(shown, Setting.Bool.class), timing = ofType(shown, Setting.Int.class),
            targets = ofType(shown, Setting.Choice.class), text = ofType(shown, Setting.Text.class);
        int body = Math.max(height(general, timing), height(targets, text));
        int panelH = 28 + body + 4 + ROW_H + 8;
        int px = (width - PANEL_W) / 2, py = Math.max(4, height / 2 - panelH / 2), y0 = py + 28;
        Panel panel = addRenderableWidget(new Panel(px, py, PANEL_W, panelH, feature.name, feature::status));

        int lx = px + PAD, rx = lx + LEFT_W + PAD;
        int y = column(panel, lx, LEFT_W, y0, "General", general);
        column(panel, lx, LEFT_W, y, "Timing", timing);
        y = column(panel, rx, RIGHT_W, y0, "Targets", targets);
        column(panel, rx, RIGHT_W, y, "Text", text);
        addRenderableWidget(new Row(lx, y0 + body + 4, PANEL_W - 2 * PAD, "Done", null, () -> "", null, this::onClose));
    }

    private void initList() {
        List<Feature> all = Features.ALL;
        int w = 220, body = (all.size() - 1) * STEP + ROW_H;
        int panelH = 28 + body + 4 + ROW_H + 8;
        int px = (width - w) / 2, py = Math.max(4, height / 2 - panelH / 2), y0 = py + 28;
        addRenderableWidget(new Panel(px, py, w, panelH, "SB Kit", () -> ""));
        for (int i = 0; i < all.size(); i++) {
            Feature f = all.get(i);
            addRenderableWidget(new Row(px + PAD, y0 + i * STEP, w - 2 * PAD, f.name, f::on, null, null, () -> reopen(f)));
        }
        addRenderableWidget(new Row(px + PAD, y0 + body + 4, w - 2 * PAD, "Done", null, () -> "", null, this::onClose));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Setting<?>> List<Setting<?>> ofType(List<Setting<?>> all, Class<T> type) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : all) if (type.isInstance(s)) out.add(s);
        return out;
    }

    /** Height of one section: title gap + rows. */
    private static int height(List<Setting<?>> list) { return list.isEmpty() ? 0 : 12 + (list.size() - 1) * STEP + ROW_H; }

    /** Two sections stacked in one column, 8 px apart. */
    private static int height(List<Setting<?>> a, List<Setting<?>> b) {
        return height(a) + height(b) + (a.isEmpty() || b.isEmpty() ? 0 : 8);
    }

    /** Adds a section and its rows; returns where the next section in the column starts. */
    private int column(Panel panel, int x, int w, int y, String title, List<Setting<?>> list) {
        if (list.isEmpty()) return y;
        panel.sections.add(new Section(x, y, title));
        y += 12;
        for (Setting<?> s : list) { addRow(s, x, y, w); y += STEP; }
        return y - STEP + 24;
    }

    private void addRow(Setting<?> s, int x, int y, int w) {
        if (s instanceof Setting.Bool v) {
            addRenderableWidget(new Row(x, y, w, v.label, v::get, null, null, () -> v.set(!v.get())));
        } else if (s instanceof Setting.Choice v) {
            // Cycling can change which rows are visible (e.g. "Editing for"), so rebuild the screen.
            addRenderableWidget(new Row(x, y, w, v.label, null, v::get, v.highlight, () -> { v.cycle(); reopen(feature); }));
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

    // ---- widgets (ported from fastleap 1.0.0) ---------------------------------------------------------------

    private static final class Panel extends AbstractWidget {
        final List<Section> sections = new ArrayList<>();
        private final String title;
        private final Supplier<String> status;

        Panel(int x, int y, int w, int h, String title, Supplier<String> status) {
            super(x, y, w, h, Component.empty());
            this.title = title;
            this.status = status;
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY();
            g.fillGradient(x, y, x + width, y + height, PANEL, PANEL);
            g.fillGradient(x, y, x + width, y + 20, ACCENT, ACCENT);
            g.text(font, title, x + 8, y + 6, -1, true);
            String s = status.get();
            g.text(font, s, x + width - 8 - font.width(s), y + 6, -1, true);
            for (Section sec : sections) g.text(font, sec.title().toUpperCase(), sec.x(), sec.y(), DIM, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** A flat row: label on the left, then either an on/off switch or a value on the right. */
    private static final class Row extends Button {
        private final String label;
        private final BooleanSupplier toggle, current;
        private final Supplier<String> value;

        Row(int x, int y, int w, String label, BooleanSupplier toggle, Supplier<String> value, BooleanSupplier current, Runnable action) {
            super(x, y, w, ROW_H, Component.empty(), b -> action.run(), DEFAULT_NARRATION);
            this.label = label;
            this.toggle = toggle;
            this.value = value;
            this.current = current;
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
