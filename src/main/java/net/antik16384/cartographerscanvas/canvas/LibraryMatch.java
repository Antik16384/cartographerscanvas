package net.antik16384.cartographerscanvas.canvas;

public class LibraryMatch {
	public final String projectPath;
	public final float[] normalized;
	public final float[] direction;

	public LibraryMatch(String projectPath, float[] normalized, float[] direction) {
		this.projectPath = projectPath;
		this.normalized = normalized;
		this.direction = direction;
	}
}
