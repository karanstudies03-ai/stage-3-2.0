package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Small preview pictures (thumbnails) for the Files app. They are made in a background thread
 * (so the game never freezes) and shrunk to at most 192 pixels, so even 4K photos stay cheap.
 */
public final class ThumbCache {
    public record Thumb(Identifier id, int w, int h) {}

    private static final Map<String, Thumb> READY = new LinkedHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gamingsetup-thumbnails");
        t.setDaemon(true);
        return t;
    });
    private static int counter = 0;

    private static String key(Path p) {
        try { return p + "|" + Files.getLastModifiedTime(p).toMillis(); }
        catch (IOException e) { return p.toString(); }
    }

    /** Returns the thumbnail if it is ready, otherwise null (and starts making it). */
    public static Thumb get(Path path) {
        String k = key(path);
        Thumb t = READY.get(k);
        if (t != null) return t;
        if (FAILED.contains(k) || !LOADING.add(k)) return null;

        POOL.execute(() -> {
            byte[] png = null;
            try {
                BufferedImage src = ImageIO.read(path.toFile());
                if (src != null) {
                    double sc = Math.min(1.0, 192.0 / Math.max(src.getWidth(), src.getHeight()));
                    int w = Math.max(1, (int) Math.round(src.getWidth() * sc));
                    int h = Math.max(1, (int) Math.round(src.getHeight() * sc));
                    Image scaled = src.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING);
                    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = dst.createGraphics();
                    g.drawImage(scaled, 0, 0, null);
                    g.dispose();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ImageIO.write(dst, "png", bos);
                    png = bos.toByteArray();
                }
            } catch (Throwable ignored) { }

            final byte[] data = png;
            MinecraftClient.getInstance().execute(() -> {
                LOADING.remove(k);
                if (data == null) { FAILED.add(k); return; }
                try {
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
                    Identifier id = Identifier.of(GamingSetupMod.MOD_ID, "dyn/thumb_" + (counter++));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id,
                            new NativeImageBackedTexture(() -> "gamingsetup thumbnail", img));
                    READY.put(k, new Thumb(id, img.getWidth(), img.getHeight()));
                    while (READY.size() > 300) {
                        String oldest = READY.keySet().iterator().next();
                        Thumb old = READY.remove(oldest);
                        MinecraftClient.getInstance().getTextureManager().destroyTexture(old.id());
                    }
                } catch (Exception e) {
                    FAILED.add(k);
                }
            });
        });
        return null;
    }

    private ThumbCache() {}
}
