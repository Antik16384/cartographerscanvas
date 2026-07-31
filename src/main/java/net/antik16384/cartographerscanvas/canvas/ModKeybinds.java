package net.antik16384.cartographerscanvas.canvas;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModKeybinds {

	public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

	public static final ModKeybinds INSTANCE = new ModKeybinds();

	public int editorKey = UNBOUND;
	public int settingsKey = UNBOUND;
	public int libraryKey = UNBOUND;

	private ModKeybinds() {
	}

	private Path getConfigFilePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("cartographerscanvas").resolve("keybinds.dat");
	}

	public void save() {
		try {
			Path path = getConfigFilePath();
			Files.createDirectories(path.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				out.writeInt(editorKey);
				out.writeInt(settingsKey);
				out.writeInt(libraryKey);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void load() {
		Path path = getConfigFilePath();
		if (!Files.exists(path)) return;

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			editorKey = in.readInt();
			settingsKey = in.readInt();
			libraryKey = in.readInt();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
