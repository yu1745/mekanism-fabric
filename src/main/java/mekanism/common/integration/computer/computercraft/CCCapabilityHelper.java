package mekanism.common.integration.computer.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralLookup;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import mekanism.common.capabilities.resolver.BasicCapabilityResolver;
import mekanism.common.integration.computer.ComputerEnergyHelper;
import mekanism.common.integration.computer.ComputerFilterHelper;
import mekanism.common.integration.computer.IComputerTile;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister.BlockEntityTypeBuilder;
import mekanism.common.tile.TileEntityBoundingBlock;
import mekanism.common.tile.base.CapabilityTileEntity;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public class CCCapabilityHelper {

    private static final BiFunction<?, @Nullable Direction, IPeripheral> PROVIDER = getProvider();

    private static <TILE extends CapabilityTileEntity & IComputerTile> BiFunction<TILE, @Nullable Direction, IPeripheral> getProvider() {
        return CapabilityTileEntity.capabilityProvider(PeripheralLookup.get(), (tile, cap) -> {
            if (tile.isComputerCapabilityPersistent()) {
                return BasicCapabilityResolver.persistent(cap, () -> MekanismPeripheral.create(tile));
            }
            return BasicCapabilityResolver.create(cap, () -> MekanismPeripheral.create(tile));
        });
    }

    @SuppressWarnings("unchecked")
    public static <TILE extends CapabilityTileEntity & IComputerTile> void addCapability(BlockEntityTypeBuilder<TILE> builder, BooleanSupplier supportsComputer) {
        builder.with(PeripheralLookup.get(), (BiFunction<? super TILE, @Nullable Direction, IPeripheral>) PROVIDER, supportsComputer);
    }

    public static void addBoundingComputerCapabilities() {
        TileEntityBoundingBlock.proxyCapability(PeripheralLookup.get());
    }

    public static void registerApis() {
        ComputerCraftAPI.registerAPIFactory(CCApiObject.create(ComputerEnergyHelper.class, "mekanismEnergyHelper"));
        ComputerCraftAPI.registerAPIFactory(CCApiObject.create(ComputerFilterHelper.class, "mekanismFilterHelper"));
    }
}
