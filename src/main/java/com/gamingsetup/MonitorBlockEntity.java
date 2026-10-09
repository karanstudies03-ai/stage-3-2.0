package com.gamingsetup;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;

/** Remembers which PC this monitor is cabled to. */
public class MonitorBlockEntity extends BlockEntity {
    private BlockPos pcPos;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITOR, pos, state);
    }

    public BlockPos getPcPos() { return pcPos; }

    public void setPcPos(BlockPos pcPos) {
        this.pcPos = pcPos;
        markDirty();
    }

    @Override
    protected void readData(ReadView view) {
        pcPos = view.read("PcPos", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void writeData(WriteView view) {
        if (pcPos != null) view.put("PcPos", BlockPos.CODEC, pcPos);
    }
}
