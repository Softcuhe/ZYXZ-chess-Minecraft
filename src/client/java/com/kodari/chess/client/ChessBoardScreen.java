package com.kodari.chess.client;

import com.kodari.chess.ChessGame;
import com.kodari.chess.ChessTiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ChessBoardScreen extends Screen {
    private enum EndMode { WIN, LOSS, DRAW }
    private record BotTurn(ChessGame.Move move, ChessGame.Move accuracyMove) {
    }
    private record BoardArrow(int from, int to, int corner) {
    }

    private static final long MOVE_ANIMATION_MS = 180;
    private static final long END_ANIMATION_MS = 1800;
    private static final List<ChessGame.Move> TUYE_NAJDORF = List.of(
            openingMove("e2", "e4"), openingMove("c7", "c5"), openingMove("g1", "f3"),
            openingMove("d7", "d6"), openingMove("d2", "d4"), openingMove("c5", "d4"),
            openingMove("f3", "d4"), openingMove("g8", "f6"), openingMove("b1", "c3"),
            openingMove("a7", "a6"));
    private static final List<ChessGame.Move> TUYE_KING_INDIAN_ATTACK = List.of(
            openingMove("g1", "f3"), openingMove("g2", "g3"), openingMove("f1", "g2"),
            openingMove("e1", "g1"), openingMove("d2", "d3"), openingMove("b1", "d2"));
    private static final List<ChessGame.Move> WITTY_ALIEN_GAMBIT = List.of(
            openingMove("e2", "e4"), openingMove("c7", "c6"), openingMove("d2", "d4"),
            openingMove("d7", "d5"), openingMove("b1", "c3"), openingMove("d5", "e4"),
            openingMove("f2", "f3"));
    private static final String[] STEVE_DIALOGUE = {
            "Good move. What is your plan?", "I am thinking a few steps ahead.",
            "That changes the board in an interesting way.", "Let us see how this plays out.",
            "Nice one. I will have to adapt.", "I have a move in mind.",
            "Careful, there is a lot going on here.", "Solid choice. Your turn.",
            "I am not out of ideas yet.", "Time to make this interesting."
    };
    private static final String[] ALEX_DIALOGUE = {
            "Bold move. I like it.", "Let us try something unexpected.",
            "I spotted a chance.", "You have my attention.",
            "I am changing up the plan.", "That is a clever one!",
            "I will find a way through.", "Show me what you have got.",
            "This board is getting exciting.", "I am ready for the next challenge."
    };
    private static final String[] TUYE_DIALOGUE = {
            "Tuye, Lord Rabbit of Fabulous Beast, approves.", "A royal hop from your fluffy ruler.",
            "The burrow sees your clever plan.", "Tuye springs toward victory!",
            "Careful, these paws are quick, fluffball.", "The rabbit lord has a whisker of a plan.",
            "One hop closer to the crown.", "The burrow watches every square.",
            "A brave move! Tuye is impressed.", "Hop to it, challenger!"
    };
    private static final String[] WITTY_ALIEN_DIALOGUE = {
            "Greetings, Earth chess enthusiast.", "My analysis suggests a dramatic move.",
            "Your strategy is delightfully terrestrial.", "Initiating suspiciously clever maneuver.",
            "The galaxy is watching this opening.", "I consulted the cosmic pawns.",
            "Unexpected move detected. Fascinating.", "Behold interstellar tactical brilliance.",
            "Your turn, carbon-based competitor.", "This position is out of this world."
    };
    private static final Map<String, Identifier> textureIds = new ConcurrentHashMap<>();
    private static final Set<String> requestedTextures = ConcurrentHashMap.newKeySet();
    private static final Map<String, int[]> textureSizes = new ConcurrentHashMap<>();
    private static ChessBoardScreen current;
    private static double backgroundOffsetX;
    private static double backgroundOffsetY;
    private static long lastBackgroundUpdate;

    private final Screen parent;
    private final boolean multiplayer;
    private final String botId;
    private final boolean humanWhite;
    private final int difficulty;
    private final String matchId;
    private final String opponentLabel;
    private String whitePlayerId = "";
    private String blackPlayerId = "";
    private String whitePlayerName = "";
    private String blackPlayerName = "";
    private boolean spectating;
    private String spectatorNames = "";
    private String opponentPiecePreset = "8qetl";
    private final List<String> matchChat = new ArrayList<>();
    private final List<ChessGame.Move> localMoveHistory = new ArrayList<>();
    private final List<ChessGame.Move> premoves = new ArrayList<>();
    private final List<BoardArrow> arrows = new ArrayList<>();
    private TextFieldWidget matchChatField;
    private ChessGame game;
    private boolean executingPremove;
    private int promotionFrom = -1;
    private int promotionTo = -1;
    private int selectedSquare = -1;
    private int arrowFrom = -1;
    private int arrowTo = -1;
    private int dragArrowCorner = -1;
    private boolean dragArrowIsL;
    private double arrowLastDeltaX;
    private double arrowLastDeltaY;
    private int dragFrom = -1;
    private int dragButton = -1;
    private double dragX;
    private double dragY;
    private int boardX;
    private int boardY;
    private int squareSize;
    private long moveAnimationStarted;
    private ChessGame.Move animatedMove;
    private char animatedPiece;
    private EndMode endMode;
    private long endAnimationStarted;
    private String endMessage = "";
    private String eloMessage = "";
    private String tierProgressMessage = "";
    private String promotedFromTier = "";
    private String promotedToTier = "";
    private long tierPromotionStartedAt;
    private String pgnOverride = "";
    private String resultNotice = "";
    private String pendingRematchName = "";
    private String botSpeech = "";
    private long botSpeechUntil;
    private long matchDurationSeconds;
    private long whiteMillisRemaining;
    private long blackMillisRemaining;
    private long clockUpdatedAt;
    private boolean clockWhiteToMove = true;
    private boolean botThinking;
    private long botTurnToken;
    private int humanMoveCount;
    private int humanBestMoveCount;
    private int botMoveCount;
    private int botBestMoveCount;
    private int[] moveQualityCounts = new int[ChessGame.MoveQuality.values().length];
    private long localGameStartedAt;

    ChessBoardScreen(Screen parent, ChessGame game, boolean multiplayer, boolean humanWhite,
                     int difficulty, String matchId, String opponentLabel) {
        super(Text.literal("Chess Match"));
        this.parent = parent;
        this.game = game;
        this.multiplayer = multiplayer;
        this.botId = multiplayer ? "cpu" : ChessPreferences.get("bot.selected", "steve");
        this.humanWhite = humanWhite;
        this.difficulty = difficulty;
        this.matchId = matchId;
        this.opponentLabel = opponentLabel;
        localGameStartedAt = System.currentTimeMillis();
        if (!multiplayer) {
            long clock = localClockMillis();
            updateClock(clock, clock, game.isWhiteToMove());
        }
    }

    static void openMultiplayer(String matchId, String snapshot, boolean humanWhite, String whiteName,
                                String blackName, String whitePlayerId, String blackPlayerId,
                                long whiteMillis, long blackMillis) {
        Screen home = new ChessHomeScreen(null, true);
        ChessGame game = ChessGame.fromSnapshot(snapshot);
        ChessBoardScreen screen = new ChessBoardScreen(home, game, true, humanWhite, 3, matchId,
                whiteName + " vs " + blackName);
        screen.whitePlayerId = whitePlayerId;
        screen.blackPlayerId = blackPlayerId;
        screen.whitePlayerName = whiteName;
        screen.blackPlayerName = blackName;
        screen.updateClock(whiteMillis, blackMillis, game.isWhiteToMove());
        MinecraftClient.getInstance().setScreen(screen);
    }

    static void openSpectator(String matchId, String snapshot, String whitePlayerId, String blackPlayerId,
                              String whiteName, String blackName, long whiteMillis, long blackMillis) {
        ChessGame game = ChessGame.fromSnapshot(snapshot);
        ChessBoardScreen screen = new ChessBoardScreen(null, game, true, true, 3, matchId,
                whiteName + " vs " + blackName);
        screen.spectating = true;
        screen.whitePlayerId = whitePlayerId;
        screen.blackPlayerId = blackPlayerId;
        screen.whitePlayerName = whiteName;
        screen.blackPlayerName = blackName;
        screen.updateClock(whiteMillis, blackMillis, game.isWhiteToMove());
        MinecraftClient.getInstance().setScreen(screen);
    }

    static void receiveSpectators(String id, String names) {
        if (current != null && current.multiplayer && current.matchId.equals(id)) current.spectatorNames = names;
    }

    static void receiveState(String id, String snapshot, long whiteMillis, long blackMillis) {
        if (current != null && current.multiplayer && current.matchId.equals(id) && current.endMode == null) {
            current.sync(snapshot);
            current.updateClock(whiteMillis, blackMillis, current.game.isWhiteToMove());
        }
    }

    static void receiveClock(String id, long whiteMillis, long blackMillis, boolean whiteToMove) {
        if (current != null && current.multiplayer && current.matchId.equals(id) && current.endMode == null) {
            current.updateClock(whiteMillis, blackMillis, whiteToMove);
        }
    }

    static void receiveMatchChat(String id, String name, String text) {
        if (current == null || !current.multiplayer || !current.matchId.equals(id)) return;
        current.matchChat.add(name + ": " + text);
        while (current.matchChat.size() > 5) current.matchChat.remove(0);
    }

    static void receiveEnd(String id, String snapshot, String result, String whiteElo, String blackElo,
                           String pgn, long durationSeconds, String whiteMoveQuality, String blackMoveQuality) {
        if (current == null || !current.multiplayer || !current.matchId.equals(id)) return;
        current.sync(snapshot);
        current.pgnOverride = pgn;
        current.matchDurationSeconds = durationSeconds;
        if (!current.spectating) {
            current.moveQualityCounts = decodeMoveQualityCounts(current.humanWhite ? whiteMoveQuality : blackMoveQuality);
        }
        String score = current.humanWhite ? whiteElo : blackElo;
        current.eloMessage = "Your Elo: " + score;
        if (result.equals("DRAW")) current.finish(EndMode.DRAW, "Draw");
        else {
            boolean won = result.equals(current.humanWhite ? "WHITE" : "BLACK");
            current.finish(won ? EndMode.WIN : EndMode.LOSS, won ? "Victory" : "Defeat");
        }
    }

    static void receiveTierResult(String id, String winnerId, String previousTier, String currentTier, String xpText) {
        if (current == null || !current.multiplayer || !current.matchId.equals(id)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.getUuid().toString().equals(winnerId)) return;
        current.tierProgressMessage = "Tier XP +25 · " + xpText + "/100";
        if (!previousTier.equals(currentTier)) {
            current.promotedFromTier = previousTier;
            current.promotedToTier = currentTier;
            current.tierPromotionStartedAt = System.currentTimeMillis();
        }
    }

    static void receiveRematchRequest(String id, String name) {
        if (current == null || !current.multiplayer || !current.matchId.equals(id) || current.endMode == null) return;
        current.pendingRematchName = name;
        current.resultNotice = name + " wants a rematch. Click Rematch to accept.";
    }

    static void receiveRematchStatus(String status) {
        if (current != null && current.multiplayer && current.endMode != null) current.resultNotice = status;
    }

    @Override
    protected void init() {
        super.init();
        current = this;
        if (!spectating) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Resign"), button -> resign())
                    .dimensions(width - 112, 8, 100, 22).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Menu"), button -> close())
                .dimensions(12, 8, 72, 22).build());
        if (multiplayer && !spectating) {
            matchChatField = addDrawableChild(new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                    width / 2 - 145, height - 25, 230, 20, Text.literal("Chat")));
            matchChatField.setMaxLength(160);
            addDrawableChild(ButtonWidget.builder(Text.literal("Send"), button -> sendMatchChat())
                    .dimensions(width / 2 + 91, height - 25, 54, 20).build());
            ChessClient.send("COSMETICS|" + matchId + "|" + ChessPreferences.getPiecePreset());
        }
        if (!multiplayer && !humanWhite && !game.isGameOver()) runBotTurn();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        long now = System.currentTimeMillis();
        drawCustomBackground(context, width, height, mouseX, mouseY);
        layoutBoard();
        double endProgress = endMode == null ? 0 : Math.min(1.0, (now - endAnimationStarted) / (double) END_ANIMATION_MS);
        int alpha = endMode == null ? 255 : (int) ((1.0 - endProgress) * 255);
        int boardFall = endMode == EndMode.WIN ? (int) (endProgress * height) : 0;
        drawBoard(context, now, alpha, boardFall, endMode == EndMode.LOSS ? endProgress : 0);
        drawMoveHistory(context);
        if (multiplayer) {
            ChessPlayerHeads.draw(context, whitePlayerId, boardX + 4, boardY - 38, 18);
            ChessPlayerHeads.draw(context, blackPlayerId, boardX + squareSize * 8 - 22, boardY - 38, 18);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(whitePlayerName),
                    boardX + 26, boardY - 32, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(blackPlayerName),
                    boardX + squareSize * 8 - 100, boardY - 32, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
        }
        if (dragButton == 0 && dragFrom >= 0) {
            char draggedPiece = game.pieceAt(dragFrom);
            if (draggedPiece != 0 && draggedPiece != '.') {
                drawPiece(context, draggedPiece, (int) dragX, (int) dragY, 255);
            }
        }
        if (promotionFrom >= 0) drawPromotionChoices(context);
        if (!multiplayer && endMode == null && displayClock(game.isWhiteToMove(), now) == 0) {
            game.forceResult(game.isWhiteToMove() ? "BLACK" : "WHITE");
            finishLocal();
        }
        long whiteClock = displayClock(true, now);
        long blackClock = displayClock(false, now);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("White " + formatClock(whiteClock)), width / 2 - 62, boardY - 18,
                whiteClock <= 30_000 ? 0xFFFF4D4D : ChessPreferences.getColor("ui.text", 0xFFE2E9E5));
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Black " + formatClock(blackClock)), width / 2 + 62, boardY - 18,
                blackClock <= 30_000 ? 0xFFFF4D4D : ChessPreferences.getColor("ui.text", 0xFFE2E9E5));

        String turnText;
        if (endMode != null) turnText = endMessage;
        else if (spectating) turnText = (game.isWhiteToMove() ? "White" : "Black") + " to move · live view";
        else if (multiplayer) turnText = game.isWhiteToMove() == humanWhite ? "Your move" : "Opponent's move";
        else turnText = game.isWhiteToMove() == humanWhite ? "Your move" : "Bot is thinking";
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(opponentLabel + "   •   " + turnText), width / 2, height - (multiplayer ? 54 : 30),
                endMode == null ? ChessPreferences.getColor("name.color", 0xFFE2E9E5)
                        : withAlpha(0xFFFFFFFF, alpha));
        if (!eloMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(eloMessage),
                    width / 2, height - (multiplayer ? 39 : 15), withAlpha(0xFF75D6A2, alpha));
        }
        if (multiplayer) {
            if (boardX >= 150) {
                int panelWidth = boardX - 18;
                context.fill(8, 42, 8 + panelWidth, 42 + Math.min(150, height - 100),
                        withAlpha(ChessPreferences.getColor("ui.panel", 0xFF1D2A30), 170));
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("MATCH CHAT"),
                        14, 48, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
                if (!spectatorNames.isBlank()) {
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                            Text.literal("◉ " + spectatorNames), 14, 63, ChessPreferences.getColor("ui.accent", 0xFF9EC8B2));
                }
                int chatY = spectatorNames.isBlank() ? 66 : 78;
                for (String line : matchChat) {
                    if (chatY > height - 82) break;
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(line),
                            14, chatY, ChessPreferences.getColor("ui.text", 0xFFE2E9E5));
                    chatY += 12;
                }
            } else {
                int chatY = height - 115;
                for (String line : matchChat) {
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(line),
                            12, chatY, ChessPreferences.getColor("ui.text", 0xFFE2E9E5));
                    chatY += 12;
                }
            }
        }
        super.render(context, mouseX, mouseY, deltaTicks);
        if (!multiplayer && !botSpeech.isEmpty() && now < botSpeechUntil) {
            if (boardX >= 150) {
                context.fill(8, 42, boardX - 10, 110,
                        withAlpha(ChessPreferences.getColor("ui.panel", 0xFF1D2A30), 170));
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(botName()), 14, 48, ChessPreferences.getColor("ui.accent", 0xFFE6C56D));
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(botSpeech), 14, 64, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
            } else {
                int bubbleWidth = Math.min(300, width - 12);
                int bubbleX = (width - bubbleWidth) / 2;
                int bubbleY = Math.max(28, boardY + squareSize * 8 - 31);
                context.fill(bubbleX, bubbleY, bubbleX + bubbleWidth, bubbleY + 23,
                        withAlpha(ChessPreferences.getColor("ui.panel", 0xFF101820), 233));
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(botName() + ": " + botSpeech), width / 2, bubbleY + 7,
                        ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
            }
        }
        if (endMode != null) {
            drawResults(context, now);
        }
    }

    private void drawResults(DrawContext context, long now) {
        int panelWidth = Math.max(260, Math.min(360, width - 20));
        int panelHeight = 216;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        context.fill(0, 0, width, height, 0xB8101820);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                ChessPreferences.getColor("ui.panel", 0xFF1D2A30));
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 3,
                ChessPreferences.getColor("ui.accent", 0xFF63B78B));
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(endMessage),
                width / 2, panelY + 14, endMode == EndMode.WIN ? 0xFF75D6A2 : 0xFFE9F2EC);
        String stats = multiplayer
                ? "Match time: " + formatDuration(matchDurationSeconds)
                : "Player accuracy: " + accuracy(humanBestMoveCount, humanMoveCount)
                + "  •  Bot accuracy: " + accuracy(botBestMoveCount, botMoveCount);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(stats),
                width / 2, panelY + 42, 0xFFE2E9E5);
        String history = multiplayer ? "Move history is ready to export as PGN."
                : "Move history: " + game.moveCount() + " plies";
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(history),
                width / 2, panelY + 62, 0xFFC6D2CD);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Brilliant " + qualityCount(ChessGame.MoveQuality.BRILLIANT)
                        + "   Great " + qualityCount(ChessGame.MoveQuality.GREAT)
                        + "   Good " + qualityCount(ChessGame.MoveQuality.GOOD)),
                width / 2, panelY + 82, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Mistakes " + qualityCount(ChessGame.MoveQuality.MISTAKE)
                        + "   Blunders " + qualityCount(ChessGame.MoveQuality.BLUNDER)),
                width / 2, panelY + 97, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
        if (!pendingRematchName.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(pendingRematchName + " wants a rematch."), width / 2, panelY + 113, 0xFF75D6A2);
        }
        if (!multiplayer && !botSpeech.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(botName() + ": " + botSpeech), width / 2, panelY + 113, 0xFF75D6A2);
        }
        if (!eloMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(eloMessage),
                    width / 2, panelY + 129, 0xFF75D6A2);
        }
        if (!resultNotice.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(resultNotice),
                    width / 2, panelY + 145, 0xFFE6C56D);
        }
        if (!tierProgressMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(tierProgressMessage), width / 2, panelY + 161, 0xFF75D6A2);
        }
        String[] labels = {"Continue", "Rematch", "Export PGN", "Chess.com"};
        int buttonWidth = (panelWidth - 24) / labels.length;
        int buttonY = panelY + 176;
        for (int i = 0; i < labels.length; i++) {
            int buttonX = panelX + 6 + i * (buttonWidth + 4);
            context.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + 22,
                    ChessPreferences.getColor("ui.accent", 0xFF344A42));
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(labels[i]),
                    buttonX + buttonWidth / 2, buttonY + 7, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
        }
        drawTierPromotion(context, now);
    }

    private void drawTierPromotion(DrawContext context, long now) {
        if (promotedToTier.isEmpty()) return;
        long elapsed = now - tierPromotionStartedAt;
        if (elapsed >= 3600L) return;
        double progress = Math.min(1.0, elapsed / 520.0);
        progress = progress * progress * (3.0 - 2.0 * progress);
        int alpha = (int) Math.round(255 * Math.min(1.0, (3600L - elapsed) / 500.0));
        int panelWidth = Math.min(width - 24, 310);
        int panelX = (width - panelWidth) / 2;
        int panelY = 12 - (int) Math.round((1.0 - progress) * 28.0);
        int color = ChessTiers.color(promotedToTier);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 64, withAlpha(0xFF11191D, alpha));
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 3, withAlpha(color, alpha));
        for (int i = 0; i < 7; i++) {
            int sparkleX = panelX + 8 + (int) ((elapsed / 24L + i * 43L) % Math.max(1, panelWidth - 16));
            context.fill(sparkleX, panelY + 4, sparkleX + 2, panelY + 6, withAlpha(color, alpha));
        }
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("TIER PROMOTION"), width / 2, panelY + 12, withAlpha(0xFFF2F3EF, alpha));
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(promotedFromTier + "  →  " + promotedToTier), width / 2, panelY + 34,
                withAlpha(color, alpha));
    }

    private void drawMoveHistory(DrawContext context) {
        if (width < 520) return;
        int x = boardX + squareSize * 8 + 12;
        int y = boardY;
        int panelWidth = width - x - 12;
        int panelHeight = squareSize * 8;
        if (panelWidth < 130 || panelHeight < 50) return;
        context.fill(x, y, x + panelWidth, y + panelHeight,
                withAlpha(ChessPreferences.getColor("ui.panel", 0xFF1D2A30), 220));
        context.fill(x, y, x + panelWidth, y + 2, ChessPreferences.getColor("ui.accent", 0xFF63B78B));
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("MOVE HISTORY"),
                x + 8, y + 8, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
        List<String> moves = game.sanMoves();
        int rows = (panelHeight - 32) / 18;
        int totalRows = (moves.size() + 1) / 2;
        int firstRow = Math.max(0, totalRows - rows);
        for (int row = firstRow; row < totalRows; row++) {
            int rowY = y + 27 + (row - firstRow) * 18;
            int whiteIndex = row * 2;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal((row + 1) + "."), x + 7, rowY + 4, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
            drawHistoryMove(context, moves.get(whiteIndex), true, x + 31, rowY);
            if (whiteIndex + 1 < moves.size()) {
                drawHistoryMove(context, moves.get(whiteIndex + 1), false, x + panelWidth / 2, rowY);
            }
        }
    }

    private void drawHistoryMove(DrawContext context, String notation, boolean white, int x, int y) {
        char kind = notation.startsWith("O-") ? 'K' : notation.isEmpty() ? 'P' : notation.charAt(0);
        if ("KQRBN".indexOf(kind) < 0) kind = 'P';
        char piece = white ? kind : Character.toLowerCase(kind);
        drawPiece(context, piece, x + 7, y + 9, 255, 13);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(notation),
                x + 16, y + 4, ChessPreferences.getColor("ui.text", 0xFFE9F2EC));
    }

    private String accuracy(int bestMoves, int totalMoves) {
        return totalMoves == 0 ? "—" : Math.round(bestMoves * 100.0 / totalMoves) + "%";
    }

    private String formatDuration(long seconds) {
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private int qualityCount(ChessGame.MoveQuality quality) {
        return moveQualityCounts[quality.ordinal()];
    }

    private static int[] decodeMoveQualityCounts(String encoded) {
        int[] counts = new int[ChessGame.MoveQuality.values().length];
        if (encoded == null || encoded.isBlank()) return counts;
        String[] values = encoded.split(",", -1);
        for (int i = 0; i < Math.min(values.length, counts.length); i++) {
            try {
                counts[i] = Math.max(0, Integer.parseInt(values[i]));
            } catch (NumberFormatException ignored) {
            }
        }
        return counts;
    }

    private boolean handleResultsClick(Click click) {
        if (click.button() != 0) return true;
        int panelWidth = Math.max(260, Math.min(360, width - 20));
        int panelHeight = 216;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        int buttonWidth = (panelWidth - 24) / 4;
        int buttonY = panelY + 176;
        if (click.y() < buttonY || click.y() > buttonY + 22) return true;
        double buttonOffset = click.x() - (panelX + 6);
        if (buttonOffset < 0 || buttonOffset >= panelWidth - 12) return true;
        int buttonIndex = (int) (buttonOffset / (buttonWidth + 4));
        if (buttonIndex < 0 || buttonIndex >= 4) return true;
        if (buttonOffset % (buttonWidth + 4) > buttonWidth) return true;
        switch (buttonIndex) {
            case 0 -> returnToParent();
            case 1 -> {
                if (multiplayer) {
                    ChessClient.send("REMATCH|" + matchId);
                    resultNotice = "Rematch request sent.";
                } else {
                    resetLocalRematch();
                }
            }
            case 2 -> exportPgn();
            case 3 -> openChessCom();
            default -> {
            }
        }
        return true;
    }

    private String pgn() {
        if (multiplayer && !pgnOverride.isEmpty()) return pgnOverride;
        return humanWhite ? game.pgn("Player", botName()) : game.pgn(botName(), "Player");
    }

    private void exportPgn() {
        String pgn = pgn();
        MinecraftClient.getInstance().keyboard.setClipboard(pgn);
        java.nio.file.Path file = java.nio.file.Path.of(System.getProperty("user.dir"), "config", "kodarichess",
                "exports", "chess-" + System.currentTimeMillis() + ".pgn");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, pgn, StandardCharsets.UTF_8);
            resultNotice = "PGN copied and saved as " + file.getFileName();
        } catch (IOException exception) {
            resultNotice = "PGN copied to clipboard; file export failed.";
        }
    }

    private void openChessCom() {
        exportPgn();
        try {
            net.minecraft.util.Util.getOperatingSystem().open("https://www.chess.com/analysis");
            resultNotice = "PGN copied. Paste it into Chess.com analysis.";
        } catch (RuntimeException exception) {
            resultNotice = "PGN copied. Open Chess.com analysis in your browser.";
        }
    }

    private void resetLocalRematch() {
        game = new ChessGame();
        humanMoveCount = 0;
        humanBestMoveCount = 0;
        botMoveCount = 0;
        botBestMoveCount = 0;
        endMode = null;
        moveQualityCounts = new int[ChessGame.MoveQuality.values().length];
        endMessage = "";
        eloMessage = "";
        pgnOverride = "";
        resultNotice = "";
        pendingRematchName = "";
        localGameStartedAt = System.currentTimeMillis();
        selectedSquare = -1;
        long clock = localClockMillis();
        updateClock(clock, clock, game.isWhiteToMove());
        if (!humanWhite) runBotTurn();
    }

    private void layoutBoard() {
        int historyWidth = width >= 520 ? 180 : 0;
        int availableWidth = Math.max(160, width - 40 - historyWidth);
        int availableHeight = Math.max(160, height - 112);
        squareSize = Math.max(20, Math.min(64, Math.min(availableWidth, availableHeight) / 8));
        int boardSize = squareSize * 8;
        boardX = (width - historyWidth - boardSize) / 2;
        boardY = (height - boardSize) / 2 + 4;
    }

    private void drawBoard(DrawContext context, long now, int alpha, int boardFall, double lossProgress) {
        boolean[] legalTargets = new boolean[64];
        if (selectedSquare >= 0 && game.isWhiteToMove() == humanWhite) {
            for (ChessGame.Move move : game.legalMovesFor(selectedSquare)) legalTargets[move.to()] = true;
        }
        for (int visualY = 0; visualY < 8; visualY++) {
            for (int visualX = 0; visualX < 8; visualX++) {
                int square = squareAt(visualX, visualY);
                int x = boardX + visualX * squareSize;
                int y = boardY + visualY * squareSize + boardFall;
                boolean light = (((square % 8) + square / 8) & 1) == 1;
                int color = ChessPreferences.getColor(light ? "board.light" : "board.dark",
                        light ? 0xFFEEE9DF : 0xFF56816F);
                context.fill(x, y, x + squareSize, y + squareSize, withAlpha(color, alpha));
                if ((square == selectedSquare) || isLastMoveSquare(square)) {
                    int highlight = square == selectedSquare
                            ? withAlpha(ChessPreferences.getColor("ui.accent", 0xFF76C893), 136)
                            : withAlpha(ChessPreferences.getColor("ui.accent", 0xFF7BC6A0), 85);
                    context.fill(x, y, x + squareSize, y + squareSize, withAlpha(highlight, alpha));
                }
                for (ChessGame.Move premove : premoves) {
                    if (square == premove.from() || square == premove.to()) {
                        context.fill(x + 2, y + 2, x + squareSize - 2, y + squareSize - 2,
                                withAlpha(ChessPreferences.getColor("ui.accent", 0xFF7DCFFF), alpha));
                        break;
                    }
                }
                if (legalTargets[square]) {
                    int dot = Math.max(5, squareSize / 7);
                    int centerX = x + squareSize / 2;
                    int centerY = y + squareSize / 2;
                    context.fill(centerX - dot, centerY - dot, centerX + dot, centerY + dot,
                            withAlpha(0x99404442, alpha));
                }
                char piece = game.pieceAt(square);
                boolean animatedTarget = animatedMove != null && square == animatedMove.to()
                        && now - moveAnimationStarted < MOVE_ANIMATION_MS;
                boolean draggedSource = dragButton == 0 && square == dragFrom;
                if (piece != 0 && piece != '.' && !animatedTarget && !draggedSource) {
                    int pieceFall = endMode == EndMode.LOSS
                            ? (int) (lossProgress * (height + ((square * 37) % 120))) : 0;
                    drawPiece(context, piece, x + squareSize / 2,
                            y + squareSize / 2 + pieceFall, alpha);
                    if (ChessPreferences.getBoolean("piece.names", false)) {
                        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                                Text.literal(pieceName(piece)), x + squareSize / 2, y + 2 + pieceFall,
                                withAlpha(ChessPreferences.getColor("name.color", 0xFFE2E9E5), alpha));
                    }
                }
                if (visualX == 0) {
                    String rank = Integer.toString(humanWhite ? 8 - visualY : visualY + 1);
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(rank),
                            x + 2, y + 2, withAlpha(light ? 0xFF56816F : 0xFFEEE9DF, alpha));
                }
                if (visualY == 7) {
                    char file = (char) ('a' + (humanWhite ? visualX : 7 - visualX));
                    context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                            Text.literal(Character.toString(file)), x + squareSize - 9,
                            y + squareSize - 12, withAlpha(light ? 0xFF56816F : 0xFFEEE9DF, alpha));
                }
            }
        }
        if (animatedMove != null && now - moveAnimationStarted < MOVE_ANIMATION_MS) {
            drawAnimatedPiece(context, now, boardFall, alpha);
        }
        for (BoardArrow arrow : arrows) {
            drawArrow(context, arrow.from(), arrow.to(), withAlpha(0xCC38A6E8, alpha), boardFall, arrow.corner());
        }
        if (arrowFrom >= 0 && arrowTo >= 0) {
            drawArrow(context, arrowFrom, arrowTo, withAlpha(0xCC38A6E8, alpha), boardFall,
                    dragArrowIsL ? dragArrowCorner : -1);
        }
        if (endMode == EndMode.LOSS && lossProgress > 0.08) drawCracks(context, alpha, boardFall);
    }

    private void drawPiece(DrawContext context, char piece, int centerX, int centerY, int alpha) {
        drawPiece(context, piece, centerX, centerY, alpha, Math.max(14, squareSize - 4));
    }

    private void drawPiece(DrawContext context, char piece, int centerX, int centerY, int alpha, int pieceSize) {
        boolean whitePiece = Character.isUpperCase(piece);
        String preset = multiplayer && whitePiece != humanWhite
                ? opponentPiecePreset : ChessPreferences.getPiecePreset();
        String textureUrl = ChessPreferences.getPieceImageUrl(preset, whitePiece, piece);
        Identifier texture = requestRemoteTexture(textureUrl);
        int[] textureSize = textureSizes.get(textureUrl);
        if (texture != null && textureSize != null) {
            int left = centerX - pieceSize / 2;
            int top = centerY - pieceSize / 2;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, left, top, 0, 0, pieceSize, pieceSize,
                    textureSize[0], textureSize[1], textureSize[0], textureSize[1], withAlpha(0xFFFFFFFF, alpha));
            return;
        }
        String glyph = switch (Character.toUpperCase(piece)) {
            case 'K' -> Character.isUpperCase(piece) ? "♔" : "♚";
            case 'Q' -> Character.isUpperCase(piece) ? "♕" : "♛";
            case 'R' -> Character.isUpperCase(piece) ? "♖" : "♜";
            case 'B' -> Character.isUpperCase(piece) ? "♗" : "♝";
            case 'N' -> Character.isUpperCase(piece) ? "♘" : "♞";
            default -> Character.isUpperCase(piece) ? "♙" : "♟";
        };
        int color = Character.isUpperCase(piece) ? 0xFFF9F8F2 : 0xFF22282B;
        int top = centerY - pieceSize / 2;
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(glyph),
                centerX, top, withAlpha(color, alpha));
    }

    public static void drawCustomBackground(DrawContext context, int width, int height, int mouseX, int mouseY) {
        if (!ChessPreferences.getBoolean("background.transparent", false)) {
            context.fill(0, 0, width, height, ChessPreferences.getColor("background.color", 0xFF101820));
        }
        boolean parallax = ChessPreferences.getBoolean("background.parallax", false);
        if (parallax) {
            long now = System.nanoTime();
            double elapsed = lastBackgroundUpdate == 0 ? 0
                    : Math.min(0.1, (now - lastBackgroundUpdate) / 1_000_000_000.0);
            double easing = 1.0 - Math.exp(-elapsed * 8.0);
            double targetX = (mouseX / (double) width - 0.5) * 16.0;
            double targetY = (mouseY / (double) height - 0.5) * 16.0;
            backgroundOffsetX += (targetX - backgroundOffsetX) * easing;
            backgroundOffsetY += (targetY - backgroundOffsetY) * easing;
            lastBackgroundUpdate = now;
        } else {
            backgroundOffsetX = 0;
            backgroundOffsetY = 0;
            lastBackgroundUpdate = 0;
        }
        String imageUrl = ChessPreferences.get("background.image", "").trim();
        if (imageUrl.isEmpty()) return;
        Identifier texture = requestRemoteTexture(imageUrl);
        int[] size = textureSizes.get(imageUrl);
        if (texture != null && size != null) {
            int margin = parallax ? 10 : 0;
            int x = parallax ? -margin + (int) Math.round(backgroundOffsetX) : 0;
            int y = parallax ? -margin + (int) Math.round(backgroundOffsetY) : 0;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width + margin * 2,
                    height + margin * 2, size[0], size[1], size[0], size[1], 0xFFFFFFFF);
        }
    }

    static void receiveCosmetics(String id, String preset) {
        if (current == null || !current.multiplayer || !current.matchId.equals(id)) return;
        if (ChessPreferences.isPiecePreset(preset)) current.opponentPiecePreset = preset;
    }

    private void sendMatchChat() {
        if (matchChatField == null) return;
        String text = matchChatField.getText().trim();
        if (text.isEmpty()) return;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        ChessClient.send("MATCH_CHAT|" + matchId + "|" + encoded);
        matchChatField.setText("");
    }

    static Identifier requestRemoteTexture(String url) {
        boolean localImage = ChessPreferences.isLocalImage(url);
        if (!localImage && !ChessPreferences.isWebUrl(url)) return null;
        Identifier texture = textureIds.computeIfAbsent(url, value -> Identifier.of("kodarichess", "remote/"
                + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "")));
        if (!requestedTextures.add(url)) return texture;
        CompletableFuture.runAsync(() -> {
            try {
                byte[] imageBytes;
                Path cacheFile = localImage ? null : Path.of(System.getProperty("user.dir"), "config",
                        "kodarichess", "cache", UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)) + ".img");
                boolean loadedFromCache = false;
                if (localImage) {
                    imageBytes = ChessPreferences.loadImage(url);
                } else {
                    imageBytes = null;
                    try {
                        if (Files.isRegularFile(cacheFile) && Files.size(cacheFile) <= 4 * 1024 * 1024) {
                            imageBytes = Files.readAllBytes(cacheFile);
                            loadedFromCache = imageBytes.length > 0;
                        }
                    } catch (IOException ignored) {
                    }
                    if (imageBytes == null || imageBytes.length == 0) imageBytes = downloadRemoteImage(url);
                }
                if (imageBytes.length == 0 || imageBytes.length > 4 * 1024 * 1024) {
                    requestedTextures.remove(url);
                    return;
                }
                NativeImage image;
                try {
                    image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                } catch (IOException | IllegalArgumentException invalidCache) {
                    if (localImage || !loadedFromCache) throw invalidCache;
                    Files.deleteIfExists(cacheFile);
                    imageBytes = downloadRemoteImage(url);
                    if (imageBytes.length == 0 || imageBytes.length > 4 * 1024 * 1024) {
                        requestedTextures.remove(url);
                        return;
                    }
                    image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                    loadedFromCache = false;
                }
                if (!localImage && !loadedFromCache) {
                    Files.createDirectories(cacheFile.getParent());
                    Files.write(cacheFile, imageBytes);
                }
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                NativeImage decodedImage = image;
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    try {
                        client.getTextureManager().registerTexture(texture,
                                new NativeImageBackedTexture(() -> "Kodari Chess remote image", decodedImage));
                        textureSizes.put(url, new int[]{imageWidth, imageHeight});
                    } catch (RuntimeException exception) {
                        requestedTextures.remove(url);
                    }
                });
            } catch (IOException | IllegalArgumentException ignored) {
                requestedTextures.remove(url);
            }
        });
        return texture;
    }

    private static byte[] downloadRemoteImage(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try (InputStream input = connection.getInputStream()) {
            return input.readNBytes(4 * 1024 * 1024 + 1);
        }
    }

    static int[] textureSize(String source) {
        return textureSizes.get(source);
    }

    private static String pieceName(char piece) {
        return switch (Character.toUpperCase(piece)) {
            case 'K' -> "King";
            case 'Q' -> "Queen";
            case 'R' -> "Rook";
            case 'B' -> "Bishop";
            case 'N' -> "Knight";
            default -> "Pawn";
        };
    }

    private void drawPromotionChoices(DrawContext context) {
        int x = width / 2 - 88;
        int y = height / 2 - 23;
        context.fill(x - 4, y - 4, x + 176, y + 48, 0xEE182329);
        char[] choices = {'Q', 'R', 'B', 'N'};
        for (int i = 0; i < choices.length; i++) {
            int choiceX = x + i * 43;
            context.fill(choiceX, y, choiceX + 39, y + 40, i % 2 == 0 ? 0xFF344A42 : 0xFF263831);
            char piece = humanWhite ? choices[i] : Character.toLowerCase(choices[i]);
            drawPiece(context, piece, choiceX + 19, y + 20, 255, 34);
        }
    }

    private void drawAnimatedPiece(DrawContext context, long now, int boardFall, int alpha) {
        double progress = Math.min(1.0, (now - moveAnimationStarted) / (double) MOVE_ANIMATION_MS);
        progress = progress * progress * (3 - 2 * progress);
        int fromX = boardX + visualX(animatedMove.from()) * squareSize + squareSize / 2;
        int fromY = boardY + visualY(animatedMove.from()) * squareSize + squareSize / 2 + boardFall;
        int toX = boardX + visualX(animatedMove.to()) * squareSize + squareSize / 2;
        int toY = boardY + visualY(animatedMove.to()) * squareSize + squareSize / 2 + boardFall;
        int x = (int) (fromX + (toX - fromX) * progress);
        int y = (int) (fromY + (toY - fromY) * progress);
        char piece = animatedPiece == 0 ? game.pieceAt(animatedMove.to()) : animatedPiece;
        if (animatedMove.promotion() != 0 && progress >= 0.9) piece = game.pieceAt(animatedMove.to());
        drawPiece(context, piece, x, y, alpha);
    }

    private void drawArrow(DrawContext context, int from, int to, int color, int fall, int corner) {
        if (corner >= 0) {
            int horizontalCorner = (from / 8) * 8 + to % 8;
            int verticalCorner = (to / 8) * 8 + from % 8;
            int horizontalDistance = Math.abs(horizontalCorner % 8 - corner % 8)
                    + Math.abs(horizontalCorner / 8 - corner / 8);
            int verticalDistance = Math.abs(verticalCorner % 8 - corner % 8)
                    + Math.abs(verticalCorner / 8 - corner / 8);
            int elbow = horizontalDistance <= verticalDistance ? horizontalCorner : verticalCorner;
            drawArrowLine(context, from, elbow, color, fall, false);
            drawArrowLine(context, elbow, to, color, fall, true);
        } else {
            drawArrowLine(context, from, to, color, fall, true);
        }
    }

    private void drawArrowLine(DrawContext context, int from, int to, int color, int fall, boolean endpoint) {
        int x1 = boardX + visualX(from) * squareSize + squareSize / 2;
        int y1 = boardY + visualY(from) * squareSize + squareSize / 2 + fall;
        int x2 = boardX + visualX(to) * squareSize + squareSize / 2;
        int y2 = boardY + visualY(to) * squareSize + squareSize / 2 + fall;
        int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 4);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) (x1 + (x2 - x1) * t);
            int y = (int) (y1 + (y2 - y1) * t);
            context.fill(x - 2, y - 2, x + 3, y + 3, color);
        }
        if (endpoint) context.fill(x2 - 7, y2 - 7, x2 + 8, y2 + 8, color);
    }

    private void drawCracks(DrawContext context, int alpha, int fall) {
        int centerX = boardX + squareSize * 4;
        int centerY = boardY + squareSize * 4 + fall;
        drawLine(context, centerX, centerY, centerX - squareSize * 2, centerY - squareSize * 3,
                withAlpha(0xFF592F39, alpha));
        drawLine(context, centerX, centerY, centerX + squareSize * 3, centerY - squareSize,
                withAlpha(0xFF592F39, alpha));
        drawLine(context, centerX, centerY, centerX + squareSize, centerY + squareSize * 3,
                withAlpha(0xFF592F39, alpha));
        drawLine(context, centerX, centerY, centerX - squareSize * 3, centerY + squareSize,
                withAlpha(0xFF592F39, alpha));
    }

    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 3);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) (x1 + (x2 - x1) * t);
            int y = (int) (y1 + (y2 - y1) * t);
            context.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void handleSquare(int square) {
        if (square < 0 || endMode != null) return;
        boolean ownTurn = game.isWhiteToMove() == humanWhite;
        char piece = game.pieceAt(square);
        if (!ownTurn) {
            if (piece != '.' && piece != 0 && Character.isUpperCase(piece) == humanWhite) {
                premoves.clear();
                selectedSquare = square;
            } else if (selectedSquare >= 0) {
                premoves.clear();
                premoves.add(new ChessGame.Move(selectedSquare, square, 'Q'));
                selectedSquare = -1;
            }
            return;
        }
        if (selectedSquare < 0) {
            if (piece != '.' && piece != 0 && Character.isUpperCase(piece) == humanWhite) selectedSquare = square;
            return;
        }
        if (piece != '.' && piece != 0 && Character.isUpperCase(piece) == humanWhite) {
            selectedSquare = square;
            return;
        }
        playMove(selectedSquare, square);
        selectedSquare = -1;
    }

    private void playMove(int from, int to) {
        char piece = game.pieceAt(from);
        if (Character.toUpperCase(piece) == 'P' && (to / 8 == 0 || to / 8 == 7)
                && game.legalMovesFor(from).stream().anyMatch(move -> move.to() == to && move.promotion() != 0)) {
            promotionFrom = from;
            promotionTo = to;
            return;
        }
        playMove(from, to, 'Q');
    }

    private void playMove(int from, int to, char promotion) {
        if (!multiplayer && endMode == null && displayClock(game.isWhiteToMove(), System.currentTimeMillis()) == 0) {
            game.forceResult(game.isWhiteToMove() ? "BLACK" : "WHITE");
            finishLocal();
            return;
        }
        boolean humanMove = game.isWhiteToMove() == humanWhite;
        ChessGame.MoveAnalysis analysis = !multiplayer && humanMove ? game.analyzeMove(from, to, promotion, 1) : null;
        ChessGame.Move accuracyMove = analysis == null ? null : analysis.bestMove();
        ChessGame before = game.copy();
        char movingPiece = game.pieceAt(from);
        if (!game.move(from, to, promotion)) return;
        if (!multiplayer) {
            localMoveHistory.add(game.lastMove());
            recordAccuracy(humanMove, game.lastMove(), accuracyMove);
            if (humanMove && analysis != null) moveQualityCounts[analysis.quality().ordinal()]++;
            updateLocalClock();
            speakAfterMove(before, game.lastMove(), game);
        }
        playMoveSound(before, game.lastMove(), game);
        animate(game.lastMove(), movingPiece);
        promotionFrom = -1;
        promotionTo = -1;
        if (!executingPremove) premoves.clear();
        if (multiplayer) {
            ChessClient.send("MOVE|" + matchId + "|" + from + "|" + to + "|" + promotion);
            return;
        }
        if (game.isGameOver()) {
            finishLocal();
        } else if (game.isWhiteToMove() != humanWhite) {
            runBotTurn();
        }
    }

    private void runBotTurn() {
        if (game.isGameOver() || game.isWhiteToMove() == humanWhite || botThinking) return;
        botThinking = true;
        long token = ++botTurnToken;
        long startedAt = System.currentTimeMillis();
        ChessGame position = game.copy();
        int selectedDifficulty = difficulty;
        ChessGame.Move openingMove = openingMoveForBot(position);
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture.supplyAsync(() -> new BotTurn(openingMove != null ? openingMove : position.bestMove(selectedDifficulty),
                position.bestMove(Math.min(2, selectedDifficulty)))).whenComplete((turn, failure) -> {
            long delay = Math.max(0, 900 - (System.currentTimeMillis() - startedAt));
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> client.execute(() -> {
                if (current != this || token != botTurnToken) return;
                botThinking = false;
                if (endMode != null || multiplayer || game.isGameOver() || game.isWhiteToMove() == humanWhite) return;
                if (failure != null || turn == null || turn.move() == null) {
                    finishLocal();
                    return;
                }
                ChessGame before = game.copy();
                char piece = game.pieceAt(turn.move().from());
                if (!game.move(turn.move().from(), turn.move().to(), turn.move().promotion())) return;
                localMoveHistory.add(game.lastMove());
                recordAccuracy(false, game.lastMove(), turn.accuracyMove());
                updateLocalClock();
                playMoveSound(before, game.lastMove(), game);
                speakAfterMove(before, game.lastMove(), game);
                animate(game.lastMove(), piece);
                if (game.isWhiteToMove() == humanWhite) arrows.clear();
                if (game.isGameOver()) finishLocal();
                else tryPremove();
            }));
        });
    }

    private void tryPremove() {
        if (game.isGameOver() || game.isWhiteToMove() != humanWhite) return;
        while (!premoves.isEmpty()) {
            ChessGame.Move queued = premoves.remove(0);
            boolean legal = game.legalMoves().stream().anyMatch(move -> move.from() == queued.from()
                    && move.to() == queued.to()
                    && (move.promotion() == 0 || move.promotion() == queued.promotion()));
            if (!legal) continue;
            executingPremove = true;
            try {
                playMove(queued.from(), queued.to(), queued.promotion());
            } finally {
                executingPremove = false;
            }
            return;
        }
    }

    private ChessGame.Move openingMoveForBot(ChessGame position) {
        if (botId.equals("tuye")) {
            if (!humanWhite) return openingMoveFromOwnLine(position, TUYE_KING_INDIAN_ATTACK, true);
            return openingMoveFromFullLine(position, TUYE_NAJDORF);
        }
        if (botId.equals("witty_alien") && !humanWhite) {
            return openingMoveFromFullLine(position, WITTY_ALIEN_GAMBIT);
        }
        return null;
    }

    private ChessGame.Move openingMoveFromFullLine(ChessGame position, List<ChessGame.Move> line) {
        int ply = localMoveHistory.size();
        if (ply >= line.size()) return null;
        for (int i = 0; i < ply; i++) {
            if (!sameMove(localMoveHistory.get(i), line.get(i))) return null;
        }
        ChessGame.Move next = line.get(ply);
        return isLegalOpeningMove(position, next) ? next : null;
    }

    private ChessGame.Move openingMoveFromOwnLine(ChessGame position, List<ChessGame.Move> line, boolean botWhite) {
        int botMoveNumber = 0;
        for (int i = 0; i < localMoveHistory.size(); i++) {
            if ((i % 2 == 0) != botWhite) continue;
            if (botMoveNumber >= line.size() || !sameMove(localMoveHistory.get(i), line.get(botMoveNumber))) return null;
            botMoveNumber++;
        }
        if (botMoveNumber >= line.size()) return null;
        ChessGame.Move next = line.get(botMoveNumber);
        return isLegalOpeningMove(position, next) ? next : null;
    }

    private boolean isLegalOpeningMove(ChessGame position, ChessGame.Move move) {
        return position.legalMoves().stream().anyMatch(legal -> sameMove(legal, move));
    }

    private static ChessGame.Move openingMove(String from, String to) {
        return new ChessGame.Move(openingSquare(from), openingSquare(to), (char) 0);
    }

    private static int openingSquare(String square) {
        return (square.charAt(1) - '1') * 8 + square.charAt(0) - 'a';
    }

    private void animate(ChessGame.Move move, char piece) {
        animatedMove = move;
        animatedPiece = piece;
        moveAnimationStarted = System.currentTimeMillis();
    }

    private void updateClock(long whiteMillis, long blackMillis, boolean whiteToMove) {
        whiteMillisRemaining = Math.max(0, whiteMillis);
        blackMillisRemaining = Math.max(0, blackMillis);
        clockWhiteToMove = whiteToMove;
        clockUpdatedAt = System.currentTimeMillis();
    }

    private long localClockMillis() {
        int minutes = ChessPreferences.getInt("timer.minutes", 5);
        if (minutes != 1 && minutes != 2 && minutes != 5 && minutes != 8 && minutes != 10) minutes = 5;
        return minutes * 60_000L;
    }

    private void updateLocalClock() {
        long now = System.currentTimeMillis();
        long white = displayClock(true, now);
        long black = displayClock(false, now);
        updateClock(white, black, game.isWhiteToMove());
    }

    private void recordAccuracy(boolean human, ChessGame.Move move, ChessGame.Move recommended) {
        if (human) {
            humanMoveCount++;
            if (sameMove(move, recommended)) humanBestMoveCount++;
        } else {
            botMoveCount++;
            if (sameMove(move, recommended)) botBestMoveCount++;
        }
    }

    private boolean sameMove(ChessGame.Move first, ChessGame.Move second) {
        return first != null && second != null && first.from() == second.from() && first.to() == second.to()
                && first.promotion() == second.promotion();
    }

    private long displayClock(boolean white, long now) {
        long remaining = white ? whiteMillisRemaining : blackMillisRemaining;
        if (endMode == null && clockWhiteToMove == white) remaining -= Math.max(0, now - clockUpdatedAt);
        return Math.max(0, remaining);
    }

    private static String formatClock(long milliseconds) {
        long seconds = (milliseconds + 999) / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void sync(String snapshot) {
        boolean changed = !game.snapshot().equals(snapshot);
        boolean wasHumanTurn = game.isWhiteToMove() == humanWhite;
        ChessGame next = ChessGame.fromSnapshot(snapshot);
        ChessGame.Move last = next.lastMove();
        if (changed && last != null) {
            char piece = game.pieceAt(last.from());
            if (piece == 0 || piece == '.') piece = next.pieceAt(last.to());
            animate(last, piece);
            playMoveSound(game, last, next);
        }
        game = next;
        if (changed && !wasHumanTurn && game.isWhiteToMove() == humanWhite) arrows.clear();
        selectedSquare = -1;
        tryPremove();
    }

    private void finishLocal() {
        if (game.result().equals("DRAW")) {
            sayBot(isTuyeBot() ? "A draw. The burrow calls for a rematch." : "A draw? Let's go again.");
            finish(EndMode.DRAW, "Draw");
        }
        else {
            boolean won = game.result().equals(humanWhite ? "WHITE" : "BLACK");
            boolean checkmate = game.inCheck(game.isWhiteToMove()) && game.legalMoves().isEmpty();
            if (won) sayBot(isTuyeBot() ? "Tuye has been dethroned! Well played, fluffball."
                    : checkmate ? "Checkmate? You got me. GG!" : "You got me. Nice one!");
            else sayBot(isTuyeBot() ? "Victory for Tuye, Lord of the burrow!"
                    : checkmate ? "Checkmate! " + botName() + " takes the win."
                    : "Time's up—" + botName() + " takes the win.");
            finish(won ? EndMode.WIN : EndMode.LOSS, won ? "Victory" : "Defeat");
        }
    }

    private String botName() {
        return switch (botId) {
            case "alex" -> "Alex";
            case "tuye" -> "Tuye";
            case "witty_alien" -> "Witty Alien";
            case "cpu" -> "CPU";
            default -> "Steve";
        };
    }

    private boolean isTuyeBot() {
        return botId.equals("tuye");
    }

    private void speakAfterMove(ChessGame before, ChessGame.Move move, ChessGame after) {
        if (after.isGameOver()) return;
        if (botId.equals("cpu")) return;
        if (isWittyAlienGambitBroken()) {
            sayBot("YOU RUINED MY ALIEN GAMBIT! I WILL PUNISH YOU FOR THAT, TERRESTRIAL!");
            return;
        }
        if (isWittyAlienGambitCompleted()) {
            sayBot("THE ALIEN GAMBIT IS LIVE! WITNESS COSMIC BRILLIANCE, EARTHLING!");
            return;
        }
        char piece = before.pieceAt(move.from());
        boolean capture = before.pieceAt(move.to()) != '.'
                || (Character.toUpperCase(piece) == 'P' && move.from() % 8 != move.to() % 8);
        if (move.promotion() != 0) {
            sayBot(isTuyeBot() ? "A promotion? Tuye approves this hop." : "Promoted! Now that's an upgrade.");
        } else if (Character.toUpperCase(piece) == 'K' && Math.abs(move.to() - move.from()) == 2) {
            sayBot(isTuyeBot() ? "A royal hop into the castle." : "Castle secured. Nice and safe.");
        } else if (after.inCheck(after.isWhiteToMove())) {
            sayBot(isTuyeBot() ? "Check! Protect your king, fluffball." : "Check! Keep an eye on your king.");
        } else if (capture) {
            sayBot(isTuyeBot() ? "Yoink! One piece for the burrow." : "Got that one. Your move.");
        } else if (after.isWhiteToMove() == humanWhite) {
            sayBot(isTuyeBot() ? chooseLine("Hop—your turn!", "The burrow is watching.", "A clever move, fluffball.")
                    : chooseLine(botDialogueLines()));
        } else {
            sayBot(isTuyeBot() ? chooseLine("The Lord Rabbit hops again.", "By the burrow, that's a move.", "Tuye has a plan.")
                    : chooseLine(botDialogueLines()));
        }
    }

    private String[] botDialogueLines() {
        return switch (botId) {
            case "alex" -> ALEX_DIALOGUE;
            case "tuye" -> TUYE_DIALOGUE;
            case "witty_alien" -> WITTY_ALIEN_DIALOGUE;
            default -> STEVE_DIALOGUE;
        };
    }

    private boolean isWittyAlienGambitBroken() {
        if (!botId.equals("witty_alien") || humanWhite || localMoveHistory.isEmpty()) return false;
        int index = localMoveHistory.size() - 1;
        if ((index & 1) == 0 || index >= WITTY_ALIEN_GAMBIT.size()) return false;
        for (int i = 0; i < index; i++) {
            if (!sameMove(localMoveHistory.get(i), WITTY_ALIEN_GAMBIT.get(i))) return false;
        }
        return !sameMove(localMoveHistory.get(index), WITTY_ALIEN_GAMBIT.get(index));
    }

    private boolean isWittyAlienGambitCompleted() {
        if (!botId.equals("witty_alien") || humanWhite
                || localMoveHistory.size() != WITTY_ALIEN_GAMBIT.size()) return false;
        for (int i = 0; i < WITTY_ALIEN_GAMBIT.size(); i++) {
            if (!sameMove(localMoveHistory.get(i), WITTY_ALIEN_GAMBIT.get(i))) return false;
        }
        return true;
    }

    private String chooseLine(String... lines) {
        return lines[(int) (Math.random() * lines.length)];
    }

    private void sayBot(String speech) {
        if (botId.equals("cpu")) return;
        botSpeech = speech;
        botSpeechUntil = System.currentTimeMillis() + 4000L;
    }

    private void finish(EndMode mode, String text) {
        if (endMode != null) return;
        botTurnToken++;
        botThinking = false;
        endMode = mode;
        endMessage = text;
        endAnimationStarted = System.currentTimeMillis();
        if (mode == EndMode.WIN) playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        else if (mode == EndMode.LOSS) playSound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.7f, 0.85f);
        selectedSquare = -1;
        premoves.clear();
        promotionFrom = -1;
        promotionTo = -1;
    }

    private void playMoveSound(ChessGame before, ChessGame.Move move, ChessGame after) {
        char piece = before.pieceAt(move.from());
        char destination = before.pieceAt(move.to());
        boolean pawn = Character.toUpperCase(piece) == 'P';
        boolean capture = (destination != 0 && destination != '.')
                || (pawn && move.from() % 8 != move.to() % 8);
        boolean castle = Character.toUpperCase(piece) == 'K' && Math.abs(move.to() - move.from()) == 2;
        if (capture) playSound(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.55f, 1.0f);
        if (castle) playSound(SoundEvents.BLOCK_IRON_DOOR_CLOSE, 0.45f, 0.9f);
        if (move.promotion() != 0) playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.2f);
        if (!capture && !castle && move.promotion() == 0) {
            playSound(SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, 0.35f, 1.25f);
        }
        if (after.inCheck(after.isWhiteToMove())) playSound(SoundEvents.BLOCK_BELL_USE, 0.65f, 1.1f);
    }

    private static void playSound(SoundEvent sound, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.playSound(sound, volume, pitch);
    }

    private void resign() {
        if (endMode != null) return;
        if (multiplayer) {
            ChessClient.send("RESIGN|" + matchId);
            endMessage = "Resigning…";
        } else {
            game.forceResult(humanWhite ? "BLACK" : "WHITE");
            sayBot(isTuyeBot() ? "Tuye wins by resignation. Hop next time!" : botName() + " wins by resignation. Nice try!");
            finish(EndMode.LOSS, "Resigned");
        }
    }

    private boolean isLastMoveSquare(int square) {
        ChessGame.Move last = game.lastMove();
        return last != null && (last.from() == square || last.to() == square);
    }

    private int squareAt(int visualX, int visualY) {
        int file = humanWhite ? visualX : 7 - visualX;
        int rank = humanWhite ? 7 - visualY : visualY;
        return rank * 8 + file;
    }

    private int visualX(int square) {
        int file = square % 8;
        return humanWhite ? file : 7 - file;
    }

    private int visualY(int square) {
        int rank = square / 8;
        return humanWhite ? 7 - rank : rank;
    }

    private int mouseSquare(double mouseX, double mouseY) {
        int visualX = (int) ((mouseX - boardX) / squareSize);
        int visualY = (int) ((mouseY - boardY) / squareSize);
        if (visualX < 0 || visualX > 7 || visualY < 0 || visualY > 7) return -1;
        return squareAt(visualX, visualY);
    }

    private static int withAlpha(int color, int alpha) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (endMode != null) return handleResultsClick(click);
        if (spectating) return super.mouseClicked(click, doubled);
        if (promotionFrom >= 0) {
            if (click.button() == 0) {
                int x = width / 2 - 88;
                int y = height / 2 - 23;
                if (click.x() >= x && click.x() < x + 172 && click.y() >= y && click.y() < y + 40) {
                    char[] choices = {'Q', 'R', 'B', 'N'};
                    int choice = Math.min(3, (int) (click.x() - x) / 43);
                    playMove(promotionFrom, promotionTo, choices[choice]);
                }
            }
            return true;
        }
        int square = mouseSquare(click.x(), click.y());
        if (square < 0) return super.mouseClicked(click, doubled);
        if (click.button() == 1) {
            dragButton = 1;
            dragFrom = square;
            dragX = click.x();
            dragY = click.y();
            arrowFrom = square;
            arrowTo = square;
            dragArrowCorner = -1;
            dragArrowIsL = false;
            arrowLastDeltaX = 0;
            arrowLastDeltaY = 0;
            return true;
        }
        if (click.button() == 0) {
            arrows.clear();
            arrowFrom = arrowTo = -1;
            char piece = game.pieceAt(square);
            if (piece != 0 && piece != '.' && Character.isUpperCase(piece) == humanWhite) {
                dragButton = 0;
                dragFrom = square;
                dragX = click.x();
                dragY = click.y();
                handleSquare(square);
            } else {
                dragButton = -1;
                dragFrom = -1;
                handleSquare(square);
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (spectating) return super.mouseDragged(click, offsetX, offsetY);
        if (dragButton != click.button()) return super.mouseDragged(click, offsetX, offsetY);
        double deltaX = click.x() - dragX;
        double deltaY = click.y() - dragY;
        if (dragButton == 1) {
            double previousLength = Math.hypot(arrowLastDeltaX, arrowLastDeltaY);
            double currentLength = Math.hypot(deltaX, deltaY);
            if (previousLength > 0 && currentLength > 0
                    && deltaX * arrowLastDeltaX + deltaY * arrowLastDeltaY < previousLength * currentLength * 0.65) {
                dragArrowCorner = arrowTo;
                dragArrowIsL = true;
            }
            if (currentLength > 0) {
                arrowLastDeltaX = deltaX;
                arrowLastDeltaY = deltaY;
            }
        }
        dragX = click.x();
        dragY = click.y();
        int square = mouseSquare(click.x(), click.y());
        if (dragButton == 1 && square >= 0) arrowTo = square;
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (spectating) return super.mouseReleased(click);
        if (click.button() != dragButton) return super.mouseReleased(click);
        int from = dragFrom;
        int to = mouseSquare(click.x(), click.y());
        int releasedButton = dragButton;
        dragButton = -1;
        dragFrom = -1;
        if (releasedButton == 1) {
            if (to >= 0 && to != from) arrows.add(new BoardArrow(from, to, dragArrowIsL ? dragArrowCorner : -1));
            arrowFrom = arrowTo = -1;
            dragArrowCorner = -1;
            dragArrowIsL = false;
            return true;
        }
        if (to >= 0 && to != from) {
            if (game.isWhiteToMove() == humanWhite) {
                playMove(from, to);
                selectedSquare = -1;
            } else {
                handleSquare(to);
            }
        }
        return true;
    }

    @Override
    public void close() {
        botTurnToken++;
        botThinking = false;
        if (spectating) {
            ChessClient.send("SPECTATE_LEAVE|" + matchId);
        } else if (endMode == null) {
            if (multiplayer) ChessClient.send("RESIGN|" + matchId);
            else {
                game.forceResult(humanWhite ? "BLACK" : "WHITE");
            }
        }
        returnToParent();
    }

    private void returnToParent() {
        if (current == this) current = null;
        ChessClient.send("PLAYER_STATE|MENU");
        MinecraftClient.getInstance().setScreen(parent);
    }
}