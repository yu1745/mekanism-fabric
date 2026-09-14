package mekanism.common.lib.transmitter.acceptor;

import java.util.Collection;
import java.util.function.Supplier;
import mekanism.common.content.network.transmitter.MechanicalPipe;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;

/**
 * Fluid network's native derived acceptor cache. The original fresh six-face snapshot algorithm is shared with
 * chemical/heat networks in {@link FreshNetworkAcceptorCache}; this public fluid-specific type and constructor remain.
 */
public class FluidNetworkAcceptorCache extends FreshNetworkAcceptorCache<Storage<FluidVariant>, MechanicalPipe> {

    public FluidNetworkAcceptorCache(Supplier<Collection<MechanicalPipe>> membership) {
        super(membership);
    }
}
