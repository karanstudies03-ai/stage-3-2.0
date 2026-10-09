package com.gamingsetup;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Delivery villager: spawns after a random wait (arrives 10-40 s after the order), rides a horse to the
 * player, shows the price on a white sign above his head, collects thrown emeralds, throws the food,
 * then rides away. Built from vanilla entities (villager, horse, display entities) spawned with commands.
 */
public class DeliveryManager {
    private enum Phase { WAIT_SPAWN, APPROACH, PAYMENT, THROWING, LEAVING }

    // ---- tuning ----
    private static final double SPEED = 0.30;            // blocks per tick (~6 blocks/s)
    private static final double SPAWN_DISTANCE = 30;
    private static final double STOP_DISTANCE = 2.5;
    private static final int MIN_ARRIVAL_SEC = 10, MAX_ARRIVAL_SEC = 40;
    private static final int PAYMENT_TIMEOUT_TICKS = 20 * 180;
    // goggles bar and price sign, relative to the villager's head (adjust if they look off)
    private static final String GOGGLES_SCALE = "[0.55f,0.11f,0.07f]";
    private static final String GOGGLES_POS = "[-0.275f,-0.38f,0.25f]";
    private static final String SIGN_POS = "[0.0f,0.7f,0.0f]";
    private static final String EMERALD_POS = "[-0.6f,0.8f,0.0f]";

    private static class Delivery {
        UUID player;
        OrderManager.Order order;
        Phase phase = Phase.WAIT_SPAWN;
        int timer, phaseTicks, throwCooldown, paid;
        UUID horse = UUID.randomUUID(), villager = UUID.randomUUID(), text = UUID.randomUUID(),
                emerald = UUID.randomUUID(), goggles = UUID.randomUUID();
        double spawnX, spawnZ;
        final Deque<ItemStack> throwQueue = new ArrayDeque<>();
    }

    private static final Map<UUID, Delivery> ACTIVE = new HashMap<>();
    private static final Random RNG = new Random();

    // ---------------------------------------------------------------- public API
    public static boolean start(UUID playerId, OrderManager.Order order) {
        if (ACTIVE.containsKey(playerId)) return false;
        Delivery d = new Delivery();
        d.player = playerId;
        d.order = order;
        int arriveSec = MIN_ARRIVAL_SEC + RNG.nextInt(MAX_ARRIVAL_SEC - MIN_ARRIVAL_SEC + 1);   // 10..40
        int travelTicks = (int) (SPAWN_DISTANCE / SPEED);
        d.timer = Math.max(0, arriveSec * 20 - travelTicks);
        ACTIVE.put(playerId, d);
        return true;
    }

    /** On server start: remove delivery entities left over from a previous session. */
    public static void cleanup(MinecraftServer server) {
        ACTIVE.clear();
        for (ServerWorld w : server.getWorlds()) run(server, w, "kill @e[tag=gs_delivery]");
    }

    // ---------------------------------------------------------------- tick
    public static void tick(MinecraftServer server) {
        for (Delivery d : new ArrayList<>(ACTIVE.values())) {
            ServerWorld world = null;
            PlayerEntity player = null;
            for (ServerWorld w : server.getWorlds()) {
                PlayerEntity p = w.getPlayerByUuid(d.player);
                if (p != null) { world = w; player = p; break; }
            }
            if (player == null || !player.isAlive()) { finish(server, d); continue; }

            if (d.phase == Phase.WAIT_SPAWN) {
                if (--d.timer <= 0) {
                    if (spawn(server, world, player, d)) { d.phase = Phase.APPROACH; d.phaseTicks = 0; }
                    else { finish(server, d); }
                }
                continue;
            }

            if (!(world.getEntity(d.horse) instanceof LivingEntity horse)
                    || !(world.getEntity(d.villager) instanceof LivingEntity villager)) {
                finish(server, d);     // killed, unloaded, or player changed dimension
                continue;
            }

            double dx = player.getX() - horse.getX(), dz = player.getZ() - horse.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            switch (d.phase) {
                case APPROACH -> {
                    face(world, d, horse, villager, yawTo(dx, dz));
                    d.phaseTicks++;
                    if (d.phaseTicks == 20 * 40) {      // stuck for 40 s: jump next to the player
                        run(server, world, "tp " + d.horse + " " + fmt(player.getX() + 3) + " " + fmt(player.getY()) + " " + fmt(player.getZ()));
                    }
                    if (dist <= STOP_DISTANCE) {
                        drive(horse, 0, 0);
                        d.phase = Phase.PAYMENT;
                        d.phaseTicks = 0;
                        run(server, world, "data modify entity " + d.text + " view_range set value 1.0f");
                        run(server, world, "data modify entity " + d.emerald + " view_range set value 1.0f");
                        sound(server, world, horse, "entity.villager.yes");
                    } else {
                        drive(horse, dx / dist * SPEED, dz / dist * SPEED);
                    }
                }
                case PAYMENT -> {
                    drive(horse, 0, 0);
                    face(world, d, horse, villager, yawTo(dx, dz));
                    Box box = new Box(horse.getX() - 5, horse.getY() - 3, horse.getZ() - 5,
                            horse.getX() + 5, horse.getY() + 4, horse.getZ() + 5);
                    boolean changed = false;
                    for (ItemEntity ie : world.getEntitiesByClass(ItemEntity.class, box, e -> e.getStack().isOf(Items.EMERALD))) {
                        d.paid += ie.getStack().getCount();
                        ie.discard();
                        changed = true;
                    }
                    int price = d.order.totalPrice();
                    if (d.paid >= price) {
                        int change = d.paid - price;
                        if (change > 0) queueStacks(d.throwQueue, Items.EMERALD, change);
                        for (Map.Entry<FoodMenu.Entry, Integer> e : d.order.items().entrySet())
                            d.throwQueue.add(new ItemStack(e.getKey().item(), e.getValue()));
                        setText(server, world, d, "Thank you!");
                        sound(server, world, horse, "entity.villager.celebrate");
                        d.phase = Phase.THROWING;
                        d.throwCooldown = 10;
                    } else if (++d.phaseTicks > PAYMENT_TIMEOUT_TICKS) {
                        if (d.paid > 0) queueStacks(d.throwQueue, Items.EMERALD, d.paid);   // give money back
                        setText(server, world, d, "No payment. Bye!");
                        sound(server, world, horse, "entity.villager.no");
                        d.phase = Phase.THROWING;
                        d.throwCooldown = 10;
                    } else if (changed) {
                        setText(server, world, d, label(price - d.paid));
                    }
                }
                case THROWING -> {
                    drive(horse, 0, 0);
                    face(world, d, horse, villager, yawTo(dx, dz));
                    if (--d.throwCooldown <= 0) {
                        ItemStack stack = d.throwQueue.poll();
                        if (stack == null) {
                            d.phase = Phase.LEAVING;
                            d.phaseTicks = 0;
                        } else {
                            double ox = villager.getX(), oy = villager.getY() + 1.5, oz = villager.getZ();
                            ItemEntity ie = new ItemEntity(world, ox, oy, oz, stack);
                            double vx = player.getX() - ox, vy = player.getY() + 1.0 - oy, vz = player.getZ() - oz;
                            double len = Math.max(0.1, Math.sqrt(vx * vx + vy * vy + vz * vz));
                            ie.setVelocity(vx / len * 0.3, vy / len * 0.3 + 0.12, vz / len * 0.3);
                            ie.setPickupDelay(25);
                            world.spawnEntity(ie);
                            d.throwCooldown = 8;
                        }
                    }
                }
                case LEAVING -> {
                    double sx = d.spawnX - horse.getX(), sz = d.spawnZ - horse.getZ();
                    double sd = Math.sqrt(sx * sx + sz * sz);
                    face(world, d, horse, villager, yawTo(sx, sz));
                    if (sd < 3 || ++d.phaseTicks > 20 * 30) finish(server, d);
                    else drive(horse, sx / sd * SPEED, sz / sd * SPEED);
                }
                default -> { }
            }
        }
    }

    // ---------------------------------------------------------------- spawning
    private static boolean spawn(MinecraftServer server, ServerWorld world, PlayerEntity player, Delivery d) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            int sx = (int) Math.floor(player.getX() + Math.cos(ang) * SPAWN_DISTANCE);
            int sz = (int) Math.floor(player.getZ() + Math.sin(ang) * SPAWN_DISTANCE);
            if (!world.isChunkLoaded(new BlockPos(sx, 0, sz))) continue;
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
            if (!world.getFluidState(new BlockPos(sx, top - 1, sz)).isEmpty()) continue;
            d.spawnX = sx + 0.5;
            d.spawnZ = sz + 0.5;

            String zero = "left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f]";
            String tag = "Tags:[\"gs_delivery\"]";
            String sign = "{id:\"minecraft:text_display\",UUID:" + nbtUuid(d.text) + "," + tag
                    + ",billboard:\"center\",background:-1,view_range:0.0f,text:{text:\"" + label(d.order.totalPrice()) + "\",color:\"black\"},"
                    + "transformation:{" + zero + ",scale:[0.5f,0.5f,0.5f],translation:" + SIGN_POS + "}}";
            String emerald = "{id:\"minecraft:item_display\",UUID:" + nbtUuid(d.emerald) + "," + tag
                    + ",billboard:\"center\",view_range:0.0f,item:{id:\"minecraft:emerald\",count:1},"
                    + "transformation:{" + zero + ",scale:[0.4f,0.4f,0.4f],translation:" + EMERALD_POS + "}}";
            String goggles = "{id:\"minecraft:block_display\",UUID:" + nbtUuid(d.goggles) + "," + tag
                    + ",block_state:{Name:\"minecraft:black_concrete\"},"
                    + "transformation:{" + zero + ",scale:" + GOGGLES_SCALE + ",translation:" + GOGGLES_POS + "}}";
            String villager = "{id:\"minecraft:villager\",UUID:" + nbtUuid(d.villager) + "," + tag
                    + ",NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,"
                    + "VillagerData:{type:\"minecraft:plains\",profession:\"minecraft:none\",level:1},"
                    + "Passengers:[" + sign + "," + emerald + "," + goggles + "]}";
            String cmd = "summon minecraft:horse " + fmt(sx + 0.5) + " " + fmt(top) + " " + fmt(sz + 0.5)
                    + " {UUID:" + nbtUuid(d.horse) + "," + tag
                    + ",NoAI:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Passengers:[" + villager + "]}";
            run(server, world, cmd);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers
    private static void finish(MinecraftServer server, Delivery d) {
        for (ServerWorld w : server.getWorlds()) {
            for (UUID id : new UUID[]{d.goggles, d.emerald, d.text, d.villager, d.horse}) {
                Entity e = w.getEntity(id);
                if (e != null) e.discard();
            }
        }
        ACTIVE.remove(d.player);
        OrderManager.clear(d.player);
    }

    private static void drive(LivingEntity horse, double vx, double vz) {
        Vec3d v = horse.getVelocity();
        double vy = v.y;
        if ((vx != 0 || vz != 0) && horse.horizontalCollision && horse.isOnGround()) vy = 0.45;   // hop over blocks
        horse.setVelocity(vx, vy, vz);
    }

    private static void face(ServerWorld world, Delivery d, LivingEntity horse, LivingEntity villager, float yaw) {
        for (LivingEntity e : new LivingEntity[]{horse, villager}) {
            e.setYaw(yaw);
            e.setBodyYaw(yaw);
            e.setHeadYaw(yaw);
        }
        Entity goggles = world.getEntity(d.goggles);
        if (goggles != null) goggles.setYaw(yaw);
    }

    private static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static void queueStacks(Deque<ItemStack> q, net.minecraft.item.Item item, int count) {
        while (count > 0) {
            int n = Math.min(64, count);
            q.add(new ItemStack(item, n));
            count -= n;
        }
    }

    private static String label(int emeralds) {
        return emeralds + (emeralds == 1 ? " Emerald" : " Emeralds");
    }

    private static void setText(MinecraftServer server, ServerWorld world, Delivery d, String text) {
        run(server, world, "data modify entity " + d.text + " text set value {text:\"" + text + "\",color:\"black\"}");
    }

    private static void sound(MinecraftServer server, ServerWorld world, Entity at, String id) {
        run(server, world, "playsound minecraft:" + id + " neutral @a " + fmt(at.getX()) + " " + fmt(at.getY()) + " " + fmt(at.getZ()));
    }

    private static String nbtUuid(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static void run(MinecraftServer server, ServerWorld world, String command) {
        ServerCommandSource source = server.getCommandSource().withWorld(world).withSilent();
        try {
            server.getCommandManager().getDispatcher().execute(command, source);
        } catch (Exception e) {
            System.err.println("[gamingsetup] command failed: " + command + " -> " + e.getMessage());
        }
    }
}
