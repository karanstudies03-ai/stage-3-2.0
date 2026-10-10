package com.gamingsetup;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: power action chosen on a monitor (see PcControl.ACTION_*). */
public record PcActionPayload(BlockPos monitor, int action) implements CustomPayload {
    public static final CustomPayload.Id<PcActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GamingSetupMod.MOD_ID, "pc_action"));
    public static final PacketCodec<RegistryByteBuf, PcActionPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, PcActionPayload::monitor,
                    PacketCodecs.VAR_INT, PcActionPayload::action, PcActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
