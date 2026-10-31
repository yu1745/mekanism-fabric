package mekanism.common.integration.lookingat.wthit;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import mekanism.api.MekanismAPI;
import mekanism.common.block.BlockBounding;
import mekanism.common.entity.EntityRobit;
import mekanism.common.integration.lookingat.LookingAtUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port delta: the official plugin implements the single {@code IWailaPlugin} interface with one
 * {@code register(IRegistrar)} method, which is the WTHIT 4.x contract. WTHIT 12 splits that contract into a common
 * half ({@link IWailaCommonPlugin}: data providers, data types, config, runs on both sides) and a client half
 * ({@link IWailaClientPlugin}: tooltip components, listeners, overrides). A class that still implements
 * {@code IWailaPlugin} is detected as a legacy plugin and WTHIT throws on startup, so this class implements both
 * halves and the original single {@code register} body is split between them.
 */
public class MekanismWTHITPlugin implements IWailaCommonPlugin, IWailaClientPlugin {

    static final ResourceLocation MEK_DATA = ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "wthit_data");

    @Override
    public void register(ICommonRegistrar registrar) {
        //Fabric port proof line: the client gates grep this exact marker to confirm the plugin ran.
        MekanismAPI.logger.info("MEKANISM_FABRIC_WTHIT_PROOF data={}", MEK_DATA);
        registrar.blockData(WTHITDataProvider.INSTANCE, BlockEntity.class);
        registrar.entityData(WTHITEntityDataProvider.INSTANCE, EntityRobit.class);
        registrar.featureConfig(LookingAtUtils.ENERGY, true);
        registrar.featureConfig(LookingAtUtils.FLUID, true);
        registrar.featureConfig(LookingAtUtils.CHEMICAL, true);
        registrar.dataType(WTHITLookingAtHelper.TYPE, WTHITLookingAtHelper.STREAM_CODEC);
    }

    @Override
    public void register(IClientRegistrar registrar) {
        //WTHITTooltipRenderer implements both provider interfaces, so the target type has to be spelled out.
        registrar.body((IEntityComponentProvider) WTHITTooltipRenderer.INSTANCE, EntityRobit.class);
        registrar.body((IBlockComponentProvider) WTHITTooltipRenderer.INSTANCE, Block.class);
        registrar.eventListener(new MekanismTooltipEventListener());
        registrar.override(new MekanismBoundingOverride(), BlockBounding.class);
    }

    private static final class MekanismBoundingOverride implements IBlockComponentProvider {

        @Nullable
        @Override
        public BlockState getOverride(IBlockAccessor accessor, IPluginConfig config) {
            BlockHitResult result = accessor.getBlockHitResult();
            if (result.getType() != Type.MISS) {
                Level level = accessor.getWorld();
                BlockPos mainPos = BlockBounding.getMainBlockPos(level, result.getBlockPos());
                if (mainPos != null) {
                    return level.getBlockState(mainPos);
                }
            }
            return null;
        }
    }
}
