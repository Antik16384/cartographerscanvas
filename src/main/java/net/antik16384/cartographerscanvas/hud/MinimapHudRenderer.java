package net.antik16384.cartographerscanvas.hud;

import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.antik16384.cartographerscanvas.canvas.MinimapOverlayConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public class MinimapHudRenderer {

	private NativeImage image;
	private NativeImageBackedTexture texture;
	private Identifier textureId;
	private boolean initialized = false;

	public void register() {
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	private void ensureInitialized() {
		if (initialized) return;

		int w = MinimapData.CANVAS_WIDTH;
		int h = MinimapData.CANVAS_HEIGHT;

		image = new NativeImage(w, h, true);
		texture = new NativeImageBackedTexture(() -> "cartographerscanvas_hud", image);
		textureId = Identifier.of("cartographerscanvas", "hud_overlay");
		MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
		initialized = true;
	}

	private void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		MinimapOverlayConfig config = MinimapOverlayConfig.INSTANCE;

		ensureInitialized();
		syncTexture(config.shape);

		int scaledWidth = client.getWindow().getScaledWidth();
		int scaledHeight = client.getWindow().getScaledHeight();
		int size = config.size;
		int x = config.getScreenX(scaledWidth);
		int y = config.getScreenY(scaledHeight);

		if (config.shape == MinimapOverlayConfig.Shape.SQUARE) {
			context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF808080);
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
				x, y, 0f, 0f, size, size,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);
	}

	private void syncTexture(MinimapOverlayConfig.Shape shape) {
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
				image.setColor(px, py, toNativeImageColor(argb));
			}
		}
		texture.upload();
	}

	private static int toNativeImageColor(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}
}
