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
import java.util.Collections;
import java.util.List;

public class MapLibrary {

	public static final MapLibrary INSTANCE = new MapLibrary();

	private static final byte TYPE_MAP_ENTRY = 0;
	private static final byte TYPE_MAP_STACK = 1;

	public final List<LibraryItem> entries = new ArrayList<>();

	public enum NoMatchMode { HIDE, SHOW_TOP_PRIORITY, SHOW_FALLBACK }

	public NoMatchMode noMatchMode = NoMatchMode.HIDE;
	public String fallbackProjectPath = null;

	private MapLibrary() {
	}

	private Path getConfigFilePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("cartographerscanvas").resolve("maplibrary.dat");
	}

	public boolean nameExists(String name) {
		for (LibraryItem item : entries) {
			if (item.getName().equals(name)) return true;
		}
		return false;
	}

	// that annoying (2) thing yk
	public String uniqueName(String baseName) {
		if (!nameExists(baseName)) return baseName;
		int suffix = 2;
		while (nameExists(baseName + " (" + suffix + ")")) {
			suffix++;
		}
		return baseName + " (" + suffix + ")";
	}

	public void moveUp(int index) {
		if (index <= 0 || index >= entries.size()) return;
		Collections.swap(entries, index, index - 1);
	}

	public void moveDown(int index) {
		if (index < 0 || index >= entries.size() - 1) return;
		Collections.swap(entries, index, index + 1);
	}

	public void save() {
		try {
			Path path = getConfigFilePath();
			Files.createDirectories(path.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
				out.writeInt(entries.size());
				for (LibraryItem item : entries) {
					if (item instanceof MapEntry entry) {
						out.writeByte(TYPE_MAP_ENTRY);
						writeMapEntry(out, entry);
					} else if (item instanceof MapStack stack) {
						out.writeByte(TYPE_MAP_STACK);
						writeMapStack(out, stack);
					}
				}
				out.writeBoolean(fallbackProjectPath != null);
				if (fallbackProjectPath != null) {
					out.writeUTF(fallbackProjectPath);
				}
				out.writeUTF(noMatchMode.name());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void load() {
		Path path = getConfigFilePath();
		if (!Files.exists(path)) return;

		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			int count = in.readInt();
			List<LibraryItem> loaded = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				byte type = in.readByte();
				if (type == TYPE_MAP_STACK) {
					loaded.add(readMapStack(in));
				} else {
					loaded.add(readMapEntry(in));
				}
			}
			entries.clear();
			entries.addAll(loaded);

			try {
				boolean hasFallback = in.readBoolean();
				fallbackProjectPath = hasFallback ? in.readUTF() : null;
				noMatchMode = NoMatchMode.valueOf(in.readUTF());
			} catch (IOException | IllegalArgumentException ignored) {
				noMatchMode = NoMatchMode.HIDE;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeMapEntry(DataOutputStream out, MapEntry entry) throws IOException {
		out.writeUTF(entry.name);
		out.writeBoolean(entry.enabled);
		out.writeUTF(entry.dimensionId);
		out.writeBoolean(entry.projectPath != null);
		if (entry.projectPath != null) {
			out.writeUTF(entry.projectPath);
		}
		out.writeInt(entry.corner1X);
		out.writeInt(entry.corner1Z);
		out.writeInt(entry.corner2X);
		out.writeInt(entry.corner2Z);
		out.writeInt(entry.rotationSteps);
	}

	private MapEntry readMapEntry(DataInputStream in) throws IOException {
		MapEntry entry = new MapEntry();
		entry.name = in.readUTF();
		entry.enabled = in.readBoolean();
		entry.dimensionId = in.readUTF();
		boolean hasPath = in.readBoolean();
		entry.projectPath = hasPath ? in.readUTF() : null;
		entry.corner1X = in.readInt();
		entry.corner1Z = in.readInt();
		entry.corner2X = in.readInt();
		entry.corner2Z = in.readInt();
		entry.rotationSteps = in.readInt() & 3;
		return entry;
	}

	private void writeMapStack(DataOutputStream out, MapStack stack) throws IOException {
		out.writeUTF(stack.name);
		out.writeBoolean(stack.enabled);
		out.writeUTF(stack.dimensionId);
		out.writeInt(stack.corner1X);
		out.writeInt(stack.corner1Z);
		out.writeInt(stack.corner2X);
		out.writeInt(stack.corner2Z);
		out.writeInt(stack.rotationSteps);
		out.writeInt(stack.levels.size());
		for (StackLevel level : stack.levels) {
			out.writeUTF(level.name);
			out.writeBoolean(level.projectPath != null);
			if (level.projectPath != null) {
				out.writeUTF(level.projectPath);
			}
			out.writeInt(level.minY);
			out.writeInt(level.maxY);
		}
		out.writeBoolean(stack.fallbackProjectPath != null);
		if (stack.fallbackProjectPath != null) {
			out.writeUTF(stack.fallbackProjectPath);
		}
	}

	private MapStack readMapStack(DataInputStream in) throws IOException {
		MapStack stack = new MapStack();
		stack.name = in.readUTF();
		stack.enabled = in.readBoolean();
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
			boolean hasPath = in.readBoolean();
			level.projectPath = hasPath ? in.readUTF() : null;
			level.minY = in.readInt();
			level.maxY = in.readInt();
			stack.levels.add(level);
		}
		try {
			boolean hasFallback = in.readBoolean();
			stack.fallbackProjectPath = hasFallback ? in.readUTF() : null;
		} catch (IOException ignored) {
			stack.fallbackProjectPath = null;
		}
		return stack;
	}
}
