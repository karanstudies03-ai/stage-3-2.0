package com.gamingsetup;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Gaming chair of any colour: right-click to sit. */
public class ChairBlock extends DecorBlock {
    public static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);
    public static final MapCodec<ChairBlock> CHAIR_CODEC = createCodec(ChairBlock::new);

    public ChairBlock(Settings settings) { super(settings, SHAPE); }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CHAIR_CODEC; }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;
        if (player.isSneaking()) return ActionResult.PASS;
        if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            SeatManager.sit(sw, sp, pos, yawOf(state.get(FACING)));
        }
        return ActionResult.SUCCESS;
    }

    /** Minecraft yaw: south 0, west 90, north 180, east 270. */
    private static float yawOf(Direction d) {
        return switch (d) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> 270f;
        };
    }
}
