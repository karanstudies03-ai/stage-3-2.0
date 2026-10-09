package com.gamingsetup;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {
    public static final BlockEntityType<MonitorBlockEntity> MONITOR = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(GamingSetupMod.MOD_ID, "monitor"),
            FabricBlockEntityTypeBuilder.create(MonitorBlockEntity::new, ModBlocks.MONITOR).build());

    public static void init() {}
}
