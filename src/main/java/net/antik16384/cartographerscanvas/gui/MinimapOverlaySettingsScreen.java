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
		super(Text.literal("Overlay Settings"));

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

		ButtonWidget resetButton = ButtonWidget.builder(Text.literal("Reset"), b -> {
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
		return "Shape: " + (round ? "Circle" : "Square");
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
			context.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF808080);
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, previewTextureId,
				x, y, 0f, 0f,
				size, size,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);

		int handleX = x + size - HANDLE_SIZE;
		int handleY = y + size - HANDLE_SIZE;
		drawResizeHandle(context, handleX, handleY, HANDLE_SIZE);

		context.drawText(textRenderer,
				Text.literal("Drag to move, drag corner to scale"),
				10, 62, 0xFFFFFFFF, true);
	}

	private void drawResizeHandle(DrawContext context, int handleX, int handleY, int handleSize) {
		int backdrop = 0xFF303030;
		int iconColor = 0xFFFFFFFF;

		context.fill(handleX, handleY, handleX + handleSize, handleY + handleSize, backdrop);

		int pad = 3;
		int x1 = handleX + pad;
		int y1 = handleY + pad;
		int x2 = handleX + handleSize - pad;
		int y2 = handleY + handleSize - pad;

		drawPixelLine(context, x1, y1, x2, y2, iconColor);

		drawPixelLine(context, x1, y1, x1 + 3, y1, iconColor);
		drawPixelLine(context, x1, y1, x1, y1 + 3, iconColor);

		drawPixelLine(context, x2, y2, x2 - 3, y2, iconColor);
		drawPixelLine(context, x2, y2, x2, y2 - 3, iconColor);
	}

	private void drawPixelLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps == 0) {
			context.fill(x1, y1, x1 + 1, y1 + 1, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			context.fill(px, py, px + 1, py + 1, color);
		}
	}

	private void syncPreviewTexture(MinimapOverlayConfig.Shape shape) {
		MinimapData data = MinimapData.INSTANCE;
		int w = MinimapData.CANVAS_WIDTH;
		int h = MinimapData.CANVAS_HEIGHT;
		float cx = w / 2f;
		float cy = h / 2f;
		float innerRadius = Math.min(w, h) / 2f - 2f;
		float outerRadius = Math.min(w, h) / 2f;

		for (int py = 0; py < h; py++) {
			for (int px = 0; px < w; px++) {
				int argb = toOpaqueColor(data.compositePixel(px, py));

				if (shape == MinimapOverlayConfig.Shape.ROUND) {
					float dx = px + 0.5f - cx;
					float dy = py + 0.5f - cy;
					float distSq = dx * dx + dy * dy;
					if (distSq > outerRadius * outerRadius) {
						argb = 0;
					} else if (distSq > innerRadius * innerRadius) {
						argb = 0xFF808080;
					}
				}

				previewImage.setColor(px, py, toNativeImageColor(argb));
			}
		}
		previewTexture.upload();
	}

	private static int toOpaqueColor(int argb) {
		int a = (argb >>> 24) & 0xFF;
		if (a == 0) {
			return 0xFFFFFFFF;
		}
		return argb | 0xFF000000;
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

			double mouseX = click.x();
			double mouseY = click.y();

			if (mouseX >= handleX - handlePad && mouseX < handleX + HANDLE_SIZE + handlePad
					&& mouseY >= handleY - handlePad && mouseY < handleY + HANDLE_SIZE + handlePad) {
				resizing = true;
				resizeAnchorX = x;
				resizeAnchorY = y;
				return true;
			}

			if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
				dragging = true;
				dragOffsetX = (int) Math.round(mouseX - x);
				dragOffsetY = (int) Math.round(mouseY - y);
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
