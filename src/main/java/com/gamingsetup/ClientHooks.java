package com.gamingsetup;

import net.minecraft.client.MinecraftClient;

/** Only ever called on the client (never loaded on a dedicated server). */
public class ClientHooks {
    public static void openMonitor(net.minecraft.util.math.BlockPos pos) {
        MinecraftClient.getInstance().setScreen(new MonitorScreen(pos));
    }
}
