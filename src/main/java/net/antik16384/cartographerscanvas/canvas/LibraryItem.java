package net.antik16384.cartographerscanvas.canvas;

public interface LibraryItem {

	String getName();

	void setName(String name);

	boolean isEnabled();

	void setEnabled(boolean enabled);

	LibraryMatch resolve(String dimension, double playerX, double playerY, double playerZ, float playerYaw, boolean roundShape);

	String getPrimaryProjectPath();
}
