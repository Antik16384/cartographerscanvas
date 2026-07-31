package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.canvas.MapEntry;
import net.antik16384.cartographerscanvas.canvas.MapLibrary;
import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;

public class MapEntryEditScreen extends Screen {

	private static final String[] DIMENSION_IDS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
	private static final int PREVIEW_X = 200;
	private static final int PREVIEW_Y = 10;
	private static final int PREVIEW_SIZE = 128;

	private final MapEntry entry;

	private TextFieldWidget nameField;
	private ButtonWidget projectButton;
	private ButtonWidget rotationButton;
	private ButtonWidget dimensionButton;
	private TextFieldWidget corner1XField;
	private TextFieldWidget corner1ZField;
	private TextFieldWidget corner2XField;
	private TextFieldWidget corner2ZField;

	private final NativeImage previewImage;
	private final NativeImageBackedTexture previewTexture;
	private final Identifier previewTextureId;
	private MinimapData previewData;
	private String previewLoadedPath;

	public MapEntryEditScreen(MapEntry entry) {
		super(Text.literal("Edit map"));
		this.entry = entry;

		this.previewImage = new NativeImage(MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT, true);
		this.previewTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_map_entry_preview", previewImage);
		this.previewTextureId = Identifier.of("cartographerscanvas", "map_entry_preview_" + System.identityHashCode(this));
		MinecraftClient.getInstance().getTextureManager().registerTexture(previewTextureId, previewTexture);
	}

	@Override
	protected void init() {
		super.init();
		int x = 10;
		int fieldWidth = 80;
		int gap = 6;

		nameField = new TextFieldWidget(textRenderer, x, 10, 170, 20, Text.literal("Name"));
		nameField.setMaxLength(32);
		nameField.setText(entry.name);
		nameField.setChangedListener(text -> {
			entry.name = text.isEmpty() ? "Map" : text;
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(nameField);

		projectButton = ButtonWidget.builder(Text.literal(projectLabel()), b -> chooseProject())
				.dimensions(x, 34, 170, 20).build();
		addDrawableChild(projectButton);

		corner1XField = new TextFieldWidget(textRenderer, x, 60, fieldWidth, 20, Text.literal("X1"));
		corner1XField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner1XField.setText(Integer.toString(entry.corner1X));
		corner1XField.setChangedListener(text -> {
			entry.corner1X = parseIntOr(text, entry.corner1X);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner1XField);

		corner1ZField = new TextFieldWidget(textRenderer, x + fieldWidth + gap, 60, fieldWidth, 20, Text.literal("Z1"));
		corner1ZField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner1ZField.setText(Integer.toString(entry.corner1Z));
		corner1ZField.setChangedListener(text -> {
			entry.corner1Z = parseIntOr(text, entry.corner1Z);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner1ZField);

		ButtonWidget point1HereButton = ButtonWidget.builder(Text.literal("Corner 1: Set here"), b -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;
			entry.corner1X = MathHelper.floor(client.player.getX());
			entry.corner1Z = MathHelper.floor(client.player.getZ());
			corner1XField.setText(Integer.toString(entry.corner1X));
			corner1ZField.setText(Integer.toString(entry.corner1Z));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 84, 170, 20).build();
		addDrawableChild(point1HereButton);

		corner2XField = new TextFieldWidget(textRenderer, x, 110, fieldWidth, 20, Text.literal("X2"));
		corner2XField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner2XField.setText(Integer.toString(entry.corner2X));
		corner2XField.setChangedListener(text -> {
			entry.corner2X = parseIntOr(text, entry.corner2X);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner2XField);

		corner2ZField = new TextFieldWidget(textRenderer, x + fieldWidth + gap, 110, fieldWidth, 20, Text.literal("Z2"));
		corner2ZField.setTextPredicate(text -> text.matches("-?\\d*"));
		corner2ZField.setText(Integer.toString(entry.corner2Z));
		corner2ZField.setChangedListener(text -> {
			entry.corner2Z = parseIntOr(text, entry.corner2Z);
			MapLibrary.INSTANCE.save();
		});
		addDrawableChild(corner2ZField);

		ButtonWidget point2HereButton = ButtonWidget.builder(Text.literal("Corner 2: Set here"), b -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null) return;
			entry.corner2X = MathHelper.floor(client.player.getX());
			entry.corner2Z = MathHelper.floor(client.player.getZ());
			corner2XField.setText(Integer.toString(entry.corner2X));
			corner2ZField.setText(Integer.toString(entry.corner2Z));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 134, 170, 20).build();
		addDrawableChild(point2HereButton);

		rotationButton = ButtonWidget.builder(Text.literal(rotationLabel()), b -> {
			entry.rotationSteps = (entry.rotationSteps + 1) & 3;
			rotationButton.setMessage(Text.literal(rotationLabel()));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 158, 170, 20).build();
		addDrawableChild(rotationButton);

		dimensionButton = ButtonWidget.builder(Text.literal(dimensionLabel()), b -> {
			entry.dimensionId = nextDimensionId(entry.dimensionId);
			dimensionButton.setMessage(Text.literal(dimensionLabel()));
			MapLibrary.INSTANCE.save();
		}).dimensions(x, 182, 170, 20).build();
		addDrawableChild(dimensionButton);

		ButtonWidget backButton = ButtonWidget.builder(Text.literal("Back to library"), b -> {
			MapLibrary.INSTANCE.save();
			MinecraftClient.getInstance().setScreen(new MapLibraryScreen());
		}).dimensions(x, 206, 170, 20).build();
		addDrawableChild(backButton);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		if (entry.projectPath == null) {
			context.drawText(textRenderer, Text.literal("No preview (No project selected)"), PREVIEW_X, PREVIEW_Y, 0xFFAAAAAA, true);
			return;
		}

		ensurePreviewLoaded();

		context.fill(PREVIEW_X - 1, PREVIEW_Y - 1, PREVIEW_X + PREVIEW_SIZE + 1, PREVIEW_Y + PREVIEW_SIZE + 1, 0xFF808080);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, previewTextureId,
				PREVIEW_X, PREVIEW_Y, 0f, 0f,
				PREVIEW_SIZE, PREVIEW_SIZE,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);
	}

	private void ensurePreviewLoaded() {
		if (entry.projectPath.equals(previewLoadedPath)) return;

		previewData = new MinimapData();
		previewData.load(Path.of(entry.projectPath));
		previewLoadedPath = entry.projectPath;

		for (int y = 0; y < MinimapData.CANVAS_HEIGHT; y++) {
			for (int x = 0; x < MinimapData.CANVAS_WIDTH; x++) {
				int argb = previewData.compositePixel(x, y);
				int a = (argb >>> 24) & 0xFF;
				if (a == 0) argb = 0xFFFFFFFF; else argb |= 0xFF000000;
				previewImage.setColor(x, y, toNativeImageColor(argb));
			}
		}
		previewTexture.upload();
	}

	private static int toNativeImageColor(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private String projectLabel() {
		if (entry.projectPath == null) return "Select project...";
		return Path.of(entry.projectPath).getFileName().toString();
	}

	private String rotationLabel() {
		return "Rotation: " + (entry.rotationSteps * 90) + "\u00b0";
	}

	private String dimensionLabel() {
		String friendly = switch (entry.dimensionId) {
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

	private void chooseProject() {
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
				entry.projectPath = result;
				projectButton.setMessage(Text.literal(projectLabel()));
				MapLibrary.INSTANCE.save();
			});
		}, "cartographerscanvas-map-project-dialog");
		thread.setDaemon(true);
		thread.start();
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

	@Override
	public void removed() {
		super.removed();
		MinecraftClient.getInstance().getTextureManager().destroyTexture(previewTextureId);
	}
}
