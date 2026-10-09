package com.gamingsetup;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "bread:2,cooked_beef:64" */
public record OrderPayload(String order) implements CustomPayload {
    public static final CustomPayload.Id<OrderPayload> ID =
            new CustomPayload.Id<>(Identifier.of(GamingSetupMod.MOD_ID, "order"));
    public static final PacketCodec<RegistryByteBuf, OrderPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, OrderPayload::order, OrderPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
