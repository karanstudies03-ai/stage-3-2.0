package com.gamingsetup;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Takes the photo: hides the HUD for a moment, grabs the screen and saves it in the camera's folder. */
public class PhotoTaker {
    private static String pendingCamera = null;
    private static int ticks = 0;
    private static boolean hudWasHidden = false;

    public static void request(String cameraName) {
        if (pendingCamera != null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        pendingCamera = cameraName;
        hudWasHidden = c.options.hudHidden;
        c.options.hudHidden = true;
        ticks = 3;
    }

    public static void tick(MinecraftClient c) {
        if (pendingCamera == null) return;
        if (--ticks > 0) return;
        String camera = pendingCamera;
        pendingCamera = null;

        ScreenshotRecorder.takeScreenshot(c.getFramebuffer(), image -> {
            String message;
            try {
                Path dir = CameraStorage.cameraDir(camera);
                Files.createDirectories(dir);
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss"));
                Path file = dir.resolve("IMG_" + stamp + ".png");
                int n = 1;
                while (Files.exists(file)) file = dir.resolve("IMG_" + stamp + "_" + (n++) + ".png");
                image.writeTo(file);
                message = "Photo saved in " + camera;
            } catch (Exception e) {
                message = "Could not save the photo.";
            } finally {
                image.close();
            }
            final String shown = message;
            c.execute(() -> {
                if (c.player != null) {
                    c.player.sendMessage(Text.literal(shown), true);
                    c.player.playSound(SoundEvents.BLOCK_LEVER_CLICK, 1.0f, 1.6f);
                }
            });
        });
        c.options.hudHidden = hudWasHidden;
    }
}
