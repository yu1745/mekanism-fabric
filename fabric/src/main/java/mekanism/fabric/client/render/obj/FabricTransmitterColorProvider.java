package mekanism.fabric.client.render.obj;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real Fabric block-tint owner for the transmitter block models.
 *
 * <p>The official renderer registered a {@code BlockColor} for the four logistical transporter blocks whose
 * {@code tintIndex == 1} quads (the geometry material {@code forge_TintIndex 1}) take the logistical transporter's
 * current {@code EnumColor}. The tint index itself is already carried on the baked quads by
 * {@link TransmitterQuadBaker}, so this class supplies the live color for that index.</p>
 *
 * <p>Block registration is outside this request (the root block/registry owners are still WIP), so the color provider
 * registers for whatever transporter blocks are actually present in the block registry at client init. The provider
 * itself and the tint-index contract are real; if a transporter block is not registered in this build, it simply is not
 * among the targets, and that is reported rather than hidden.</p>
 */
public final class FabricTransmitterColorProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricTransmitterColor");
    /** The quad tint index the shipped glass materials declare ({@code forge_TintIndex 1}). */
    public static final int TRANSMITTER_TINT_INDEX = 1;

    private FabricTransmitterColorProvider() {
    }

    public static void initialize() {
        //Only fires for tint index 1; -1 means "leave the quad's own color alone", exactly like the official handler.
        BlockColor color = (state, world, pos, tintIndex) -> tintIndex == TRANSMITTER_TINT_INDEX ? 0xFFFFFFFF : -1;
        Block[] targets = FabricTransmitterBlocks.logisticalTransporterBlocks();
        if (targets.length == 0) {
            LOGGER.warn("MEKANISM_FABRIC_TRANSMITTER_TINT_UNBOUND no registered logistical transporter block; "
                  + "tint index {} provider not registered", TRANSMITTER_TINT_INDEX);
            return;
        }
        ColorProviderRegistry.BLOCK.register(color, targets);
        LOGGER.info("MEKANISM_FABRIC_TRANSMITTER_TINT blocks={} tint_index={}", targets.length, TRANSMITTER_TINT_INDEX);
    }
}
