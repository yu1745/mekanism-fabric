package mekanism.common.registration.impl;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import mekanism.common.registration.MekanismDeferredHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

public class TileEntityTypeRegistryObject<BE extends BlockEntity> extends MekanismDeferredHolder<BlockEntityType<?>, BlockEntityType<BE>> {

    @Nullable
    private List<CapabilityData<BE, ?, ?>> capabilityProviders;
    @Nullable
    private BlockEntityTicker<BE> clientTicker;
    @Nullable
    private BlockEntityTicker<BE> serverTicker;

    public TileEntityTypeRegistryObject(ResourceKey<BlockEntityType<?>> key) {
        super(key);
    }

    @Nullable
    public BlockEntityTicker<BE> getTicker(boolean isClient) {
        return isClient ? clientTicker : serverTicker;
    }

    @Internal
    void tickers(@Nullable BlockEntityTicker<BE> clientTicker, @Nullable BlockEntityTicker<BE> serverTicker) {
        this.clientTicker = clientTicker;
        this.serverTicker = serverTicker;
    }

    @Internal
    void capabilities(@Nullable List<CapabilityData<BE, ?, ?>> capabilityProviders) {
        this.capabilityProviders = capabilityProviders;
    }

    @Internal
    void registerCapabilityProviders() {
        if (capabilityProviders != null) {
            for (CapabilityData<BE, ?, ?> capabilityProvider : capabilityProviders) {
                capabilityProvider.registerProvider(value());
            }
        }
    }

    record CapabilityData<BE extends BlockEntity, CAP, CONTEXT>(BlockApiLookup<CAP, CONTEXT> capability, BiFunction<? super BE, CONTEXT, CAP> provider,
                                                                        BooleanSupplier shouldApply) {

        private void registerProvider(BlockEntityType<BE> type) {
            if (shouldApply.getAsBoolean()) {
                capability.registerForBlockEntity(provider, type);
            }
        }
    }
}