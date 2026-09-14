package mekanism.fabric.client.render.obj;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Locates the registered logistical transporter blocks for the tint provider.
 *
 * <p>The official renderer referenced {@code MekanismBlocks.BASIC/ADVANCED/ELITE/ULTIMATE_LOGISTICAL_TRANSPORTER}
 * directly, but the root block registry owner is still outside this request, so this looks the same four ids up in the
 * live block registry instead. A block that is not registered in this build is simply absent from the result.</p>
 */
final class FabricTransmitterBlocks {

    private static final String[] LOGISTICAL_TRANSPORTERS = {
          "basic_logistical_transporter",
          "advanced_logistical_transporter",
          "elite_logistical_transporter",
          "ultimate_logistical_transporter"
    };

    private FabricTransmitterBlocks() {
    }

    static Block[] logisticalTransporterBlocks() {
        List<Block> blocks = new ArrayList<>(LOGISTICAL_TRANSPORTERS.length);
        for (String path : LOGISTICAL_TRANSPORTERS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", path);
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                blocks.add(BuiltInRegistries.BLOCK.get(id));
            }
        }
        return blocks.toArray(Block[]::new);
    }
}
