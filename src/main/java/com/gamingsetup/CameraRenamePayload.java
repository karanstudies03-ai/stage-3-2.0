package com.gamingsetup;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: rename the camera called oldName. */
public record CameraRenamePayload(String oldName, String newName) implements CustomPayload {
    public static final CustomPayload.Id<CameraRenamePayload> ID =
            new CustomPayload.Id<>(Identifier.of(GamingSetupMod.MOD_ID, "camera_rename"));
    public static final PacketCodec<RegistryByteBuf, CameraRenamePayload> CODEC =
            PacketCodec.tuple(PacketCodecs.STRING, CameraRenamePayload::oldName,
                    PacketCodecs.STRING, CameraRenamePayload::newName, CameraRenamePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
