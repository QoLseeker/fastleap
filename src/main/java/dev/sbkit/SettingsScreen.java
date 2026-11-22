package dev.sbkit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Generic screen: feature == null lists features, otherwise lists that feature's settings, paged. */
final class SettingsScreen extends Screen {
    private static final int W = 220, ROW = 24, PER_PAGE = 7;
    private final Feature feature;
    private final int page;

    SettingsScreen(Feature feature, int page) {
        super(Component.literal(feature == null ? "SB Kit" : feature.name));
        this.feature = feature;
        this.page = page;
    }

    private void reopen(Feature f, int p) { Minecraft.getInstance().gui.setScreen(new SettingsScreen(f, p)); }

    private Button button(String text, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(Component.literal(text), press).bounds(x, y, w, 20).build());
    }

    private void header(int x, int y, String text) { button(text, x, y, W, b -> {}).active = false; }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = 24;
        header(x, y, getTitle().getString());
        y += ROW + 4;

        if (feature == null) {
            for (Feature f : Features.ALL) {
                button(f.name + ": " + (f.on() ? "ON" : "OFF"), x, y, W - 24, b -> { f.enabled.set(!f.on()); reopen(null, 0); });
                button("⚙", x + W - 20, y, 20, b -> reopen(f, 0));
                y += ROW;
            }
            button("Done", x, height - 28, W, b -> onClose());
            return;
        }

        List<Setting<?>> shown = feature.settings.stream().filter(s -> s.visible.getAsBoolean()).toList();
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.min(page, pages - 1);
        for (Setting<?> s : shown.subList(p * PER_PAGE, Math.min(shown.size(), (p + 1) * PER_PAGE))) {
            row(s, x, y, p);
            y += ROW;
        }
        int ny = height - 28;
        button("Back", x, ny, 60, b -> reopen(null, 0));
        if (pages > 1) {
            button("<", x + 64, ny, 20, b -> reopen(feature, (p + pages - 1) % pages)).active = pages > 1;
            button((p + 1) + "/" + pages, x + 86, ny, 48, b -> {}).active = false;
            button(">", x + 136, ny, 20, b -> reopen(feature, (p + 1) % pages));
        }
        button("Done", x + W - 60, ny, 60, b -> onClose());
    }

    private void row(Setting<?> s, int x, int y, int p) {
        if (s instanceof Setting.Bool v) {
            button(v.label + ": " + (v.get() ? "ON" : "OFF"), x, y, W, b -> { v.set(!v.get()); reopen(feature, p); });
        } else if (s instanceof Setting.Choice v) {
            button(v.label + ": " + v.get(), x, y, W, b -> { v.cycle(); reopen(feature, p); });
        } else if (s instanceof Setting.Int v) {
            button("-", x, y, 24, b -> { v.set(v.get() - v.step); reopen(feature, p); });
            button(v.label + ": " + v.get(), x + 26, y, W - 52, b -> {}).active = false;
            button("+", x + W - 24, y, 24, b -> { v.set(v.get() + v.step); reopen(feature, p); });
        } else if (s instanceof Setting.Text v) {
            button(v.label, x, y, W / 2 - 2, b -> {}).active = false;
            EditBox box = new EditBox(Minecraft.getInstance().font, x + W / 2, y, W / 2, 20, Component.literal(v.label));
            box.setMaxLength(40);
            box.setValue(v.get());
            box.setResponder(v::set);
            addRenderableWidget(box);
        }
    }

    @Override
    public void removed() { Config.save(Features.ALL); }
}
