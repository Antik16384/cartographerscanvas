package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.canvas.MapLibrary;
import net.antik16384.cartographerscanvas.canvas.MapStack;
import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.antik16384.cartographerscanvas.canvas.StackLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class MapStackEditScreen extends Screen {

	private static final String[] DIMENSION_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
	private static final int LEFT_X = 10;
	private static final int LEVELS_X = 200;
	private static final int LEVEL_ROW_HEIGHT = 46;

	private final MapStack stack;

	private TextFieldWidget nameField;
	private ButtonWidget enabledButton;
	private ButtonWidget rotationButton;
	private ButtonWidget dimensionButton;
	private ButtonWidget fallbackButton;
	private TextFieldWidget corner1XField;
	private TextFieldWidget corner1ZField;
	private TextFieldWidget corner2XField;
	private TextFieldWidget corner2ZField;

	public MapStackEditScreen(MapStack stack) {
		super(Text.literal("Edit stack"));
		this.stack = stack;
	}

	@Override
	protected void init() {
		super.init();
		initLeftColumn();
		initLevelsColumn();
	}

	private void initLeftColumn() {
		int x = LEFT_X;
		int fieldWidth = 80;
		int gap = 6;

		nameField = new TextFieldWidget(textRenderer, x, 10, 170, 20, Text.literal("Name"));
		nameField.setMaxLength(32);
		nameField.setText(stack.name);
		nameField.setChangedListener(text -> {
			stack.name = text.isEmpty() ? "Stack" : text;
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(nameField);

		enabledButton = ButtonWidget.builder(Text.literal(enabledLabel()), b -> {
			stack.enabled = !stack.enabled;
			enabledButton.setMessage(Text.literal(enabledLabel()));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 34, 170, 20).build();
		addDrawableChild(enabledButton);

		dimensionButton = ButtonWidget.builder(Text.literal(dimensionLabel()), b -> {
			stack.dimensionId = nextDimensionId(stack.dimensionId);
			dimensionButton.setMessage(Text.literal(dimensionLabel()));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 58, 170, 20).build();
		addDrawableChild(dimensionButton);

		corner1XField = new TextFieldWidget(textRenderer, x, 84, fieldWidth, 20, Text.literal("X1"));
		corner1XField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner1XField.setText(Integer.toString(stack.corner1X));
		corner1XField.setChangedListener(text -> {
			stack.corner1X = parseIntOr(text, stack.corner1X);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner1XField);

		corner1ZField = new TextFieldWidget(textRenderer, x + fieldWidth + gap, 84, fieldWidth, 20, Text.literal("Z1"));
		corner1ZField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner1ZField.setText(Integer.toString(stack.corner1Z));
		corner1ZField.setChangedListener(text -> {
			stack.corner1Z = parseIntOr(text, stack.corner1Z);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner1ZField);

		ButtonWidget point1HereButton = ButtonWidget.builder(Text.literal("Corner 1: Set here"), b -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;
			stack.corner1X = MathHelper.floor(client.player.getX());
			stack.corner1Z = MathHelper.floor(client.player.getZ());
			corner1XField.setText(Integer.toString(stack.corner1X));
			corner1ZField.setText(Integer.toString(stack.corner1Z));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 108, 170, 20).build();
		addDrawableChild(point1HereButton);

		corner2XField = new TextFieldWidget(textRenderer, x, 134, fieldWidth, 20, Text.literal("X2"));
		corner2XField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner2XField.setText(Integer.toString(stack.corner2X));
		corner2XField.setChangedListener(text -> {
			stack.corner2X = parseIntOr(text, stack.corner2X);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner2XField);

		corner2ZField = new TextFieldWidget(textRenderer, x + fieldWidth + gap, 134, fieldWidth, 20, Text.literal("Z2"));
		corner2ZField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner2ZField.setText(Integer.toString(stack.corner2Z));
		corner2ZField.setChangedListener(text -> {
			stack.corner2Z = parseIntOr(text, stack.corner2Z);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner2ZField);

		ButtonWidget point2HereButton = ButtonWidget.builder(Text.literal("Corner 2: Set here"), b -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;
			stack.corner2X = MathHelper.floor(client.player.getX());
			stack.corner2Z = MathHelper.floor(client.player.getZ());
			corner2XField.setText(Integer.toString(stack.corner2X));
			corner2ZField.setText(Integer.toString(stack.corner2Z));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 158, 170, 20).build();
		addDrawableChild(point2HereButton);

		rotationButton = ButtonWidget.builder(Text.literal(rotationLabel()), b -> {
			stack.rotationSteps = (stack.rotationSteps + 1) & 3;
			rotationButton.setMessage(Text.literal(rotationLabel()));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 182, 170, 20).build();
		addDrawableChild(rotationButton);

		fallbackButton = ButtonWidget.builder(Text.literal(fallbackLabel()), b -> chooseFallbackProject())
				.dimensions(x, 206, 170, 20).build();
		addDrawableChild(fallbackButton);

		ButtonWidget exportButton = ButtonWidget.builder(Text.literal("Export stack..."), b -> exportStack())
				.dimensions(x, 230, 170, 20).build();
		addDrawableChild(exportButton);

		ButtonWidget backButton = ButtonWidget.builder(Text.literal("Back to library"), b -> {
			MapLibrary.INSTANCE.save();
			MinecraftClient.getInstance().setScreen(new MapLibraryScreen());
		}).dimensions(x, 254, 170, 20).build();
		addDrawableChild(backButton);
	}

	private void initLevelsColumn() {
		int x = LEVELS_X;
		int y = 10;

		ButtonWidget addLevelButton = ButtonWidget.builder(Text.literal("+ Level"), b -> {
			StackLevel level = new StackLevel();
			if (!stack.levels.isEmpty()) {
				level.minY = stack.levels.get(stack.levels.size() - 1).maxY + 1;
			}
			level.name = "Level " + (stack.levels.size() + 1);
			stack.levels.add(level);
			MapLibrary.INSTANCE.save();
			clearChildren();
			init();
		}).dimensions(x, y, 150, 20).build();
		addDrawableChild(addLevelButton);
		y += LEVEL_ROW_HEIGHT - 20;

		List<StackLevel> levels = stack.levels;
		for (int i = 0; i < levels.size(); i++) {
			int index = i;
			StackLevel level = levels.get(i);

			TextFieldWidget minYField = new TextFieldWidget(textRenderer, x, y, 50, 20, Text.literal("minY"));
			minYField.setTextPredicate(text -> text.matches("-?\\d*"));
			minYField.setText(Integer.toString(level.minY));
			minYField.setChangedListener(text -> {
				level.minY = parseIntOr(text, level.minY);
				MapLibrary.INSTANCE.save();
			});
			addDrawableChild(minYField);

			TextFieldWidget maxYField = new TextFieldWidget(textRenderer, x + 56, y, 50, 20, Text.literal("maxY"));
			maxYField.setTextPredicate(text -> text.matches("-?\\d*"));
			maxYField.setText(Integer.toString(level.maxY));
			maxYField.setChangedListener(text -> {
				level.maxY = parseIntOr(text, level.maxY);
				MapLibrary.INSTANCE.save();
			});
			addDrawableChild(maxYField);

			ButtonWidget upButton = ButtonWidget.builder(Text.literal("^"), b -> {
				Collections.swap(levels, index, index - 1);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(x + 112, y, 16, 20).build();
			upButton.active = index > 0;
			addDrawableChild(upButton);

			ButtonWidget downButton = ButtonWidget.builder(Text.literal("v"), b -> {
				Collections.swap(levels, index, index + 1);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(x + 130, y, 16, 20).build();
			downButton.active = index < levels.size() - 1;
			addDrawableChild(downButton);

			ButtonWidget removeButton = ButtonWidget.builder(Text.literal("X"), b -> {
				levels.remove(index);
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			}).dimensions(x + 148, y, 20, 20).build();
			addDrawableChild(removeButton);

			ButtonWidget projectButton = ButtonWidget.builder(Text.literal(levelProjectLabel(level)), b -> chooseLevelProject(level))
					.dimensions(x, y + 22, 168, 20).build();
			addDrawableChild(projectButton);

			y += LEVEL_ROW_HEIGHT;
		}
	}

	private String fallbackLabel() {
		if (stack.fallbackProjectPath == null) return "Fallback: None";
		return "Fallback: " + Path.of(stack.fallbackProjectPath).getFileName().toString();
	}

	private void chooseFallbackProject() {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack memStack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = memStack.mallocPointer(1);
				filters.put(memStack.UTF8("*.ccmap"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().toString() + java.io.File.separator;
				result = TinyFileDialogs.tinyfd_openFileDialog("Select fallback project", defaultPath, filters, "Cartographers Canvas Projects (*.ccmap)", false);
			}
			if (result == null) return;
			MinecraftClient.getInstance().execute(() -> {
				stack.fallbackProjectPath = result;
				fallbackButton.setMessage(Text.literal(fallbackLabel()));
				MapLibrary.INSTANCE.save();
			});
		}, "cartographerscanvas-stack-fallback-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	private String levelProjectLabel(StackLevel level) {
		if (level.projectPath == null) return "Select project...";
		return Path.of(level.projectPath).getFileName().toString();
	}

	private void chooseLevelProject(StackLevel level) {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack memStack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = memStack.mallocPointer(1);
				filters.put(memStack.UTF8("*.ccmap"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().toString() + java.io.File.separator;
				result = TinyFileDialogs.tinyfd_openFileDialog("Select project", defaultPath, filters, "Cartographers Canvas Projects (*.ccmap)", false);
			}
			if (result == null) return;
			MinecraftClient.getInstance().execute(() -> {
				level.projectPath = result;
				MapLibrary.INSTANCE.save();
				clearChildren();
				init();
			});
		}, "cartographerscanvas-level-project-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	private void exportStack() {
		Thread thread = new Thread(() -> {
			String result;
			try (org.lwjgl.system.MemoryStack memStack = org.lwjgl.system.MemoryStack.stackPush()) {
				org.lwjgl.PointerBuffer filters = memStack.mallocPointer(1);
				filters.put(memStack.UTF8("*.ccstack"));
				filters.flip();
				String defaultPath = MinimapData.INSTANCE.exportsDir().resolve(sanitizedName() + ".ccstack").toString();
				result = TinyFileDialogs.tinyfd_saveFileDialog("Export stack", defaultPath, filters, "Cartographers Canvas Stacks (*.ccstack)");
			}
			if (result == null) return;

			Path path = result.toLowerCase().endsWith(".ccstack") ? Path.of(result) : Path.of(result + ".ccstack");
			try {
				stack.exportToFile(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, "cartographerscanvas-export-stack-dialog");
		thread.setDaemon(true);
		thread.start();
	}

	private String sanitizedName() {
		return stack.name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
	}

	private String enabledLabel() {
		return "Stack: " + (stack.enabled ? "On" : "Off");
	}

	private String rotationLabel() {
		return "Rotation: " + (stack.rotationSteps * 90) + "\u00b0";
	}

	private String dimensionLabel() {
		String friendly = switch (stack.dimensionId) {
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> "Overworld";
		};
		return "Dimension: " + friendly;
	}

	private String nextDimensionId(String current) {
		for (int i = 0; i < DIMENSION_IDS.length; i++) {
			if (DIMENSION_IDS[i].equals(current)) {
				return DIMENSION_IDS[(i + 1) % DIMENSION_IDS.length];
			}
		}
		return DIMENSION_IDS[0];
	}

	private int parseIntOr(String text, int fallback) {
		if (text == null || text.isEmpty() || text.equals("-")) return fallback;
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		if (stack.levels.isEmpty()) {
			context.drawText(textRenderer, Text.literal("No levels yet. '+ Level' to add one."), LEVELS_X, 36, 0xFFAAAAAA, true);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MapLibrary.INSTANCE.save();
		super.close();
	}
}
