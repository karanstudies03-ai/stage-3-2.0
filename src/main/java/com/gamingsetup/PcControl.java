package com.gamingsetup;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Power actions chosen from the monitor's Start menu. */
public class PcControl {
    public static final int ACTION_SHUTDOWN = 0, ACTION_SLEEP = 1, ACTION_RESTART = 2, ACTION_WAKE = 3;
    private static final int RESTART_TICKS = 60;   // 3 seconds

    private static class Restart {
        final RegistryKey<World> world; final BlockPos pc; int ticks = RESTART_TICKS;
        Restart(RegistryKey<World> world, BlockPos pc) { this.world = world; this.pc = pc; }
    }
    private static final List<Restart> RESTARTS = new ArrayList<>();

    /** Kept for the old shut-down packet. */
    public static void shutDown(MinecraftServer server, ServerPlayerEntity player, BlockPos monitorPos) {
        handle(server, player, monitorPos, ACTION_SHUTDOWN);
    }

    public static void handle(MinecraftServer server, ServerPlayerEntity player, BlockPos monitorPos, int action) {
        for (ServerWorld w : server.getWorlds()) {
            if (w.getPlayerByUuid(player.getUuid()) == null) continue;
            if (!monitorPos.isWithinDistance(player.getBlockPos(), 12)) return;        // too far away
            if (!(w.getBlockEntity(monitorPos) instanceof MonitorBlockEntity be)) return;
            BlockState monitor = w.getBlockState(monitorPos);
            if (!(monitor.getBlock() instanceof MonitorBlock)) return;

            BlockPos pcPos = be.getPcPos();
            BlockState pc = pcPos == null ? null : w.getBlockState(pcPos);
            boolean pcOn = pc != null && pc.getBlock() instanceof PcBlock && pc.get(PcBlock.ON);

            switch (action) {
                case ACTION_SHUTDOWN -> {
                    be.setSleeping(false);
                    if (pcOn) turnOff(w, pcPos, pc);
                }
                case ACTION_SLEEP -> {
                    if (pcOn) {
                        be.setSleeping(true);
                        w.setBlockState(monitorPos, monitor.with(MonitorBlock.SCREEN, MonitorBlock.Screen.SLEEP));
                    }
                }
                case ACTION_WAKE -> {
                    if (pcOn && be.isSleeping()) {
                        be.setSleeping(false);
                        w.setBlockState(monitorPos, monitor.with(MonitorBlock.SCREEN, MonitorBlock.Screen.ON));
                    }
                }
                case ACTION_RESTART -> {
                    be.setSleeping(false);
                    if (pcOn) {
                        turnOff(w, pcPos, pc);
                        boolean already = false;
                        for (Restart r : RESTARTS) if (r.pc.equals(pcPos)) already = true;
                        if (!already) RESTARTS.add(new Restart(w.getRegistryKey(), pcPos.toImmutable()));
                    }
                }
                default -> { }
            }
            return;
        }
    }

    private static void turnOff(ServerWorld w, BlockPos pcPos, BlockState pc) {
        w.setBlockState(pcPos, pc.with(PcBlock.ON, false));
        w.playSound(null, pcPos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 0.8f);
    }

    /** After 3 seconds a restarting PC switches itself back on. */
    public static void tick(MinecraftServer server) {
        if (RESTARTS.isEmpty()) return;
        Iterator<Restart> it = RESTARTS.iterator();
        while (it.hasNext()) {
            Restart r = it.next();
            if (--r.ticks > 0) continue;
            it.remove();
            ServerWorld w = server.getWorld(r.world);
            if (w == null) continue;
            BlockState st = w.getBlockState(r.pc);
            if (st.getBlock() instanceof PcBlock && !st.get(PcBlock.ON) && PcBlock.hasPower(w, r.pc)) {
                w.setBlockState(r.pc, st.with(PcBlock.ON, true));
                w.playSound(null, r.pc, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 1.4f);
                w.scheduleBlockTick(r.pc, st.getBlock(), 20);
            }
        }
    }
}
