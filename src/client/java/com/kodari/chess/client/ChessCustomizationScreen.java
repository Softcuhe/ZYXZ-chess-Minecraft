package com.kodari.chess.client;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ChessCustomizationScreen extends Screen {
    private enum Page { PIECES, STYLE }

    private static final String[] STYLE_NAMES = {
            "Background image URL", "Background color", "Light square color", "Dark square color", "Name color",
            "UI panel color", "UI accent color", "UI text color"
    };

    private final Screen parent;
    private final Page page;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private ButtonWidget piecePresetButton;
    private ButtonWidget transparentButton;
    private ButtonWidget parallaxButton;
    private ButtonWidget pieceNamesButton;
    private int selectedColorField = 1;
    private float pickerHue;
    private float pickerSaturation = 1.0f;
    private float pickerValue = 1.0f;
    private String notice = "Choose a built-in piece set; both colors use that set.";

    public ChessCustomizationScreen(Screen parent) {
        this(parent, Page.PIECES);
    }

    private ChessCustomizationScreen(Screen parent, Page page) {
        super(Text.literal("Chess Customization"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();
        int centerX = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Piece set"), button -> open(Page.PIECES))
                .dimensions(centerX - 105, 25, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Board / colors"), button -> open(Page.STYLE))
                .dimensions(centerX + 5, 25, 100, 20).build());

        String[] labels = page == Page.STYLE ? STYLE_NAMES : new String[0];
        int firstY = Math.min(56, Math.max(44, height - (labels.length * 20 + 80)));
        if (page == Page.PIECES) {
            piecePresetButton = addDrawableChild(ButtonWidget.builder(Text.literal(piecePresetLabel()), button -> cyclePiecePreset())
                    .dimensions(centerX - 100, height / 2 - 10, 200, 22).build());
        }
        for (int i = 0; i < labels.length; i++) {
            int y = firstY + i * 20;
            int fieldIndex = i;
            TextFieldWidget field = addDrawableChild(new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                    centerX - 45, y, 130, 19, Text.literal(labels[i])));
            field.setMaxLength(2048);
            field.setText(fieldValue(i));
            fields.add(field);
            if (i == 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("PNG..."), button -> chooseImage(field))
                        .dimensions(centerX + 88, y, 62, 19).build());
            } else {
                addDrawableChild(ButtonWidget.builder(Text.literal("Pick"), button -> selectColor(fieldIndex))
                        .dimensions(centerX + 88, y, 62, 19).build());
            }
        }
        if (page == Page.STYLE) {
            transparentButton = addDrawableChild(ButtonWidget.builder(Text.literal(transparentLabel()), button -> toggleTransparent())
                    .dimensions(centerX - 150, height - 52, 100, 20).build());
            parallaxButton = addDrawableChild(ButtonWidget.builder(Text.literal(parallaxLabel()), button -> toggleParallax())
                    .dimensions(centerX - 45, height - 52, 90, 20).build());
            pieceNamesButton = addDrawableChild(ButtonWidget.builder(Text.literal(pieceNamesLabel()), button -> togglePieceNames())
                    .dimensions(centerX + 50, height - 52, 100, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), button -> apply())
                .dimensions(centerX + 5, height - 28, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> returnToParent())
                .dimensions(centerX + 80, height - 28, 70, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ChessBoardScreen.drawCustomBackground(context, width, height, mouseX, mouseY);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("CUSTOMIZE CHESS"), width / 2, 8,
                ChessPreferences.getColor("ui.text", 0xFFF2F3EF));
        String[] labels = page == Page.STYLE ? STYLE_NAMES : new String[0];
        int firstY = Math.min(56, Math.max(44, height - (labels.length * 20 + 80)));
        for (int i = 0; i < labels.length; i++) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(labels[i]),
                    width / 2 - 150, firstY + i * 20 + 5, ChessPreferences.getColor("ui.text", 0xFFE2E9E5));
        }
        if (!notice.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(notice),
                    width / 2, page == Page.STYLE ? colorPickerY() - 11 : height - 73, 0xFFE6C56D);
        }
        if (page == Page.STYLE) drawColorPicker(context);
        else context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Both white and black pieces use this preset"), width / 2, height / 2 + 18,
                ChessPreferences.getColor("ui.text", 0xFFE2E9E5));
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (page == Page.STYLE && click.button() == 0 && updateColorPicker(click.x(), click.y())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (page == Page.STYLE && click.button() == 0 && updateColorPicker(click.x(), click.y())) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }

    private String fieldValue(int index) {
        return switch (index) {
            case 0 -> ChessPreferences.get("background.image", "");
            case 1 -> ChessPreferences.get("background.color", "101820");
            case 2 -> ChessPreferences.get("board.light", "EEE9DF");
            case 3 -> ChessPreferences.get("board.dark", "56816F");
            case 4 -> ChessPreferences.get("name.color", "E2E9E5");
            case 5 -> ChessPreferences.get("ui.panel", "1D2A30");
            case 6 -> ChessPreferences.get("ui.accent", "63B78B");
            default -> ChessPreferences.get("ui.text", "E9F2EC");
        };
    }

    private String piecePresetLabel() {
        return "Piece set: " + ChessPreferences.getPiecePreset();
    }

    private void cyclePiecePreset() {
        String[] presets = ChessPreferences.piecePresets();
        String selected = ChessPreferences.getPiecePreset();
        int next = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(selected)) {
                next = (i + 1) % presets.length;
                break;
            }
        }
        ChessPreferences.setPiecePreset(presets[next]);
        piecePresetButton.setMessage(Text.literal(piecePresetLabel()));
        notice = "Both sides now use the " + presets[next] + " piece set.";
    }

    private String transparentLabel() {
        return "Transparent: " + (ChessPreferences.getBoolean("background.transparent", false) ? "ON" : "OFF");
    }

    private String parallaxLabel() {
        return "Parallax: " + (ChessPreferences.getBoolean("background.parallax", false) ? "ON" : "OFF");
    }

    private String pieceNamesLabel() {
        return "Names: " + (ChessPreferences.getBoolean("piece.names", false) ? "ON" : "OFF");
    }

    private void toggleTransparent() {
        boolean enabled = !ChessPreferences.getBoolean("background.transparent", false);
        ChessPreferences.setBoolean("background.transparent", enabled);
        transparentButton.setMessage(Text.literal(transparentLabel()));
    }

    private void togglePieceNames() {
        boolean enabled = !ChessPreferences.getBoolean("piece.names", false);
        ChessPreferences.setBoolean("piece.names", enabled);
        pieceNamesButton.setMessage(Text.literal(pieceNamesLabel()));
    }

    private void toggleParallax() {
        boolean enabled = !ChessPreferences.getBoolean("background.parallax", false);
        ChessPreferences.setBoolean("background.parallax", enabled);
        parallaxButton.setMessage(Text.literal(parallaxLabel()));
    }

    private void selectColor(int fieldIndex) {
        selectedColorField = fieldIndex;
        String value = fields.get(fieldIndex).getText().trim().replace("#", "");
        try {
            int color = Integer.parseInt(value, 16);
            float red = ((color >> 16) & 255) / 255.0f;
            float green = ((color >> 8) & 255) / 255.0f;
            float blue = (color & 255) / 255.0f;
            float max = Math.max(red, Math.max(green, blue));
            float min = Math.min(red, Math.min(green, blue));
            float delta = max - min;
            pickerValue = max;
            pickerSaturation = max == 0 ? 0 : delta / max;
            if (delta == 0) pickerHue = 0;
            else if (max == red) pickerHue = ((green - blue) / delta) % 6 / 6.0f;
            else if (max == green) pickerHue = ((blue - red) / delta + 2) / 6.0f;
            else pickerHue = ((red - green) / delta + 4) / 6.0f;
            if (pickerHue < 0) pickerHue += 1.0f;
        } catch (NumberFormatException ignored) {
            pickerHue = 0;
            pickerSaturation = 1;
            pickerValue = 1;
        }
    }

    private int colorPickerY() {
        int firstY = Math.min(56, Math.max(44, height - (STYLE_NAMES.length * 20 + 80)));
        return Math.max(firstY + STYLE_NAMES.length * 20 + 6, height - 132);
    }

    private void drawColorPicker(DrawContext context) {
        int x = width / 2 - 56;
        int y = colorPickerY();
        context.fill(x - 1, y - 1, x + 97, y + 65, 0xFF101820);
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 12; column++) {
                context.fill(x + column * 8, y + row * 8, x + column * 8 + 8, y + row * 8 + 8,
                        0xFF000000 | hsvToRgb(pickerHue, 1.0f - column / 11.0f, 1.0f - row / 7.0f));
            }
            context.fill(x + 101, y + row * 8, x + 113, y + row * 8 + 8,
                    0xFF000000 | hsvToRgb(row / 8.0f, 1.0f, 1.0f));
        }
        context.fill(x + 99, y - 1, x + 115, y + 65, 0xFF101820);
        for (int row = 0; row < 8; row++) {
            context.fill(x + 101, y + row * 8, x + 113, y + row * 8 + 8,
                    0xFF000000 | hsvToRgb(row / 8.0f, 1.0f, 1.0f));
        }
        context.fill(x + 115, y - 1, x + 116, y + 65, 0xFFE2E9E5);
    }

    private boolean updateColorPicker(double mouseX, double mouseY) {
        int x = width / 2 - 56;
        int y = colorPickerY();
        if (mouseY < y || mouseY >= y + 64) return false;
        if (mouseX >= x && mouseX < x + 96) {
            pickerSaturation = 1.0f - (float) (mouseX - x) / 96.0f;
            pickerValue = 1.0f - (float) (mouseY - y) / 64.0f;
        } else if (mouseX >= x + 101 && mouseX < x + 113) {
            pickerHue = Math.max(0, Math.min(1, (float) (mouseY - y) / 64.0f));
        } else {
            return false;
        }
        int color = hsvToRgb(pickerHue, pickerSaturation, pickerValue);
        fields.get(selectedColorField).setText(String.format(java.util.Locale.ROOT, "%06X", color));
        return true;
    }

    private int hsvToRgb(float hue, float saturation, float value) {
        float chroma = value * saturation;
        float segment = hue * 6.0f;
        float secondary = chroma * (1.0f - Math.abs(segment % 2.0f - 1.0f));
        float red;
        float green;
        float blue;
        switch ((int) segment % 6) {
            case 0 -> { red = chroma; green = secondary; blue = 0; }
            case 1 -> { red = secondary; green = chroma; blue = 0; }
            case 2 -> { red = 0; green = chroma; blue = secondary; }
            case 3 -> { red = 0; green = secondary; blue = chroma; }
            case 4 -> { red = secondary; green = 0; blue = chroma; }
            default -> { red = chroma; green = 0; blue = secondary; }
        }
        float match = value - chroma;
        return (Math.round((red + match) * 255) << 16)
                | (Math.round((green + match) * 255) << 8)
                | Math.round((blue + match) * 255);
    }

    private void apply() {
        if (page == Page.PIECES) {
            ChessPreferences.save();
            notice = "Piece set applied and saved.";
            return;
        } else {
            String imageUrl = fields.get(0).getText().trim();
            if (!imageUrl.isEmpty() && !ChessPreferences.isImageSource(imageUrl)) {
                notice = "Background must use an HTTP(S) image URL or a picked PNG.";
                return;
            }
            for (int i = 1; i < fields.size(); i++) {
                if (!ChessPreferences.isHexColor(fields.get(i).getText())) {
                    notice = "Colors must be six hex digits, for example 56816F.";
                    return;
                }
            }
            ChessPreferences.set("background.image", imageUrl);
            ChessPreferences.set("background.color", normalizeColor(fields.get(1).getText()));
            ChessPreferences.set("board.light", normalizeColor(fields.get(2).getText()));
            ChessPreferences.set("board.dark", normalizeColor(fields.get(3).getText()));
            ChessPreferences.set("name.color", normalizeColor(fields.get(4).getText()));
            ChessPreferences.set("ui.panel", normalizeColor(fields.get(5).getText()));
            ChessPreferences.set("ui.accent", normalizeColor(fields.get(6).getText()));
            ChessPreferences.set("ui.text", normalizeColor(fields.get(7).getText()));
        }
        ChessPreferences.save();
        notice = "Customization applied and saved.";
    }

    private String normalizeColor(String value) {
        return value.trim().replace("#", "").toUpperCase();
    }

    private void chooseImage(TextFieldWidget field) {
        notice = "Choose a PNG image...";
        if (isArmDevice()) {
            MinecraftClient.getInstance().setScreen(new ChessPngPickerScreen(this, initialImageDirectory(),
                    path -> importPickedImage(field, path)));
            return;
        }
        CompletableFuture.supplyAsync(ChessCustomizationScreen::openPngPicker).thenAccept(path ->
                MinecraftClient.getInstance().execute(() -> {
                    if (path == null) {
                        notice = "No file selected, or this launcher has no native file picker.";
                        return;
                    }
                    importPickedImage(field, path);
                }));
    }

    private void importPickedImage(TextFieldWidget field, Path path) {
        String image = ChessPreferences.storeImage(path);
        if (image.isEmpty()) {
            notice = "Pick a valid PNG no larger than 1 MiB.";
            return;
        }
        field.setText(image);
        ChessPreferences.set("background.image", image);
        ChessPreferences.save();
        notice = "PNG imported and applied.";
    }

    private static boolean isArmDevice() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return architecture.contains("arm") || architecture.contains("aarch64");
    }

    private static Path initialImageDirectory() {
        String externalStorage = System.getenv("EXTERNAL_STORAGE");
        Path mainStorage = externalStorage == null || externalStorage.isBlank()
                ? Path.of("/storage/emulated/0") : Path.of(externalStorage);
        if (Files.isDirectory(mainStorage) && Files.isReadable(mainStorage)) return mainStorage;
        Path home = Path.of(System.getProperty("user.home", "."));
        return Files.isDirectory(home) ? home : Path.of(".");
    }

    private static Path openPngPicker() {
        try {
            Class<?> frameClass = Class.forName("java.awt.Frame");
            Class<?> dialogClass = Class.forName("java.awt.FileDialog");
            Object dialog = dialogClass.getConstructor(frameClass, String.class, int.class)
                    .newInstance(null, "Choose a PNG image", 0);
            dialogClass.getMethod("setVisible", boolean.class).invoke(dialog, true);
            String fileName = (String) dialogClass.getMethod("getFile").invoke(dialog);
            String directory = (String) dialogClass.getMethod("getDirectory").invoke(dialog);
            if (fileName == null || !fileName.toLowerCase().endsWith(".png") || directory == null) return null;
            return Path.of(directory, fileName);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private void open(Page newPage) {
        MinecraftClient.getInstance().setScreen(new ChessCustomizationScreen(parent, newPage));
    }

    private void returnToParent() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void close() {
        returnToParent();
    }
}