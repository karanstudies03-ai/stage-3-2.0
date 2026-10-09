package com.gamingsetup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public class GamingSetupMod implements ModInitializer {
    public static final String MOD_ID = "gamingsetup";

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.init();

        PayloadTypeRegistry.playC2S().register(OrderPayload.ID, OrderPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OrderPayload.ID,
                (payload, context) -> OrderManager.place(context.player(), payload.order()));

        ServerTickEvents.END_SERVER_TICK.register(DeliveryManager::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(DeliveryManager::cleanup);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(ModBlocks.PC);
            entries.add(ModBlocks.MONITOR);
            entries.add(ModBlocks.GAMING_CHAIR);
            entries.add(ModBlocks.DESK);
            entries.add(ModBlocks.KEYBOARD);
            entries.add(ModBlocks.MOUSE);
            entries.add(ModItems.CABLE);
        });
    }
}
