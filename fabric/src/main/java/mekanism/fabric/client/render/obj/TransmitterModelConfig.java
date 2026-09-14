package mekanism.fabric.client.render.obj;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Access seam for the {@code opaqueTransmitters} client render option.
 *
 * <p>The official renderer read {@code MekanismConfig.client.opaqueTransmitters} directly. That client config cannot
 * compile inside the current Fabric client slices: {@code ClientConfig} closes over {@code QIOItemViewerContainer},
 * which needs {@code VirtualCraftingOutputSlot}/{@code InventoryUtils}, and it calls NeoForge's patched
 * {@code ResourceLocation#compareNamespaced}. Neither exists in the vanilla/Fabric closure, so the value is not
 * reachable from this owner yet.</p>
 *
 * <p>Rather than fake the option or leave it silently inert, the real owner installs itself through this seam; until
 * then the getter returns the official default ({@code false}, matching {@code ClientConfig}'s
 * {@code .define("opaqueTransmitters", false)}) and says so once, so the gap is visible instead of hidden. The seam has
 * no platform or client imports and no fallback owner to mutate.</p>
 */
public final class TransmitterModelConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricTransmitterModels");
    /** Official config default; used only while no real owner is installed, and reported when so. */
    private static final boolean UNBOUND_DEFAULT = false;

    private static volatile BooleanSupplier owner;
    private static volatile boolean reportedUnbound;

    private TransmitterModelConfig() {
    }

    /** Installs the real owner. Called by the client config lifecycle once that closure is admitted. */
    public static synchronized void install(BooleanSupplier newOwner) {
        Objects.requireNonNull(newOwner, "owner");
        if (owner != null) {
            throw new IllegalStateException("An opaque-transmitters owner is already installed");
        }
        owner = newOwner;
        reportedUnbound = false;
    }

    /** Removes the owner so the process cannot keep a stale owner across client restarts. */
    public static synchronized void remove(BooleanSupplier currentOwner) {
        if (owner != currentOwner) {
            throw new IllegalStateException("The requested opaque-transmitters owner is not installed");
        }
        owner = null;
    }

    /**
     * The effective opaque-transmitter setting. Consulted on every bake, so a rebuild (which a resource reload performs)
     * observes a changed value. The official option is declared {@code gameRestart}, so a live toggle without a rebuild is
     * not part of the contract.
     */
    public static boolean opaqueTransmitters() {
        BooleanSupplier current = owner;
        if (current != null) {
            return current.getAsBoolean();
        }
        if (!reportedUnbound) {
            reportedUnbound = true;
            LOGGER.warn("MEKANISM_FABRIC_TRANSMITTER_OPAQUE_UNBOUND using official default {} because no config owner is "
                  + "installed (ClientConfig closure is not admitted to the Fabric client slices)", UNBOUND_DEFAULT);
        }
        return UNBOUND_DEFAULT;
    }
}
