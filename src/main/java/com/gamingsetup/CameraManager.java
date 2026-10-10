package com.gamingsetup;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

/** Server side: gives new cameras their default name (Camera 1, Camera 2, ...) and handles renaming. */
public class CameraManager {
    public static String nameOf(ItemStack stack) {
        Text t = stack.get(DataComponentTypes.CUSTOM_NAME);
        return t == null ? null : t.getString();
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(ModItems.CAMERA) && nameOf(s) == null) {
                    s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(CameraStorage.nextCameraName()));
                }
            }
        }
    }

    public static void rename(ServerPlayerEntity player, String oldName, String requested) {
        String newName = CameraStorage.sanitize(requested);
        if (newName.isEmpty()) { player.sendMessage(Text.literal("Invalid camera name."), true); return; }
        PlayerInventory inv = player.getInventory();
        ItemStack target = null;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(ModItems.CAMERA) && oldName.equals(nameOf(s))) { target = s; break; }
        }
        if (target == null) return;
        if (newName.equals(oldName)) return;

        // name must be unique (other cameras in the inventory and existing photo folders)
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s != target && s.isOf(ModItems.CAMERA) && newName.equalsIgnoreCase(nameOf(s))) {
                player.sendMessage(Text.literal("You already have a camera with that name."), true);
                return;
            }
        }
        Path oldDir = CameraStorage.cameraDir(oldName), newDir = CameraStorage.cameraDir(newName);
        boolean sameIgnoringCase = oldName.equalsIgnoreCase(newName);
        if (!sameIgnoringCase && Files.exists(newDir)) {
            player.sendMessage(Text.literal("That name is already used."), true);
            return;
        }
        try {
            if (Files.exists(oldDir)) Files.move(oldDir, newDir);
        } catch (Exception e) {
            player.sendMessage(Text.literal("Could not rename the camera folder."), true);
            return;
        }
        target.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newName));
        player.sendMessage(Text.literal("Camera renamed to " + newName), true);
    }
}
