package com.gamingsetup;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;

/** Client side: while sitting on a chair you can only look 90 degrees left/right (180 degrees total). */
public class GamingSetupClient implements ClientModInitializer {
    private static boolean wasSeated = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(PhotoTaker::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity p = client.player;
            if (p == null) { wasSeated = false; return; }
            Entity vehicle = p.getVehicle();
            if (vehicle instanceof ArmorStandEntity stand && stand.isMarker()) {
                float base = stand.getYaw();                 // direction the chair faces
                if (!wasSeated) {
                    p.setYaw(base);
                    p.setHeadYaw(base);
                    wasSeated = true;
                }
                float diff = MathHelper.wrapDegrees(p.getYaw() - base);
                float clamped = MathHelper.clamp(diff, -90.0f, 90.0f);
                if (clamped != diff) {
                    p.setYaw(base + clamped);
                    p.setHeadYaw(base + clamped);
                }
            } else {
                wasSeated = false;
            }
        });
    }
}
