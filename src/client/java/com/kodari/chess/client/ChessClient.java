package com.kodari.chess.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.kodari.chess.ChessPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ChessClient implements ClientModInitializer {
    private static boolean tutorialShown;

    @Override
    public void onInitializeClient() {
        ChessHoloClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(ChessPayload.ID,
                (payload, context) -> receive(payload.data(), context.client()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> send("PRESENCE"));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof GameMenuScreen)) return;
            boolean inWorld = screen instanceof GameMenuScreen;
            ButtonWidget chessButton = ButtonWidget.builder(Text.literal("♟ Chess"), button ->
                            client.setScreen(new ChessHomeScreen(screen, inWorld)))
                    .dimensions(scaledWidth - 112, scaledHeight - 30, 104, 20)
                    .build();
            Screens.getButtons(screen).add(chessButton);
            if (!tutorialShown && !ChessPreferences.getBoolean("onboarding.complete", false)) {
                tutorialShown = true;
                client.setScreen(new ChessTutorialScreen(screen, inWorld));
            }
        });
    }

    public static void send(String message) {
        if (serverSupportsChess()) {
            ClientPlayNetworking.send(new ChessPayload(message));
        }
    }

    public static boolean serverSupportsChess() {
        return MinecraftClient.getInstance().getNetworkHandler() != null
                && ClientPlayNetworking.canSend(ChessPayload.ID);
    }

    private static void receive(String message, MinecraftClient client) {
        String[] parts = message.split("\\|", 10);
        switch (parts[0]) {
            case "LOBBY" -> ChessLobbyScreen.updateLobby(parts.length > 1 ? parts[1] : "");
            case "WAIT" -> ChessLobbyScreen.waiting(parts.length > 1 ? parts[1] : "");
            case "LEFT" -> ChessLobbyScreen.waiting("");
            case "RANKED_WAIT" -> ChessLobbyScreen.rankedWaiting(parts.length > 1 ? parts[1] : "Searching for a ranked opponent...");
            case "RANKED_STATUS" -> ChessLobbyScreen.rankedStatus(parts.length > 1 ? decode(parts[1]) : "");
            case "RANKED_CANCEL" -> ChessLobbyScreen.rankedCancelled(parts.length > 1 ? decode(parts[1]) : "Ranked timer ban cancelled.");
            case "TIMER_BAN" -> {
                if (parts.length > 3) ChessLobbyScreen.timerBan(parts[1], parts[2], parts[3]);
            }
            case "CHAT" -> {
                if (parts.length > 3) ChessLobbyScreen.addChat(decode(parts[2]), decode(parts[3]));
            }
            case "MATCH_CHAT" -> {
                if (parts.length > 4) ChessBoardScreen.receiveMatchChat(parts[1], decode(parts[3]), decode(parts[4]));
            }
            case "PLAYERS" -> ChessLobbyScreen.updatePlayers(parts.length > 1 ? parts[1] : "");
            case "DUEL_REQUEST" -> {
                if (parts.length > 2) ChessLobbyScreen.duelRequest(parts[1], decode(parts[2]));
            }
            case "DUEL_STATUS" -> ChessLobbyScreen.duelStatus(parts.length > 1 ? decode(parts[1]) : "");
            case "SPECTATE_STATUS" -> ChessLobbyScreen.spectateStatus(parts.length > 1 ? decode(parts[1]) : "");
            case "HOLO_ACTION" -> openHoloAction(parts.length > 1 ? parts[1] : "");
            case "REMATCH_REQUEST" -> {
                if (parts.length > 2) ChessBoardScreen.receiveRematchRequest(parts[1], decode(parts[2]));
            }
            case "REMATCH_STATUS" -> ChessBoardScreen.receiveRematchStatus(parts.length > 1 ? decode(parts[1]) : "");
            case "BOT_SECRET_UNLOCKED" -> ChessPreferences.setBoolean("bot.secret.unlocked", true);
            case "START" -> {
                if (parts.length < 9 || client.player == null) return;
                ChessLobbyScreen.clearTimerBan();
                boolean white = client.player.getUuid().toString().equals(parts[2]);
                String whiteName = decode(parts[4]);
                String blackName = decode(parts[5]);
                ChessBoardScreen.openMultiplayer(parts[1], parts[6], white, whiteName, blackName,
                        parts[2], parts[3], parseClock(parts[7]), parseClock(parts[8]));
            }
            case "SPECTATE_START" -> {
                if (parts.length < 9) return;
                ChessBoardScreen.openSpectator(parts[1], parts[6], parts[2], parts[3], decode(parts[4]),
                        decode(parts[5]), parseClock(parts[7]), parseClock(parts[8]));
            }
            case "SPECTATORS" -> {
                if (parts.length > 1) ChessBoardScreen.receiveSpectators(parts[1], parts.length > 2 ? decode(parts[2]) : "");
            }
            case "STATE" -> {
                if (parts.length > 4) ChessBoardScreen.receiveState(parts[1], parts[2],
                        parseClock(parts[3]), parseClock(parts[4]));
            }
            case "CLOCK" -> {
                if (parts.length > 4) ChessBoardScreen.receiveClock(parts[1], parseClock(parts[2]),
                        parseClock(parts[3]), Boolean.parseBoolean(parts[4]));
            }
            case "COSMETICS" -> {
                if (parts.length > 2) ChessBoardScreen.receiveCosmetics(parts[1], parts[2]);
            }
            case "ROSTER" -> ChessProfileScreen.updateRoster(parts.length > 1 ? parts[1] : "");
            case "PROFILE" -> ChessProfileScreen.receiveProfile(parts);
            case "TIER_RESULT" -> {
                if (parts.length > 5) ChessBoardScreen.receiveTierResult(parts[1], parts[2], parts[3], parts[4], parts[5]);
            }
            case "END" -> {
                if (parts.length > 7) ChessBoardScreen.receiveEnd(parts[1], parts[2], parts[3], parts[4], parts[5],
                        decode(parts[7]), parseSeconds(parts[6]), parts.length > 8 ? parts[8] : "",
                        parts.length > 9 ? parts[9] : "");
            }
            default -> {
            }
        }
    }

    private static void openHoloAction(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        switch (action) {
            case "play:menu" -> client.setScreen(new ChessHomeScreen(null, true));
            case "play:multiplayer" -> client.setScreen(new ChessLobbyScreen(null));
            case "play:bot" -> client.setScreen(ChessHomeScreen.botSelection(null, true));
            case "play:profile", "gui:profile", "gui:tier" -> client.setScreen(new ChessProfileScreen(null));
            case "play:customize" -> client.setScreen(new ChessCustomizationScreen(null));
            case "gui:playground" -> client.setScreen(ChessLobbyScreen.playground());
            case "gui:spectate" -> client.setScreen(new ChessLobbyScreen(null, true));
            default -> {
            }
        }
    }

    private static long parseClock(String value) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 300_000L;
        }
    }

    private static long parseSeconds(String value) {
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}