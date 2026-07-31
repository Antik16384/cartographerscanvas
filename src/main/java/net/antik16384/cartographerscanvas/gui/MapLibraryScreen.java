package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.CartographersCanvas;
import net.antik16384.cartographerscanvas.canvas.LibraryItem;
import net.antik16384.cartographerscanvas.canvas.MapEntry;
import net.antik16384.cartographerscanvas.canvas.MapLibrary;
import net.antik16384.cartographerscanvas.canvas.MapStack;
import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class MapLibraryScreen extends Screen {

	private static final int ROW_HEIGHT = 22;
	private static final int LIST_X = 10;
	private static final int LIST_Y = 30;

	private ButtonWidget fallbackButton;
	private ButtonWidget clearFallbackButton;
	private ButtonWidget noMatchModeButton;

	public MapLibraryScreen() {
		super(Text.literal("Library"));
	}

	@Override
	protected void init() {
		super.init();

		List<LibraryItem> entries = MapLibrary.INSTANCE.entries;
		int y = LIST_Y;

		for (int i = 0; i < entries.size(); i++) {
			int index = i;
			LibraryItem item = entries.get(i);

			ButtonWidget toggle = ButtonWidget.builder(Text.literal(item.isEnabled() ? "On" : "Off"), b -> {
				item.setEnabled(!item.isEnabled());
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(LIST_X, y, 34, 20).build();
			addDrawableChild(toggle);

			String typeTag = item instanceof MapStack ? "[Stack] " : "";
			ButtonWidget editButton = ButtonWidget.builder(Text.literal(typeTag + item.getName()), b -> openEditScreen(item))
					.dimensions(LIST_X + 38, y, 130, 20).build();
			addDrawableChild(editButton);

			ButtonWidget upButton = ButtonWidget.builder(Text.literal("^"), b -> {
				MapLibrary.INSTANCE.moveUp(index);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(LIST_X + 172, y, 18, 20).build();
			upButton.active = index > 0;
			addDrawableChild(upButton);

			ButtonWidget downButton = ButtonWidget.builder(Text.literal("v"), b -> {
				MapLibrary.INSTANCE.moveDown(index);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(LIST_X + 192, y, 18, 20).build();
			downButton.active = index < entries.size() - 1;
			addDrawableChild(downButton);

			ButtonWidget removeButton = ButtonWidget.builder(Text.literal("X"), b -> {
				entries.remove(index);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(LIST_X + 214, y, 20, 20).build();
			addDrawableChild(removeButton);

			y += ROW_HEIGHT;
		}

		y += entries.isEmpty() ? 20 : 6;

		ButtonWidget addButton = ButtonWidget.builder(Text.literal("+ Import map..."), b -> chooseNewProject())
				.dimensions(LIST_X, y, 234, 20).build();
		addDrawableChild(addButton);
		y += ROW_HEIGHT;

		ButtonWidget addStackButton = ButtonWidget.builder(Text.literal("+ New stack"), b -> createNewStack())
				.dimensions(LIST_X, y, 234, 20).build();
		addDrawableChild(addStackButton);
		y += ROW_HEIGHT;

		ButtonWidget importStackButton = ButtonWidget.builder(Text.literal("+ Import stack..."), b -> importStack())
				.dimensions(LIST_X, y, 234, 20).build();
		addDrawableChild(importStackButton);
		y += ROW_HEIGHT + 4;

		ButtonWidget reloadButton = ButtonWidget.builder(Text.literal("Reload all"), b -> CartographersCanvas.getHudRenderer().clearProjectCache())
				.dimensions(LIST_X, y, 234, 20).build();
		addDrawableChild(reloadButton);
		y += ROW_HEIGHT + 10;

		noMatchModeButton = ButtonWidget.builder(Text.literal(noMatchModeLabel()), b -> {
			MapLibrary.NoMatchMode[] modes = MapLibrary.NoMatchMode.values();
			MapLibrary library = MapLibrary.INSTANCE;
			library.noMatchMode = modes[(library.noMatchMode.ordinal() + 1) % modes.length];
			noMatchModeButton.setMessage(Text.literal(noMatchModeLabel()));
			boolean showFallback = library.noMatchMode == MapLibrary.NoMatchMode.SHOW_FALLBACK;
			fallbackButton.visible = showFallback;
			fallbackButton.active = showFallback;
			clearFallbackButton.visible = showFallback;
			clearFallbackButton.active = showFallback;
			library.save();
		}).dimensions(LIST_X, y, 234, 20).build();
		addDrawableChild(noMatchModeButton);
		y += ROW_HEIGHT + 4;

		boolean showFallback = MapLibrary.INSTANCE.noMatchMode == MapLibrary.NoMatchMode.SHOW_FALLBACK;

		fallbackButton = ButtonWidget.builder(Text.literal(fallbackLabel()), b -> chooseFallbackProject())
				.dimensions(LIST_X, y, 190, 20).build();
		fallbackButton.visible = showFallback;
		fallbackButton.active = showFallback;
		addDrawableChild(fallbackButton);

		clearFallbackButton = ButtonWidget.builder(Text.literal("X"), b -> {
			MapLibrary.INSTANCE.fallbackProjectPath = null;
			MapLibrary.INSTANCE.save();
			fallbackButton.setMessage(Text.literal(fallbackLabel()));
		}).dimensions(LIST_X + 194, y, 20, 20).build();
		clearFallbackButton.visible = showFallback;
		clearFallbackButton.active = showFallback;
		addDrawableChild(clearFallbackButton);
	}

	private void openEditScreen(LibraryItem item) {
		if (item instanceof MapStack stack) {
			MinecraftClient.getInstance().setScreen(new MapStackEditScreen(stack));
		} else if (item instanceof MapEntry entry) {
			MinecraftClient.getInstance().setScreen(new MapEntryEditScreen(entry));
		}
	}

	private void createNewStack() {
		MapStack stack = new MapStack();
		stack.name = MapLibrary.INSTANCE.uniqueName("New stack");
		MapLibrary.INSTANCE.entries.add(stack);
		MapLibrary.INSTANCE.save();
		MinecraftClient.getInstance().setScreen(new MapStackEditScreen(stack));
	}

	private void importStack() {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack memStack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = memStack.mallocPointer(1);
				filters.put(memStack.UTF8("*.ccstack"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().toString() + java.io.File.separator;
				result = TinyFileDialogs.tinyfd_openFileDialog("Import stack", defaultPath, filters, "Cartographers Canvas Stacks (*.ccstack)", false);
			}
			if (result == null) return;

			try {
				MapStack stack = MapStack.importFromFile(Path.of(result));
				MinecraftClient.getInstance().execute(() -> {
					stack.name = MapLibrary.INSTANCE.uniqueName(stack.name);
					MapLibrary.INSTANCE.entries.add(stack);
					MapLibrary.INSTANCE.save();
					MinecraftClient.getInstance().setScreen(new MapStackEditScreen(stack));
				});
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, "cartographerscanvas-import-stack-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	private String noMatchModeLabel() {
		return switch (MapLibrary.INSTANCE.noMatchMode) {
			case HIDE -> "Fallback: Hide";
			case SHOW_TOP_PRIORITY -> "Fallback: Highest priority";
			case SHOW_FALLBACK -> "Fallback: Project";
		};
	}

	private String fallbackLabel() {
		String path = MapLibrary.INSTANCE.fallbackProjectPath;
		if (path == null) return "Fallback: None";
		return "Fallback: " + Path.of(path).getFileName().toString();
	}

	private void chooseFallbackProject() {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.ccmap"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().toString() + java.io.File.separator;
				result = TinyFileDialogs.tinyfd_openFileDialog("Select fallback project", defaultPath, filters, "Cartographers Canvas Projects (*.ccmap)", false);
			}
			if (result == null) return;
			MinecraftClient.getInstance().execute(() -> {
				MapLibrary.INSTANCE.fallbackProjectPath = result;
				MapLibrary.INSTANCE.save();
				fallbackButton.setMessage(Text.literal(fallbackLabel()));
			});
		}, "cartographerscanvas-fallback-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	private void chooseNewProject() {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.ccmap"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().toString() + java.io.File.separator;
				result = TinyFileDialogs.tinyfd_openFileDialog("Select project", defaultPath, filters, "Cartographers Canvas Projects (*.ccmap)", false);
			}
			if (result == null) return;
			MinecraftClient.getInstance().execute(() -> {
				MapEntry entry = new MapEntry();
				entry.projectPath = result;
				entry.name = MapLibrary.INSTANCE.uniqueName(Path.of(result).getFileName().toString());
				MapLibrary.INSTANCE.entries.add(entry);
				MapLibrary.INSTANCE.save();
				MinecraftClient.getInstance().setScreen(new MapLibraryScreen());
			});
		}, "cartographerscanvas-new-map-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		if (MapLibrary.INSTANCE.entries.isEmpty()) {
			context.drawText(textRenderer, Text.literal("No maps yet. '+ Import map...' to add one."), LIST_X, LIST_Y, 0xFFAAAAAA, true);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
