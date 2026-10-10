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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Colour variants. The original "gaming_chair" is the red chair and the original "desk" is the dark oak desk;
 * this class adds the other 15 wool colours and the other 11 woods.
 */
public class ModVariants {
    public static final String[] WOOLS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "black"};
    public static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry",
            "pale_oak", "bamboo", "crimson", "warped"};

    public static final List<Block> CHAIRS = new ArrayList<>();
    public static final List<Block> DESKS = new ArrayList<>();

    private static final VoxelShape DESK_SHAPE = VoxelShapes.union(
            Block.createCuboidShape(0, 13, 0, 16, 16, 16),
            Block.createCuboidShape(0, 0, 0, 2, 13, 2),
            Block.createCuboidShape(14, 0, 0, 16, 13, 2),
            Block.createCuboidShape(0, 0, 14, 2, 13, 16),
            Block.createCuboidShape(14, 0, 14, 16, 13, 16));

    public static void init() {
        for (String wool : WOOLS) {
            CHAIRS.add(register("gaming_chair_" + wool, s -> new ChairBlock(s), 1.5f));
        }
        for (String wood : WOODS) {
            DESKS.add(register("desk_" + wood, s -> new DecorBlock(s, DESK_SHAPE), 2.0f));
        }
    }

    private static Block register(String name, Function<AbstractBlock.Settings, Block> factory, float strength) {
        Identifier id = Identifier.of(GamingSetupMod.MOD_ID, name);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = factory.apply(AbstractBlock.Settings.create().strength(strength).nonOpaque().registryKey(blockKey));
        Registry.register(Registries.BLOCK, blockKey, block);
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Registry.register(Registries.ITEM, itemKey,
                new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
        return block;
    }
}
