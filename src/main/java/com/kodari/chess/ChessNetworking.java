package com.kodari.chess;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

public final class ChessNetworking {
    private record Waiting(UUID id, ServerPlayerEntity player, int minutes) {
    }

    private record DuelInvite(UUID sender, long expiresAt, int minutes) {
    }

    private record HistoryEntry(long timestamp, String opponent, String result, int ratingChange, int ratingAfter) {
    }

    private static final class TimerBan {
        private final UUID id = UUID.randomUUID();
        private final ServerPlayerEntity first;
        private final ServerPlayerEntity second;
        private final ArrayList<Integer> availableMinutes = new ArrayList<>(List.of(1, 2, 5, 8, 10));
        private int bansMade;

        private TimerBan(ServerPlayerEntity first, ServerPlayerEntity second) {
            this.first = first;
            this.second = second;
        }

        private ServerPlayerEntity currentPlayer() {
            return bansMade % 2 == 0 ? first : second;
        }
    }

    private static final class Profile {
        private int rating = 1200;
        private int wins;
        private int losses;
        private int draws;
        private int tierIndex;
        private int tierXp;
        private String name = "Unknown player";
        private final ArrayList<HistoryEntry> history = new ArrayList<>();
    }

    private static final class Match {
        private final UUID id;
        private final ServerPlayerEntity whitePlayer;
        private final ServerPlayerEntity blackPlayer;
        private final UUID white;
        private final UUID black;
        private final ChessGame game = new ChessGame();
        private final int minutes;
        private final boolean ranked;
        private final int[][] moveQualityCounts = new int[2][ChessGame.MoveQuality.values().length];
        private final long startedAt;
        private long whiteMillisRemaining;
        private long blackMillisRemaining;
        private long clockStartedAt;
        private long lastClockBroadcastAt;
        private boolean clockWhiteToMove = true;
        private final Map<UUID, Integer> cosmeticBytes = new HashMap<>();
        private final Set<UUID> rematchVotes = new HashSet<>();
        private final Map<UUID, ServerPlayerEntity> spectators = new LinkedHashMap<>();
        private long endedAt;

        private Match(UUID id, ServerPlayerEntity whitePlayer, ServerPlayerEntity blackPlayer, int minutes, boolean ranked) {
            this.id = id;
            this.whitePlayer = whitePlayer;
            this.blackPlayer = blackPlayer;
            this.white = whitePlayer.getUuid();
            this.black = blackPlayer.getUuid();
            this.minutes = minutes;
            this.ranked = ranked;
            this.startedAt = System.currentTimeMillis();
            this.whiteMillisRemaining = minutes * 60_000L;
            this.blackMillisRemaining = minutes * 60_000L;
            this.clockStartedAt = this.startedAt;
            this.lastClockBroadcastAt = this.clockStartedAt;
        }
    }

    private static final Map<UUID, Waiting> WAITING = new LinkedHashMap<>();
    private static final ArrayList<ServerPlayerEntity> RANKED_QUEUE = new ArrayList<>();
    private static final Map<UUID, TimerBan> RANKED_BANS_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, ServerPlayerEntity> LOBBY_CLIENTS = new HashMap<>();
    private static final Map<UUID, ServerPlayerEntity> MODDED_CLIENTS = new LinkedHashMap<>();
    private static final Map<UUID, Match> MATCHES_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Match> MATCHES_BY_SPECTATOR = new HashMap<>();
    private static final Map<UUID, Profile> PROFILES = new HashMap<>();
    private static final Map<UUID, DuelInvite> DUEL_INVITES = new HashMap<>();
    private static final Map<UUID, Long> DUEL_REQUEST_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, String> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, Match> FINISHED_MATCHES = new HashMap<>();
    private static Path profileFile;
    private static Path legacyEloFile;

    private ChessNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(ChessPayload.ID, ChessPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChessPayload.ID, ChessPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChessPayload.ID,
                (payload, context) -> handle(context.player(), payload.data()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> disconnect(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(ChessNetworking::loadProfiles);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveProfiles());
        ServerTickEvents.END_SERVER_TICK.register(server -> tickClocks());
    }

    private static void handle(ServerPlayerEntity player, String message) {
        String[] parts = message.split("\\|", 6);
        UUID playerId = player.getUuid();
        switch (parts[0]) {
            case "LIST" -> {
                LOBBY_CLIENTS.put(playerId, player);
                sendLobby(player);
            }
            case "CREATE" -> create(player, parts.length > 1 ? parseMinutes(parts[1]) : 5);
            case "RANKED" -> rankedQueue(player);
            case "RANKED_BAN" -> {
                if (parts.length > 2) rankedBan(player, parts[1], parts[2]);
            }
            case "LEAVE" -> leaveWaiting(player);
            case "CHAT" -> chat(player, parts.length > 1 ? parts[1] : "");
            case "MATCH_CHAT" -> {
                if (parts.length > 2) matchChat(player, parts[1], parts[2]);
            }
            case "SPECTATE_LEAVE" -> {
                if (parts.length > 1) removeSpectator(player, parts[1]);
            }
            case "JOIN" -> {
                if (parts.length > 1) join(player, parts[1]);
            }
            case "MOVE" -> {
                if (parts.length > 4) move(player, parts);
            }
            case "RESIGN" -> resign(player, parts.length > 1 ? parts[1] : "");
            case "REMATCH" -> rematch(player, parts.length > 1 ? parts[1] : "");
            case "COSMETICS" -> {
                if (parts.length > 2) cosmetics(player, parts[1], parts[2]);
            }
            case "PRESENCE" -> registerModdedPlayer(player);
            case "PLAYER_STATE" -> setPlayerState(player, parts.length > 1 ? parts[1] : "");
            case "ROSTER" -> {
                registerModdedPlayer(player);
                sendRoster(player);
            }
            case "PROFILE" -> requestProfile(player, parts.length > 1 ? parts[1] : "");
            case "ONLINE" -> sendOnlinePlayers(player);
            case "SPECTATE_PLAYER" -> {
                if (parts.length > 1) spectatePlayer(player, parts[1]);
            }
            case "DUEL_REQUEST" -> {
                if (parts.length > 1) requestDuel(player, parts[1], parts.length > 2 ? parseMinutes(parts[2]) : 5);
            }
            case "DUEL_ACCEPT" -> {
                if (parts.length > 1) acceptDuel(player, parts[1]);
            }
            case "DUEL_DECLINE" -> {
                if (parts.length > 1) declineDuel(player, parts[1]);
            }
            default -> {
            }
        }
    }

    private static void create(ServerPlayerEntity player, int minutes) {
        UUID playerId = player.getUuid();
        if (MATCHES_BY_PLAYER.containsKey(playerId) || isRankedBusy(playerId)) return;
        LOBBY_CLIENTS.put(playerId, player);
        WAITING.putIfAbsent(playerId, new Waiting(UUID.randomUUID(), player, minutes));
        Waiting waiting = WAITING.get(playerId);
        send(player, "WAIT|" + waiting.id());
        broadcastLobby();
    }

    private static void leaveWaiting(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        WAITING.remove(playerId);
        RANKED_QUEUE.removeIf(candidate -> candidate.getUuid().equals(playerId));
        cancelTimerBan(playerId, "Ranked timer ban cancelled because your opponent left.");
        LOBBY_CLIENTS.remove(playerId);
        send(player, "LEFT");
        broadcastLobby();
    }

    private static void rankedQueue(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        if (MATCHES_BY_PLAYER.containsKey(playerId) || WAITING.containsKey(playerId) || isRankedBusy(playerId)) {
            send(player, "RANKED_STATUS|" + encode("You are already in a match or queue."));
            return;
        }
        LOBBY_CLIENTS.put(playerId, player);
        if (RANKED_QUEUE.isEmpty()) {
            RANKED_QUEUE.add(player);
            send(player, "RANKED_WAIT|Searching for a ranked opponent...");
            return;
        }
        ServerPlayerEntity opponent = RANKED_QUEUE.remove(0);
        if (opponent.getUuid().equals(playerId)) return;
        LOBBY_CLIENTS.put(opponent.getUuid(), opponent);
        beginTimerBan(opponent, player);
    }

    private static boolean isRankedBusy(UUID playerId) {
        if (RANKED_BANS_BY_PLAYER.containsKey(playerId)) return true;
        for (ServerPlayerEntity queued : RANKED_QUEUE) {
            if (queued.getUuid().equals(playerId)) return true;
        }
        return false;
    }

    private static void beginTimerBan(ServerPlayerEntity first, ServerPlayerEntity second) {
        TimerBan timerBan = new TimerBan(first, second);
        RANKED_BANS_BY_PLAYER.put(first.getUuid(), timerBan);
        RANKED_BANS_BY_PLAYER.put(second.getUuid(), timerBan);
        sendTimerBan(timerBan);
    }

    private static void sendTimerBan(TimerBan timerBan) {
        StringBuilder available = new StringBuilder();
        for (int minutes : timerBan.availableMinutes) {
            if (available.length() > 0) available.append(',');
            available.append(minutes);
        }
        String message = "TIMER_BAN|" + timerBan.id + "|" + timerBan.currentPlayer().getUuid() + "|" + available;
        send(timerBan.first, message);
        send(timerBan.second, message);
    }

    private static void rankedBan(ServerPlayerEntity player, String banIdText, String minutesText) {
        TimerBan timerBan = RANKED_BANS_BY_PLAYER.get(player.getUuid());
        if (timerBan == null || !timerBan.id.toString().equals(banIdText)
                || !timerBan.currentPlayer().getUuid().equals(player.getUuid())) return;
        int minutes;
        try {
            minutes = Integer.parseInt(minutesText);
        } catch (NumberFormatException exception) {
            return;
        }
        if (!timerBan.availableMinutes.remove(Integer.valueOf(minutes))) return;
        timerBan.bansMade++;
        if (timerBan.availableMinutes.size() == 1) {
            int selectedMinutes = timerBan.availableMinutes.get(0);
            RANKED_BANS_BY_PLAYER.remove(timerBan.first.getUuid());
            RANKED_BANS_BY_PLAYER.remove(timerBan.second.getUuid());
            startMatch(timerBan.first, timerBan.second, selectedMinutes, true);
        } else {
            sendTimerBan(timerBan);
        }
    }

    private static void cancelTimerBan(UUID playerId, String message) {
        TimerBan timerBan = RANKED_BANS_BY_PLAYER.remove(playerId);
        if (timerBan == null) return;
        ServerPlayerEntity opponent = timerBan.first.getUuid().equals(playerId) ? timerBan.second : timerBan.first;
        RANKED_BANS_BY_PLAYER.remove(opponent.getUuid());
        send(opponent, "RANKED_CANCEL|" + encode(message));
    }

    private static void chat(ServerPlayerEntity player, String encodedMessage) {
        if (!LOBBY_CLIENTS.containsKey(player.getUuid())) return;
        String message;
        try {
            message = new String(Base64.getUrlDecoder().decode(encodedMessage), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return;
        }
        message = clean(message);
        if (message.isBlank()) return;
        String encodedName = encode(player.getStringifiedName());
        String encodedText = encode(message);
        String payload = "CHAT|" + player.getUuid() + "|" + encodedName + "|" + encodedText;
        for (ServerPlayerEntity recipient : new ArrayList<>(LOBBY_CLIENTS.values())) send(recipient, payload);
    }

    private static void matchChat(ServerPlayerEntity player, String matchId, String encodedMessage) {
        Match match = MATCHES_BY_PLAYER.get(player.getUuid());
        if (match == null || !match.id.toString().equals(matchId) || encodedMessage.length() > 1024) return;
        String message;
        try {
            message = new String(Base64.getUrlDecoder().decode(encodedMessage), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return;
        }
        message = clean(message);
        if (message.isBlank()) return;
        if (message.length() > 160) message = message.substring(0, 160);
        broadcast(match, "MATCH_CHAT|" + match.id + "|" + player.getUuid() + "|"
                + encode(player.getStringifiedName()) + "|" + encode(message));
    }

    private static void sendOnlinePlayers(ServerPlayerEntity recipient) {
        StringBuilder players = new StringBuilder("PLAYERS|");
        for (Map.Entry<UUID, ServerPlayerEntity> entry : MODDED_CLIENTS.entrySet()) {
            UUID id = entry.getKey();
            if (id.equals(recipient.getUuid()) || WAITING.containsKey(id) || isRankedBusy(id)) continue;
            if (players.length() > 8) players.append(';');
            Profile profile = profile(id);
            players.append(id).append('~').append(encode(profile.name)).append('~').append(profile.rating)
                    .append('~').append(MATCHES_BY_PLAYER.containsKey(id));
        }
        send(recipient, players.toString());
    }

    private static void requestDuel(ServerPlayerEntity sender, String targetText, int minutes) {
        UUID senderId = sender.getUuid();
        UUID targetId;
        try {
            targetId = UUID.fromString(targetText);
        } catch (IllegalArgumentException exception) {
            sendDuelStatus(sender, "That player is not available.");
            return;
        }
        long now = System.currentTimeMillis();
        if (DUEL_REQUEST_COOLDOWNS.getOrDefault(senderId, 0L) > now) {
            sendDuelStatus(sender, "Wait a few seconds before sending another challenge.");
            return;
        }
        ServerPlayerEntity target = MODDED_CLIENTS.get(targetId);
        if (target == null || senderId.equals(targetId) || MATCHES_BY_PLAYER.containsKey(senderId)
                || MATCHES_BY_PLAYER.containsKey(targetId) || WAITING.containsKey(senderId) || WAITING.containsKey(targetId)
                || isRankedBusy(senderId) || isRankedBusy(targetId)) {
            sendDuelStatus(sender, "That player is not available for a duel.");
            return;
        }
        DuelInvite pending = DUEL_INVITES.get(targetId);
        if (pending != null && pending.expiresAt() > now) {
            sendDuelStatus(sender, "That player already has a pending challenge.");
            return;
        }
        DUEL_REQUEST_COOLDOWNS.put(senderId, now + 10_000L);
        DUEL_INVITES.put(targetId, new DuelInvite(senderId, now + 60_000L, minutes));
        send(target, "DUEL_REQUEST|" + senderId + "|" + encode(sender.getStringifiedName()));
        sendDuelStatus(sender, "Challenge sent to " + target.getStringifiedName() + ".");
    }

    private static void acceptDuel(ServerPlayerEntity recipient, String senderText) {
        UUID senderId;
        try {
            senderId = UUID.fromString(senderText);
        } catch (IllegalArgumentException exception) {
            return;
        }
        UUID recipientId = recipient.getUuid();
        DuelInvite invite = DUEL_INVITES.get(recipientId);
        if (invite == null || !invite.sender().equals(senderId)) return;
        DUEL_INVITES.remove(recipientId);
        ServerPlayerEntity sender = MODDED_CLIENTS.get(senderId);
        if (invite.expiresAt() < System.currentTimeMillis() || sender == null
                || MATCHES_BY_PLAYER.containsKey(senderId) || MATCHES_BY_PLAYER.containsKey(recipientId)
                || WAITING.containsKey(senderId) || WAITING.containsKey(recipientId)
                || isRankedBusy(senderId) || isRankedBusy(recipientId)) {
            sendDuelStatus(recipient, "That challenge has expired or the player is busy.");
            return;
        }
        startMatch(sender, recipient, invite.minutes());
    }

    private static void declineDuel(ServerPlayerEntity recipient, String senderText) {
        UUID senderId;
        try {
            senderId = UUID.fromString(senderText);
        } catch (IllegalArgumentException exception) {
            return;
        }
        DuelInvite invite = DUEL_INVITES.get(recipient.getUuid());
        if (invite == null || !invite.sender().equals(senderId)) return;
        DUEL_INVITES.remove(recipient.getUuid());
        ServerPlayerEntity sender = MODDED_CLIENTS.get(senderId);
        if (sender != null) sendDuelStatus(sender, recipient.getStringifiedName() + " declined the challenge.");
    }

    private static void sendDuelStatus(ServerPlayerEntity player, String status) {
        send(player, "DUEL_STATUS|" + encode(status));
    }

    private static void join(ServerPlayerEntity joiner, String idText) {
        UUID waitingId;
        try {
            waitingId = UUID.fromString(idText);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Waiting waiting = null;
        for (Waiting candidate : WAITING.values()) {
            if (candidate.id().equals(waitingId)) {
                waiting = candidate;
                break;
            }
        }
        if (waiting == null || waiting.player().getUuid().equals(joiner.getUuid())
                || MATCHES_BY_PLAYER.containsKey(joiner.getUuid()) || isRankedBusy(joiner.getUuid())) return;
        WAITING.remove(waiting.player().getUuid());
        LOBBY_CLIENTS.remove(waiting.player().getUuid());
        LOBBY_CLIENTS.remove(joiner.getUuid());
        startMatch(waiting.player(), joiner, waiting.minutes());
    }

    private static void startMatch(ServerPlayerEntity first, ServerPlayerEntity second, int minutes) {
        startMatch(first, second, minutes, false);
    }

    private static void startMatch(ServerPlayerEntity first, ServerPlayerEntity second, int minutes, boolean ranked) {
        boolean firstIsWhite = Math.random() < 0.5;
        ServerPlayerEntity white = firstIsWhite ? first : second;
        ServerPlayerEntity black = firstIsWhite ? second : first;
        LOBBY_CLIENTS.remove(white.getUuid());
        LOBBY_CLIENTS.remove(black.getUuid());
        Match match = new Match(UUID.randomUUID(), white, black, minutes, ranked);
        MATCHES_BY_PLAYER.put(match.white, match);
        MATCHES_BY_PLAYER.put(match.black, match);
        PLAYER_STATES.put(match.white, "On match");
        PLAYER_STATES.put(match.black, "On match");
        updateNameTag(match.whitePlayer);
        updateNameTag(match.blackPlayer);
        int whiteElo = profile(match.white).rating;
        int blackElo = profile(match.black).rating;
        String start = "START|" + match.id + "|" + match.white + "|" + match.black + "|"
                + encode(white.getStringifiedName()) + "|" + encode(black.getStringifiedName()) + "|"
                + match.game.snapshot() + "|" + match.whiteMillisRemaining + "|" + match.blackMillisRemaining;
        send(white, start);
        send(black, start);
        broadcastLobby();
    }

    public static int spectate(ServerCommandSource source, String targetName) {
        ServerPlayerEntity spectator;
        try {
            spectator = source.getPlayerOrThrow();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            source.sendFeedback(() -> Text.literal("Run /spec in Minecraft chat to open a match."), false);
            return 0;
        }
        UUID spectatorId = spectator.getUuid();
        if (!MODDED_CLIENTS.containsKey(spectatorId) || MATCHES_BY_PLAYER.containsKey(spectatorId)) {
            source.sendFeedback(() -> Text.literal("You need the mod and must not be playing a match to spectate."), false);
            return 0;
        }
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        Match match = target == null ? null : MATCHES_BY_PLAYER.get(target.getUuid());
        if (match == null) {
            source.sendFeedback(() -> Text.literal("That player is not in an active chess match."), false);
            return 0;
        }
        if (match.spectators.size() >= 32 && !match.spectators.containsKey(spectatorId)) {
            source.sendFeedback(() -> Text.literal("That match already has the maximum number of spectators."), false);
            return 0;
        }
        Match previous = MATCHES_BY_SPECTATOR.get(spectatorId);
        if (previous != null && previous != match) removeSpectator(spectatorId, previous);
        match.spectators.put(spectatorId, spectator);
        MATCHES_BY_SPECTATOR.put(spectatorId, match);
        send(spectator, "SPECTATE_START|" + match.id + "|" + match.white + "|" + match.black + "|"
                + encode(match.whitePlayer.getStringifiedName()) + "|" + encode(match.blackPlayer.getStringifiedName()) + "|"
                + match.game.snapshot() + "|" + match.whiteMillisRemaining + "|" + match.blackMillisRemaining);
        broadcastSpectators(match);
        source.sendFeedback(() -> Text.literal("Watching " + target.getStringifiedName() + "'s live chess match."), false);
        return 1;
    }

    private static void spectatePlayer(ServerPlayerEntity spectator, String targetText) {
        UUID spectatorId = spectator.getUuid();
        UUID targetId;
        try {
            targetId = UUID.fromString(targetText);
        } catch (IllegalArgumentException exception) {
            send(spectator, "SPECTATE_STATUS|" + encode("That match is no longer available."));
            return;
        }
        if (!MODDED_CLIENTS.containsKey(spectatorId) || MATCHES_BY_PLAYER.containsKey(spectatorId)) {
            send(spectator, "SPECTATE_STATUS|" + encode("You cannot spectate while playing a match."));
            return;
        }
        ServerPlayerEntity target = MODDED_CLIENTS.get(targetId);
        Match match = target == null ? null : MATCHES_BY_PLAYER.get(targetId);
        if (match == null) {
            send(spectator, "SPECTATE_STATUS|" + encode("No active match is available for that player."));
            return;
        }
        if (match.spectators.size() >= 32 && !match.spectators.containsKey(spectatorId)) {
            send(spectator, "SPECTATE_STATUS|" + encode("That match already has the maximum number of spectators."));
            return;
        }
        Match previous = MATCHES_BY_SPECTATOR.get(spectatorId);
        if (previous != null && previous != match) removeSpectator(spectatorId, previous);
        match.spectators.put(spectatorId, spectator);
        MATCHES_BY_SPECTATOR.put(spectatorId, match);
        send(spectator, "SPECTATE_START|" + match.id + "|" + match.white + "|" + match.black + "|"
                + encode(match.whitePlayer.getStringifiedName()) + "|" + encode(match.blackPlayer.getStringifiedName()) + "|"
                + match.game.snapshot() + "|" + match.whiteMillisRemaining + "|" + match.blackMillisRemaining);
        broadcastSpectators(match);
    }

    private static void removeSpectator(ServerPlayerEntity spectator, String matchId) {
        Match match = MATCHES_BY_SPECTATOR.get(spectator.getUuid());
        if (match != null && match.id.toString().equals(matchId)) removeSpectator(spectator.getUuid(), match);
    }

    private static void removeSpectator(UUID spectatorId, Match match) {
        if (match.spectators.remove(spectatorId) != null) {
            MATCHES_BY_SPECTATOR.remove(spectatorId, match);
            broadcastSpectators(match);
        }
    }

    private static void broadcastSpectators(Match match) {
        ArrayList<String> names = new ArrayList<>();
        for (ServerPlayerEntity spectator : match.spectators.values()) names.add(spectator.getStringifiedName());
        broadcast(match, "SPECTATORS|" + match.id + "|" + encode(String.join(", ", names)));
    }

    private static void move(ServerPlayerEntity player, String[] parts) {
        Match match = MATCHES_BY_PLAYER.get(player.getUuid());
        if (match == null || !match.id.toString().equals(parts[1])) return;
        long now = System.currentTimeMillis();
        if (!updateClock(match, now)) return;
        boolean white = player.getUuid().equals(match.white);
        if (match.game.isWhiteToMove() != white) return;
        try {
            int from = Integer.parseInt(parts[2]);
            int to = Integer.parseInt(parts[3]);
            char promotion = parts[4].isEmpty() ? 'Q' : parts[4].charAt(0);
            if (from < 0 || from > 63 || to < 0 || to > 63) return;
            ChessGame.MoveAnalysis analysis = match.game.analyzeMove(from, to, promotion, 1);
            if (analysis == null || !match.game.move(from, to, promotion)) return;
            match.moveQualityCounts[white ? 0 : 1][analysis.quality().ordinal()]++;
        } catch (NumberFormatException exception) {
            return;
        }
        match.clockWhiteToMove = match.game.isWhiteToMove();
        match.clockStartedAt = now;
        match.lastClockBroadcastAt = now;
        if (match.game.isGameOver()) {
            finish(match, match.game.result());
        } else {
            broadcast(match, "STATE|" + match.id + "|" + match.game.snapshot() + "|"
                    + match.whiteMillisRemaining + "|" + match.blackMillisRemaining);
        }
    }

    private static void resign(ServerPlayerEntity player, String matchId) {
        Match match = MATCHES_BY_PLAYER.get(player.getUuid());
        if (match == null || !match.id.toString().equals(matchId)) return;
        finish(match, player.getUuid().equals(match.white) ? "BLACK" : "WHITE");
    }

    private static void rematch(ServerPlayerEntity player, String matchIdText) {
        UUID matchId;
        try {
            matchId = UUID.fromString(matchIdText);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Match finished = FINISHED_MATCHES.get(matchId);
        UUID playerId = player.getUuid();
        if (finished == null || System.currentTimeMillis() - finished.endedAt > 120_000L
                || (!playerId.equals(finished.white) && !playerId.equals(finished.black))) {
            send(player, "REMATCH_STATUS|" + encode("That rematch is no longer available."));
            return;
        }
        UUID opponentId = playerId.equals(finished.white) ? finished.black : finished.white;
        ServerPlayerEntity opponent = MODDED_CLIENTS.get(opponentId);
        if (opponent == null || MATCHES_BY_PLAYER.containsKey(playerId) || MATCHES_BY_PLAYER.containsKey(opponentId)) {
            send(player, "REMATCH_STATUS|" + encode("Your opponent is no longer available."));
            return;
        }
        if (finished.rematchVotes.add(playerId)) {
            send(opponent, "REMATCH_REQUEST|" + matchId + "|" + encode(player.getStringifiedName()));
        }
        send(player, "REMATCH_STATUS|" + encode("Rematch request sent."));
        if (finished.rematchVotes.contains(finished.white) && finished.rematchVotes.contains(finished.black)) {
            FINISHED_MATCHES.remove(matchId);
            if (finished.ranked) beginTimerBan(finished.whitePlayer, finished.blackPlayer);
            else startMatch(finished.whitePlayer, finished.blackPlayer, finished.minutes);
        }
    }

    private static void cosmetics(ServerPlayerEntity player, String matchId, String preset) {
        if (!("bubblegum".equals(preset) || "book".equals(preset) || "bases".equals(preset)
                || "alpha".equals(preset) || "8qetl".equals(preset))) return;
        Match match = MATCHES_BY_PLAYER.get(player.getUuid());
        if (match == null || !match.id.toString().equals(matchId)) return;
        ServerPlayerEntity opponent = player.getUuid().equals(match.white) ? match.blackPlayer : match.whitePlayer;
        send(opponent, "COSMETICS|" + match.id + "|" + preset);
    }

    private static void finish(Match match, String result) {
        finish(match, result, null);
    }

    private static void finish(Match match, String result, UUID disconnectedPlayer) {
        match.game.forceResult(result);
        Profile whiteProfile = profile(match.white);
        Profile blackProfile = profile(match.black);
        int whiteBefore = whiteProfile.rating;
        int blackBefore = blackProfile.rating;
        int whiteElo = whiteBefore;
        int blackElo = blackBefore;
        UUID tierWinner = null;
        String tierResult = null;
        if (result.equals("WHITE") || result.equals("BLACK")) {
            double expectedWhite = 1.0 / (1.0 + Math.pow(10.0, (blackElo - whiteElo) / 400.0));
            double score = result.equals("WHITE") ? 1.0 : 0.0;
            whiteElo = (int) Math.round(whiteElo + 24.0 * (score - expectedWhite));
            blackElo = (int) Math.round(blackElo + 24.0 * ((1.0 - score) - (1.0 - expectedWhite)));
            whiteProfile.rating = whiteElo;
            blackProfile.rating = blackElo;
            if (result.equals("WHITE")) {
                whiteProfile.wins++;
                blackProfile.losses++;
            } else {
                blackProfile.wins++;
                whiteProfile.losses++;
            }
            if (match.ranked) {
                tierWinner = result.equals("WHITE") ? match.white : match.black;
                Profile winnerProfile = result.equals("WHITE") ? whiteProfile : blackProfile;
                String previousTier = ChessTiers.name(winnerProfile.tierIndex);
                winnerProfile.tierXp = Math.min(100, winnerProfile.tierXp + 25);
                if (winnerProfile.tierXp >= 100 && winnerProfile.tierIndex < 9) {
                    winnerProfile.tierIndex++;
                    winnerProfile.tierXp = 0;
                }
                tierResult = "TIER_RESULT|" + match.id + "|" + tierWinner + "|" + previousTier + "|"
                        + ChessTiers.name(winnerProfile.tierIndex) + "|" + winnerProfile.tierXp;
            }
        } else {
            whiteProfile.draws++;
            blackProfile.draws++;
        }
        addHistory(whiteProfile, match.blackPlayer.getStringifiedName(), result.equals("DRAW") ? "DRAW" : result.equals("WHITE") ? "WIN" : "LOSS", whiteProfile.rating - whiteBefore);
        addHistory(blackProfile, match.whitePlayer.getStringifiedName(), result.equals("DRAW") ? "DRAW" : result.equals("BLACK") ? "WIN" : "LOSS", blackProfile.rating - blackBefore);
        saveProfiles();
        match.endedAt = System.currentTimeMillis();
        FINISHED_MATCHES.put(match.id, match);
        long durationSeconds = Math.max(0, (match.endedAt - match.startedAt) / 1000L);
        String encodedPgn = encodeRaw(match.game.pgn(match.whitePlayer.getStringifiedName(),
                match.blackPlayer.getStringifiedName()));
        String end = "END|" + match.id + "|" + match.game.snapshot() + "|" + result + "|" + whiteElo + "|"
                + blackElo + "|" + durationSeconds + "|" + encodedPgn + "|"
                + encodeMoveQualityCounts(match.moveQualityCounts[0]) + "|"
                + encodeMoveQualityCounts(match.moveQualityCounts[1]);
        if (!match.white.equals(disconnectedPlayer)) send(match.whitePlayer, end);
        if (!match.black.equals(disconnectedPlayer)) send(match.blackPlayer, end);
        if (tierResult != null && !tierWinner.equals(disconnectedPlayer)) {
            send(tierWinner.equals(match.white) ? match.whitePlayer : match.blackPlayer, tierResult);
        }
        for (ServerPlayerEntity spectator : new ArrayList<>(match.spectators.values())) send(spectator, end);
        MATCHES_BY_PLAYER.remove(match.white);
        MATCHES_BY_PLAYER.remove(match.black);
        for (UUID spectatorId : new ArrayList<>(match.spectators.keySet())) {
            MATCHES_BY_SPECTATOR.remove(spectatorId, match);
        }
        match.spectators.clear();
        if (!match.white.equals(disconnectedPlayer)) {
            PLAYER_STATES.put(match.white, "Chess menu");
            updateNameTag(match.whitePlayer);
        }
        if (!match.black.equals(disconnectedPlayer)) {
            PLAYER_STATES.put(match.black, "Chess menu");
            updateNameTag(match.blackPlayer);
        }
        broadcastRoster();
    }

    private static void disconnect(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        Match spectated = MATCHES_BY_SPECTATOR.get(playerId);
        if (spectated != null) removeSpectator(playerId, spectated);
        WAITING.remove(playerId);
        RANKED_QUEUE.removeIf(candidate -> candidate.getUuid().equals(playerId));
        cancelTimerBan(playerId, "Ranked timer ban cancelled because your opponent left.");
        LOBBY_CLIENTS.remove(playerId);
        MODDED_CLIENTS.remove(playerId);
        PLAYER_STATES.remove(playerId);
        FINISHED_MATCHES.values().removeIf(finished -> finished.white.equals(playerId) || finished.black.equals(playerId));
        DUEL_INVITES.remove(playerId);
        DUEL_INVITES.values().removeIf(invite -> invite.sender().equals(playerId));
        DUEL_REQUEST_COOLDOWNS.remove(playerId);
        Match match = MATCHES_BY_PLAYER.get(playerId);
        if (match != null) {
            finish(match, playerId.equals(match.white) ? "BLACK" : "WHITE", playerId);
        }
        clearNameTag(player);
        broadcastLobby();
        broadcastRoster();
    }

    private static void tickClocks() {
        long now = System.currentTimeMillis();
        FINISHED_MATCHES.values().removeIf(match -> now - match.endedAt > 120_000L);
        Set<Match> matches = new HashSet<>(MATCHES_BY_PLAYER.values());
        for (Match match : matches) {
            if (!updateClock(match, now)) continue;
            if (now - match.lastClockBroadcastAt >= 1000) {
                match.lastClockBroadcastAt = now;
                broadcast(match, "CLOCK|" + match.id + "|" + match.whiteMillisRemaining + "|"
                        + match.blackMillisRemaining + "|" + match.clockWhiteToMove);
            }
        }
    }

    private static boolean updateClock(Match match, long now) {
        long elapsed = Math.max(0, now - match.clockStartedAt);
        if (match.clockWhiteToMove) {
            match.whiteMillisRemaining = Math.max(0, match.whiteMillisRemaining - elapsed);
        } else {
            match.blackMillisRemaining = Math.max(0, match.blackMillisRemaining - elapsed);
        }
        match.clockStartedAt = now;
        if (match.whiteMillisRemaining == 0 || match.blackMillisRemaining == 0) {
            finish(match, match.whiteMillisRemaining == 0 ? "BLACK" : "WHITE");
            return false;
        }
        return true;
    }

    private static void sendLobby(ServerPlayerEntity player) {
        StringBuilder list = new StringBuilder("LOBBY|");
        for (Waiting waiting : WAITING.values()) {
            if (list.length() > 6) list.append(';');
            list.append(waiting.id()).append('~').append(waiting.player().getUuid()).append('~')
                    .append(encode(waiting.player().getStringifiedName())).append('~').append(waiting.minutes());
        }
        send(player, list.toString());
    }

    private static void broadcastLobby() {
        for (ServerPlayerEntity player : new ArrayList<>(LOBBY_CLIENTS.values())) sendLobby(player);
    }

    private static void broadcast(Match match, String payload) {
        send(match.whitePlayer, payload);
        send(match.blackPlayer, payload);
        for (ServerPlayerEntity spectator : new ArrayList<>(match.spectators.values())) send(spectator, payload);
    }

    private static void send(ServerPlayerEntity player, String message) {
        ServerPlayNetworking.send(player, new ChessPayload(message));
    }

    private static String encodeMoveQualityCounts(int[] counts) {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < counts.length; i++) {
            if (i > 0) encoded.append(',');
            encoded.append(counts[i]);
        }
        return encoded.toString();
    }

    private static Profile profile(UUID player) {
        return PROFILES.computeIfAbsent(player, ignored -> new Profile());
    }

    private static int parseMinutes(String value) {
        try {
            int minutes = Integer.parseInt(value);
            return minutes == 1 || minutes == 2 || minutes == 5 || minutes == 8 || minutes == 10 ? minutes : 5;
        } catch (NumberFormatException exception) {
            return 5;
        }
    }

    private static void loadProfiles(MinecraftServer server) {
        profileFile = server.getSavePath(WorldSavePath.ROOT).resolve("kodarichess-profiles.dat");
        legacyEloFile = server.getSavePath(WorldSavePath.ROOT).resolve("kodarichess-elo.dat");
        PROFILES.clear();
        if (Files.exists(profileFile)) {
            try {
                for (String line : Files.readAllLines(profileFile, StandardCharsets.UTF_8)) {
                    String[] fields = line.split("\\|", 9);
                    if (fields.length < 6) continue;
                    Profile profile = new Profile();
                    profile.rating = Integer.parseInt(fields[1]);
                    profile.wins = Integer.parseInt(fields[2]);
                    profile.losses = Integer.parseInt(fields[3]);
                    profile.draws = Integer.parseInt(fields[4]);
                    String history;
                    if (fields.length >= 9) {
                        profile.tierIndex = Math.max(0, Math.min(9, Integer.parseInt(fields[5])));
                        profile.tierXp = Math.max(0, Math.min(100, Integer.parseInt(fields[6])));
                        profile.name = decodeRaw(fields[7]);
                        history = decodeRaw(fields[8]);
                    } else {
                        history = fields.length == 7 ? decodeRaw(fields[6]) : decodeRaw(fields[5]);
                        if (fields.length == 7) profile.name = decodeRaw(fields[5]);
                    }
                    for (String row : history.split("\\n")) {
                        String[] entry = row.split("~", 5);
                        if (entry.length == 5) profile.history.add(new HistoryEntry(Long.parseLong(entry[0]),
                                decodeRaw(entry[1]), entry[2], Integer.parseInt(entry[3]), Integer.parseInt(entry[4])));
                    }
                    PROFILES.put(UUID.fromString(fields[0]), profile);
                }
            } catch (Exception ignored) {
                PROFILES.clear();
            }
            return;
        }
        if (!Files.exists(legacyEloFile)) return;
        try {
            for (String line : Files.readAllLines(legacyEloFile, StandardCharsets.UTF_8)) {
                String[] fields = line.split("=", 2);
                if (fields.length == 2) profile(UUID.fromString(fields[0])).rating = Integer.parseInt(fields[1]);
            }
        } catch (Exception ignored) {
            PROFILES.clear();
        }
    }

    private static void saveProfiles() {
        if (profileFile == null) return;
        ArrayList<String> lines = new ArrayList<>();
        PROFILES.forEach((player, profile) -> lines.add(player + "|" + profile.rating + "|" + profile.wins + "|"
                + profile.losses + "|" + profile.draws + "|" + profile.tierIndex + "|" + profile.tierXp + "|"
                + encodeRaw(profile.name) + "|" + encodeRaw(serializeHistory(profile))));
        try {
            Files.createDirectories(profileFile.getParent());
            Files.write(profileFile, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void addHistory(Profile profile, String opponent, String result, int ratingChange) {
        profile.history.add(0, new HistoryEntry(System.currentTimeMillis(), clean(opponent), result, ratingChange,
                profile.rating));
        while (profile.history.size() > 30) profile.history.remove(profile.history.size() - 1);
    }

    private static String serializeHistory(Profile profile) {
        ArrayList<String> rows = new ArrayList<>();
        for (HistoryEntry entry : profile.history) {
            rows.add(entry.timestamp() + "~" + encodeRaw(entry.opponent()) + "~" + entry.result() + "~"
                    + entry.ratingChange() + "~" + entry.ratingAfter());
        }
        return String.join("\n", rows);
    }

    private static void registerModdedPlayer(ServerPlayerEntity player) {
        MODDED_CLIENTS.put(player.getUuid(), player);
        profile(player.getUuid()).name = clean(player.getStringifiedName());
        PLAYER_STATES.putIfAbsent(player.getUuid(), "--");
        updateNameTag(player);
        broadcastRoster();
    }

    private static void setPlayerState(ServerPlayerEntity player, String state) {
        String label = switch (state.toUpperCase()) {
            case "MENU" -> "Chess menu";
            case "BOT" -> "Playing vs bot";
            case "MULTIPLAYER" -> "On match";
            case "IDLE" -> "--";
            default -> null;
        };
        if (label == null) return;
        UUID playerId = player.getUuid();
        if (MATCHES_BY_PLAYER.containsKey(playerId)) label = "On match";
        PLAYER_STATES.put(playerId, label);
        updateNameTag(player);
    }

    private static void updateNameTag(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        Scoreboard scoreboard = world.getServer().getScoreboard();
        String playerName = player.getNameForScoreboard();
        String teamName = "kch_" + player.getUuid().toString().replace("-", "").substring(0, 12);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) team = scoreboard.addTeam(teamName);
        Team currentTeam = scoreboard.getScoreHolderTeam(playerName);
        if (currentTeam != null && currentTeam != team) return;
        Profile profile = profile(player.getUuid());
        String state = PLAYER_STATES.getOrDefault(player.getUuid(), "--");
        team.setPrefix(Text.literal("[" + ChessTiers.name(profile.tierIndex) + "] [" + profile.rating
                + " Elo | " + state + "] ").withColor(ChessTiers.color(profile.tierIndex)));
        if (currentTeam == null) scoreboard.addScoreHolderToTeam(playerName, team);
    }

    private static void clearNameTag(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        Scoreboard scoreboard = world.getServer().getScoreboard();
        String teamName = "kch_" + player.getUuid().toString().replace("-", "").substring(0, 12);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) return;
        scoreboard.removeScoreHolderFromTeam(player.getNameForScoreboard(), team);
        scoreboard.removeTeam(team);
    }

    public static int getRating(UUID playerId) {
        Profile profile = PROFILES.get(playerId);
        return profile == null ? 1200 : profile.rating;
    }

    public static void unlockSecretBot(ServerPlayerEntity player) {
        send(player, "BOT_SECRET_UNLOCKED");
    }

    public static void activateHolo(ServerPlayerEntity player, String action) {
        if (!MODDED_CLIENTS.containsKey(player.getUuid())) return;
        if (!("play:menu".equals(action) || "play:multiplayer".equals(action) || "play:bot".equals(action)
                || "play:profile".equals(action) || "play:customize".equals(action)
                || "gui:playground".equals(action) || "gui:spectate".equals(action)
                || "gui:profile".equals(action) || "gui:tier".equals(action))) return;
        send(player, "HOLO_ACTION|" + action);
    }

    public static List<String> topTenLines() {
        ArrayList<Map.Entry<UUID, Profile>> ranked = new ArrayList<>(PROFILES.entrySet());
        ranked.sort((first, second) -> Integer.compare(second.getValue().rating, first.getValue().rating));
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Chess Elo Top 10");
        for (int i = 0; i < Math.min(10, ranked.size()); i++) {
            Profile profile = ranked.get(i).getValue();
            lines.add((i + 1) + ". " + profile.name + " — " + profile.rating + " Elo");
        }
        return lines;
    }

    private static void sendRoster(ServerPlayerEntity recipient) {
        StringBuilder roster = new StringBuilder("ROSTER|");
        ArrayList<Map.Entry<UUID, Profile>> ranked = new ArrayList<>(PROFILES.entrySet());
        ranked.sort((first, second) -> Integer.compare(second.getValue().rating, first.getValue().rating));
        for (int i = 0; i < Math.min(100, ranked.size()); i++) {
            Map.Entry<UUID, Profile> entry = ranked.get(i);
            if (roster.length() > 7) roster.append(';');
            Profile profile = entry.getValue();
            roster.append(entry.getKey()).append('~').append(encode(profile.name)).append('~')
                    .append(profile.rating).append('~').append(profile.wins).append('~').append(profile.losses)
                    .append('~').append(profile.draws).append('~').append(ChessTiers.name(profile.tierIndex))
                    .append('~').append(profile.tierXp);
        }
        send(recipient, roster.toString());
    }

    private static void broadcastRoster() {
        for (ServerPlayerEntity player : new ArrayList<>(MODDED_CLIENTS.values())) sendRoster(player);
    }

    private static void requestProfile(ServerPlayerEntity requester, String idText) {
        UUID target = requester.getUuid();
        if (!idText.isBlank()) {
            try {
                target = UUID.fromString(idText);
            } catch (IllegalArgumentException exception) {
                return;
            }
        }
        Profile profile = PROFILES.get(target);
        if (profile == null) return;
        send(requester, "PROFILE|" + target + "|" + encode(profile.name) + "|"
                + profile.rating + "|" + profile.wins + "|" + profile.losses + "|" + profile.draws + "|"
                + ChessTiers.name(profile.tierIndex) + "|" + profile.tierXp + "|"
                + encodeRaw(serializeHistory(profile)));
    }

    private static String encodeRaw(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeRaw(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(clean(text).getBytes(StandardCharsets.UTF_8));
    }

    private static String clean(String text) {
        return text.replace('|', ' ').replace(';', ' ').replace('~', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}