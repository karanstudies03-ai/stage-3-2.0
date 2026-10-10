package com.gamingsetup;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: "Shut down" clicked on the monitor at this position. */
public record ShutdownPayload(BlockPos monitor) implements CustomPayload {
    public static final CustomPayload.Id<ShutdownPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GamingSetupMod.MOD_ID, "shutdown"));
    public static final PacketCodec<RegistryByteBuf, ShutdownPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, ShutdownPayload::monitor, ShutdownPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
