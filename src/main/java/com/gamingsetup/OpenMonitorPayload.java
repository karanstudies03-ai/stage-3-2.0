package com.gamingsetup;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server -> client: open this monitor's screen; pcId says which PC's disk to use. */
public record OpenMonitorPayload(BlockPos monitor, String pcId) implements CustomPayload {
    public static final CustomPayload.Id<OpenMonitorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GamingSetupMod.MOD_ID, "open_monitor"));
    public static final PacketCodec<RegistryByteBuf, OpenMonitorPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, OpenMonitorPayload::monitor,
                    PacketCodecs.STRING, OpenMonitorPayload::pcId, OpenMonitorPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
