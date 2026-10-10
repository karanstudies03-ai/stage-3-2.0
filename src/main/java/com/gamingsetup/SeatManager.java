package com.gamingsetup;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Sitting: an invisible marker armor stand is placed on the chair and the player rides it.
 * The stand removes itself when the player gets up (Shift) or the chair is broken.
 */
public class SeatManager {
    /** Height of the seat stand above the chair block's base. If you float or sink, change this number. */
    private static final double SEAT_Y = 0.625;

    private static final Map<UUID, BlockPos> SEATS = new HashMap<>();

    public static void sit(ServerWorld world, ServerPlayerEntity player, BlockPos chairPos, float yaw) {
        if (player.hasVehicle()) return;
        MinecraftServer server = world.getServer();

        // already occupied?
        for (Map.Entry<UUID, BlockPos> e : new ArrayList<>(SEATS.entrySet())) {
            if (!e.getValue().equals(chairPos)) continue;
            Entity stand = world.getEntity(e.getKey());
            if (stand != null && stand.hasPassengers()) {
                player.sendMessage(Text.literal("Someone is already sitting here."), true);
                return;
            }
            if (stand != null) stand.discard();
            SEATS.remove(e.getKey());
        }

        UUID id = UUID.randomUUID();
        String cmd = "summon minecraft:armor_stand " + fmt(chairPos.getX() + 0.5) + " " + fmt(chairPos.getY() + SEAT_Y)
                + " " + fmt(chairPos.getZ() + 0.5) + " {UUID:" + nbtUuid(id)
                + ",Tags:[\"gs_seat\"],Invisible:1b,Marker:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,"
                + "Rotation:[" + yaw + "f,0f]}";
        run(server, world, cmd);
        run(server, world, "ride " + player.getUuid() + " mount " + id);
        SEATS.put(id, chairPos.toImmutable());
        player.sendMessage(Text.literal("Press Shift to stand up."), true);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 5 != 0 || SEATS.isEmpty()) return;
        for (Map.Entry<UUID, BlockPos> e : new ArrayList<>(SEATS.entrySet())) {
            Entity stand = null;
            ServerWorld where = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity found = w.getEntity(e.getKey());
                if (found != null) { stand = found; where = w; break; }
            }
            if (stand == null) { SEATS.remove(e.getKey()); continue; }
            boolean chairGone = !(where.getBlockState(e.getValue()).getBlock() instanceof ChairBlock);
            if (!stand.hasPassengers() || chairGone) {
                stand.discard();
                SEATS.remove(e.getKey());
            }
        }
    }

    /** On server start: remove seat stands left behind by a crash or by logging out while sitting. */
    public static void cleanup(MinecraftServer server) {
        SEATS.clear();
        for (ServerWorld w : server.getWorlds()) run(server, w, "kill @e[tag=gs_seat]");
    }

    private static String nbtUuid(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.3f", v); }

    private static void run(MinecraftServer server, ServerWorld world, String command) {
        ServerCommandSource source = server.getCommandSource().withWorld(world).withSilent();
        try {
            server.getCommandManager().getDispatcher().execute(command, source);
        } catch (Exception e) {
            System.err.println("[gamingsetup] command failed: " + command + " -> " + e.getMessage());
        }
    }
}
