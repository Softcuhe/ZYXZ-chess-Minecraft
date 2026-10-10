package com.kodari.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class ChessTutorialScreen extends Screen {
    private final Screen parent;
    private final boolean inWorld;
    private int selectedDifficulty;
    private ButtonWidget beginnerButton;
    private ButtonWidget proButton;
    private ButtonWidget masterButton;

    ChessTutorialScreen(Screen parent, boolean inWorld) {
        super(Text.literal("Chess quick start"));
        this.parent = parent;
        this.inWorld = inWorld;
        selectedDifficulty = Math.max(1, Math.min(5, ChessPreferences.getInt("difficulty", 3)));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int panelY = height / 2 - 88;
        beginnerButton = addDrawableChild(ButtonWidget.builder(levelLabel(1, "Beginner"), button -> selectLevel(1))
                .dimensions(centerX - 139, panelY + 77, 88, 20).build());
        proButton = addDrawableChild(ButtonWidget.builder(levelLabel(3, "Pro"), button -> selectLevel(3))
                .dimensions(centerX - 44, panelY + 77, 88, 20).build());
        masterButton = addDrawableChild(ButtonWidget.builder(levelLabel(5, "Master"), button -> selectLevel(5))
                .dimensions(centerX + 51, panelY + 77, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play vs bot"), button -> startBotGame())
                .dimensions(centerX - 104, panelY + 120, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Skip"), button -> finishAndReturn())
                .dimensions(centerX + 4, panelY + 120, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        parent.renderWithTooltip(context, mouseX, mouseY, deltaTicks);
        context.fill(0, 0, width, height, 0x99070D0F);
        int panelWidth = Math.min(340, width - 20);
        int panelX = (width - panelWidth) / 2;
        int panelY = height / 2 - 88;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 151, 0xF01A282D);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF63B78B);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Hey, welcome to Chess."), width / 2, panelY + 10, 0xFFF2F3EF);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Images download once, then stay saved offline."), width / 2, panelY + 29, 0xFFD5DED9);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Where are you at?"), width / 2, panelY + 56, 0xFFE6C56D);
        drawArrow(context, panelX + panelWidth - 12, panelY + 132, width - 60, height - 20);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Chess is down here"), width - 112, height - 43, 0xFFFFD166);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void selectLevel(int level) {
        selectedDifficulty = level;
        ChessPreferences.setInt("difficulty", level);
        beginnerButton.setMessage(levelLabel(1, "Beginner"));
        proButton.setMessage(levelLabel(3, "Pro"));
        masterButton.setMessage(levelLabel(5, "Master"));
    }

    private Text levelLabel(int level, String name) {
        return Text.literal((selectedDifficulty == level ? "✓ " : "") + name);
    }

    private void startBotGame() {
        ChessPreferences.setBoolean("onboarding.complete", true);
        MinecraftClient.getInstance().setScreen(ChessHomeScreen.botSelection(parent, inWorld));
    }

    private void finishAndReturn() {
        ChessPreferences.setBoolean("onboarding.complete", true);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void drawArrow(DrawContext context, int fromX, int fromY, int toX, int toY) {
        int steps = Math.max(1, Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY)) / 3);
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            int x = (int) (fromX + (toX - fromX) * progress);
            int y = (int) (fromY + (toY - fromY) * progress);
            context.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFD166);
        }
        context.fill(toX - 6, toY - 5, toX - 2, toY - 1, 0xFFFFD166);
        context.fill(toX - 6, toY + 1, toX - 2, toY + 5, 0xFFFFD166);
    }

    @Override
    public void close() {
        finishAndReturn();
    }
}