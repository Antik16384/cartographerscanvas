package net.antik16384.cartographerscanvas.canvas;

import java.util.ArrayList;
import java.util.List;

public class MinimapCanvas {

	private final int width;
	private final int height;
	private final int[] pixels;
	private final List<Label> labels = new ArrayList<>();

	public MinimapCanvas(int width, int height) {
		this.width = width;
		this.height = height;
		this.pixels = new int[width * height];
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getPixel(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) return 0;
		return pixels[y * width + x];
	}

	public void setPixel(int x, int y, int argbColor) {
		if (x < 0 || y < 0 || x >= width || y >= height) return;
		pixels[y * width + x] = argbColor;
	}

	public int[] copyPixels() {
		return pixels.clone();
	}

	public void restorePixels(int[] snapshot) {
		System.arraycopy(snapshot, 0, pixels, 0, pixels.length);
	}

	public List<Label> getLabels() {
		return labels;
	}

	public void addLabel(Label label) {
		labels.add(label);
	}
}
