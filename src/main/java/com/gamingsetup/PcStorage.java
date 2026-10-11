package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;

/**
 * Every PC has its own disk: <world folder>/gamingsetup/pc_<id>/drive_c and wallpaper.png.
 * Monitors cabled to the same PC share it; other PCs (and other worlds) never see it.
 * Client side only.
 */
public final class PcStorage {
    public static Path root(String pcId) {
        Path base = CameraStorage.root();   // fallback: game folder
        var server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            base = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().resolve("gamingsetup");
        }
        return base.resolve("pc_" + pcId);
    }

    public static Path driveC(String pcId) { return root(pcId).resolve("drive_c"); }
    public static Path wallpaper(String pcId) { return root(pcId).resolve("wallpaper.png"); }

    private PcStorage() {}
}
