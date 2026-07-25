package net.antik16384.cartographerscanvas.canvas;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Datenhaltung für die Minimap: Ebenen, Compositing sowie Speichern/Laden.
 * Wird sowohl vom Editor (MinimapScreen) als auch vom HUD-Renderer benutzt,
 * damit beide immer denselben Stand sehen.
 */
public class MinimapData {

	public static final MinimapData INSTANCE = new MinimapData();

	public static final int CANVAS_WIDTH = 128;
	public static final int CANVAS_HEIGHT = 128;
	public static final int MAX_LAYERS = 8;

	public static class Layer {
		public final MinimapCanvas canvas;
		public boolean visible = true;
		public String name;

		public Layer(MinimapCanvas canvas, String name) {
			this.canvas = canvas;
			this.name = name;
		}
	}

	public final List<Layer> layers = new ArrayList<>();
	public int activeLayerIndex = 0;

	private MinimapData() {
		layers.add(new Layer(new MinimapCanvas(CANVAS_WIDTH, CANVAS_HEIGHT), "Ebene 1"));
	}

	public Layer activeLayer() {
		return layers.get(activeLayerIndex);
	}

	public MinimapCanvas activeCanvas() {
		return activeLayer().canvas;
	}

	public void renumberLayers() {
		for (int i = 0; i < layers.size(); i++) {
			layers.get(i).name = "Ebene " + (i + 1);
		}
	}

	public void reset() {
		layers.clear();
		layers.add(new Layer(new MinimapCanvas(CANVAS_WIDTH, CANVAS_HEIGHT), "Ebene 1"));
		activeLayerIndex = 0;
	}

	public int compositePixel(int x, int y) {
		int a = 0;
		int r = 0;
		int g = 0;
		int b = 0;

		for (Layer layer : layers) {
			if (!layer.visible) continue;
			int c = layer.canvas.getPixel(x, y);
			int srcA = (c >>> 24) & 0xFF;
			if (srcA == 0) continue;

			int srcR = (c >>> 16) & 0xFF;
			int srcG = (c >>> 8) & 0xFF;
			int srcB = c & 0xFF;

			if (a == 0) {
				a = srcA;
				r = srcR;
				g = srcG;
				b = srcB;
				continue;
			}

			float sa = srcA / 255f;
			float da = a / 255f;
			float outA = sa + da * (1 - sa);
			if (outA <= 0f) {
				a = 0; r = 0; g = 0; b = 0;
				continue;
			}

			r = Math.round((srcR * sa + r * da * (1 - sa)) / outA);
			g = Math.round((srcG * sa + g * da * (1 - sa)) / outA);
			b = Math.round((srcB * sa + b * da * (1 - sa)) / outA);
			a = Math.round(outA * 255);
		}

		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private Path getSaveFilePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("cartographerscanvas").resolve("autosave.dat");
	}

	public void save() {
		try {
			Path path = getSaveFilePath();
			Files.createDirectories(path.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				out.writeInt(CANVAS_WIDTH);
				out.writeInt(CANVAS_HEIGHT);
				out.writeInt(layers.size());
				out.writeInt(activeLayerIndex);
				for (Layer layer : layers) {
					out.writeUTF(layer.name);
					out.writeBoolean(layer.visible);
					for (int y = 0; y < CANVAS_HEIGHT; y++) {
						for (int x = 0; x < CANVAS_WIDTH; x++) {
							out.writeInt(layer.canvas.getPixel(x, y));
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void load() {
		Path path = getSaveFilePath();
		if (!Files.exists(path)) return;

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			int w = in.readInt();
			int h = in.readInt();
			int layerCount = in.readInt();
			int savedActive = in.readInt();

			List<Layer> loaded = new ArrayList<>();
			for (int i = 0; i < layerCount; i++) {
				String name = in.readUTF();
				boolean visible = in.readBoolean();
				MinimapCanvas layerCanvas = new MinimapCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int color = in.readInt();
						if (x < CANVAS_WIDTH && y < CANVAS_HEIGHT) {
							layerCanvas.setPixel(x, y, color);
						}
					}
				}
				Layer entry = new Layer(layerCanvas, name);
				entry.visible = visible;
				loaded.add(entry);
			}

			if (!loaded.isEmpty()) {
				layers.clear();
				layers.addAll(loaded);
				activeLayerIndex = Math.max(0, Math.min(savedActive, layers.size() - 1));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
