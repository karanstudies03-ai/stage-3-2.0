package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Loads PNG files from disk as GUI textures. Keeps only a few in memory. */
public final class ImageCache {
    public record Entry(Identifier id, int w, int h) {}

    private static final Map<Path, Entry> CACHE = new LinkedHashMap<>();
    private static final Set<Path> FAILED = new HashSet<>();
    private static int counter = 0;

    public static Entry get(Path path) {
        Entry e = CACHE.get(path);
        if (e != null) return e;
        if (FAILED.contains(path)) return null;
        try (InputStream in = Files.newInputStream(path)) {
            NativeImage img = NativeImage.read(in);
            Identifier id = Identifier.of(GamingSetupMod.MOD_ID, "dyn/img_" + (counter++));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                    new NativeImageBackedTexture(() -> "gamingsetup image", img));
            e = new Entry(id, img.getWidth(), img.getHeight());
            CACHE.put(path, e);
            trim();
            return e;
        } catch (Exception ex) {
            FAILED.add(path);
            return null;
        }
    }

    public static void release(Path path) {
        FAILED.remove(path);
        Entry e = CACHE.remove(path);
        if (e != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(e.id());
    }

    public static void releaseAllExceptWallpaper() {
        FAILED.clear();
        for (Path p : new ArrayList<>(CACHE.keySet())) {
            if (!p.equals(CameraStorage.wallpaper())) release(p);
        }
    }

    private static void trim() {
        while (CACHE.size() > 6) {
            Path victim = null;
            for (Path p : CACHE.keySet()) if (!p.equals(CameraStorage.wallpaper())) { victim = p; break; }
            if (victim == null) return;
            release(victim);
        }
    }

    private ImageCache() {}
}
