package mekanism.common.tile.interfaces;

import mekanism.api.Chunk3D;
import mekanism.common.base.ILevelProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ITileWrapper extends ILevelProvider {

    /**
     * Fabric port delta: default rather than an abstract redeclaration. A mod interface cannot require a mapped vanilla method name: the interface itself is not in
     * a Minecraft supertype chain, so the remapper keeps {@code getBlockPos} while {@code BlockEntity#getBlockPos} is renamed, leaving the abstract method
     * unimplemented at runtime. Plain (non block entity) implementers override this default.
     */
    default BlockPos getBlockPos() {
        return ((BlockEntity) this).getBlockPos();
    }

    default GlobalPos getTileGlobalPos() {
        return GlobalPos.of(getLevel().dimension(), getBlockPos());
    }

    default Chunk3D getTileChunk() {
        return new Chunk3D(getTileGlobalPos());
    }
}