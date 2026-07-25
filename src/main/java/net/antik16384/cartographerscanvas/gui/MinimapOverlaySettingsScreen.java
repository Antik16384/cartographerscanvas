package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.antik16384.cartographerscanvas.canvas.MinimapOverlayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class MinimapOverlaySettingsScreen extends Screen {

	private static final int HANDLE_SIZE = 10;

	private final NativeImage previewImage;
	private final NativeImageBackedTexture previewTexture;
	private final Identifier previewTextureId;

	private ButtonWidget shapeButton;

	private boolean dragging = false;
	private boolean resizing = false;
	private int dragOffsetX;
	private int dragOffsetY;
	private int resizeAnchorX;
	private int resizeAnchorY;

	public MinimapOverlaySettingsScreen() {
		super(Text.literal("Minimap Einstellungen"));

		this.previewImage = new NativeImage(MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT, true);
		this.previewTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_overlay_preview", previewImage);
		this.previewTextureId = Identifier.of("cartographerscanvas", "overlay_preview_" + System.identityHashCode(this));
		MinecraftClient.getInstance().getTextureManager().registerTexture(previewTextureId, previewTexture);
	}

	@Override
	protected void init() {
		super.init();

		shapeButton = ButtonWidget.builder(Text.literal(shapeLabel()), b -> {
			MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;
			config.shape = config.shape == MinimapOverlayConfig.Shape.SQUARE
					? MinimapOverlayConfig.Shape.ROUND
					: MinimapOverlayConfig.Shape.SQUARE;
			shapeButton.setMessage(Text.literal(shapeLabel()));
			config.save();
		}).dimensions(10, 10, 130, 20).build();
		addDrawableChild(shapeButton);

		ButtonWidget resetButton = ButtonWidget.builder(Text.literal("Zuruecksetzen"), b -> {
			MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;
			config.anchorXFraction = 1f;
			config.anchorYFraction = 0f;
			config.size = 128;
			config.shape = MinimapOverlayConfig.Shape.SQUARE;
			shapeButton.setMessage(Text.literal(shapeLabel()));
			config.save();
		}).dimensions(10, 34, 130, 20).build();
		addDrawableChild(resetButton);
	}

	private String shapeLabel() {
		boolean round = MinimapOverlayConfig.INSTANCE.shape == MinimapOverlayConfig.Shape.ROUND;
		return "Form: " + (round ? "Rund" : "Quadrat");
	}

	private int[] currentBox() {
		MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;
		int size = config.size;
		int x = config.getScreenX(width);
		int y = config.getScreenY(height);
		return new int[]{x, y, size};
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;
		syncPreviewTexture(config.shape);

		int[] box = currentBox();
		int x = box[0];
		int y = box[1];
		int size = box[2];

		if (config.shape == MinimapOverlayConfig.Shape.SQUARE) {
			context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF55CCFF);
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, previewTextureId,
				x, y, 0f, 0f, size, size,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);

		int handleX = x + size - HANDLE_SIZE;
		int handleY = y + size - HANDLE_SIZE;
		context.fill(handleX, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, 0xFF55CCFF);
		context.fill(handleX + 1, handleY + 1, handleX + HANDLE_SIZE - 1, handleY + HANDLE_SIZE - 1, 0xFF000000);

		context.drawText(textRenderer,
				Text.literal("Ziehen zum Verschieben, Ecke unten rechts ziehen zum Skalieren"),
				10, 62, 0xFFFFFFFF, true);
	}

	private void syncPreviewTexture(MinimapOverlayConfig.Shape shape) {
		MinimapData data = MinimapData.INSTANCE;
		int w = MinimapData.CANVAS_WIDTH;
		int h = MinimapData.CANVAS_HEIGHT;
		float cx = w / 2f;
		float cy = h / 2f;
		float radius = Math.min(w, h) / 2f;

		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int argb = data.compositePixel(px, py);
				if (shape == MinimapOverlayConfig.Shape.ROUND) {
					float dx = px + 0.5f - cx;
					float dy = py + 0.5f - cy;
					if (dx * dx + dy * dy > radius * radius) {
						argb &= 0x00FFFFFF;
					}
				}
				previewImage.setColor(px, py, toNativeImageColor(argb));
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

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0) {
			int[] box = currentBox();
			int x = box[0];
			int y = box[1];
			int size = box[2];

			int handleX = x + size - HANDLE_SIZE;
			int handleY = y + size - HANDLE_SIZE;
			int handlePad = 4;

			if (click.x() >= handleX - handlePad && click.x() < handleX + HANDLE_SIZE + handlePad
					&& click.y() >= handleY - handlePad && click.y() < handleY + HANDLE_SIZE + handlePad) {
				resizing = true;
				resizeAnchorX = x;
				resizeAnchorY = y;
				return true;
			}

			if (click.x() >= x && click.x() < x + size && click.y() >= y && click.y() < y + size) {
				dragging = true;
				dragOffsetX = (int) Math.round(click.x() - x);
				dragOffsetY = (int) Math.round(click.y() - y);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double deltaX, double deltaY) {
		MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;

		if (dragging) {
			int newX = (int) Math.round(click.x() - dragOffsetX);
			int newY = (int) Math.round(click.y() - dragOffsetY);
			newX = MathHelper.clamp(newX, 0, Math.max(0, width - config.size));
			newY = MathHelper.clamp(newY, 0, Math.max(0, height - config.size));
			config.setScreenPosition(newX, newY, width, height);
			return true;
		}

		if (resizing) {
			int newSize = (int) Math.round(Math.max(click.x() - resizeAnchorX, click.y() - resizeAnchorY));
			newSize = MathHelper.clamp(newSize, MinimapOverlayConfig.MIN_SIZE, MinimapOverlayConfig.MAX_SIZE);
			config.size = newSize;
			config.setScreenPosition(resizeAnchorX, resizeAnchorY, width, height);
			return true;
		}

		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (dragging || resizing) {
			dragging = false;
			resizing = false;
			MinimapOverlayConfig.INSTANCE.save();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public void close() {
		MinimapOverlayConfig.INSTANCE.save();
		super.close();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		MinecraftClient.getInstance().getTextureManager().destroyTexture(previewTextureId);
	}
}
