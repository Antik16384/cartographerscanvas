package net.antik16384.cartographerscanvas;

import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.antik16384.cartographerscanvas.canvas.MinimapOverlayConfig;
import net.antik16384.cartographerscanvas.gui.MinimapOverlaySettingsScreen;
import net.antik16384.cartographerscanvas.gui.MinimapScreen;
import net.antik16384.cartographerscanvas.hud.MinimapHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class CartographersCanvas implements ClientModInitializer {

	public static final String MOD_ID = "cartographerscanvas";

	private static final KeyBinding.Category CATEGORY =
			new KeyBinding.Category(Identifier.of(MOD_ID, "general"));

	private static KeyBinding openMapKey;
	private static KeyBinding openSettingsKey;
	private static final MinimapHudRenderer HUD_RENDERER = new MinimapHudRenderer();

	@Override
	public void onInitializeClient() {
		MinimapData.INSTANCE.load();
		MinimapOverlayConfig.INSTANCE.load();
		HUD_RENDERER.register();

		openMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.cartographerscanvas.openmap",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY
		));

		openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.cartographerscanvas.opensettings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMapKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MinimapScreen());
				}
			}
			while (openSettingsKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new MinimapOverlaySettingsScreen());
				}
			}
		});
	}
}
