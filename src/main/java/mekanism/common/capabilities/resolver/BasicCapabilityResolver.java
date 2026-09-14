package mekanism.common.capabilities.resolver;

import java.util.List;
import java.util.function.Supplier;
import mekanism.api.annotations.NothingNullByDefault;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import com.google.common.base.Suppliers;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

@NothingNullByDefault
public class BasicCapabilityResolver<CAPABILITY, CONTEXT> implements ICapabilityResolver<CONTEXT> {

    public static <CAPABILITY, CONTEXT> BasicCapabilityResolver<CAPABILITY, CONTEXT> create(BlockApiLookup<CAPABILITY, CONTEXT> supportedCapability,
          Supplier<CAPABILITY> supplier) {
        return new BasicCapabilityResolver<>(supportedCapability, supplier);
    }

    /**
     * Creates a capability resolver that strongly caches the result of the supplier. Persisting the calculated value through capability invalidation.
     */
    public static <CAPABILITY, CONTEXT> BasicCapabilityResolver<CAPABILITY, CONTEXT> persistent(BlockApiLookup<CAPABILITY, CONTEXT> supportedCapability,
          Supplier<CAPABILITY> supplier) {
        return create(supportedCapability, Suppliers.memoize(supplier::get));
    }

    private final List<BlockApiLookup<?, CONTEXT>> supportedCapabilities;
    private final Supplier<CAPABILITY> supplier;
    @Nullable
    private CAPABILITY cachedCapability;

    protected BasicCapabilityResolver(BlockApiLookup<CAPABILITY, CONTEXT> capabilityType, Supplier<CAPABILITY> supplier) {
        this.supportedCapabilities = List.of(capabilityType);
        this.supplier = supplier;
    }

    @Override
    public List<BlockApiLookup<?, CONTEXT>> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Nullable
    @Override
    public <T> T resolve(BlockApiLookup<T, CONTEXT> capability, @UnknownNullability CONTEXT context) {
        if (cachedCapability == null) {
            //If the capability has not been retrieved yet, or it is not valid then recreate it
            cachedCapability = supplier.get();
        }
        return (T) cachedCapability;
    }

    @Override
    public void invalidate(BlockApiLookup<?, CONTEXT> capability, @UnknownNullability CONTEXT side) {
        cachedCapability = null;
    }

    @Override
    public void invalidateAll() {
        cachedCapability = null;
    }
}