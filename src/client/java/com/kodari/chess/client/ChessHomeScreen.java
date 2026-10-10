package com.kodari.chess.client;

import com.kodari.chess.ChessGame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class ChessHomeScreen extends Screen {
    private record TweenButton(ButtonWidget button, int x, int y) {
    }

    private static final int[] CLOCK_PRESETS = {1, 2, 5, 8, 10};
    private static final String LOGO_URL = "https://raw.githubusercontent.com/Softcuhe/ZYXZ-chess/main/assets/logo/Untitled256_20261005001553.png";
    private static final String BOT_ICON_URL = "https://raw.githubusercontent.com/Softcuhe/ZYXZ-chess/main/assets/bot%20icon/";
    private static final String[] BOT_ICON_IDS = {"steve", "alex", "tuye", "witty_alien"};
    private static int difficulty = Math.max(1, Math.min(5, ChessPreferences.getInt("difficulty", 3)));
    private final Screen parent;
    private final boolean inWorld;
    private final boolean botSelection;
    private final List<TweenButton> tweenButtons = new ArrayList<>();
    private int sliderX;
    private int sliderY;
    private long menuOpenedAt;
    private String notice = "";
    private ButtonWidget timerButton;

    public ChessHomeScreen(Screen parent, boolean inWorld) {
        this(parent, inWorld, false);
    }

    private ChessHomeScreen(Screen parent, boolean inWorld, boolean botSelection) {
        super(Text.literal("Chess"));
        this.parent = parent;
        this.inWorld = inWorld;
        this.botSelection = botSelection;
    }

    @Override
    protected void init() {
        super.init();
        tweenButtons.clear();
        menuOpenedAt = System.currentTimeMillis();
        if (inWorld) ChessClient.send("PLAYER_STATE|MENU");
        int centerX = width / 2;
        int centerY = height / 2;
        if (botSelection) {
            sliderX = centerX - 100;
            sliderY = centerY - 72;
            int timerX = Math.min(centerX + 108, width - 74);
            timerButton = addTweenButton(ButtonWidget.builder(Text.literal(timerLabel()), button -> cycleTimer())
                    .dimensions(timerX, centerY - 72, 68, 20).build(), timerX, centerY - 72);
            addBotChoice(centerX, centerY - 48, "Steve  ★★☆☆☆  Easy", "steve");
            addBotChoice(centerX, centerY - 24, "Alex  ★★★☆☆", "alex");
            addBotChoice(centerX, centerY, ChessPreferences.getBoolean("bot.secret.unlocked", false)
                    ? "Tuye  ★★★★★  Hard" : "???  ★★★★★  Secret", "tuye");
            addBotChoice(centerX, centerY + 24, ChessPreferences.getBoolean("bot.secret.unlocked", false)
                    ? "Witty Alien  ★★★★★" : "?????  ★★★★★  Secret", "witty_alien");
            addBotChoice(centerX, centerY + 48, "CPU  ·  Silent", "cpu");
            addTweenButton(ButtonWidget.builder(Text.literal("Back"), button -> returnToParent())
                    .dimensions(centerX - 55, height - 25, 110, 20).build(), centerX - 55, height - 25);
            return;
        }
        sliderY = centerY - 48;
        addTweenButton(ButtonWidget.builder(Text.literal("VS BOT"), button ->
                        MinecraftClient.getInstance().setScreen(new ChessHomeScreen(this, inWorld, true)))
                .dimensions(centerX - 90, centerY - 7, 180, 22).build(), centerX - 90, centerY - 7);
        addTweenButton(ButtonWidget.builder(Text.literal(inWorld ? "Multiplayer" : "Multiplayer (in a world)"), button -> {
                    if (!inWorld) notice = "Join a world or server to play multiplayer.";
                    else if (ChessClient.serverSupportsChess()) MinecraftClient.getInstance().setScreen(new ChessLobbyScreen(this));
                    else notice = "This server needs Kodari Chess for multiplayer; VS BOT works here.";
                })
                .dimensions(centerX - 90, centerY + 22, 180, 22).build(), centerX - 90, centerY + 22);
        addTweenButton(ButtonWidget.builder(Text.literal("Customize"), button ->
                        MinecraftClient.getInstance().setScreen(new ChessCustomizationScreen(this)))
                .dimensions(centerX - 90, centerY + 51, 85, 22).build(), centerX - 90, centerY + 51);
        addTweenButton(ButtonWidget.builder(Text.literal("Profile"), button -> {
                    if (inWorld && !ChessClient.serverSupportsChess()) {
                        notice = "Profiles need a server running Kodari Chess.";
                    } else {
                        MinecraftClient.getInstance().setScreen(new ChessProfileScreen(this));
                    }
                })
                .dimensions(centerX + 5, centerY + 51, 85, 22).build(), centerX + 5, centerY + 51);
        addTweenButton(ButtonWidget.builder(Text.literal("Learn chess"), button ->
                        MinecraftClient.getInstance().setScreen(new ChessLessonsScreen(inWorld)))
                .dimensions(centerX - 90, centerY + 80, 180, 22).build(), centerX - 90, centerY + 80);
        addTweenButton(ButtonWidget.builder(Text.literal("Back"), button -> returnToParent())
                .dimensions(centerX - 55, height - 25, 110, 20).build(), centerX - 55, height - 25);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        updateTweenButtons();
        ChessBoardScreen.drawCustomBackground(context, width, height, mouseX, mouseY);
        context.fill(0, 0, width, 3, ChessPreferences.getColor("ui.accent", 0xFF63B78B));
        if (botSelection) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("CHOOSE YOUR OPPONENT"), width / 2, 10, 0xFFF2F3EF);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("CPU DIFFICULTY  " + difficulty + " / 5"), width / 2, sliderY - 15, 0xFFD5DED9);
            context.fill(sliderX, sliderY, sliderX + 200, sliderY + 6, 0xFF39464B);
            int knob = sliderX + (difficulty - 1) * 50;
            context.fill(sliderX, sliderY, knob + 5, sliderY + 6, 0xFF63B78B);
            context.fill(knob, sliderY - 5, knob + 10, sliderY + 11, 0xFFE9F2EC);
        } else {
            int floatOffset = (int) Math.round(Math.sin(System.currentTimeMillis() / 700.0) * 5.0);
            var logo = ChessBoardScreen.requestRemoteTexture(LOGO_URL);
            int[] logoSize = ChessBoardScreen.textureSize(LOGO_URL);
            if (logo != null && logoSize != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, logo, width / 2 - 48,
                        height / 2 - 196 + floatOffset, 0, 0, 96, 96,
                        logoSize[0], logoSize[1], logoSize[0], logoSize[1], 0xFFFFFFFF);
            }
        }
        if (!notice.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(notice),
                    width / 2, botSelection ? height - 42 : height / 2 + 55, 0xFFE6C56D);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
        if (botSelection) {
            int iconX = width / 2 - 137;
            for (int i = 0; i < BOT_ICON_IDS.length; i++) {
                String url = BOT_ICON_URL + BOT_ICON_IDS[i] + ".png";
                var icon = ChessBoardScreen.requestRemoteTexture(url);
                int[] iconSize = ChessBoardScreen.textureSize(url);
                if (icon != null && iconSize != null) {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, iconX, height / 2 - 47 + i * 24,
                            0, 0, 22, 22, iconSize[0], iconSize[1], iconSize[0], iconSize[1], 0xFFFFFFFF);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (botSelection && click.button() == 0 && click.y() >= sliderY - 12 && click.y() <= sliderY + 17
                && click.x() >= sliderX - 8 && click.x() <= sliderX + 208) {
            updateDifficulty(click.x());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (botSelection && click.button() == 0 && click.y() >= sliderY - 12 && click.y() <= sliderY + 17) {
            updateDifficulty(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    private void updateDifficulty(double mouseX) {
        double fraction = Math.max(0, Math.min(1, (mouseX - sliderX) / 200.0));
        difficulty = Math.max(1, Math.min(5, (int) Math.round(fraction * 4) + 1));
        ChessPreferences.setInt("difficulty", difficulty);
    }

    private void cycleTimer() {
        int current = 0;
        int minutes = timerMinutes();
        while (current < CLOCK_PRESETS.length && CLOCK_PRESETS[current] != minutes) current++;
        int next = CLOCK_PRESETS[(current + 1) % CLOCK_PRESETS.length];
        ChessPreferences.setInt("timer.minutes", next);
        timerButton.setMessage(Text.literal(timerLabel()));
    }

    private static int timerMinutes() {
        int minutes = ChessPreferences.getInt("timer.minutes", 5);
        for (int preset : CLOCK_PRESETS) if (preset == minutes) return minutes;
        return 5;
    }

    private static String timerLabel() {
        return "Timer " + timerMinutes() + "m";
    }

    private void addBotChoice(int centerX, int y, String label, String botId) {
        addTweenButton(ButtonWidget.builder(Text.literal(label), button -> startBotMatch(botId))
                .dimensions(centerX - 110, y, 220, 20).build(), centerX - 110, y);
    }

    private ButtonWidget addTweenButton(ButtonWidget button, int x, int y) {
        button.setY(y + 12);
        tweenButtons.add(new TweenButton(button, x, y));
        return addDrawableChild(button);
    }

    private void updateTweenButtons() {
        double progress = Math.min(1.0, (System.currentTimeMillis() - menuOpenedAt) / 260.0);
        progress = progress * progress * (3 - 2 * progress);
        int offset = (int) Math.round((1.0 - progress) * 12.0);
        for (TweenButton entry : tweenButtons) {
            entry.button().setX(entry.x());
            entry.button().setY(entry.y() + offset);
        }
    }

    private void startBotMatch(String botId) {
        boolean secret = botId.equals("tuye") || botId.equals("witty_alien");
        if (secret && !ChessPreferences.getBoolean("bot.secret.unlocked", false)) {
            notice = "Secret bots unlock with /chess bot secret.";
            return;
        }
        String name = switch (botId) {
            case "alex" -> "Alex";
            case "tuye" -> "Tuye";
            case "witty_alien" -> "Witty Alien";
            case "cpu" -> "CPU";
            default -> "Steve";
        };
        int strength = switch (botId) {
            case "steve" -> 2;
            case "alex" -> 3;
            case "tuye", "witty_alien" -> 5;
            default -> difficulty;
        };
        ChessPreferences.set("bot.selected", botId);
        ChessPreferences.save();
        if (inWorld) ChessClient.send("PLAYER_STATE|BOT");
        MinecraftClient.getInstance().setScreen(new ChessBoardScreen(this, new ChessGame(), false,
                Math.random() < 0.5, strength, "", "You vs " + name));
    }

    private void returnToParent() {
        if (botSelection) {
            MinecraftClient.getInstance().setScreen(parent);
            return;
        }
        if (inWorld) ChessClient.send("PLAYER_STATE|IDLE");
        if (parent == null) MinecraftClient.getInstance().setScreen(null);
        else MinecraftClient.getInstance().setScreen(parent);
    }

    static ChessHomeScreen botSelection(Screen parent, boolean inWorld) {
        return new ChessHomeScreen(parent, inWorld, true);
    }

    @Override
    public void close() {
        returnToParent();
    }
}