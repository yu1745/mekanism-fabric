package mekanism.common.block.basic;

import mekanism.common.block.BlockMekanism;
import mekanism.common.resource.BlockResourceInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class BlockResource extends BlockMekanism {

    @NotNull
    private final BlockResourceInfo resource;

    //TODO: Isn't as "generic"? So make it be from one BlockType thing?
    public BlockResource(@NotNull BlockResourceInfo resource) {
        super(resource.modifyProperties(BlockBehaviour.Properties.of().requiresCorrectToolForDrops()));
        this.resource = resource;
    }

    @NotNull
    public BlockResourceInfo getResourceInfo() {
        return resource;
    }

    //Fabric port delta: NeoForge's IBlockExtension#isPortalFrame hook (used by PortalShape) has no vanilla equivalent,
    // so Mekanism frame blocks cannot extend vanilla nether-portal frame detection on Fabric.

}