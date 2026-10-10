package com.gamingsetup;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item CABLE = register("cable");
    public static final Item CAMERA = registerCamera();

    private static Item registerCamera() {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(GamingSetupMod.MOD_ID, "camera"));
        return Registry.register(Registries.ITEM, key, new CameraItem(new Item.Settings().registryKey(key).maxCount(1)));
    }

    private static Item register(String name) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(GamingSetupMod.MOD_ID, name));
        return Registry.register(Registries.ITEM, key, new CableItem(new Item.Settings().registryKey(key).maxCount(1)));
    }

    public static void init() {}
}
