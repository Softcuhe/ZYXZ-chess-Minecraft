package com.kodari.chess.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

import java.util.UUID;

final class ChessPlayerHeads {
    private ChessPlayerHeads() {
    }

    static void draw(DrawContext context, String playerId, int x, int y, int size) {
        if (playerId == null || playerId.isEmpty()) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(playerId);
        } catch (IllegalArgumentException exception) {
            return;
        }
        var networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) return;
        var entry = networkHandler.getPlayerListEntry(uuid);
        if (entry == null) return;
        var skin = entry.getSkinTextures().body().texturePath();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, size, size, 8, 8, 64, 64);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f, size, size, 8, 8, 64, 64);
    }
}