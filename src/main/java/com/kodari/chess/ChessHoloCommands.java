package com.kodari.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public final class ChessHoloCommands {
    private static final String TYPE_TAG_PREFIX = "kodarichess_holo_type_";
    private static final String GROUP_TAG_PREFIX = "kodarichess_holo_group_";
    private static final String ACTION_TAG_PREFIX = "kodarichess_holo_action_";
    private static final String HOLO_NAME_PREFIX = "♟ ";

    private ChessHoloCommands() {
    }

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> handleInteraction(player, entity));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> handleInteraction(player, entity));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var chessCommand = CommandManager.literal("chess");
            var holoCommand = CommandManager.literal("holo")
                    .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS))
                    .then(CommandManager.literal("top10")
                            .executes(context -> spawn(context.getSource(), "top10")))
                    .then(CommandManager.literal("local")
                            .executes(context -> spawn(context.getSource(), "local")))
                    .then(CommandManager.literal("server")
                            .executes(context -> spawn(context.getSource(), "server")))
                    .then(CommandManager.literal("play")
                            .then(CommandManager.literal("menu").executes(context -> spawnAction(context.getSource(), "play", "play:menu")))
                            .then(CommandManager.literal("multiplayer").executes(context -> spawnAction(context.getSource(), "play", "play:multiplayer")))
                            .then(CommandManager.literal("bot").executes(context -> spawnAction(context.getSource(), "play", "play:bot")))
                            .then(CommandManager.literal("profile").executes(context -> spawnAction(context.getSource(), "play", "play:profile")))
                            .then(CommandManager.literal("customize").executes(context -> spawnAction(context.getSource(), "play", "play:customize"))))
                    .then(CommandManager.literal("gui")
                            .then(CommandManager.literal("playground").executes(context -> spawnAction(context.getSource(), "gui", "gui:playground")))
                            .then(CommandManager.literal("spectate").executes(context -> spawnAction(context.getSource(), "gui", "gui:spectate")))
                            .then(CommandManager.literal("profile").executes(context -> spawnAction(context.getSource(), "gui", "gui:profile")))
                            .then(CommandManager.literal("tier").executes(context -> spawnAction(context.getSource(), "gui", "gui:tier"))))
                    .then(CommandManager.literal("clear")
                            .then(clearType("top10"))
                            .then(clearType("local"))
                            .then(clearType("server"))
                            .then(clearType("play"))
                            .then(clearType("gui")));
            chessCommand.then(holoCommand);
            chessCommand.then(CommandManager.literal("bot")
                    .then(CommandManager.literal("secret")
                            .executes(context -> unlockSecretBot(context.getSource()))));
            dispatcher.register(chessCommand);
            dispatcher.register(CommandManager.literal("spec")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(context -> ChessNetworking.spectate(context.getSource(),
                                    context.getArgument("player", String.class)))));
        });
    }

    private static int unlockSecretBot(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ChessNetworking.unlockSecretBot(player);
        source.sendFeedback(() -> Text.literal("Unlocked Tuye, the Lord Rabbit. Select Tuye in the chess menu."), false);
        return 1;
    }

    private static int spawnAction(ServerCommandSource source, String category, String action)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String groupTag = GROUP_TAG_PREFIX + UUID.randomUUID().toString().replace("-", "");
        ArmorStandEntity hologram = new ArmorStandEntity(source.getWorld(), player.getX(), player.getY() + 1.0,
                player.getZ());
        hologram.setInvisible(true);
        hologram.setNoGravity(true);
        hologram.setCustomName(Text.literal(HOLO_NAME_PREFIX + actionLabel(action)).withColor(0xFFE9F2EC));
        hologram.setCustomNameVisible(true);
        hologram.addCommandTag(TYPE_TAG_PREFIX + category);
        hologram.addCommandTag(groupTag);
        hologram.addCommandTag(ACTION_TAG_PREFIX + action);
        if (!source.getWorld().spawnEntity(hologram)) return 0;
        source.sendFeedback(() -> Text.literal("Spawned interactive chess " + category + " hologram."), true);
        return 1;
    }

    private static String actionLabel(String action) {
        return switch (action) {
            case "play:menu" -> "Open Chess Menu";
            case "play:multiplayer" -> "Open Multiplayer";
            case "play:bot" -> "Choose a Bot";
            case "play:profile", "gui:profile" -> "Chess Profile";
            case "play:customize" -> "Customize Chess";
            case "gui:playground" -> "Multiplayer Playground";
            case "gui:spectate" -> "Spectate Matches";
            default -> "Chess Tiers";
        };
    }

    private static ActionResult handleInteraction(Object player, Entity entity) {
        String action = null;
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(ACTION_TAG_PREFIX)) {
                action = tag.substring(ACTION_TAG_PREFIX.length());
                break;
            }
        }
        if (action == null) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity serverPlayer) ChessNetworking.activateHolo(serverPlayer, action);
        return ActionResult.SUCCESS;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> clearType(String type) {
        return CommandManager.literal(type)
                .then(CommandManager.literal("nearest")
                        .executes(context -> clear(context.getSource(), type, false)))
                .then(CommandManager.literal("all")
                        .executes(context -> clear(context.getSource(), type, true)));
    }

    private static int spawn(ServerCommandSource source, String type) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        List<String> lines = switch (type) {
            case "top10" -> ChessNetworking.topTenLines();
            case "local" -> List.of("Local Elo: " + ChessNetworking.getRating(player.getUuid()));
            default -> List.of("World / server Elo: " + ChessNetworking.getRating(player.getUuid()));
        };
        String groupTag = GROUP_TAG_PREFIX + UUID.randomUUID().toString().replace("-", "");
        int spawned = 0;
        for (int i = 0; i < lines.size(); i++) {
            ArmorStandEntity hologramLine = new ArmorStandEntity(source.getWorld(), player.getX(),
                    player.getY() + 1.0 + (lines.size() - 1 - i) * 0.25, player.getZ());
            hologramLine.setInvisible(true);
            hologramLine.setNoGravity(true);
            var line = Text.literal(lines.get(i));
            if (type.equals("top10")) {
                line = switch (i) {
                    case 1 -> line.withColor(0xFFFFD700);
                    case 2 -> line.withColor(0xFFC0C0C0);
                    case 3 -> line.withColor(0xFFCD7F32);
                    default -> line;
                };
            }
            hologramLine.setCustomName(line);
            hologramLine.setCustomNameVisible(true);
            hologramLine.addCommandTag(TYPE_TAG_PREFIX + type);
            hologramLine.addCommandTag(groupTag);
            if (source.getWorld().spawnEntity(hologramLine)) spawned++;
        }
        source.sendFeedback(() -> Text.literal("Spawned " + type + " Elo hologram."), true);
        return spawned;
    }

    private static int clear(ServerCommandSource source, String type, boolean all) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String typeTag = TYPE_TAG_PREFIX + type;
        ArrayList<ArmorStandEntity> matches = new ArrayList<>();
        for (Entity entity : source.getWorld().iterateEntities()) {
            if (entity instanceof ArmorStandEntity stand && stand.getCommandTags().contains(typeTag)) matches.add(stand);
        }
        String selectedGroup = null;
        if (!all) {
            ArmorStandEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (ArmorStandEntity stand : matches) {
                double dx = stand.getX() - player.getX();
                double dy = stand.getY() - player.getY();
                double dz = stand.getZ() - player.getZ();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearestDistance) {
                    nearest = stand;
                    nearestDistance = distance;
                }
            }
            if (nearest != null) {
                for (String tag : nearest.getCommandTags()) {
                    if (tag.startsWith(GROUP_TAG_PREFIX)) selectedGroup = tag;
                }
            }
        }
        int removed = 0;
        for (ArmorStandEntity stand : matches) {
            if (all || (selectedGroup != null && stand.getCommandTags().contains(selectedGroup))) {
                stand.discard();
                removed++;
            }
        }
        String scope = all ? "all" : "nearest";
        int removedCount = removed;
        source.sendFeedback(() -> Text.literal("Cleared " + removedCount + " " + scope + " " + type + " hologram lines."), true);
        return removedCount;
    }
}