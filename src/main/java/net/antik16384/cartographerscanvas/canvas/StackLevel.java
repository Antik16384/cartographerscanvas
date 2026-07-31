package net.antik16384.cartographerscanvas.canvas;

public class StackLevel {
	public String name = "Layer";
	public String projectPath;
	public int minY = -64;
	public int maxY = 320;

	public boolean matchesY(double y) {
		return y >= minY && y < maxY + 1;
	}
}
