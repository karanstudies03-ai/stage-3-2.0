package com.gamingsetup;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.function.Function;

public class ModBlocks {
    // PC glows (light level 10) only while its "on" state is true
    public static final Block PC = register("pc",
            s -> new PcBlock(s),
            AbstractBlock.Settings.create().strength(3.0f).nonOpaque()
                    .luminance(state -> state.get(PcBlock.ON) ? 10 : 0));

    public static final Block MONITOR = register("monitor",
            s -> new MonitorBlock(s),
            AbstractBlock.Settings.create().strength(2.0f).nonOpaque());

    public static final Block GAMING_CHAIR = register("gaming_chair",
            s -> new DecorBlock(s, Block.createCuboidShape(2, 0, 2, 14, 16, 14)),
            AbstractBlock.Settings.create().strength(1.5f).nonOpaque());

    public static final Block DESK = register("desk",
            s -> new DecorBlock(s, VoxelShapes.union(
                    Block.createCuboidShape(0, 13, 0, 16, 16, 16),
                    Block.createCuboidShape(0, 0, 0, 2, 13, 2),
                    Block.createCuboidShape(14, 0, 0, 16, 13, 2),
                    Block.createCuboidShape(0, 0, 14, 2, 13, 16),
                    Block.createCuboidShape(14, 0, 14, 16, 13, 16))),
            AbstractBlock.Settings.create().strength(2.0f).nonOpaque());

    public static final Block KEYBOARD = register("keyboard",
            s -> new DecorBlock(s, Block.createCuboidShape(2, 0, 5, 14, 2, 11)),
            AbstractBlock.Settings.create().strength(0.5f).nonOpaque());

    public static final Block MOUSE = register("mouse",
            s -> new DecorBlock(s, Block.createCuboidShape(6, 0, 5, 10, 2, 11)),
            AbstractBlock.Settings.create().strength(0.5f).nonOpaque());

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory,
                                  AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(GamingSetupMod.MOD_ID, name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = factory.apply(settings.registryKey(blockKey));
        Registry.register(Registries.BLOCK, blockKey, block);

        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Registry.register(Registries.ITEM, itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
        return block;
    }

    public static void init() {}
}
