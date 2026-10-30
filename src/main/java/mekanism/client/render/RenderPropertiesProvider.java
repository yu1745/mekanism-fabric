package mekanism.client.render;

import mekanism.common.block.BlockBounding;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Mekanism-owned block particle behavior, invoked from the native client particle engine. */
public class RenderPropertiesProvider {

    private RenderPropertiesProvider() {
    }

    public static boolean addBoundingHitEffects(BlockState state, ClientLevel world, BlockPos pos, Direction side, ParticleEngine manager) {
        BlockPos mainPos = BlockBounding.getMainBlockPos(world, pos);
        if (mainPos == null) {
            return false;
        }
        BlockState mainState = world.getBlockState(mainPos);
        if (mainState.isAir()) {
            return false;
        }
        //Vanilla TerrainParticle obtains the static particle sprite from the supplied main state.
        AABB axisalignedbb = state.getShape(world, pos).bounds();
        double x = pos.getX() + world.random.nextDouble() * (axisalignedbb.maxX - axisalignedbb.minX - 0.2) + 0.1 + axisalignedbb.minX;
        double y = pos.getY() + world.random.nextDouble() * (axisalignedbb.maxY - axisalignedbb.minY - 0.2) + 0.1 + axisalignedbb.minY;
        double z = pos.getZ() + world.random.nextDouble() * (axisalignedbb.maxZ - axisalignedbb.minZ - 0.2) + 0.1 + axisalignedbb.minZ;
        switch (side) {
            case DOWN -> y = pos.getY() + axisalignedbb.minY - 0.1;
            case UP -> y = pos.getY() + axisalignedbb.maxY + 0.1;
            case NORTH -> z = pos.getZ() + axisalignedbb.minZ - 0.1;
            case SOUTH -> z = pos.getZ() + axisalignedbb.maxZ + 0.1;
            case WEST -> x = pos.getX() + axisalignedbb.minX - 0.1;
            case EAST -> x = pos.getX() + axisalignedbb.maxX + 0.1;
        }
        manager.add(new TerrainParticle(world, x, y, z, 0, 0, 0, mainState)
              .setPower(0.2F).scale(0.6F));
        return true;
    }

    public static void addDestroyEffects(BlockState state, ClientLevel world, BlockPos pos, ParticleEngine manager) {
        //Copy of ParticleManager#addBlockDestroyEffects, but removes the minimum number of particles each voxel shape produces
        state.getShape(world, pos).forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double xDif = Math.min(1, maxX - minX);
            double yDif = Math.min(1, maxY - minY);
            double zDif = Math.min(1, maxZ - minZ);
            //Don't force the counts to be at least two
            int xCount = Mth.ceil(xDif / 0.25);
            int yCount = Mth.ceil(yDif / 0.25);
            int zCount = Mth.ceil(zDif / 0.25);
            if (xCount > 0 && yCount > 0 && zCount > 0) {
                for (int x = 0; x < xCount; x++) {
                    for (int y = 0; y < yCount; y++) {
                        for (int z = 0; z < zCount; z++) {
                            double d4 = (x + 0.5) / xCount;
                            double d5 = (y + 0.5) / yCount;
                            double d6 = (z + 0.5) / zCount;
                            double d7 = d4 * xDif + minX;
                            double d8 = d5 * yDif + minY;
                            double d9 = d6 * zDif + minZ;
                            manager.add(new TerrainParticle(world, pos.getX() + d7, pos.getY() + d8,
                                  pos.getZ() + d9, d4 - 0.5, d5 - 0.5, d6 - 0.5, state));
                        }
                    }
                }
            }
        });
    }
}
