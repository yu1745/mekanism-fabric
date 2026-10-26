package mekanism.client.render.armor;

import org.jetbrains.annotations.NotNull;

/**
 * Fabric port: the NeoForge {@code IClientItemExtensions} parent is replaced by the port's per-item registry
 * ({@code FabricGearModels}); the lookup happens in the armor layer instead of the extension surface.
 */
public interface ISpecialGear {

    @NotNull
    ICustomArmor gearModel();
}
