package com.gamingsetup;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

/** Foods on sale, price in emeralds per item. Enchanted golden apple is deliberately NOT sold. */
public final class FoodMenu {
    public record Entry(String id, Item item, int price) {}

    public static final List<Entry> ENTRIES = List.of(
            new Entry("bread", Items.BREAD, 1),
            new Entry("apple", Items.APPLE, 1),
            new Entry("cookie", Items.COOKIE, 1),
            new Entry("baked_potato", Items.BAKED_POTATO, 1),
            new Entry("cooked_chicken", Items.COOKED_CHICKEN, 2),
            new Entry("pumpkin_pie", Items.PUMPKIN_PIE, 2),
            new Entry("cooked_mutton", Items.COOKED_MUTTON, 2),
            new Entry("cooked_porkchop", Items.COOKED_PORKCHOP, 3),
            new Entry("cooked_beef", Items.COOKED_BEEF, 3),
            new Entry("golden_carrot", Items.GOLDEN_CARROT, 4),
            new Entry("golden_apple", Items.GOLDEN_APPLE, 16)
    );

    public static Entry find(String id) {
        for (Entry e : ENTRIES) if (e.id().equals(id)) return e;
        return null;
    }

    private FoodMenu() {}
}
