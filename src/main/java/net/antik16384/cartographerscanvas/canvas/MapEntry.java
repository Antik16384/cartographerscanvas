package net.antik16384.cartographerscanvas.canvas;

public class MapEntry implements LibraryItem {

	public String name = "New map";
	public String projectPath;
	public boolean enabled = true;
	public String dimensionId = "minecraft:overworld";
	public int corner1X = 0;
	public int corner1Z = 0;
	public int corner2X = 100;
	public int corner2Z = 100;
	public int rotationSteps = 0;

	@Override
	public String getName() { return name; }

	@Override
	public void setName(String name) { this.name = name; }

	@Override
	public boolean isEnabled() { return enabled; }

	@Override
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	@Override
	public String getPrimaryProjectPath() { return projectPath; }

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
		if (!enabled || projectPath == null || !dimensionId.equals(dimension)) return null;

		float[] normalized = worldToNormalized(playerX, playerZ);
		if (normalized == null) return null;

		if (roundShape) {
			float du = normalized[0] - 0.5f;
			float dv = normalized[1] - 0.5f;
			if (du * du + dv * dv > 0.25f) return null;
		}

		float baseDu = (float) -Math.sin(Math.toRadians(playerYaw));
		float baseDv = (float) Math.cos(Math.toRadians(playerYaw));
		float[] direction = rotateDirection(baseDu, baseDv);
		return new LibraryMatch(projectPath, normalized, direction);
	}
}
