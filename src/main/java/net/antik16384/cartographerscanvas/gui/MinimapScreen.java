package net.antik16384.cartographerscanvas.gui;

import net.antik16384.cartographerscanvas.canvas.MinimapCanvas;
import net.antik16384.cartographerscanvas.canvas.MinimapData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

public class MinimapScreen extends Screen {

	private enum Tool { PEN, ERASER, FILL, LINE, RECTANGLE, CIRCLE, SELECT, EYEDROPPER }

	private enum ColorSlot { PRIMARY, SECONDARY }

	private enum ActiveButton { NONE, LEFT, RIGHT }

	@FunctionalInterface
	private interface PixelConsumer {
		void accept(int x, int y);
	}

	private static final int SV_SIZE = 80;
	private static final int HUE_STRIP_SIZE = 80;
	private static final int HUE_STRIP_THICKNESS = 22;
	private static final int SIDE_BUTTON_WIDTH = 70;
	private static final int SIDE_BUTTON_HEIGHT = 20;
	private static final int SIDE_BUTTON_GAP = 4;
	private static final int SIDE_CANVAS_GAP = 12;
	private static final int TOOL_COLS = 2;
	private static final int TOOL_ROWS = 3;
	private static final int LEFT_COLUMN_WIDTH = 150;
	private static final float MIN_ZOOM = 1f;
	private static final float MAX_ZOOM = 8f;
	private static final int CANVAS_WIDTH = MinimapData.CANVAS_WIDTH;
	private static final int CANVAS_HEIGHT = MinimapData.CANVAS_HEIGHT;

	private final NativeImage canvasImage;
	private final NativeImageBackedTexture canvasTexture;
	private final Identifier canvasTextureId;

	private final NativeImage svImage;
	private final NativeImageBackedTexture svTexture;
	private final Identifier svTextureId;

	private final NativeImage hueImage;
	private final NativeImageBackedTexture hueTexture;
	private final Identifier hueTextureId;

	private int canvasX;
	private int leftColumnX;
	private int rightColumnX;
	private int viewportTop;
	private int viewportBottom;
	private float leftScroll = 0f;
	private float rightScroll = 0f;
	private final List<ClickableWidget> leftColumnWidgets = new ArrayList<>();
	private final List<ClickableWidget> rightColumnWidgets = new ArrayList<>();
	private int canvasY;
	private int pixelScale = 2;
	private float viewZoom = 1f;
	private float viewCenterX;
	private float viewCenterY;
	private float regionU;
	private float regionV;
	private int regionWidth;
	private int regionHeight;
	private GLFWScrollCallback previousScrollCallback;

	private int brushSize = 1;

	private int primaryR = 255;
	private int primaryG = 0;
	private int primaryB = 0;
	private int primaryA = 255;
	private int secondaryR = 255;
	private int secondaryG = 255;
	private int secondaryB = 255;
	private int secondaryA = 255;
	private int primaryColor;
	private int secondaryColor;
	private ColorSlot activeSlot = ColorSlot.PRIMARY;

	private float pickerHue = 0f;
	private float pickerSat = 1f;
	private float pickerVal = 1f;

	private Tool currentTool = Tool.PEN;
	private ActiveButton activeButton = ActiveButton.NONE;
	private boolean suppressUntilRelease = false;

	private int strokeLastCanvasX;
	private int strokeLastCanvasY;
	private int shapeStartCanvasX;
	private int shapeStartCanvasY;

	private boolean hasSelection = false;
	private int selectionMinX;
	private int selectionMinY;
	private int selectionMaxX;
	private int selectionMaxY;

	private boolean hasClipboard = false;
	private boolean pendingPaste = false;
	private int clipboardWidth;
	private int clipboardHeight;
	private int[] clipboardPixels;

	private boolean clearArmed = false;
	private boolean resetArmed = false;
	private ButtonWidget resetButton;
	private static final int MAX_UNDO = 50;

	private Tool selectedShape = Tool.LINE;
	private ButtonWidget shapeCycleButton;
	private ButtonWidget shapeSelectButton;
	private long saveFeedbackUntil = 0L;

	private ButtonWidget[] toolButtons;
	private Tool[] toolButtonTools;
	private ButtonWidget clearButton;
	private ButtonWidget saveButton;
	private ButtonWidget primaryButton;
	private ButtonWidget secondaryButton;
	private int primarySwatchX;
	private int primarySwatchY;
	private int secondarySwatchX;
	private int secondarySwatchY;

	private int svX;
	private int svY;
	private int hueX;
	private int hueY;

	private ColorSlider redSlider;
	private ColorSlider greenSlider;
	private ColorSlider blueSlider;
	private ColorSlider alphaSlider;
	private TextFieldWidget hexField;

	public MinimapScreen() {
		super(Text.literal("Cartographer's Canvas"));

		this.canvasImage = new NativeImage(CANVAS_WIDTH, CANVAS_HEIGHT, true);
		this.canvasTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_canvas", canvasImage);
		this.canvasTextureId = Identifier.of("cartographerscanvas", "canvas_" + System.identityHashCode(this));
		MinecraftClient.getInstance().getTextureManager().registerTexture(canvasTextureId, canvasTexture);

		this.svImage = new NativeImage(SV_SIZE, SV_SIZE, true);
		this.svTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_sv", svImage);
		this.svTextureId = Identifier.of("cartographerscanvas", "sv_" + System.identityHashCode(this));
		MinecraftClient.getInstance().getTextureManager().registerTexture(svTextureId, svTexture);

		this.hueImage = new NativeImage(HUE_STRIP_SIZE, HUE_STRIP_THICKNESS, true);
		this.hueTexture = new NativeImageBackedTexture(() -> "cartographerscanvas_hue", hueImage);
		this.hueTextureId = Identifier.of("cartographerscanvas", "hue_" + System.identityHashCode(this));
		MinecraftClient.getInstance().getTextureManager().registerTexture(hueTextureId, hueTexture);

		this.viewCenterX = CANVAS_WIDTH / 2f;
		this.viewCenterY = CANVAS_HEIGHT / 2f;

		generateHueStripTexture();

		long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
		previousScrollCallback = GLFW.glfwSetScrollCallback(windowHandle, (win, xoffset, yoffset) -> {
			int mx = getLiveMouseX();
			int my = getLiveMouseY();

			if (isInsideCanvas(mx, my)) {
				if (yoffset > 0) zoomAtCursor(1.25, mx, my);
				else if (yoffset < 0) zoomAtCursor(0.8, mx, my);
			} else if (mx >= leftColumnX && mx < leftColumnX + LEFT_COLUMN_WIDTH && my >= viewportTop) {
				leftScroll -= yoffset * 20;
				clearChildren();
				init();
			} else if (mx >= rightColumnX && mx < rightColumnX + LEFT_COLUMN_WIDTH && my >= viewportTop) {
				rightScroll -= yoffset * 20;
				clearChildren();
				init();
			}
		});
	}

	@Override
	protected void init() {
		super.init();

		int marginTop = 40;
		int outerMargin = 10;
		int bottomMargin = 20;

		int reservedHorizontal = LEFT_COLUMN_WIDTH + SIDE_CANVAS_GAP + LEFT_COLUMN_WIDTH + SIDE_CANVAS_GAP + outerMargin * 2;
		int maxScaleByWidth = Math.max(1, (width - reservedHorizontal) / CANVAS_WIDTH);
		int maxScaleByHeight = Math.max(1, (height - marginTop - bottomMargin) / CANVAS_HEIGHT);
		pixelScale = Math.max(1, Math.min(6, Math.min(maxScaleByWidth, maxScaleByHeight)));

		// Beide Seiten bekommen die GLEICHE reservierte Breite (LEFT_COLUMN_WIDTH), damit die Canvas wirklich zentriert ist
		int totalContentWidth = LEFT_COLUMN_WIDTH + SIDE_CANVAS_GAP + CANVAS_WIDTH * pixelScale
				+ SIDE_CANVAS_GAP + LEFT_COLUMN_WIDTH;
		int contentStartX = (width - totalContentWidth) / 2;
		leftColumnX = contentStartX;
		canvasX = leftColumnX + LEFT_COLUMN_WIDTH + SIDE_CANVAS_GAP;
		rightColumnX = canvasX + CANVAS_WIDTH * pixelScale + SIDE_CANVAS_GAP;
		canvasY = marginTop;

		viewportTop = canvasY;
		viewportBottom = height - bottomMargin;

		leftColumnWidgets.clear();
		rightColumnWidgets.clear();

		initLeftColumn(leftColumnX, canvasY);
		initSideActions(rightColumnX, canvasY);

		clampAndApplyScroll(leftColumnWidgets, leftScroll, true);
		clampAndApplyScroll(rightColumnWidgets, rightScroll, false);
	}

	private void clampAndApplyScroll(List<ClickableWidget> widgets, float requestedScroll, boolean isLeft) {
		if (widgets.isEmpty()) return;

		int naturalBottom = viewportTop;
		for (ClickableWidget w : widgets) {
			naturalBottom = Math.max(naturalBottom, w.getY() + w.getHeight());
		}
		int contentHeight = naturalBottom - viewportTop;
		int available = viewportBottom - viewportTop;
		int maxScroll = Math.max(0, contentHeight - available);
		float clamped = MathHelper.clamp(requestedScroll, 0f, maxScroll);

		if (isLeft) leftScroll = clamped; else rightScroll = clamped;

		int shift = Math.round(clamped);
		for (ClickableWidget w : widgets) {
			w.setY(w.getY() - shift);
			boolean inView = w.getY() + w.getHeight() > viewportTop && w.getY() < viewportBottom;
			w.visible = inView;
			w.active = w.active && inView;
		}

		if (isLeft) {
			svY -= shift;
			hueY -= shift;
			primarySwatchY -= shift;
			secondarySwatchY -= shift;
		}
	}

	private void updateViewRegion() {
		regionWidth = Math.max(1, Math.min(CANVAS_WIDTH, Math.round(CANVAS_WIDTH / viewZoom)));
		regionHeight = Math.max(1, Math.min(CANVAS_HEIGHT, Math.round(CANVAS_HEIGHT / viewZoom)));
		regionU = MathHelper.clamp(viewCenterX - regionWidth / 2f, 0f, CANVAS_WIDTH - regionWidth);
		regionV = MathHelper.clamp(viewCenterY - regionHeight / 2f, 0f, CANVAS_HEIGHT - regionHeight);
	}

	private void zoomAtCursor(double factor, int mouseX, int mouseY) {
		if (!isInsideCanvas(mouseX, mouseY)) return;

		int pointX = toCanvasX(mouseX);
		int pointY = toCanvasY(mouseY);

		viewZoom = MathHelper.clamp((float) (viewZoom * factor), MIN_ZOOM, MAX_ZOOM);
		updateViewRegion();

		float scale = (CANVAS_WIDTH * pixelScale) / (float) regionWidth;
		float newU = pointX - (mouseX - canvasX) / scale;
		float newV = pointY - (mouseY - canvasY) / scale;
		viewCenterX = newU + regionWidth / 2f;
		viewCenterY = newV + regionHeight / 2f;
		updateViewRegion();
	}

	private <T extends ClickableWidget> T addLeftWidget(T widget) {
		addDrawableChild(widget);
		leftColumnWidgets.add(widget);
		return widget;
	}

	private <T extends ClickableWidget> T addRightWidget(T widget) {
		addDrawableChild(widget);
		rightColumnWidgets.add(widget);
		return widget;
	}

	private void initLeftColumn(int x, int startY) {
		int cursorY = startY;

		int toolBlockWidth = TOOL_COLS * SIDE_BUTTON_WIDTH + (TOOL_COLS - 1) * SIDE_BUTTON_GAP;
		int toolX = x + (LEFT_COLUMN_WIDTH - toolBlockWidth) / 2;
		cursorY = initToolbar(toolX, cursorY) + 10;

		int brushX = x + (LEFT_COLUMN_WIDTH - 150) / 2;
		addLeftWidget(new BrushSizeSlider(brushX, cursorY, 150, SIDE_BUTTON_HEIGHT, brushSize, value -> brushSize = value));
		cursorY += SIDE_BUTTON_HEIGHT + 14;

		cursorY = initColorControls(x, cursorY);
	}

	private int initToolbar(int x, int startY) {
		toolButtons = new ButtonWidget[5];
		toolButtonTools = new Tool[5];

		String[] leftLabels = {"Stift", "Radierer", "Eimer"};
		Tool[] leftTools = {Tool.PEN, Tool.ERASER, Tool.FILL};

		for (int i = 0; i < 3; i++) {
			int index = i;
			int by = startY + i * (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP);
			ButtonWidget button = ButtonWidget.builder(Text.literal(leftLabels[i]), b -> {
				currentTool = leftTools[index];
				clearArmed = false;
				resetClearButtonLabel();
				updateToolFocus();
			}).dimensions(x, by, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();
			addLeftWidget(button);
			toolButtons[i] = button;
			toolButtonTools[i] = leftTools[i];
		}

		int rightX = x + SIDE_BUTTON_WIDTH + SIDE_BUTTON_GAP;

		int cycleWidth = 20;
		int selectWidth = SIDE_BUTTON_WIDTH - cycleWidth - 2;

		shapeCycleButton = ButtonWidget.builder(Text.literal(""), b -> {
			cycleShape();
			updateToolFocus();
		}).dimensions(rightX, startY, cycleWidth, SIDE_BUTTON_HEIGHT).build();
		shapeSelectButton = ButtonWidget.builder(Text.literal(shapeLabel(selectedShape)), b -> {
			currentTool = selectedShape;
			clearArmed = false;
			resetClearButtonLabel();
			updateToolFocus();
		}).dimensions(rightX + cycleWidth + 2, startY, selectWidth, SIDE_BUTTON_HEIGHT).build();

		addLeftWidget(shapeCycleButton);
		addLeftWidget(shapeSelectButton);

		int selectY = startY + (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP);
		ButtonWidget selectButton = ButtonWidget.builder(Text.literal("Auswahl"), b -> {
			if (currentTool == Tool.SELECT) {
				clearSelection();
			}
			currentTool = Tool.SELECT;
			clearArmed = false;
			resetClearButtonLabel();
			updateToolFocus();
		}).dimensions(rightX, selectY, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();
		addLeftWidget(selectButton);
		toolButtons[3] = selectButton;
		toolButtonTools[3] = Tool.SELECT;

		int eyedropperY = startY + 2 * (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP);
		ButtonWidget eyedropperButton = ButtonWidget.builder(Text.literal("Pipette"), b -> {
			currentTool = Tool.EYEDROPPER;
			clearArmed = false;
			resetClearButtonLabel();
			updateToolFocus();
		}).dimensions(rightX, eyedropperY, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();
		addLeftWidget(eyedropperButton);
		toolButtons[4] = eyedropperButton;
		toolButtonTools[4] = Tool.EYEDROPPER;

		int toolStackHeight = TOOL_ROWS * (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP) - SIDE_BUTTON_GAP;
		int result = startY + toolStackHeight;
		updateToolFocus();
		return result;
	}

	private void updateToolFocus() {
		for (int i = 0; i < toolButtons.length; i++) {
			toolButtons[i].setFocused(toolButtonTools[i] == currentTool);
		}
		boolean shapeActive = currentTool == Tool.LINE || currentTool == Tool.RECTANGLE || currentTool == Tool.CIRCLE;
		shapeSelectButton.setFocused(shapeActive);
		shapeCycleButton.setFocused(false);
	}

	private void cycleShape() {
		Tool[] order = {Tool.LINE, Tool.RECTANGLE, Tool.CIRCLE};
		int idx = 0;
		for (int i = 0; i < order.length; i++) {
			if (order[i] == selectedShape) idx = i;
		}
		selectedShape = order[(idx + 1) % order.length];
		shapeSelectButton.setMessage(Text.literal(shapeLabel(selectedShape)));
		currentTool = selectedShape;
		clearArmed = false;
		resetClearButtonLabel();
	}

	private String shapeLabel(Tool t) {
		return switch (t) {
			case RECTANGLE -> "Rechteck";
			case CIRCLE -> "Kreis";
			default -> "Linie";
		};
	}

	private void initSideActions(int x, int startY) {
		int buttonX = x + (LEFT_COLUMN_WIDTH - SIDE_BUTTON_WIDTH) / 2;

		clearButton = ButtonWidget.builder(Text.literal("Ebene leeren"), b -> {
			if (!clearArmed) {
				clearArmed = true;
				clearButton.setMessage(Text.literal("Sicher? Klick!"));
			} else {
				clearCanvas();
				clearArmed = false;
				resetClearButtonLabel();
			}
		}).dimensions(buttonX, startY, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();

		resetButton = ButtonWidget.builder(Text.literal("Canvas leeren"), b -> {
			if (!resetArmed) {
				resetArmed = true;
				resetButton.setMessage(Text.literal("Sicher? Klick!"));
			} else {
				resetProject();
			}
		}).dimensions(buttonX, startY + SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();

		saveButton = ButtonWidget.builder(Text.literal("Speichern"), b -> {
			saveToDisk();
			saveButton.setMessage(Text.literal("Gespeichert!"));
			saveFeedbackUntil = System.currentTimeMillis() + 1500;
			clearArmed = false;
			resetClearButtonLabel();
		}).dimensions(buttonX, startY + (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP) * 2, SIDE_BUTTON_WIDTH, SIDE_BUTTON_HEIGHT).build();

		addRightWidget(clearButton);
		addRightWidget(resetButton);
		addRightWidget(saveButton);

		int layerPanelY = startY + (SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP) * 3 + 6;
		initLayerPanel(x, layerPanelY);
	}

	private void initLayerPanel(int x, int startY) {
		int cursorY = startY;

		ButtonWidget addButton = ButtonWidget.builder(Text.literal("+ Ebene"), b -> addLayer())
				.dimensions(x, cursorY, LEFT_COLUMN_WIDTH, SIDE_BUTTON_HEIGHT).build();
		addButton.active = MinimapData.INSTANCE.layers.size() < MinimapData.MAX_LAYERS;
		addRightWidget(addButton);
		cursorY += SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP;

		ButtonWidget deleteButton = ButtonWidget.builder(Text.literal("Ebene loeschen"), b -> deleteActiveLayer())
				.dimensions(x, cursorY, LEFT_COLUMN_WIDTH, SIDE_BUTTON_HEIGHT).build();
		deleteButton.active = MinimapData.INSTANCE.layers.size() > 1;
		addRightWidget(deleteButton);
		cursorY += SIDE_BUTTON_HEIGHT + 8;

		int toggleWidth = 34;
		int nameWidth = LEFT_COLUMN_WIDTH - toggleWidth - 4;

		List<MinimapData.Layer> layers = MinimapData.INSTANCE.layers;
		for (int i = layers.size() - 1; i >= 0; i--) {
			int index = i;
			MinimapData.Layer layer = layers.get(i);

			ButtonWidget toggle = ButtonWidget.builder(Text.literal(layer.visible ? "An" : "Aus"), b -> toggleLayerVisibility(index))
					.dimensions(x, cursorY, toggleWidth, SIDE_BUTTON_HEIGHT).build();
			addRightWidget(toggle);

			String label = (index == MinimapData.INSTANCE.activeLayerIndex ? "> " : "") + layer.name;
			ButtonWidget nameButton = ButtonWidget.builder(Text.literal(label), b -> selectLayer(index))
					.dimensions(x + toggleWidth + 4, cursorY, nameWidth, SIDE_BUTTON_HEIGHT).build();
			addRightWidget(nameButton);

			cursorY += SIDE_BUTTON_HEIGHT + SIDE_BUTTON_GAP;
		}
	}

	private void resetProject() {
		MinimapData.INSTANCE.reset();
		undoStack.clear();
		redoStack.clear();
		hasSelection = false;
		hasClipboard = false;
		resetArmed = false;
		clearChildren();
		init();
	}

	private void addLayer() {
		if (MinimapData.INSTANCE.layers.size() >= MinimapData.MAX_LAYERS) return;
		MinimapData.INSTANCE.layers.add(new MinimapData.Layer(new MinimapCanvas(CANVAS_WIDTH, CANVAS_HEIGHT), ""));
		MinimapData.INSTANCE.renumberLayers();
		MinimapData.INSTANCE.activeLayerIndex = MinimapData.INSTANCE.layers.size() - 1;
		resetArmed = false;
		clearChildren();
		init();
	}

	private void deleteActiveLayer() {
		if (MinimapData.INSTANCE.layers.size() <= 1) return;
		MinimapData.INSTANCE.layers.remove(MinimapData.INSTANCE.activeLayerIndex);
		MinimapData.INSTANCE.renumberLayers();
		MinimapData.INSTANCE.activeLayerIndex = Math.max(0, MinimapData.INSTANCE.activeLayerIndex - 1);
		resetArmed = false;
		clearChildren();
		init();
	}

	private void toggleLayerVisibility(int index) {
		MinimapData.INSTANCE.layers.get(index).visible = !MinimapData.INSTANCE.layers.get(index).visible;
		resetArmed = false;
		clearChildren();
		init();
	}

	private void selectLayer(int index) {
		MinimapData.INSTANCE.activeLayerIndex = index;
		resetArmed = false;
		clearChildren();
		init();
	}

	private int initColorControls(int x, int startY) {
		int cursorY = startY;
		int centerX = x + LEFT_COLUMN_WIDTH / 2;

		int swatchSize = 20;
		int halfButtonWidth = (LEFT_COLUMN_WIDTH - 4) / 2;
		int primaryBtnX = x;
		int secondaryBtnX = x + halfButtonWidth + 4;

		primarySwatchX = primaryBtnX + (halfButtonWidth - swatchSize) / 2;
		primarySwatchY = cursorY;
		secondarySwatchX = secondaryBtnX + (halfButtonWidth - swatchSize) / 2;
		secondarySwatchY = cursorY;

		cursorY += swatchSize + 4;

		primaryButton = ButtonWidget.builder(Text.literal("Primaer"), b -> {
			activeSlot = ColorSlot.PRIMARY;
			syncPickerToActiveSlot();
			updateSlotButtonLabels();
			clearArmed = false;
			resetClearButtonLabel();
		}).dimensions(primaryBtnX, cursorY, halfButtonWidth, SIDE_BUTTON_HEIGHT).build();

		secondaryButton = ButtonWidget.builder(Text.literal("Sekundaer"), b -> {
			activeSlot = ColorSlot.SECONDARY;
			syncPickerToActiveSlot();
			updateSlotButtonLabels();
			clearArmed = false;
			resetClearButtonLabel();
		}).dimensions(secondaryBtnX, cursorY, halfButtonWidth, SIDE_BUTTON_HEIGHT).build();

		addLeftWidget(primaryButton);
		addLeftWidget(secondaryButton);

		cursorY += SIDE_BUTTON_HEIGHT + 6;

		svX = centerX - SV_SIZE / 2;
		svY = cursorY;
		cursorY += SV_SIZE + 4;

		hueX = centerX - HUE_STRIP_SIZE / 2;
		hueY = cursorY;
		cursorY += HUE_STRIP_THICKNESS + 8;

		int sliderWidth = 150;
		int sliderX = centerX - sliderWidth / 2;

		redSlider = new ColorSlider(sliderX, cursorY, sliderWidth, 18, "R", getActiveR(), value -> {
			setActiveR(value);
			applyRgbSliderColor();
		});
		cursorY += 20;
		greenSlider = new ColorSlider(sliderX, cursorY, sliderWidth, 18, "G", getActiveG(), value -> {
			setActiveG(value);
			applyRgbSliderColor();
		});
		cursorY += 20;
		blueSlider = new ColorSlider(sliderX, cursorY, sliderWidth, 18, "B", getActiveB(), value -> {
			setActiveB(value);
			applyRgbSliderColor();
		});
		cursorY += 20;
		alphaSlider = new ColorSlider(sliderX, cursorY, sliderWidth, 18, "A", getActiveA(), value -> {
			setActiveA(value);
			updateColors();
		});
		cursorY += 18 + 8;

		addLeftWidget(redSlider);
		addLeftWidget(greenSlider);
		addLeftWidget(blueSlider);
		addLeftWidget(alphaSlider);

		hexField = new TextFieldWidget(textRenderer, centerX - 50, cursorY, 100, 20, Text.literal("Hex"));
		hexField.setMaxLength(9);
		hexField.setChangedListener(text -> applyHexColor());
		addLeftWidget(hexField);
		cursorY += 20;

		syncPickerToActiveSlot();
		updateSlotButtonLabels();

		return cursorY;
	}

	private void resetClearButtonLabel() {
		if (clearButton != null) clearButton.setMessage(Text.literal("Leeren"));
	}

	private int getActiveR() {
		return activeSlot == ColorSlot.PRIMARY ? primaryR : secondaryR;
	}

	private int getActiveG() {
		return activeSlot == ColorSlot.PRIMARY ? primaryG : secondaryG;
	}

	private int getActiveB() {
		return activeSlot == ColorSlot.PRIMARY ? primaryB : secondaryB;
	}

	private int getActiveA() {
		return activeSlot == ColorSlot.PRIMARY ? primaryA : secondaryA;
	}

	private void setActiveR(int v) {
		if (activeSlot == ColorSlot.PRIMARY) primaryR = v; else secondaryR = v;
	}

	private void setActiveG(int v) {
		if (activeSlot == ColorSlot.PRIMARY) primaryG = v; else secondaryG = v;
	}

	private void setActiveB(int v) {
		if (activeSlot == ColorSlot.PRIMARY) primaryB = v; else secondaryB = v;
	}

	private void setActiveA(int v) {
		if (activeSlot == ColorSlot.PRIMARY) primaryA = v; else secondaryA = v;
	}

	private void syncPickerToActiveSlot() {
		redSlider.setValueSilently(getActiveR() / 255.0);
		greenSlider.setValueSilently(getActiveG() / 255.0);
		blueSlider.setValueSilently(getActiveB() / 255.0);
		alphaSlider.setValueSilently(getActiveA() / 255.0);

		float[] hsv = hsvFromRgb(getActiveR(), getActiveG(), getActiveB());
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		regenerateSvTexture();
		setHexFieldText();
	}

	private void applyRgbSliderColor() {
		updateColors();
		float[] hsv = hsvFromRgb(getActiveR(), getActiveG(), getActiveB());
		pickerHue = hsv[0];
		pickerSat = hsv[1];
		pickerVal = hsv[2];
		regenerateSvTexture();
		setHexFieldText();
	}

	private void applyPickerColor() {
		int[] rgb = rgbFromHsv(pickerHue, pickerSat, pickerVal);
		setActiveR(rgb[0]);
		setActiveG(rgb[1]);
		setActiveB(rgb[2]);
		updateColors();
		redSlider.setValueSilently(getActiveR() / 255.0);
		greenSlider.setValueSilently(getActiveG() / 255.0);
		blueSlider.setValueSilently(getActiveB() / 255.0);
		setHexFieldText();
	}

	private void applyHexColor() {
		String text = hexField.getText().replace("#", "").trim();
		if (text.length() != 6) return;

		try {
			int value = Integer.parseInt(text, 16);
			int r = (value >> 16) & 0xFF;
			int g = (value >> 8) & 0xFF;
			int b = value & 0xFF;

			setActiveR(r);
			setActiveG(g);
			setActiveB(b);
			updateColors();

			redSlider.setValueSilently(r / 255.0);
			greenSlider.setValueSilently(g / 255.0);
			blueSlider.setValueSilently(b / 255.0);

			float[] hsv = hsvFromRgb(r, g, b);
			pickerHue = hsv[0];
			pickerSat = hsv[1];
			pickerVal = hsv[2];
			regenerateSvTexture();
		} catch (NumberFormatException ignored) {
		}
	}

	private void setHexFieldText() {
		String hex = String.format("%02X%02X%02X", getActiveR(), getActiveG(), getActiveB());
		hexField.setText(hex);
	}

	private void updateSlotButtonLabels() {
		primaryButton.setMessage(Text.literal(activeSlot == ColorSlot.PRIMARY ? "Primaer *" : "Primaer"));
		secondaryButton.setMessage(Text.literal(activeSlot == ColorSlot.SECONDARY ? "Sekund. *" : "Sekundaer"));
	}

	private void updateColors() {
		primaryColor = (primaryA << 24) | (primaryR << 16) | (primaryG << 8) | primaryB;
		secondaryColor = (secondaryA << 24) | (secondaryR << 16) | (secondaryG << 8) | secondaryB;
	}

	private static int[] rgbFromHsv(float h, float s, float v) {
		float c = v * s;
		float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
		float m = v - c;
		float r;
		float g;
		float b;

		if (h < 60) { r = c; g = x; b = 0; }
		else if (h < 120) { r = x; g = c; b = 0; }
		else if (h < 180) { r = 0; g = c; b = x; }
		else if (h < 240) { r = 0; g = x; b = c; }
		else if (h < 300) { r = x; g = 0; b = c; }
		else { r = c; g = 0; b = x; }

		int ri = Math.round((r + m) * 255);
		int gi = Math.round((g + m) * 255);
		int bi = Math.round((b + m) * 255);
		return new int[]{MathHelper.clamp(ri, 0, 255), MathHelper.clamp(gi, 0, 255), MathHelper.clamp(bi, 0, 255)};
	}

	private static float[] hsvFromRgb(int r, int g, int b) {
		float rf = r / 255f;
		float gf = g / 255f;
		float bf = b / 255f;
		float max = Math.max(rf, Math.max(gf, bf));
		float min = Math.min(rf, Math.min(gf, bf));
		float delta = max - min;

		float h;
		if (delta == 0) h = 0;
		else if (max == rf) h = 60 * (((gf - bf) / delta) % 6);
		else if (max == gf) h = 60 * (((bf - rf) / delta) + 2);
		else h = 60 * (((rf - gf) / delta) + 4);
		if (h < 0) h += 360;

		float s = max == 0 ? 0 : delta / max;
		float v = max;
		return new float[]{h, s, v};
	}

	private void regenerateSvTexture() {
		for (int y = 0; y < SV_SIZE; y++) {
			float v = 1f - (y / (float) (SV_SIZE - 1));
			for (int x = 0; x < SV_SIZE; x++) {
				float s = x / (float) (SV_SIZE - 1);
				int[] rgb = rgbFromHsv(pickerHue, s, v);
				int argb = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
				svImage.setColor(x, y, toNativeImageColor(argb));
			}
		}
		svTexture.upload();
	}

	private void generateHueStripTexture() {
		for (int x = 0; x < HUE_STRIP_SIZE; x++) {
			float hue = (x / (float) (HUE_STRIP_SIZE - 1)) * 360f;
			int[] rgb = rgbFromHsv(hue, 1f, 1f);
			int argb = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
			int nativeColor = toNativeImageColor(argb);
			for (int y = 0; y < HUE_STRIP_THICKNESS; y++) {
				hueImage.setColor(x, y, nativeColor);
			}
		}
		hueTexture.upload();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		updateViewRegion();

		int canvasEndX = canvasX + CANVAS_WIDTH * pixelScale;
		int canvasEndY = canvasY + CANVAS_HEIGHT * pixelScale;

		context.fill(canvasX - 2, canvasY - 2, canvasEndX + 2, canvasEndY + 2, 0xFF808080);
		context.fill(canvasX, canvasY, canvasEndX, canvasEndY, 0xFFFFFFFF);

		syncCanvasToTexture();
		context.drawTexture(RenderPipelines.GUI_TEXTURED, canvasTextureId,
				canvasX, canvasY, regionU, regionV,
				CANVAS_WIDTH * pixelScale, CANVAS_HEIGHT * pixelScale,
				regionWidth, regionHeight,
				CANVAS_WIDTH, CANVAS_HEIGHT);

		drawToolHighlight(context);
		drawColorSwatches(context);
		drawColorPicker(context);
		drawSelectionOverlay(context, mouseX, mouseY);
		drawShapePreview(context, mouseX, mouseY);
		drawPastePreview(context, mouseX, mouseY);

		if (saveButton != null && saveFeedbackUntil != 0 && System.currentTimeMillis() > saveFeedbackUntil) {
			saveButton.setMessage(Text.literal("Speichern"));
			saveFeedbackUntil = 0;
		}

		handleInput(mouseX, mouseY);
	}

	private void drawColorPicker(DrawContext context) {
		if (!isColorPickerInView()) return;

		context.fill(svX - 1, svY - 1, svX + SV_SIZE + 1, svY + SV_SIZE + 1, 0xFF808080);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, svTextureId,
				svX, svY, 0f, 0f, SV_SIZE, SV_SIZE, SV_SIZE, SV_SIZE, SV_SIZE, SV_SIZE);

		context.fill(hueX - 1, hueY - 1, hueX + HUE_STRIP_SIZE + 1, hueY + HUE_STRIP_THICKNESS + 1, 0xFF808080);
		context.drawTexture(RenderPipelines.GUI_TEXTURED, hueTextureId,
				hueX, hueY, 0f, 0f, HUE_STRIP_SIZE, HUE_STRIP_THICKNESS, HUE_STRIP_SIZE, HUE_STRIP_THICKNESS, HUE_STRIP_SIZE, HUE_STRIP_THICKNESS);

		int svCursorX = svX + Math.round(pickerSat * (SV_SIZE - 1));
		int svCursorY = svY + Math.round((1 - pickerVal) * (SV_SIZE - 1));
		context.fill(svCursorX - 3, svCursorY - 3, svCursorX + 4, svCursorY + 4, 0xFF000000);
		context.fill(svCursorX - 2, svCursorY - 2, svCursorX + 3, svCursorY + 3, 0xFFFFFFFF);

		int hueCursorX = hueX + Math.round((pickerHue / 360f) * (HUE_STRIP_SIZE - 1));
		context.fill(hueCursorX - 1, hueY - 2, hueCursorX, hueY + HUE_STRIP_THICKNESS + 2, 0xFF000000);
		context.fill(hueCursorX, hueY - 2, hueCursorX + 1, hueY + HUE_STRIP_THICKNESS + 2, 0xFFFFFFFF);
	}

	private boolean isColorPickerInView() {
		int top = Math.min(svY, hueY);
		int bottom = Math.max(svY + SV_SIZE, hueY + HUE_STRIP_THICKNESS);
		return bottom > viewportTop && top < viewportBottom;
	}

	private boolean isInsideSvSquare(double mouseX, double mouseY) {
		if (!isColorPickerInView()) return false;
		return mouseX >= svX && mouseX < svX + SV_SIZE && mouseY >= svY && mouseY < svY + SV_SIZE;
	}

	private boolean isInsideHueStrip(double mouseX, double mouseY) {
		if (!isColorPickerInView()) return false;
		int pad = 5;
		return mouseX >= hueX - pad && mouseX < hueX + HUE_STRIP_SIZE + pad
				&& mouseY >= hueY - pad && mouseY < hueY + HUE_STRIP_THICKNESS + pad;
	}

	private void updateSvFromMouse(double mouseX, double mouseY) {
		pickerSat = MathHelper.clamp((float) (mouseX - svX) / (SV_SIZE - 1), 0f, 1f);
		pickerVal = 1f - MathHelper.clamp((float) (mouseY - svY) / (SV_SIZE - 1), 0f, 1f);
		applyPickerColor();
	}

	private void updateHueFromMouse(double mouseX) {
		pickerHue = MathHelper.clamp((float) (mouseX - hueX) / (HUE_STRIP_SIZE - 1), 0f, 1f) * 360f;
		if (pickerSat < 0.05f) pickerSat = 1f;
		if (pickerVal < 0.05f) pickerVal = 1f;
		regenerateSvTexture();
		applyPickerColor();
	}

	private MinimapCanvas activeCanvas() {
		return MinimapData.INSTANCE.activeCanvas();
	}

	private int compositePixel(int x, int y) {
		return MinimapData.INSTANCE.compositePixel(x, y);
	}

	private void syncCanvasToTexture() {
		for (int y = 0; y < CANVAS_HEIGHT; y++) {
			for (int x = 0; x < CANVAS_WIDTH; x++) {
				canvasImage.setColor(x, y, toNativeImageColor(compositePixel(x, y)));
			}
		}
		canvasTexture.upload();
	}

	private static int toNativeImageColor(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (argb >>> 16) & 0xFF;
		int g = (argb >>> 8) & 0xFF;
		int b = argb & 0xFF;
		return (a << 24) | (b << 16) | (g << 8) | r;
	}

	private void drawToolHighlight(DrawContext context) {
		drawShapeIcon(context);
	}

	private void drawShapeIcon(DrawContext context) {
		int x = shapeCycleButton.getX();
		int y = shapeCycleButton.getY();
		int w = shapeCycleButton.getWidth();
		int h = shapeCycleButton.getHeight();
		int cx = x + w / 2;
		int cy = y + h / 2;
		int color = 0xFFFFFFFF;

		switch (selectedShape) {
			case RECTANGLE -> {
				int left = x + 4;
				int right = x + w - 4;
				int top = y + 5;
				int bottom = y + h - 5;
				context.fill(left, top, right, top + 1, color);
				context.fill(left, bottom - 1, right, bottom, color);
				context.fill(left, top, left + 1, bottom, color);
				context.fill(right - 1, top, right, bottom, color);
			}
			case CIRCLE -> {
				int radius = Math.min(w, h) / 2 - 4;
				for (int angle = 0; angle < 360; angle += 6) {
					int px = cx + Math.round(radius * (float) Math.cos(Math.toRadians(angle)));
					int py = cy + Math.round(radius * (float) Math.sin(Math.toRadians(angle)));
					context.fill(px, py, px + 1, py + 1, color);
				}
			}
			default -> context.fill(x + 4, y + h - 6, x + w - 4, y + h - 5, color);
		}
	}

	private void drawColorSwatches(DrawContext context) {
		if (primarySwatchY + 20 <= viewportTop || primarySwatchY >= viewportBottom) return;

		context.fill(primarySwatchX - 1, primarySwatchY - 1, primarySwatchX + 21, primarySwatchY + 21, 0xFF808080);
		context.fill(primarySwatchX, primarySwatchY, primarySwatchX + 20, primarySwatchY + 20, primaryColor | 0xFF000000);

		context.fill(secondarySwatchX - 1, secondarySwatchY - 1, secondarySwatchX + 21, secondarySwatchY + 21, 0xFF808080);
		context.fill(secondarySwatchX, secondarySwatchY, secondarySwatchX + 20, secondarySwatchY + 20, secondaryColor | 0xFF000000);
	}

	private boolean isPixelEditable(int x, int y) {
		if (!hasSelection) return true;
		return x >= selectionMinX && x <= selectionMaxX && y >= selectionMinY && y <= selectionMaxY;
	}

	private void drawSelectionOverlay(DrawContext context, int mouseX, int mouseY) {
		int minX;
		int minY;
		int maxX;
		int maxY;

		if (currentTool == Tool.SELECT && activeButton != ActiveButton.NONE) {
			int cx = clampCanvasX(toCanvasX(mouseX));
			int cy = clampCanvasY(toCanvasY(mouseY));
			minX = Math.min(shapeStartCanvasX, cx);
			maxX = Math.max(shapeStartCanvasX, cx);
			minY = Math.min(shapeStartCanvasY, cy);
			maxY = Math.max(shapeStartCanvasY, cy);
		} else if (hasSelection) {
			minX = selectionMinX;
			maxX = selectionMaxX;
			minY = selectionMinY;
			maxY = selectionMaxY;
		} else {
			return;
		}

		int sx1 = toScreenX(minX);
		int sy1 = toScreenY(minY);
		int sx2 = toScreenX(maxX + 1);
		int sy2 = toScreenY(maxY + 1);
		int color = 0xFF55CCFF;

		context.fill(sx1, sy1, sx2, sy1 + 1, color);
		context.fill(sx1, sy2 - 1, sx2, sy2, color);
		context.fill(sx1, sy1, sx1 + 1, sy2, color);
		context.fill(sx2 - 1, sy1, sx2, sy2, color);
	}

	private void drawShapePreview(DrawContext context, int mouseX, int mouseY) {
		if (activeButton == ActiveButton.NONE) return;
		if (currentTool != Tool.LINE && currentTool != Tool.RECTANGLE && currentTool != Tool.CIRCLE) return;

		int cx = toCanvasX(mouseX);
		int cy = toCanvasY(mouseY);
		int previewColor = strokeColor() | 0xFF000000;
		int half = brushSize / 2;

		PixelConsumer consumer = (x, y) -> {
			int minX = Math.max(0, x - half);
			int minY = Math.max(0, y - half);
			int maxX = Math.min(CANVAS_WIDTH - 1, x - half + brushSize - 1);
			int maxY = Math.min(CANVAS_HEIGHT - 1, y - half + brushSize - 1);
			if (minX > maxX || minY > maxY) return;

			int sx1 = toScreenX(minX);
			int sy1 = toScreenY(minY);
			int sx2 = toScreenX(maxX + 1);
			int sy2 = toScreenY(maxY + 1);
			context.fill(sx1, sy1, sx2, sy2, previewColor);
		};

		switch (currentTool) {
			case LINE -> traceLine(shapeStartCanvasX, shapeStartCanvasY, cx, cy, consumer);
			case RECTANGLE -> traceRectangle(shapeStartCanvasX, shapeStartCanvasY, cx, cy, consumer);
			case CIRCLE -> {
				int radius = (int) Math.round(Math.hypot(cx - shapeStartCanvasX, cy - shapeStartCanvasY));
				traceCircle(shapeStartCanvasX, shapeStartCanvasY, radius, consumer);
			}
			default -> {}
		}
	}

	private void drawPastePreview(DrawContext context, int mouseX, int mouseY) {
		if (!pendingPaste || !hasClipboard) return;

		int anchorX = toCanvasX(mouseX) - clipboardWidth / 2;
		int anchorY = toCanvasY(mouseY) - clipboardHeight / 2;

		for (int y = 0; y < clipboardHeight; y++) {
			for (int x = 0; x < clipboardWidth; x++) {
				int color = clipboardPixels[y * clipboardWidth + x];
				if ((color >>> 24) == 0) continue;

				int px = anchorX + x;
				int py = anchorY + y;
				if (px < 0 || py < 0 || px >= CANVAS_WIDTH || py >= CANVAS_HEIGHT) continue;

				int sx = toScreenX(px);
				int sy = toScreenY(py);
				int previewColor = 0x80000000 | (color & 0x00FFFFFF);
				context.fill(sx, sy, toScreenX(px + 1), toScreenY(py + 1), previewColor);
			}
		}
	}

	private void handleInput(int mouseX, int mouseY) {
		long handle = MinecraftClient.getInstance().getWindow().getHandle();
		boolean leftDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean rightDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

		if (leftDown && isInsideSvSquare(mouseX, mouseY)) {
			updateSvFromMouse(mouseX, mouseY);
			return;
		}
		if (leftDown && isInsideHueStrip(mouseX, mouseY)) {
			updateHueFromMouse(mouseX);
			return;
		}

		if (suppressUntilRelease) {
			if (!leftDown && !rightDown) suppressUntilRelease = false;
			return;
		}

		if (pendingPaste) {
			if (isInsideCanvas(mouseX, mouseY) && leftDown) {
				commitPaste(toCanvasX(mouseX), toCanvasY(mouseY));
				pendingPaste = false;
				suppressUntilRelease = true;
			} else if (rightDown) {
				pendingPaste = false;
				suppressUntilRelease = true;
			}
			return;
		}

		if (activeButton == ActiveButton.NONE) {
			if (isInsideCanvas(mouseX, mouseY) && (leftDown || rightDown)) {
				activeButton = leftDown ? ActiveButton.LEFT : ActiveButton.RIGHT;
				onStrokeStart(mouseX, mouseY);
			}
			return;
		}

		boolean stillDown = activeButton == ActiveButton.LEFT ? leftDown : rightDown;
		if (stillDown) {
			onStrokeDrag(mouseX, mouseY);
		} else {
			onStrokeEnd(mouseX, mouseY);
			activeButton = ActiveButton.NONE;
		}
	}

	private void clearSelection() {
		hasSelection = false;
	}

	private int strokeColor() {
		return activeButton == ActiveButton.LEFT ? primaryColor : secondaryColor;
	}

	private int clampCanvasX(int x) {
		return MathHelper.clamp(x, 0, CANVAS_WIDTH - 1);
	}

	private int clampCanvasY(int y) {
		return MathHelper.clamp(y, 0, CANVAS_HEIGHT - 1);
	}

	private void onStrokeStart(int mouseX, int mouseY) {
		int cx = toCanvasX(mouseX);
		int cy = toCanvasY(mouseY);
		clearArmed = false;
		resetClearButtonLabel();

		if (currentTool != Tool.EYEDROPPER && currentTool != Tool.SELECT) {
			pushUndo();
		}

		switch (currentTool) {
			case PEN -> {
				strokeLastCanvasX = cx;
				strokeLastCanvasY = cy;
				paintBrush(cx, cy, strokeColor());
			}
			case ERASER -> {
				strokeLastCanvasX = cx;
				strokeLastCanvasY = cy;
				paintBrush(cx, cy, 0x00000000);
			}
			case FILL -> floodFill(cx, cy, strokeColor());
			case EYEDROPPER -> pickColorFromCanvas(cx, cy);
			case LINE, RECTANGLE, CIRCLE, SELECT -> {
				shapeStartCanvasX = clampCanvasX(cx);
				shapeStartCanvasY = clampCanvasY(cy);
			}
		}
	}

	private void pickColorFromCanvas(int cx, int cy) {
		int sampled = compositePixel(cx, cy);
		int a = (sampled >>> 24) & 0xFF;
		int r = (sampled >>> 16) & 0xFF;
		int g = (sampled >>> 8) & 0xFF;
		int b = sampled & 0xFF;

		if (activeButton == ActiveButton.LEFT) {
			primaryR = r; primaryG = g; primaryB = b; primaryA = a;
			if (activeSlot == ColorSlot.PRIMARY) syncPickerToActiveSlot(); else updateColors();
		} else {
			secondaryR = r; secondaryG = g; secondaryB = b; secondaryA = a;
			if (activeSlot == ColorSlot.SECONDARY) syncPickerToActiveSlot(); else updateColors();
		}
	}

	private void onStrokeDrag(int mouseX, int mouseY) {
		if (currentTool != Tool.PEN && currentTool != Tool.ERASER) return;

		int cx = toCanvasX(mouseX);
		int cy = toCanvasY(mouseY);
		int color = currentTool == Tool.ERASER ? 0x00000000 : strokeColor();

		traceLine(strokeLastCanvasX, strokeLastCanvasY, cx, cy, (x, y) -> paintBrush(x, y, color));
		strokeLastCanvasX = cx;
		strokeLastCanvasY = cy;
	}

	private void onStrokeEnd(int mouseX, int mouseY) {
		if (currentTool != Tool.LINE && currentTool != Tool.RECTANGLE
				&& currentTool != Tool.CIRCLE && currentTool != Tool.SELECT) return;

		int cx = toCanvasX(mouseX);
		int cy = toCanvasY(mouseY);
		int color = strokeColor();

		switch (currentTool) {
			case LINE -> traceLine(shapeStartCanvasX, shapeStartCanvasY, cx, cy, (x, y) -> paintBrush(x, y, color));
			case RECTANGLE -> traceRectangle(shapeStartCanvasX, shapeStartCanvasY, cx, cy, (x, y) -> paintBrush(x, y, color));
			case CIRCLE -> {
				int radius = (int) Math.round(Math.hypot(cx - shapeStartCanvasX, cy - shapeStartCanvasY));
				traceCircle(shapeStartCanvasX, shapeStartCanvasY, radius, (x, y) -> paintBrush(x, y, color));
			}
			case SELECT -> {
				int ccx = clampCanvasX(cx);
				int ccy = clampCanvasY(cy);
				if (ccx == shapeStartCanvasX && ccy == shapeStartCanvasY) {
					clearSelection();
				} else {
					selectionMinX = Math.min(shapeStartCanvasX, ccx);
					selectionMaxX = Math.max(shapeStartCanvasX, ccx);
					selectionMinY = Math.min(shapeStartCanvasY, ccy);
					selectionMaxY = Math.max(shapeStartCanvasY, ccy);
					hasSelection = true;
				}
			}
			default -> {}
		}
	}

	private void copySelection() {
		if (!hasSelection) return;

		int w = selectionMaxX - selectionMinX + 1;
		int h = selectionMaxY - selectionMinY + 1;

		clipboardWidth = w;
		clipboardHeight = h;
		clipboardPixels = new int[w * h];

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				clipboardPixels[y * w + x] = activeCanvas().getPixel(selectionMinX + x, selectionMinY + y);
			}
		}

		hasClipboard = true;
	}

	private void commitPaste(int centerX, int centerY) {
		if (!hasClipboard) return;

		pushUndo();

		int atX = centerX - clipboardWidth / 2;
		int atY = centerY - clipboardHeight / 2;

		for (int y = 0; y < clipboardHeight; y++) {
			for (int x = 0; x < clipboardWidth; x++) {
				activeCanvas().setPixel(atX + x, atY + y, clipboardPixels[y * clipboardWidth + x]);
			}
		}
	}

	private void clearCanvas() {
		pushUndo();
		for (int y = 0; y < CANVAS_HEIGHT; y++) {
			for (int x = 0; x < CANVAS_WIDTH; x++) {
				activeCanvas().setPixel(x, y, 0x00000000);
			}
		}
	}

	private void clearSelectionArea() {
		pushUndo();
		for (int y = selectionMinY; y <= selectionMaxY; y++) {
			for (int x = selectionMinX; x <= selectionMaxX; x++) {
				activeCanvas().setPixel(x, y, 0x00000000);
			}
		}
	}

	private static class UndoEntry {
		final MinimapData.Layer layer;
		final int[] pixels;

		UndoEntry(MinimapData.Layer layer, int[] pixels) {
			this.layer = layer;
			this.pixels = pixels;
		}
	}

	private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
	private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

	private void pushUndo() {
		MinimapData.Layer active = MinimapData.INSTANCE.activeLayer();
		undoStack.push(new UndoEntry(active, active.canvas.copyPixels()));
		if (undoStack.size() > MAX_UNDO) undoStack.removeLast();
		redoStack.clear();
	}

	private void undo() {
		if (undoStack.isEmpty()) return;
		UndoEntry entry = undoStack.pop();
		redoStack.push(new UndoEntry(entry.layer, entry.layer.canvas.copyPixels()));
		entry.layer.canvas.restorePixels(entry.pixels);
	}

	private void redo() {
		if (redoStack.isEmpty()) return;
		UndoEntry entry = redoStack.pop();
		undoStack.push(new UndoEntry(entry.layer, entry.layer.canvas.copyPixels()));
		entry.layer.canvas.restorePixels(entry.pixels);
	}

	private void paintBrush(int cx, int cy, int color) {
		int half = brushSize / 2;
		for (int dy = 0; dy < brushSize; dy++) {
			for (int dx = 0; dx < brushSize; dx++) {
				int px = cx - half + dx;
				int py = cy - half + dy;
				if (!isPixelEditable(px, py)) continue;
				activeCanvas().setPixel(px, py, color);
			}
		}
	}

	private void floodFill(int startX, int startY, int newColor) {
		if (startX < 0 || startY < 0 || startX >= CANVAS_WIDTH || startY >= CANVAS_HEIGHT) return;
		if (!isPixelEditable(startX, startY)) return;

		int targetColor = activeCanvas().getPixel(startX, startY);
		if (targetColor == newColor) return;

		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[]{startX, startY});

		while (!stack.isEmpty()) {
			int[] p = stack.pop();
			int x = p[0];
			int y = p[1];

			if (x < 0 || y < 0 || x >= CANVAS_WIDTH || y >= CANVAS_HEIGHT) continue;
			if (!isPixelEditable(x, y)) continue;
			if (activeCanvas().getPixel(x, y) != targetColor) continue;

			activeCanvas().setPixel(x, y, newColor);

			stack.push(new int[]{x + 1, y});
			stack.push(new int[]{x - 1, y});
			stack.push(new int[]{x, y + 1});
			stack.push(new int[]{x, y - 1});
		}
	}

	private void traceLine(int x1, int y1, int x2, int y2, PixelConsumer consumer) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps == 0) {
			consumer.accept(x1, y1);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			int px = x1 + (x2 - x1) * i / steps;
			int py = y1 + (y2 - y1) * i / steps;
			consumer.accept(px, py);
		}
	}

	private void traceRectangle(int x1, int y1, int x2, int y2, PixelConsumer consumer) {
		int minX = Math.min(x1, x2);
		int maxX = Math.max(x1, x2);
		int minY = Math.min(y1, y2);
		int maxY = Math.max(y1, y2);

		for (int x = minX; x <= maxX; x++) {
			consumer.accept(x, minY);
			consumer.accept(x, maxY);
		}
		for (int y = minY; y <= maxY; y++) {
			consumer.accept(minX, y);
			consumer.accept(maxX, y);
		}
	}

	private void traceCircle(int cx, int cy, int radius, PixelConsumer consumer) {
		int x = radius;
		int y = 0;
		int err = 0;

		while (x >= y) {
			consumer.accept(cx + x, cy + y);
			consumer.accept(cx + y, cy + x);
			consumer.accept(cx - y, cy + x);
			consumer.accept(cx - x, cy + y);
			consumer.accept(cx - x, cy - y);
			consumer.accept(cx - y, cy - x);
			consumer.accept(cx + y, cy - x);
			consumer.accept(cx + x, cy - y);

			y += 1;
			err += 1 + 2 * y;
			if (2 * (err - x) + 1 > 0) {
				x -= 1;
				err += 1 - 2 * x;
			}
		}
	}

	private boolean isInsideCanvas(double mouseX, double mouseY) {
		return mouseX >= canvasX && mouseX < canvasX + CANVAS_WIDTH * pixelScale
				&& mouseY >= canvasY && mouseY < canvasY + CANVAS_HEIGHT * pixelScale;
	}

	private int toCanvasX(int screenX) {
		float scale = (CANVAS_WIDTH * pixelScale) / (float) regionWidth;
		return (int) Math.floor(regionU + (screenX - canvasX) / scale);
	}

	private int toCanvasY(int screenY) {
		float scale = (CANVAS_HEIGHT * pixelScale) / (float) regionHeight;
		return (int) Math.floor(regionV + (screenY - canvasY) / scale);
	}

	private float screenScaleX() {
		return (CANVAS_WIDTH * pixelScale) / (float) regionWidth;
	}

	private float screenScaleY() {
		return (CANVAS_HEIGHT * pixelScale) / (float) regionHeight;
	}

	private int toScreenX(int canvasPixelX) {
		return canvasX + Math.round((canvasPixelX - regionU) * screenScaleX());
	}

	private int toScreenY(int canvasPixelY) {
		return canvasY + Math.round((canvasPixelY - regionV) * screenScaleY());
	}

	private int getLiveMouseX() {
		MinecraftClient client = MinecraftClient.getInstance();
		return (int) client.mouse.getScaledX(client.getWindow());
	}

	private int getLiveMouseY() {
		MinecraftClient client = MinecraftClient.getInstance();
		return (int) client.mouse.getScaledY(client.getWindow());
	}

	private void saveToDisk() {
		MinimapData.INSTANCE.save();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (hexField != null && hexField.isFocused()) {
			return super.keyPressed(input);
		}

		boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;

		if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (pendingPaste) {
				pendingPaste = false;
				return true;
			}
			if (hasSelection) {
				clearSelection();
				return true;
			}
			return super.keyPressed(input);
		}

		if (ctrl && input.key() == GLFW.GLFW_KEY_Z) {
			undo();
			return true;
		}

		if (ctrl && input.key() == GLFW.GLFW_KEY_Y) {
			redo();
			return true;
		}

		if (ctrl && input.key() == GLFW.GLFW_KEY_C) {
			if (hasSelection) copySelection();
			return true;
		}

		if (ctrl && input.key() == GLFW.GLFW_KEY_X) {
			if (hasSelection) {
				copySelection();
				clearSelectionArea();
			}
			return true;
		}

		if (ctrl && input.key() == GLFW.GLFW_KEY_V) {
			if (hasClipboard) {
				pendingPaste = true;
			}
			return true;
		}

		if ((input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE) && hasSelection) {
			clearSelectionArea();
			return true;
		}

		return super.keyPressed(input);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		super.removed();
		MinecraftClient.getInstance().getTextureManager().destroyTexture(canvasTextureId);
		MinecraftClient.getInstance().getTextureManager().destroyTexture(svTextureId);
		MinecraftClient.getInstance().getTextureManager().destroyTexture(hueTextureId);

		long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
		GLFW.glfwSetScrollCallback(windowHandle, previousScrollCallback);
	}

	private static class ColorSlider extends SliderWidget {

		private final String label;
		private final IntConsumer onChange;

		public ColorSlider(int x, int y, int width, int height, String label, int initialValue, IntConsumer onChange) {
			super(x, y, width, height, Text.literal(label + ": " + initialValue), initialValue / 255.0);
			this.label = label;
			this.onChange = onChange;
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(label + ": " + (int) Math.round(this.value * 255)));
		}

		@Override
		protected void applyValue() {
			onChange.accept((int) Math.round(this.value * 255));
		}

		public void setValueSilently(double value) {
			this.value = value;
			updateMessage();
		}
	}

	private static class BrushSizeSlider extends SliderWidget {

		private final IntConsumer onChange;

		public BrushSizeSlider(int x, int y, int width, int height, int initialValue, IntConsumer onChange) {
			super(x, y, width, height, Text.literal("Pinselgroesse: " + initialValue), (initialValue - 1) / 9.0);
			this.onChange = onChange;
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal("Pinselgroesse: " + currentSize()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(currentSize());
		}

		private int currentSize() {
			return 1 + (int) Math.round(this.value * 9);
		}
	}
}
