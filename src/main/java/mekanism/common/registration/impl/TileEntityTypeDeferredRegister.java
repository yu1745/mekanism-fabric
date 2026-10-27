package mekanism.common.registration.impl;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.security.IBlockSecurityUtils;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.Attributes.AttributeComputerIntegration;
import mekanism.common.block.attribute.Attributes.AttributeSecurity;
import mekanism.common.capabilities.resolver.manager.ChemicalHandlerManager;
import mekanism.common.capabilities.resolver.manager.HeatHandlerManager;
import mekanism.common.integration.computer.ComputerCapabilityHelper;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject.CapabilityData;
import mekanism.common.tile.base.CapabilityTileEntity;
import mekanism.common.tile.base.TileEntityMekanism;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

public class TileEntityTypeDeferredRegister extends MekanismDeferredRegister<BlockEntityType<?>> {

    public TileEntityTypeDeferredRegister(String modid) {
        super(Registries.BLOCK_ENTITY_TYPE, modid, TileEntityTypeRegistryObject::new);
    }

    public <BE extends TileEntityMekanism> BlockEntityTypeBuilder<BE> mekBuilder(MekanismDeferredHolder<Block, ?> block, BlockEntitySupplier<? extends BE> factory) {
        BooleanSupplier hasSecurity = () -> Attribute.has(block, AttributeSecurity.class);
        BlockEntityTypeBuilder<BE> builder = this.<BE>builder(block, factory)
              //Evaluate optional providers only after the actual blocks and types have registered.
              .withSimple(IBlockSecurityUtils.INSTANCE.ownerCapability(), hasSecurity)
              .withSimple(IBlockSecurityUtils.INSTANCE.securityCapability(), hasSecurity)
              .with(ChemicalHandlerManager.CHEMICAL_HANDLER, CapabilityTileEntity.CHEMICAL_HANDLER_PROVIDER)
              .with(HeatHandlerManager.HEAT_HANDLER, CapabilityTileEntity.HEAT_HANDLER_PROVIDER)
              .with(ItemStorage.SIDED, CapabilityTileEntity.ITEM_HANDLER_PROVIDER)
              .with(FluidStorage.SIDED, CapabilityTileEntity.FLUID_HANDLER_PROVIDER)
              .with(mekanism.common.capabilities.resolver.manager.EnergyHandlerManager.STRICT_ENERGY, CapabilityTileEntity::basicCapabilityProvider)
              .with(EnergyStorage.SIDED, CapabilityTileEntity::basicCapabilityProvider);
        ComputerCapabilityHelper.addComputerCapabilities(builder, () -> Attribute.has(block, AttributeComputerIntegration.class));
        return builder;
    }

    public <BE extends BlockEntity> BlockEntityTypeBuilder<BE> builder(MekanismDeferredHolder<Block, ?> block, BlockEntitySupplier<? extends BE> factory) {
        return new BlockEntityTypeBuilder<>(block, factory);
    }

    @SuppressWarnings("unchecked")
    private <BE extends BlockEntity> TileEntityTypeRegistryObject<BE> registerMek(String name, Supplier<? extends BlockEntityType<BE>> sup) {
        return (TileEntityTypeRegistryObject<BE>) super.register(name, sup);
    }

    @Override
    public void registerAll(Registry<BlockEntityType<?>> registry) {
        super.registerAll(registry);
        registerCapabilities();
    }

    private void registerCapabilities() {
        for (MekanismDeferredHolder<BlockEntityType<?>, ?> entry : getEntries()) {
            if (entry instanceof TileEntityTypeRegistryObject<?> tileRO) {
                tileRO.registerCapabilityProviders();
            } else {
                throw new IllegalStateException("Expected entry to be a TileEntityTypeRegistryObject");
            }
        }
    }

    public class BlockEntityTypeBuilder<BE extends BlockEntity> {

        private final MekanismDeferredHolder<Block, ?> block;
        private final BlockEntitySupplier<? extends BE> factory;
        private final List<CapabilityData<BE, ?, ?>> capabilityProviders = new ArrayList<>();
        @Nullable
        private BlockEntityTicker<BE> clientTicker;
        @Nullable
        private BlockEntityTicker<BE> serverTicker;

        BlockEntityTypeBuilder(MekanismDeferredHolder<Block, ?> block, BlockEntitySupplier<? extends BE> factory) {
            this.block = block;
            this.factory = factory;
        }

        public <CAP, CONTEXT> BlockEntityTypeBuilder<BE> withSimple(BlockApiLookup<CAP, CONTEXT> capability) {
            return withSimple(capability, ConstantPredicates.ALWAYS_TRUE);
        }

        public <CAP, CONTEXT> BlockEntityTypeBuilder<BE> withSimple(BlockApiLookup<CAP, CONTEXT> capability, BooleanSupplier shouldApply) {
            //Native lookup identity owns the runtime API type, rather than an untyped universal provider cast.
            return with(capability, (tile, context) -> capability.apiClass().cast(tile), shouldApply);
        }

        public <CAP, CONTEXT> BlockEntityTypeBuilder<BE> with(BlockApiLookup<CAP, CONTEXT> capability,
              Function<BlockApiLookup<CAP, CONTEXT>, BiFunction<? super BE, CONTEXT, CAP>> provider) {
            return with(capability, provider.apply(capability));
        }

        public <CAP, CONTEXT> BlockEntityTypeBuilder<BE> with(BlockApiLookup<CAP, CONTEXT> capability, BiFunction<? super BE, CONTEXT, CAP> provider) {
            return with(capability, provider, ConstantPredicates.ALWAYS_TRUE);
        }

        /** Determines whether a provider is attached at all, based on actual loaded integrations and block attributes. */
        public <CAP, CONTEXT> BlockEntityTypeBuilder<BE> with(BlockApiLookup<CAP, CONTEXT> capability, BiFunction<? super BE, CONTEXT, CAP> provider,
              BooleanSupplier shouldApply) {
            capabilityProviders.add(new CapabilityData<>(capability, provider, shouldApply));
            return this;
        }

        public BlockEntityTypeBuilder<BE> without(BlockApiLookup<?, ?>... capabilities) {
            for (BlockApiLookup<?, ?> capability : capabilities) {
                for (Iterator<CapabilityData<BE, ?, ?>> iterator = capabilityProviders.iterator(); iterator.hasNext(); ) {
                    if (iterator.next().capability() == capability) {
                        iterator.remove();
                    }
                }
            }
            return this;
        }

        public BlockEntityTypeBuilder<BE> without(Collection<? extends BlockApiLookup<?, ?>> capabilities) {
            for (Iterator<CapabilityData<BE, ?, ?>> iterator = capabilityProviders.iterator(); iterator.hasNext(); ) {
                if (capabilities.contains(iterator.next().capability())) {
                    iterator.remove();
                }
            }
            return this;
        }

        public BlockEntityTypeBuilder<BE> clientTicker(BlockEntityTicker<BE> ticker) {
            Preconditions.checkState(clientTicker == null, "Client ticker may only be set once.");
            clientTicker = ticker;
            return this;
        }

        public BlockEntityTypeBuilder<BE> serverTicker(BlockEntityTicker<BE> ticker) {
            Preconditions.checkState(serverTicker == null, "Server ticker may only be set once.");
            serverTicker = ticker;
            return this;
        }

        public BlockEntityTypeBuilder<BE> commonTicker(BlockEntityTicker<BE> ticker) {
            return clientTicker(ticker).serverTicker(ticker);
        }

        @SuppressWarnings("ConstantConditions")
        public TileEntityTypeRegistryObject<BE> build() {
            TileEntityTypeRegistryObject<BE> holder = registerMek(block.getId().getPath(), () -> BlockEntityType.Builder.<BE>of(factory, block.value()).build(null));
            holder.tickers(clientTicker, serverTicker);
            holder.capabilities(capabilityProviders.isEmpty() ? null : List.copyOf(capabilityProviders));
            return holder;
        }
    }
}
