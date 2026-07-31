package net.antik16384.cartographerscanvas.canvas;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MapStack implements LibraryItem {

	public String name = "New stack";
	public boolean enabled = true;
	public String dimensionId = "minecraft:overworld";
	public int corner1X = 0;
	public int corner1Z = 0;
	public int corner2X = 100;
	public int corner2Z = 100;
	public int rotationSteps = 0;
	public final List<StackLevel> levels = new ArrayList<>();

	public String fallbackProjectPath = null;

	@Override
	public String getName() { return name; }

	@Override
	public void setName(String name) { this.name = name; }

	@Override
	public boolean isEnabled() { return enabled; }

	@Override
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	@Override
	public String getPrimaryProjectPath() {
		return levels.isEmpty() ? null : levels.get(0).projectPath;
	}

	public int minX() { return Math.min(corner1X, corner2X); }
	public int maxX() { return Math.max(corner1X, corner2X); }
	public int minZ() { return Math.min(corner1Z, corner2Z); }
	public int maxZ() { return Math.max(corner1Z, corner2Z); }

	public float[] worldToNormalized(double worldX, double worldZ) {
		int spanX = Math.max(1, maxX() - minX());
		int spanZ = Math.max(1, maxZ() - minZ());

		float u = (float) ((worldX - minX()) / spanX);
		float v = (float) ((worldZ - minZ()) / spanZ);

		if (u < 0f || u > 1f || v < 0f || v > 1f) return null;

		float ru;
		float rv;
		switch (rotationSteps & 3) {
			case 1 -> { ru = v; rv = 1f - u; }
			case 2 -> { ru = 1f - u; rv = 1f - v; }
			case 3 -> { ru = 1f - v; rv = u; }
			default -> { ru = u; rv = v; }
		}
		return new float[]{ru, rv};
	}

	public float[] rotateDirection(float du, float dv) {
		return switch (rotationSteps & 3) {
			case 1 -> new float[]{dv, -du};
			case 2 -> new float[]{-du, -dv};
			case 3 -> new float[]{-dv, du};
			default -> new float[]{du, dv};
		};
	}

	@Override
	public LibraryMatch resolve(String dimension, double playerX, double playerY, double playerZ, float playerYaw, boolean roundShape) {
		if (!enabled || !dimensionId.equals(dimension)) return null;

		float[] normalized = worldToNormalized(playerX, playerZ);
		if (normalized == null) return null;

		if (roundShape) {
			float du = normalized[0] - 0.5f;
			float dv = normalized[1] - 0.5f;
			if (du * du + dv * dv > 0.25f) return null;
		}

		for (StackLevel level : levels) {
			if (level.projectPath == null) continue;
			if (!level.matchesY(playerY)) continue;

			float baseDu = (float) -Math.sin(Math.toRadians(playerYaw));
			float baseDv = (float) Math.cos(Math.toRadians(playerYaw));
			float[] direction = rotateDirection(baseDu, baseDv);
			return new LibraryMatch(level.projectPath, normalized, direction);
		}

		if (fallbackProjectPath != null) {
			float baseDu = (float) -Math.sin(Math.toRadians(playerYaw));
			float baseDv = (float) Math.cos(Math.toRadians(playerYaw));
			float[] direction = rotateDirection(baseDu, baseDv);
			return new LibraryMatch(fallbackProjectPath, normalized, direction);
		}

		return null;
	}

	public void exportToFile(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
			out.writeUTF(name);
			out.writeUTF(dimensionId);
			out.writeInt(corner1X);
			out.writeInt(corner1Z);
			out.writeInt(corner2X);
			out.writeInt(corner2Z);
			out.writeInt(rotationSteps);
			out.writeInt(levels.size());

			for (StackLevel level : levels) {
				out.writeUTF(level.name);
				out.writeInt(level.minY);
				out.writeInt(level.maxY);
				writeEmbeddedProject(out, level.projectPath);
			}

			out.writeBoolean(fallbackProjectPath != null);
			if (fallbackProjectPath != null) {
				writeEmbeddedProject(out, fallbackProjectPath);
			}
		}
	}

	private static void writeEmbeddedProject(DataOutputStream out, String projectPath) throws IOException {
		MinimapData data = new MinimapData();
		if (projectPath != null) {
			data.load(Path.of(projectPath));
		}

		out.writeInt(data.layers.size());
		for (MinimapData.Layer layer : data.layers) {
			out.writeUTF(layer.name);
			out.writeBoolean(layer.visible);
			for (int y = 0; y < MinimapData.CANVAS_HEIGHT; y++) {
				for (int x = 0; x < MinimapData.CANVAS_WIDTH; x++) {
					out.writeInt(layer.canvas.getPixel(x, y));
				}
			}
		}
	}

	public static MapStack importFromFile(Path path) throws IOException {
		MapStack stack = new MapStack();

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			stack.name = in.readUTF();
			stack.dimensionId = in.readUTF();
			stack.corner1X = in.readInt();
			stack.corner1Z = in.readInt();
			stack.corner2X = in.readInt();
			stack.corner2Z = in.readInt();
			stack.rotationSteps = in.readInt() & 3;
			int levelCount = in.readInt();

			for (int i = 0; i < levelCount; i++) {
				StackLevel level = new StackLevel();
				level.name = in.readUTF();
				level.minY = in.readInt();
				level.maxY = in.readInt();
				level.projectPath = readEmbeddedProject(in, stack.name, level.name).toString();
				stack.levels.add(level);
			}

			try {
				boolean hasFallback = in.readBoolean();
				if (hasFallback) {
					stack.fallbackProjectPath = readEmbeddedProject(in, stack.name, "Fallback").toString();
				}
			} catch (IOException ignored) {
				stack.fallbackProjectPath = null;
			}
		}

		return stack;
	}

	private static Path readEmbeddedProject(DataInputStream in, String stackName, String levelName) throws IOException {
		MinimapData data = new MinimapData();
		data.layers.clear();
		int layerCount = in.readInt();
		for (int l = 0; l < layerCount; l++) {
			String layerName = in.readUTF();
			boolean visible = in.readBoolean();
			MinimapCanvas canvas = new MinimapCanvas(MinimapData.CANVAS_WIDTH, MinimapData.CANVAS_HEIGHT);
			for (int y = 0; y < MinimapData.CANVAS_HEIGHT; y++) {
				for (int x = 0; x < MinimapData.CANVAS_WIDTH; x++) {
					canvas.setPixel(x, y, in.readInt());
				}
			}
			MinimapData.Layer layer = new MinimapData.Layer(canvas, layerName);
			layer.visible = visible;
			data.layers.add(layer);
		}

		Path materializedPath = uniqueMaterializedPath(stackName, levelName);
		data.save(materializedPath);
		return materializedPath;
	}

	private static Path uniqueMaterializedPath(String stackName, String levelName) {
		String baseName = sanitizeFileName(stackName + "_" + levelName);
		Path dir = MinimapData.INSTANCE.exportsDir();
		Path candidate = dir.resolve(baseName + ".ccmap");
		int suffix = 2;
		while (Files.exists(candidate)) {
			candidate = dir.resolve(baseName + "_" + suffix + ".ccmap");
			suffix++;
		}
		return candidate;
	}

	private static String sanitizeFileName(String raw) {
		return raw.replaceAll("[^a-zA-Z0-9_\\-]", "_");
	}
}
