package com.kodari.chess;

import net.fabricmc.api.ModInitializer;

public final class ChessMod implements ModInitializer {
    public static final String MOD_ID = "kodarichess";

    @Override
    public void onInitialize() {
        ChessNetworking.initialize();
        ChessHoloCommands.initialize();
    }
}