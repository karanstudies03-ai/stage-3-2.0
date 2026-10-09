package com.gamingsetup;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server side: validates orders. Stage 3 will spawn the delivery villager from here. */
public class OrderManager {
    public record Order(Map<FoodMenu.Entry, Integer> items, int totalPrice) {}

    private static final Map<UUID, Order> PENDING = new HashMap<>();

    public static Order getPending(UUID player) { return PENDING.get(player); }
    public static void clear(UUID player) { PENDING.remove(player); }

    public static void place(ServerPlayerEntity player, String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > 500) { reject(player); return; }
        Map<FoodMenu.Entry, Integer> items = new LinkedHashMap<>();
        int total = 0;
        try {
            for (String part : raw.split(",")) {
                String[] kv = part.split(":");
                FoodMenu.Entry entry = FoodMenu.find(kv[0]);
                int qty = Integer.parseInt(kv[1]);
                if (entry == null || qty < 1 || qty > 64 || items.containsKey(entry)) { reject(player); return; }
                items.put(entry, qty);
                total += entry.price() * qty;
            }
        } catch (Exception e) {
            reject(player);
            return;
        }
        Order order = new Order(items, total);
        if (!DeliveryManager.start(player.getUuid(), order)) {
            player.sendMessage(Text.literal("You already have an order on the way."), true);
            return;
        }
        PENDING.put(player.getUuid(), order);
        // The price is NOT shown in chat: the delivery villager shows it on his sign.
        player.sendMessage(Text.literal("Order placed! Your delivery is on the way."), true);
    }

    private static void reject(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("Invalid order."), true);
    }
}
