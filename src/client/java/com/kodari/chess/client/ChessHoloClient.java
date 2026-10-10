package com.kodari.chess.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

final class ChessHoloClient {
    private static final String NAME_PREFIX = "♟ ";
    private static Entity hoveredEntity;
    private static String hoveredName = "";
    private static float hoverAmount;

    private ChessHoloClient() {
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(ChessHoloClient::tick);
    }

    private static void tick(MinecraftClient client) {
        Entity target = null;
        if (client.crosshairTarget instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            Text name = entity.getCustomName();
            if (name != null && name.getString().startsWith(NAME_PREFIX)) target = entity;
        }
        if (target != null && target != hoveredEntity) {
            resetHoveredName();
            hoveredEntity = target;
            hoveredName = target.getCustomName().getString();
            hoverAmount = 0;
        }
        float targetAmount = target == hoveredEntity && hoveredEntity != null ? 1.0f : 0.0f;
        hoverAmount += (targetAmount - hoverAmount) * 0.32f;
        if (hoveredEntity != null) {
            hoveredEntity.setCustomName(Text.literal(hoveredName).withColor(blendColor(hoverAmount)));
            if (targetAmount == 0.0f && hoverAmount < 0.015f) resetHoveredName();
        }
    }

    private static int blendColor(float amount) {
        int red = Math.round(0xE9 + (0x63 - 0xE9) * amount);
        int green = Math.round(0xF2 + (0xB7 - 0xF2) * amount);
        int blue = Math.round(0xEC + (0x8B - 0xEC) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static void resetHoveredName() {
        if (hoveredEntity != null) {
            hoveredEntity.setCustomName(Text.literal(hoveredName).withColor(0xE9F2EC));
        }
        hoveredEntity = null;
        hoveredName = "";
        hoverAmount = 0;
    }
}