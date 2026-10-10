package com.gamingsetup;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

public class PcControl {
    /** Turn off the PC that the given monitor is cabled to (called when "Shut down" is clicked). */
    public static void shutDown(MinecraftServer server, ServerPlayerEntity player, BlockPos monitorPos) {
        for (ServerWorld w : server.getWorlds()) {
            if (w.getPlayerByUuid(player.getUuid()) == null) continue;
            if (!monitorPos.isWithinDistance(player.getBlockPos(), 12)) return;   // too far away
            if (w.getBlockEntity(monitorPos) instanceof MonitorBlockEntity be && be.getPcPos() != null) {
                BlockPos pcPos = be.getPcPos();
                BlockState pc = w.getBlockState(pcPos);
                if (pc.getBlock() instanceof PcBlock && pc.get(PcBlock.ON)) {
                    w.setBlockState(pcPos, pc.with(PcBlock.ON, false));
                    w.playSound(null, pcPos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 0.8f);
                }
            }
            return;
        }
    }
}
