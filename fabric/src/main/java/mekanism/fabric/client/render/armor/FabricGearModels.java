package mekanism.fabric.client.render.armor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import mekanism.client.render.armor.ICustomArmor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric port of the NeoForge {@code IClientItemExtensions} surface for the custom gear armor: the special-gear
 * lookup keys off the item instead of the loader extension point, and the registrations happen at client init
 * (mirroring the official render-properties registrations).
 */
public final class FabricGearModels {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricGearModels");
    private static final Map<Item, ICustomArmor> GEAR = new HashMap<>();
    private static final AtomicBoolean PROOF = new AtomicBoolean();

    private FabricGearModels() {
    }

    public static void register(Item item, ICustomArmor gear) {
        GEAR.put(item, gear);
        if (PROOF.compareAndSet(false, true)) {
            LOGGER.info("MEKANISM_FABRIC_GEAR_MODELS_PROOF registered");
        }
    }

    /** The custom armor model for an item, or null when the item has no custom armor render. */
    public static ICustomArmor gearModel(ItemStack stack) {
        return GEAR.get(stack.getItem());
    }
}
