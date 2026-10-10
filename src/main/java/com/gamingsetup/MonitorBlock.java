package com.gamingsetup;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class MonitorBlock extends BlockWithEntity {
    public static final MapCodec<MonitorBlock> CODEC = createCodec(MonitorBlock::new);
    public static final EnumProperty<Screen> SCREEN = EnumProperty.of("screen", Screen.class);
    private static final VoxelShape SHAPE_NS = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
    private static final VoxelShape SHAPE_EW = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

    public enum Screen implements StringIdentifiable {
        NO_PC("no_pc"),   // not cabled to a PC  -> "PC not connected"
        OFF("off"),       // cabled, PC is off   -> black screen
        SLEEP("sleep"),   // PC on but this monitor asleep -> black, right-click wakes it
        ON("on");         // cabled, PC is on    -> desktop (Stage 2 adds right-click fullscreen)

        private final String name;
        Screen(String name) { this.name = name; }
        @Override public String asString() { return name; }
    }

    public MonitorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(HorizontalFacingBlock.FACING, Direction.NORTH).with(SCREEN, Screen.NO_PC));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HorizontalFacingBlock.FACING, SCREEN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        Direction d = state.get(HorizontalFacingBlock.FACING);
        return (d == Direction.NORTH || d == Direction.SOUTH) ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    /** Called by the cable: connect this monitor to a PC. */
    public static void link(World world, BlockPos monitorPos, BlockPos pcPos) {
        if (world.getBlockEntity(monitorPos) instanceof MonitorBlockEntity be) {
            be.setPcPos(pcPos.toImmutable());
            world.scheduleBlockTick(monitorPos, world.getBlockState(monitorPos).getBlock(), 1);
        }
    }

    // Poll the PC every half second so the screen follows its state (on / off / PC removed)
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!(world.getBlockEntity(pos) instanceof MonitorBlockEntity be)) return;
        BlockPos pcPos = be.getPcPos();
        Screen target;
        if (pcPos == null) {
            target = Screen.NO_PC;
        } else if (!world.isChunkLoaded(pcPos)) {
            target = Screen.OFF;
        } else {
            BlockState pc = world.getBlockState(pcPos);
            if (pc.getBlock() instanceof PcBlock) {
                target = pc.get(PcBlock.ON) ? Screen.ON : Screen.OFF;
            } else {            // PC was broken -> cable is dead
                be.setPcPos(null);
                target = Screen.NO_PC;
            }
        }
        if (target == Screen.ON && be.isSleeping()) target = Screen.SLEEP;
        else if (target != Screen.ON && target != Screen.SLEEP && be.isSleeping()) be.setSleeping(false);
        if (state.get(SCREEN) != target) world.setBlockState(pos, state.with(SCREEN, target), Block.NOTIFY_ALL);
        if (be.getPcPos() != null) world.scheduleBlockTick(pos, this, 10);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof CableItem) {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp) CableItem.clickMonitor(sp, pos);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            Screen shown = state.get(SCREEN);
            if (shown == Screen.ON) ClientHooks.openMonitor(pos, false);         // fullscreen, Esc closes
            else if (shown == Screen.SLEEP) ClientHooks.openMonitor(pos, true);  // wake up and open
            return ActionResult.SUCCESS;
        }
        switch (state.get(SCREEN)) {
            case NO_PC -> player.sendMessage(Text.literal("PC not connected. Use a Link Cable: monitor first, then PC."), true);
            case OFF -> player.sendMessage(Text.literal("The PC is off. Right-click the PC to start it."), true);
            case ON, SLEEP -> { }
        }
        return ActionResult.SUCCESS;
    }
}
