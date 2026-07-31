package net.antik16384.cartographerscanvas;

import com.mojang.brigadier.context.CommandContext;
import net.antik16384.cartographerscanvas.canvas.MapLibrary;
import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.antik16384.cartographerscanvas.canvas.MinimapOverlayConfig;
import net.antik16384.cartographerscanvas.canvas.ModKeybinds;
import net.antik16384.cartographerscanvas.gui.CartographersCanvasConfigScreen;
import net.antik16384.cartographerscanvas.gui.MapLibraryScreen;
import net.antik16384.cartographerscanvas.gui.MinimapOverlaySettingsScreen;
import net.antik16384.cartographerscanvas.gui.MinimapScreen;
import net.antik16384.cartographerscanvas.hud.MinimapHudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

public class CartographersCanvas implements ClientModInitializer {

	public static final String MOD_ID = "cartographerscanvas";

	private static final MinimapHudRenderer HUD_RENDERER = new MinimapHudRenderer();

	private boolean editorKeyWasDown = false;
	private boolean settingsKeyWasDown = false;
	private boolean libraryKeyWasDown = false;

	public static MinimapHudRenderer getHudRenderer() {
		return HUD_RENDERER;
	}

	@Override
	public void onInitializeClient() {
		MinimapData.INSTANCE.load();
		MinimapOverlayConfig.INSTANCE.load();
		MapLibrary.INSTANCE.load();
		ModKeybinds.INSTANCE.load();
		HUD_RENDERER.register();

		// KEYBINDS, don't appear in vanilla menu
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long handle = client.getWindow().getHandle();
			ModKeybinds kb = ModKeybinds.INSTANCE;

			editorKeyWasDown = pollKeybind(client, handle, kb.editorKey, editorKeyWasDown, MinimapScreen::new);
			settingsKeyWasDown = pollKeybind(client, handle, kb.settingsKey, settingsKeyWasDown, MinimapOverlaySettingsScreen::new);
			libraryKeyWasDown = pollKeybind(client, handle, kb.libraryKey, libraryKeyWasDown, MapLibraryScreen::new);
		});

		registerCommands();
	}

	private boolean pollKeybind(MinecraftClient client, long windowHandle, int key, boolean wasDown, Supplier<Screen> screenFactory) {
		if (key == ModKeybinds.UNBOUND) return false;

		boolean isDown = GLFW.glfwGetKey(windowHandle, key) == GLFW.GLFW_PRESS;
		if (isDown && !wasDown && client.currentScreen == null) {
			client.setScreen(screenFactory.get());
		}
		return isDown;
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("cartographerscanvas")
						.then(ClientCommandManager.literal("editor").executes(this::runOpenEditor))
						.then(ClientCommandManager.literal("settings").executes(this::runOpenSettings))
						.then(ClientCommandManager.literal("library").executes(this::runOpenLibrary))
						.then(ClientCommandManager.literal("config").executes(this::runOpenConfig))
				)
		);
	}

	private int runOpenEditor(CommandContext<FabricClientCommandSource> ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(new MinimapScreen()));
		return 1;
	}

	private int runOpenSettings(CommandContext<FabricClientCommandSource> ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(new MinimapOverlaySettingsScreen()));
		return 1;
	}

	private int runOpenLibrary(CommandContext<FabricClientCommandSource> ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(new MapLibraryScreen()));
		return 1;
	}

	private int runOpenConfig(CommandContext<FabricClientCommandSource> ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.setScreen(new CartographersCanvasConfigScreen(null)));
		return 1;
	}
}
