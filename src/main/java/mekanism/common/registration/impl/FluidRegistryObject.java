package mekanism.common.registration.impl;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.fluid.FluidStack;
import mekanism.api.text.IHasTextComponent;
import mekanism.api.text.IHasTranslationKey;
import mekanism.common.registration.MekanismDeferredHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;

/**
 * The four registered pieces of one Mekanism fluid family. NeoForge also had a registry entry for the {@code FluidType};
 * vanilla has no such registry, so the metadata is carried as a plain {@link FluidDeferredRegister.MekanismFluidType}
 * value while the still, flowing, block, and bucket entries stay canonical registry holders.
 */
@NothingNullByDefault
public class FluidRegistryObject<STILL extends Fluid, FLOWING extends Fluid, BLOCK extends LiquidBlock, BUCKET extends BucketItem>
    extends MekanismDeferredHolder<Fluid, STILL> implements IHasTextComponent, IHasTranslationKey {

    private final FluidDeferredRegister.MekanismFluidType fluidType;
    private final MekanismDeferredHolder<Fluid, FLOWING> flowing;
    private final ItemRegistryObject<BUCKET> bucket;
    private final MekanismDeferredHolder<Block, BLOCK> block;

    FluidRegistryObject(FluidDeferredRegister.MekanismFluidType fluidType, MekanismDeferredHolder<Fluid, STILL> still,
          MekanismDeferredHolder<Fluid, FLOWING> flowing, ItemRegistryObject<BUCKET> bucket, MekanismDeferredHolder<Block, BLOCK> block) {
        //Default our fluid to being the still variant
        super(still);
        this.fluidType = fluidType;
        this.flowing = flowing;
        this.bucket = bucket;
        this.block = block;
    }

    public FluidDeferredRegister.MekanismFluidType getFluidType() {
        return fluidType;
    }

    public MekanismDeferredHolder<Fluid, FLOWING> getFlowingFluid() {
        return flowing;
    }

    public BLOCK getBlock() {
        return block.get();
    }

    public ItemRegistryObject<BUCKET> getBucket() {
        return bucket;
    }

    public FluidStack asStack(int amount) {
        return new FluidStack(get(), amount);
    }

    @Override
    public Component getTextComponent() {
        return getFluidType().getDescription(asStack(1));
    }

    @Override
    public String getTranslationKey() {
        return getFluidType().getDescriptionId();
    }
}
