package com.kodari.chess;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ChessPayload(String data) implements CustomPayload {
    public static final Id<ChessPayload> ID = new Id<>(Identifier.of(ChessMod.MOD_ID, "message"));
    public static final PacketCodec<ByteBuf, ChessPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            ChessPayload::data,
            ChessPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}