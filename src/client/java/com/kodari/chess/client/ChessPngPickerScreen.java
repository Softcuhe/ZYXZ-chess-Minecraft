package com.kodari.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class ChessPngPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Path> onSelected;
    private final Path directory;
    private final int page;
    private final List<Path> entries = new ArrayList<>();
    private TextFieldWidget directoryField;
    private String notice = "Choose a PNG or open a folder.";
    private int rowsPerPage;

    ChessPngPickerScreen(Screen parent, Path directory, Consumer<Path> onSelected) {
        this(parent, directory, onSelected, 0);
    }

    private ChessPngPickerScreen(Screen parent, Path directory, Consumer<Path> onSelected, int page) {
        super(Text.literal("Choose a PNG"));
        this.parent = parent;
        this.directory = directory;
        this.onSelected = onSelected;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        super.init();
        directoryField = addDrawableChild(new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                10, 30, Math.max(100, width - 72), 20, Text.literal("Image folder path")));
        directoryField.setMaxLength(2048);
        directoryField.setText(directory.toString());
        addDrawableChild(ButtonWidget.builder(Text.literal("Go"), button -> navigateToPath())
                .dimensions(width - 56, 30, 46, 20).build());

        loadEntries();
        rowsPerPage = Math.max(1, Math.min(18, (height - 100) / 20));
        int first = Math.min(page * rowsPerPage, Math.max(0, entries.size() - 1));
        int last = Math.min(entries.size(), first + rowsPerPage);
        for (int i = first; i < last; i++) {
            Path entry = entries.get(i);
            boolean folder = Files.isDirectory(entry);
            String name = entry.getFileName() == null ? entry.toString() : entry.getFileName().toString();
            Text label = Text.literal((folder ? "[Folder] " : "") + name);
            addDrawableChild(ButtonWidget.builder(label, button -> openEntry(entry))
                    .dimensions(10, 56 + (i - first) * 20, width - 20, 18).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), button -> goUp())
                .dimensions(10, height - 28, 44, 20).build());
        if (page > 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), button -> openPage(page - 1))
                    .dimensions(60, height - 28, 48, 20).build());
        }
        if ((page + 1) * rowsPerPage < entries.size()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> openPage(page + 1))
                    .dimensions(114, height - 28, 48, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> returnToParent())
                .dimensions(width - 74, height - 28, 64, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ChessBoardScreen.drawCustomBackground(context, width, height, mouseX, mouseY);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("CHOOSE A PNG IMAGE"), width / 2, 8, 0xFFE6C56D);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(notice),
                10, 52, 0xFFE2E9E5);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void loadEntries() {
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> Files.isDirectory(path) || isPng(path))
                    .sorted(Comparator.comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(entries::add);
            if (entries.isEmpty()) notice = "No folders or PNG images here.";
        } catch (IOException | SecurityException exception) {
            notice = "Can't read this folder. Check its path or storage permission.";
        }
    }

    private static boolean isPng(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private void navigateToPath() {
        try {
            Path path = Path.of(directoryField.getText().trim());
            if (isPng(path)) {
                select(path);
            } else if (Files.isDirectory(path) && Files.isReadable(path)) {
                openDirectory(path);
            } else {
                notice = "Enter a readable folder path or a PNG file path.";
            }
        } catch (InvalidPathException exception) {
            notice = "That path doesn't look valid.";
        }
    }

    private void openEntry(Path path) {
        if (Files.isDirectory(path)) openDirectory(path);
        else if (isPng(path)) select(path);
    }

    private void openDirectory(Path path) {
        MinecraftClient.getInstance().setScreen(new ChessPngPickerScreen(parent, path, onSelected));
    }

    private void openPage(int page) {
        MinecraftClient.getInstance().setScreen(new ChessPngPickerScreen(parent, directory, onSelected, page));
    }

    private void goUp() {
        Path parentDirectory = directory.getParent();
        if (parentDirectory != null) openDirectory(parentDirectory);
    }

    private void select(Path path) {
        MinecraftClient.getInstance().setScreen(parent);
        onSelected.accept(path);
    }

    private void returnToParent() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void close() {
        returnToParent();
    }
}