package com.gamingsetup;

import net.minecraft.client.MinecraftClient;

/** Only ever called on the client (never loaded on a dedicated server). */
public class ClientHooks {
    public static void openMonitor(net.minecraft.util.math.BlockPos pos, boolean wake) {
        if (wake) net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new PcActionPayload(pos, PcControl.ACTION_WAKE));
        MinecraftClient.getInstance().setScreen(new MonitorScreen(pos));
    }
}
