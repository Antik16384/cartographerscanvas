package net.antik16384.cartographerscanvas.hud;

import net.antik16384.cartographerscanvas.canvas.LibraryItem;
import net.antik16384.cartographerscanvas.canvas.LibraryMatch;
import net.antik16384.cartographerscanvas.canvas.MapLibrary;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MinimapHudRenderer {

	private NativeImage image;
	private NativeImageBackedTexture texture;
	private Identifier textureId;
	private boolean initialized = false;

	private final Map<String, MinimapData> projectCache = new HashMap<>();

	private NativeImage indicatorImage;
	private NativeImageBackedTexture indicatorTexture;
	private Identifier indicatorTextureId;
	private int indicatorSize;
	private boolean indicatorLoadAttempted = false;

	private static class RenderPlan {
		MinimapData data;
		float[] normalized;
		float[] direction;
	}

	public void register() {
		HudRenderCallback.EVENT.register(this::onHudRender);
	}

	public void clearProjectCache() {
		projectCache.clear();
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
		String dimension = client.player.getEntityWorld().getRegistryKey().getValue().toString();

		RenderPlan plan = resolveRenderPlan(config, dimension, client.player.getX(), client.player.getY(), client.player.getZ(), client.player.getYaw());
		if (plan == null) return;

		ensureInitialized();
		syncTexture(config.shape, plan.data);

		int scaledWidth = client.getWindow().getScaledWidth();
		int scaledHeight = client.getWindow().getScaledHeight();
		int size = config.size;
		int x = config.getScreenX(scaledWidth);
		int y = config.getScreenY(scaledHeight);

		if (config.shape == MinimapOverlayConfig.Shape.SQUARE) {
			context.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF808080);
		}

		context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
				x, y, 0f, 0f,
				size, size,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT,
				MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);

		if (plan.normalized != null) {
			drawPlayerIndicator(context, x, y, size, plan.normalized[0], plan.normalized[1], plan.direction[0], plan.direction[1]);
		}
	}

	private RenderPlan resolveRenderPlan(MinimapOverlayConfig config, String dimension, double playerX, double playerY, double playerZ, float playerYaw) {
		boolean round = config.shape == MinimapOverlayConfig.Shape.ROUND;

		for (LibraryItem item : MapLibrary.INSTANCE.entries) {
			LibraryMatch match = item.resolve(dimension, playerX, playerY, playerZ, playerYaw, round);
			if (match == null) continue;

			RenderPlan plan = new RenderPlan();
			plan.data = resolveCachedProject(match.projectPath);
			plan.normalized = match.normalized;
			plan.direction = match.direction;
			return plan;
		}

		return switch (MapLibrary.INSTANCE.noMatchMode) {
			case HIDE -> null;
			case SHOW_TOP_PRIORITY -> resolveTopPriorityPlan();
			case SHOW_FALLBACK -> resolveFallbackPlan();
		};
	}

	private RenderPlan resolveTopPriorityPlan() {
		for (LibraryItem item : MapLibrary.INSTANCE.entries) {
			if (!item.isEnabled()) continue;
			String path = item.getPrimaryProjectPath();
			if (path == null) continue;

			RenderPlan plan = new RenderPlan();
			plan.data = resolveCachedProject(path);
			return plan;
		}
		return null;
	}

	private RenderPlan resolveFallbackPlan() {
		String fallbackPath = MapLibrary.INSTANCE.fallbackProjectPath;
		if (fallbackPath == null) return null;
		RenderPlan plan = new RenderPlan();
		plan.data = resolveCachedProject(fallbackPath);
		return plan;
	}

	private MinimapData resolveCachedProject(String pathString) {
		return projectCache.computeIfAbsent(pathString, p -> {
			MinimapData data = new MinimapData();
			data.load(Path.of(p));
			return data;
		});
	}

	// PLAYER INDICATOR
	private void ensureIndicatorLoaded() {
		if (indicatorLoadAttempted) return;
		indicatorLoadAttempted = true;

		BufferedImage sourceImage = null;
		try (InputStream stream = MinimapHudRenderer.class.getResourceAsStream("/assets/cartographerscanvas/textures/gui/player_indicator.png")) {
			if (stream != null) {
				sourceImage = ImageIO.read(stream);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// FALLBACK
		if (sourceImage == null) return;

		indicatorSize = Math.max(sourceImage.getWidth(), sourceImage.getHeight());
		indicatorImage = new NativeImage(indicatorSize, indicatorSize, true);
		for (int y = 0; y < indicatorSize; y++) {
			for (int x = 0; x < indicatorSize; x++) {
				indicatorImage.setColor(x, y, toNativeImageColor(sourceImage.getRGB(x, y)));
			}
		}
		indicatorTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_player_indicator", indicatorImage);
		indicatorTextureId = Identifier.of("cartographerscanvas", "player_indicator_dynamic");
		MinecraftClient.getInstance().getTextureManager().registerTexture(indicatorTextureId, indicatorTexture);
		indicatorTexture.upload();
	}

	private void drawPlayerIndicator(DrawContext context, int overlayX, int overlayY, int overlaySize, float normU, float normV, float dirU, float dirV) {
		ensureIndicatorLoaded();

		int px = overlayX + Math.round(normU * overlaySize);
		int py = overlayY + Math.round(normV * overlaySize);

		if (indicatorTexture == null) {
			drawFallbackIndicator(context, px, py, dirU, dirV);
			return;
		}

		float angleRadians = (float) Math.atan2(dirU, -dirV);
		int half = indicatorSize / 2;

		context.getMatrices().pushMatrix();
		context.getMatrices().translate((float) px, (float) py);
		context.getMatrices().rotate(angleRadians);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, indicatorTextureId,
				-half, -half, 0f, 0f,
				indicatorSize, indicatorSize,
				indicatorSize, indicatorSize,
				indicatorSize, indicatorSize);
		context.getMatrices().popMatrix();
	}

	private void drawFallbackIndicator(DrawContext context, int px, int py, float dirU, float dirV) {
		int color = 0xFFFF4040;
		int dotRadius = 2;
		context.fill(px - dotRadius, py - dotRadius, px + dotRadius + 1, py + dotRadius + 1, 0xFF000000);
		context.fill(px - dotRadius + 1, py - dotRadius + 1, px + dotRadius, py + dotRadius, color);

		int noseLength = 6;
		int noseX = px + Math.round(dirU * noseLength);
		int noseY = py + Math.round(dirV * noseLength);
		drawPixelLine(context, px, py, noseX, noseY, color);
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

	private void syncTexture(MinimapOverlayConfig.Shape shape, MinimapData data) {
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

				image.setColor(px, py, toNativeImageColor(argb));
			}
		}
		texture.upload();
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
}
