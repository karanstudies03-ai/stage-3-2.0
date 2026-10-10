package com.gamingsetup;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Where photos and files live: <game folder>/gamingsetup/ (single player: client and server share it). */
public final class CameraStorage {
    public static Path root() { return FabricLoader.getInstance().getGameDir().resolve("gamingsetup"); }
    public static Path cameras() { return root().resolve("cameras"); }
    public static Path cameraDir(String name) { return cameras().resolve(name); }
    public static Path driveC() { return root().resolve("drive_c"); }
    public static Path wallpaper() { return root().resolve("wallpaper.png"); }

    /** Letters, digits, space, underscore and dash only (safe for folder names), max 24 characters. */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        if (s.length() > 24) s = s.substring(0, 24).trim();
        return s;
    }

    /** "Camera 1", "Camera 2", ... never reusing a number. */
    public static synchronized String nextCameraName() {
        Path counter = root().resolve("camera_counter.txt");
        int n = 0;
        try {
            if (Files.exists(counter)) n = Integer.parseInt(Files.readString(counter).trim());
        } catch (Exception ignored) { }
        String name;
        do { n++; name = "Camera " + n; } while (Files.exists(cameraDir(name)));
        try {
            Files.createDirectories(root());
            Files.writeString(counter, String.valueOf(n));
        } catch (IOException ignored) { }
        return name;
    }

    private CameraStorage() {}
}
