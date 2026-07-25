package net.antik16384.cartographerscanvas.canvas;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class MinimapOverlayConfig {

	public static final MinimapOverlayConfig INSTANCE = new MinimapOverlayConfig();

	public enum Shape { SQUARE, ROUND }

	public static final int MIN_SIZE = 48;
	public static final int MAX_SIZE = 256;

	public float anchorXFraction = 1f;
	public float anchorYFraction = 0f;
	public int size = 128;
	public Shape shape = Shape.SQUARE;

	private MinimapOverlayConfig() {
	}

	private Path getConfigFilePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("cartographerscanvas").resolve("overlay.dat");
	}

	public void save() {
		try {
			Path path = getConfigFilePath();
			Files.createDirectories(path.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				out.writeFloat(anchorXFraction);
				out.writeFloat(anchorYFraction);
				out.writeInt(size);
				out.writeUTF(shape.name());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void load() {
		Path path = getConfigFilePath();
		if (!Files.exists(path)) return;

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			anchorXFraction = in.readFloat();
			anchorYFraction = in.readFloat();
			size = in.readInt();
			try {
				shape = Shape.valueOf(in.readUTF());
			} catch (IllegalArgumentException ignored) {
				shape = Shape.SQUARE;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getScreenX(int scaledWidth) {
		int maxX = Math.max(1, scaledWidth - size);
		return Math.round(MathHelper.clamp(anchorXFraction, 0f, 1f) * maxX);
	}

	public int getScreenY(int scaledHeight) {
		int maxY = Math.max(1, scaledHeight - size);
		return Math.round(MathHelper.clamp(anchorYFraction, 0f, 1f) * maxY);
	}

	public void setScreenPosition(int screenX, int screenY, int scaledWidth, int scaledHeight) {
		int maxX = Math.max(1, scaledWidth - size);
		int maxY = Math.max(1, scaledHeight - size);
		anchorXFraction = MathHelper.clamp(screenX / (float) maxX, 0f, 1f);
		anchorYFraction = MathHelper.clamp(screenY / (float) maxY, 0f, 1f);
	}
}
