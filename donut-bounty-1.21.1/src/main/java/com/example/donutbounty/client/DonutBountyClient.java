package com.example.donutbounty.client;

import com.example.donutbounty.BountyService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only entrypoint. This mod only READS incoming messages. It never sends chat, runs commands,
 * makes payments or touches packets.
 */
public class DonutBountyClient implements ClientModInitializer {
    public static final String MOD_ID = "donutbounty";

    private static BountyService service;
    private static KeyBinding openKey;

    public static BountyService service() {
        return service;
    }

    @Override
    public void onInitializeClient() {
        service = new BountyService(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID),
                () -> MinecraftClient.getInstance().getSession().getUsername(),
                () -> {
                    ServerInfo info = MinecraftClient.getInstance().getCurrentServerEntry();
                    return info == null ? null : info.address;
                });

        // Configurable in Options > Controls > Key Binds. Default: H.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutbounty.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.donutbounty"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    client.setScreen(new BountyScreen(null));
                }
            }
        });

        // System / game messages: where server plugins normally print payment notices. Action-bar text is ignored.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) service.onIncoming(message.getString(), false);
        });

        // Chat channel: ignored unless payments.acceptChatChannel is true in the config, and then only for
        // messages with no sender profile (a real player's signed chat never counts).
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender == null) service.onIncoming(message.getString(), true);
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> BountyHud.render(context));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> service.saveConfig());
    }
}
