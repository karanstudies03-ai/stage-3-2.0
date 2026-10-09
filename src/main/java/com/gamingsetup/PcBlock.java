package com.gamingsetup;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class PcBlock extends HorizontalFacingBlock {
    public static final MapCodec<PcBlock> CODEC = createCodec(PcBlock::new);
    public static final BooleanProperty ON = BooleanProperty.of("on");
    public static final int POWER_RADIUS = 3;
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 15, 14);

    public PcBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(ON, false));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, ON); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return SHAPE;
    }

    /** True if a redstone block is within POWER_RADIUS blocks of the PC. */
    public static boolean hasPower(World world, BlockPos pos) {
        for (BlockPos p : BlockPos.iterate(
                pos.add(-POWER_RADIUS, -POWER_RADIUS, -POWER_RADIUS),
                pos.add(POWER_RADIUS, POWER_RADIUS, POWER_RADIUS))) {
            if (world.getBlockState(p).isOf(Blocks.REDSTONE_BLOCK)) return true;
        }
        return false;
    }

    // Cable in hand: second click of the link (monitor first, then PC)
    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof CableItem) {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp) CableItem.clickPc(sp, world, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    // Right-click: turn the PC on / off
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        if (!state.get(ON)) {
            if (!hasPower(world, pos)) {
                player.sendMessage(Text.literal("No power! Place a redstone block within " + POWER_RADIUS + " blocks."), true);
                return ActionResult.CONSUME;
            }
            world.setBlockState(pos, state.with(ON, true));
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 1.4f);
            world.scheduleBlockTick(pos, this, 20);
        } else {
            world.setBlockState(pos, state.with(ON, false));
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        }
        return ActionResult.SUCCESS;
    }

    // Every second: if the redstone block was removed, shut the PC down
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(ON)) return;
        if (!hasPower(world, pos)) {
            world.setBlockState(pos, state.with(ON, false));
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 0.8f);
        } else {
            world.scheduleBlockTick(pos, this, 20);
        }
    }
}
