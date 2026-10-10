package com.kodari.chess.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ChessLobbyScreen extends Screen {
    private record LobbyEntry(String id, String playerId, String name, int minutes) {
    }

    private record OnlineEntry(String id, String name, int rating, boolean inMatch) {
    }

    private static final int[] CLOCK_PRESETS = {1, 2, 5, 8, 10};
    private static final List<LobbyEntry> ENTRIES = new ArrayList<>();
    private static final List<OnlineEntry> ONLINE_PLAYERS = new ArrayList<>();
    private static final List<String> CHAT = new ArrayList<>();
    private static final List<Integer> TIMER_BAN_CHOICES = new ArrayList<>();
    private static String waitingId = "";
    private static String message = "Browse open matches or create one.";
    private static String timerBanId = "";
    private static String timerBanTurnId = "";
    private static String incomingDuelId = "";
    private static String incomingDuelName = "";
    private static String duelNotice = "";
    private static int selectedMinutes = validClock(ChessPreferences.getInt("timer.minutes", 5));
    private final Screen parent;
    private final boolean spectatePlayers;
    private TextFieldWidget chatField;
    private TextFieldWidget searchField;
    private ButtonWidget timerButton;
    private boolean showDuelPlayers;

    public ChessLobbyScreen(Screen parent) {
        this(parent, false);
    }

    ChessLobbyScreen(Screen parent, boolean spectatePlayers) {
        super(Text.literal("Chess Multiplayer"));
        this.parent = parent;
        this.spectatePlayers = spectatePlayers;
        this.showDuelPlayers = spectatePlayers;
    }

    static ChessLobbyScreen playground() {
        message = "Shared multiplayer playground: create or join a match to play together.";
        return new ChessLobbyScreen(null);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Create"), button -> ChessClient.send("CREATE|" + selectedMinutes))
                .dimensions(centerX - 190, 42, 60, 22).build());
        timerButton = addDrawableChild(ButtonWidget.builder(Text.literal(selectedMinutes + " min"), button -> cycleClock())
                .dimensions(centerX - 126, 42, 48, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ranked"), button -> ChessClient.send("RANKED"))
                .dimensions(centerX - 73, 42, 57, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), button -> {
                    ChessClient.send("LIST");
                    ChessClient.send("ONLINE");
                })
                .dimensions(centerX - 10, 42, 55, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(spectatePlayers ? "Matches" : "Duels"), button -> {
                    showDuelPlayers = !showDuelPlayers;
                    if (showDuelPlayers) ChessClient.send("ONLINE");
                })
                .dimensions(centerX + 50, 42, 55, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> leave())
                .dimensions(centerX + 110, 42, 50, 22).build());
        searchField = addDrawableChild(new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                centerX - 190, 96, 300, 20, Text.literal("Search duel players")));
        searchField.setMaxLength(32);
        chatField = addDrawableChild(new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                centerX - 190, height - 48, 310, 22, Text.literal("Chat message")));
        chatField.setFocused(false);
        addDrawableChild(ButtonWidget.builder(Text.literal("Send"), button -> sendChat())
                .dimensions(centerX + 125, height - 48, 65, 22).build());
        ChessClient.send("LIST");
        ChessClient.send("ONLINE");
    }

    public static void updateLobby(String data) {
        ENTRIES.clear();
        if (data.isEmpty()) return;
        for (String entry : data.split(";")) {
            String[] fields = entry.split("~", 4);
            if (fields.length >= 3) {
                int minutes = 5;
                if (fields.length == 4) {
                    try {
                        minutes = Integer.parseInt(fields[3]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                ENTRIES.add(new LobbyEntry(fields[0], fields[1], decode(fields[2]), minutes));
            }
        }
    }

    public static void waiting(String id) {
        waitingId = id;
        if (id.isEmpty()) clearTimerBan();
        message = id.isEmpty() ? "Browse open matches or create one." : "Waiting for an opponent. You can chat below.";
    }

    public static void rankedWaiting(String status) {
        message = status;
    }

    public static void rankedStatus(String status) {
        message = status;
    }

    public static void timerBan(String id, String turnId, String choices) {
        timerBanId = id;
        timerBanTurnId = turnId;
        TIMER_BAN_CHOICES.clear();
        for (String choice : choices.split(",")) {
            try {
                TIMER_BAN_CHOICES.add(Integer.parseInt(choice));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static void rankedCancelled(String status) {
        clearTimerBan();
        message = status;
    }

    public static void clearTimerBan() {
        timerBanId = "";
        timerBanTurnId = "";
        TIMER_BAN_CHOICES.clear();
    }

    public static void addChat(String name, String text) {
        CHAT.add(name + ": " + text);
        while (CHAT.size() > 7) CHAT.remove(0);
    }

    public static void updatePlayers(String data) {
        ONLINE_PLAYERS.clear();
        if (data.isEmpty()) return;
        for (String entry : data.split(";")) {
            String[] fields = entry.split("~", 3);
            if (fields.length < 3) continue;
            int rating = 1200;
            try {
                rating = Integer.parseInt(fields[2]);
            } catch (NumberFormatException ignored) {
            }
            ONLINE_PLAYERS.add(new OnlineEntry(fields[0], decode(fields[1]), rating,
                    fields.length > 3 && Boolean.parseBoolean(fields[3])));
        }
    }

    public static void duelRequest(String playerId, String playerName) {
        incomingDuelId = playerId;
        incomingDuelName = playerName;
    }

    public static void duelStatus(String status) {
        duelNotice = status;
    }

    public static void spectateStatus(String status) {
        duelNotice = status;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        ChessBoardScreen.drawCustomBackground(context, width, height, mouseX, mouseY);
        if (!timerBanId.isEmpty()) {
            super.render(context, mouseX, mouseY, deltaTicks);
            drawTimerBan(context);
            return;
        }
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("MULTIPLAYER LOBBY"), width / 2, 14, 0xFFF2F3EF);
        if (incomingDuelId.isEmpty()) {
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(duelNotice.isEmpty() ? message : duelNotice), width / 2, 72, 0xFF9EC8B2);
        } else {
            context.fill(width / 2 - 190, 68, width / 2 + 190, 92, 0xFF344A42);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal("Duel request from " + incomingDuelName), width / 2 - 182, 76, 0xFFE9F2EC);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("ACCEPT"),
                    width / 2 + 75, 76, 0xFF75D6A2);
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("DECLINE"),
                    width / 2 + 132, 76, 0xFFE6C56D);
        }
        if (showDuelPlayers) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(spectatePlayers ? "ACTIVE CHESS MATCHES — click to spectate"
                            : "ONLINE PLAYERS — click to challenge"), width / 2 - 190, 120, 0xFFE9F2EC);
            String ownId = MinecraftClient.getInstance().player == null ? ""
                    : MinecraftClient.getInstance().player.getUuid().toString();
            String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
            int row = 0;
            for (OnlineEntry entry : ONLINE_PLAYERS) {
                if (entry.id().equals(ownId) || !entry.name().toLowerCase(Locale.ROOT).contains(query)
                        || (spectatePlayers ? !entry.inMatch() : entry.inMatch())) continue;
                int y = 139 + row * 20;
                context.fill(width / 2 - 194, y - 3, width / 2 + 194, y + 16, 0xFF1D2A30);
                ChessPlayerHeads.draw(context, entry.id(), width / 2 - 184, y, 14);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(entry.name() + "  •  " + entry.rating() + " Elo"), width / 2 - 165, y, 0xFFE9F2EC);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(spectatePlayers ? "SPECTATE" : "CHALLENGE"),
                        width / 2 + 125, y, 0xFF75D6A2);
                row++;
                if (row >= Math.max(1, (height - 280) / 20)) break;
            }
            if (row == 0) context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(spectatePlayers ? "No active matches. Start a multiplayer match first."
                            : "No matching players are available."), width / 2, 145, 0xFF9BA8A4);
        } else {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("OPEN MATCHES"),
                    width / 2 - 190, 120, 0xFFE9F2EC);
            String ownId = MinecraftClient.getInstance().player == null ? ""
                    : MinecraftClient.getInstance().player.getUuid().toString();
            int row = 0;
            for (LobbyEntry entry : ENTRIES) {
                if (entry.playerId().equals(ownId)) continue;
                int y = 139 + row * 23;
                context.fill(width / 2 - 194, y - 3, width / 2 + 194, y + 18, 0xFF1D2A30);
                ChessPlayerHeads.draw(context, entry.playerId(), width / 2 - 184, y + 1, 14);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal(entry.name() + "  •  " + entry.minutes() + " min"),
                        width / 2 - 165, y + 2, 0xFFE9F2EC);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("JOIN →"),
                        width / 2 + 140, y + 2, 0xFF75D6A2);
                row++;
            }
            if (row == 0) {
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                        Text.literal("No opponents are waiting yet."), width / 2, 150, 0xFF9BA8A4);
            }
        }
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("WAITING ROOM CHAT"),
                width / 2 - 190, height - 137, 0xFFE9F2EC);
        int chatY = height - 119;
        for (String line : CHAT) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(line),
                    width / 2 - 190, chatY, 0xFFC6D2CD);
            chatY += 14;
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!timerBanId.isEmpty()) {
            String playerId = MinecraftClient.getInstance().player == null ? ""
                    : MinecraftClient.getInstance().player.getUuid().toString();
            if (click.button() == 0 && playerId.equals(timerBanTurnId)
                    && click.y() >= timerBanButtonY() && click.y() <= timerBanButtonY() + 26) {
                int buttonWidth = timerBanButtonWidth();
                int gap = 6;
                int startX = (width - TIMER_BAN_CHOICES.size() * buttonWidth
                        - Math.max(0, TIMER_BAN_CHOICES.size() - 1) * gap) / 2;
                for (int i = 0; i < TIMER_BAN_CHOICES.size(); i++) {
                    int left = startX + i * (buttonWidth + gap);
                    if (click.x() >= left && click.x() <= left + buttonWidth) {
                        ChessClient.send("RANKED_BAN|" + timerBanId + "|" + TIMER_BAN_CHOICES.get(i));
                        return true;
                    }
                }
            }
            return true;
        }
        if (!incomingDuelId.isEmpty() && click.button() == 0 && click.y() >= 68 && click.y() <= 92) {
            if (click.x() >= width / 2 + 70 && click.x() < width / 2 + 128) {
                ChessClient.send("DUEL_ACCEPT|" + incomingDuelId);
                incomingDuelId = "";
                return true;
            }
            if (click.x() >= width / 2 + 128 && click.x() <= width / 2 + 190) {
                ChessClient.send("DUEL_DECLINE|" + incomingDuelId);
                incomingDuelId = "";
                return true;
            }
        }
        if (click.button() == 0 && click.x() >= width / 2 - 194 && click.x() <= width / 2 + 194) {
            String ownId = MinecraftClient.getInstance().player == null ? ""
                    : MinecraftClient.getInstance().player.getUuid().toString();
            int row = 0;
            if (showDuelPlayers) {
                String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
                for (OnlineEntry entry : ONLINE_PLAYERS) {
                    if (entry.id().equals(ownId) || !entry.name().toLowerCase(Locale.ROOT).contains(query)
                            || (spectatePlayers ? !entry.inMatch() : entry.inMatch())) continue;
                    int y = 139 + row * 20;
                    if (click.y() >= y - 3 && click.y() <= y + 16) {
                        ChessClient.send(spectatePlayers ? "SPECTATE_PLAYER|" + entry.id()
                                : "DUEL_REQUEST|" + entry.id() + "|" + selectedMinutes);
                        return true;
                    }
                    row++;
                    if (row >= Math.max(1, (height - 280) / 20)) break;
                }
            } else {
                for (LobbyEntry entry : ENTRIES) {
                    if (entry.playerId().equals(ownId)) continue;
                    int y = 139 + row * 23;
                    if (click.y() >= y - 3 && click.y() <= y + 18) {
                        ChessClient.send("JOIN|" + entry.id());
                        return true;
                    }
                    row++;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void drawTimerBan(DrawContext context) {
        int panelWidth = Math.min(width - 20, 440);
        int panelX = (width - panelWidth) / 2;
        int panelY = height / 2 - 78;
        context.fill(0, 0, width, height, 0xC9101820);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 150, 0xFF1D2A30);
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 3, 0xFF63B78B);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("RANKED TIMER BAN"), width / 2, panelY + 17, 0xFFE9F2EC);
        String playerId = MinecraftClient.getInstance().player == null ? ""
                : MinecraftClient.getInstance().player.getUuid().toString();
        boolean yourTurn = playerId.equals(timerBanTurnId);
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal(yourTurn ? "Choose one time control to ban" : "Opponent is choosing a time control to ban"),
                width / 2, panelY + 43, yourTurn ? 0xFF75D6A2 : 0xFFE6C56D);
        int buttonWidth = timerBanButtonWidth();
        int gap = 6;
        int startX = (width - TIMER_BAN_CHOICES.size() * buttonWidth
                - Math.max(0, TIMER_BAN_CHOICES.size() - 1) * gap) / 2;
        for (int i = 0; i < TIMER_BAN_CHOICES.size(); i++) {
            int left = startX + i * (buttonWidth + gap);
            int top = timerBanButtonY();
            context.fill(left, top, left + buttonWidth, top + 26, yourTurn ? 0xFF47775F : 0xFF34423E);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    Text.literal(TIMER_BAN_CHOICES.get(i) + " min"), left + buttonWidth / 2, top + 9, 0xFFE9F2EC);
        }
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("Both players ban alternately; the remaining timer starts the match."),
                width / 2, panelY + 119, 0xFFB9C8C0);
    }

    private int timerBanButtonWidth() {
        int count = Math.max(1, TIMER_BAN_CHOICES.size());
        return Math.max(34, Math.min(64, (width - 32 - (count - 1) * 6) / count));
    }

    private int timerBanButtonY() {
        return height / 2 - 28;
    }

    private void sendChat() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) return;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
        ChessClient.send("CHAT|" + encoded);
        chatField.setText("");
    }

    private void cycleClock() {
        int current = 0;
        while (current < CLOCK_PRESETS.length && CLOCK_PRESETS[current] != selectedMinutes) current++;
        selectedMinutes = CLOCK_PRESETS[(current + 1) % CLOCK_PRESETS.length];
        ChessPreferences.setInt("timer.minutes", selectedMinutes);
        timerButton.setMessage(Text.literal(selectedMinutes + " min"));
    }

    private static int validClock(int minutes) {
        for (int preset : CLOCK_PRESETS) if (preset == minutes) return minutes;
        return 5;
    }

    private void leave() {
        ChessClient.send("LEAVE");
        waitingId = "";
        incomingDuelId = "";
        if (parent == null) MinecraftClient.getInstance().setScreen(null);
        else MinecraftClient.getInstance().setScreen(parent);
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "Unknown player";
        }
    }

    @Override
    public void close() {
        leave();
    }
}