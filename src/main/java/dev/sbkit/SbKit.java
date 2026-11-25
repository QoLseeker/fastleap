package dev.sbkit;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class SbKit implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("sbkit");
    private static KeyMapping menuKey;
    private static Object lastLevel;

    @Override
    public void onInitializeClient() {
        Config.load(Features.ALL);
        each(Feature::init);

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.parse("sbkit:keys"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbkit.menu", InputConstants.Type.KEYSYM, -1, category));

        ClientTickEvents.START_CLIENT_TICK.register(mc -> each(f -> f.onStartTick(mc)));
        ClientTickEvents.END_CLIENT_TICK.register(SbKit::endTick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // action bar, not chat
            Minecraft mc = Minecraft.getInstance();
            String text = message.getString();
            each(f -> f.onMessage(mc, text));
        });

        // /sbkit is the only way in; everything else lives in that one screen.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
            dispatcher.register(ClientCommands.literal("sbkit").executes(c -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.setScreen(new SettingsScreen()));
                return 1;
            })));
    }

    private static void endTick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            each(f -> f.onWorldChange(mc));
        }
        while (menuKey.consumeClick()) mc.gui.setScreen(new SettingsScreen());
        each(f -> f.onTick(mc));
    }

    /** One broken feature must not take the others down. */
    private static void each(Consumer<Feature> call) {
        for (Feature f : Features.ALL) {
            try { call.accept(f); } catch (RuntimeException e) { LOG.error("feature {} failed", f.id, e); }
        }
    }
}
