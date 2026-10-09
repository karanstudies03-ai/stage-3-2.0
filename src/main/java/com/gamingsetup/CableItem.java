package com.gamingsetup;

import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Link cable. Right-click a monitor, then right-click a PC (don't sneak).
 * One PC can have any number of monitors; each monitor has one PC.
 */
public class CableItem extends Item {
    public static final int MAX_DISTANCE = 16;
    private static final Map<UUID, BlockPos> PENDING_MONITOR = new HashMap<>();

    public CableItem(Settings settings) { super(settings); }

    public static void clickMonitor(ServerPlayerEntity player, BlockPos monitorPos) {
        PENDING_MONITOR.put(player.getUuid(), monitorPos.toImmutable());
        player.sendMessage(Text.literal("Monitor selected. Now right-click the PC."), true);
    }

    public static void clickPc(ServerPlayerEntity player, World world, BlockPos pcPos) {
        BlockPos monitorPos = PENDING_MONITOR.get(player.getUuid());
        if (monitorPos == null) {
            player.sendMessage(Text.literal("Right-click a monitor with the cable first."), true);
            return;
        }
        if (!(world.getBlockState(monitorPos).getBlock() instanceof MonitorBlock)) {
            PENDING_MONITOR.remove(player.getUuid());
            player.sendMessage(Text.literal("That monitor is gone. Select a monitor again."), true);
            return;
        }
        if (!monitorPos.isWithinDistance(pcPos, MAX_DISTANCE)) {
            player.sendMessage(Text.literal("Cable too short! Max " + MAX_DISTANCE + " blocks."), true);
            return;
        }
        MonitorBlock.link(world, monitorPos, pcPos);
        PENDING_MONITOR.remove(player.getUuid());
        player.sendMessage(Text.literal("Monitor connected to PC!"), true);
    }
}
