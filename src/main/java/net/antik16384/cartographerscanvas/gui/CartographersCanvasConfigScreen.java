package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.canvas.ModKeybinds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class CartographersCanvasConfigScreen extends Screen {

	private final Screen parent;

	private ButtonWidget editorKeyButton;
	private ButtonWidget settingsKeyButton;
	private ButtonWidget libraryKeyButton;

	private String capturing = null;

	public CartographersCanvasConfigScreen(Screen parent) {
		super(Text.literal("Cartographer's Canvas Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		int x = 10;
		int y = 10;
		int openWidth = 150;
		int keyWidth = 140;
		int rowGap = 6;
		int colGap = 6;

		ButtonWidget openEditorButton = ButtonWidget.builder(Text.literal("Open Editor"), b ->
				MinecraftClient.getInstance().setScreen(new MinimapScreen())
		).dimensions(x, y, openWidth, 20).build();
		addDrawableChild(openEditorButton);

		editorKeyButton = ButtonWidget.builder(Text.literal(keyLabel("Editor", ModKeybinds.INSTANCE.editorKey)), b -> startCapture("editor"))
				.dimensions(x + openWidth + colGap, y, keyWidth, 20).build();
		addDrawableChild(editorKeyButton);
		y += 20 + rowGap;

		ButtonWidget openSettingsButton = ButtonWidget.builder(Text.literal("Open Overlay Settings"), b ->
				MinecraftClient.getInstance().setScreen(new MinimapOverlaySettingsScreen())
		).dimensions(x, y, openWidth, 20).build();
		addDrawableChild(openSettingsButton);

		settingsKeyButton = ButtonWidget.builder(Text.literal(keyLabel("Overlay Settings", ModKeybinds.INSTANCE.settingsKey)), b -> startCapture("settings"))
				.dimensions(x + openWidth + colGap, y, keyWidth, 20).build();
		addDrawableChild(settingsKeyButton);
		y += 20 + rowGap;

		ButtonWidget openLibraryButton = ButtonWidget.builder(Text.literal("Open Library"), b ->
				MinecraftClient.getInstance().setScreen(new MapLibraryScreen())
		).dimensions(x, y, openWidth, 20).build();
		addDrawableChild(openLibraryButton);

		libraryKeyButton = ButtonWidget.builder(Text.literal(keyLabel("Library", ModKeybinds.INSTANCE.libraryKey)), b -> startCapture("library"))
				.dimensions(x + openWidth + colGap, y, keyWidth, 20).build();
		addDrawableChild(libraryKeyButton);
	}

	private void startCapture(String which) {
		capturing = which;
		refreshButtonLabels();
	}

	private void refreshButtonLabels() {
		ModKeybinds kb = ModKeybinds.INSTANCE;
		editorKeyButton.setMessage(Text.literal("editor".equals(capturing) ? "> Press key <" : keyLabel("Editor", kb.editorKey)));
		settingsKeyButton.setMessage(Text.literal("settings".equals(capturing) ? "> Press key <" : keyLabel("Overlay Settings", kb.settingsKey)));
		libraryKeyButton.setMessage(Text.literal("library".equals(capturing) ? "> Press key <" : keyLabel("Library", kb.libraryKey)));
	}

	private String keyLabel(String actionName, int key) {
		String keyName = key == ModKeybinds.UNBOUND
				? "Not bound"
				: InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
		return actionName + ": " + keyName;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (capturing != null) {
			int key = input.key();
			ModKeybinds kb = ModKeybinds.INSTANCE;

			int assigned = key == GLFW.GLFW_KEY_ESCAPE ? ModKeybinds.UNBOUND : key;

			switch (capturing) {
				case "editor" -> kb.editorKey = assigned;
				case "settings" -> kb.settingsKey = assigned;
				case "library" -> kb.libraryKey = assigned;
			}

			kb.save();
			capturing = null;
			refreshButtonLabels();
			return true;
		}

		return super.keyPressed(input);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}
}
