package mekanism.fabric;

import net.fabricmc.api.ModInitializer;

/**
 * Minimal Fabric entrypoint for the first migration stage.
 *
 * <p>The upstream Mekanism source tree is deliberately not on this build's
 * source path yet. Subsystems will be admitted incrementally as they are ported.</p>
 */
public final class MekanismFabricBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        // Stage one intentionally has no gameplay behavior.
    }
}
