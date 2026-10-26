package mekanism.common.integration.curios;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Task052 WIP presence seam for the Curios accessory integration (see docs/TASK052_ADJUDICATION_GEAR_CATALOG.md).
 *
 * <p>Official Mekanism guards its curios branches with {@code Mekanism.hooks.curios.isLoaded()} and consults this class for
 * accessory-slot lookups. The Curios Fabric port (curios-fabric 9.5.1+1.21.1) exists, but its maven host
 * (maven.illusivesoulworks.com) is unreachable from this environment, so the artifact cannot be pinned and the real port
 * cannot compile against the Curios API. Until the artifact becomes available (maven allowlist or a manually supplied jar)
 * and the accessor surface is ported with a slot-semantics audit, this seam reports {@link #isLoaded()} as {@code false} and
 * every lookup behaves exactly like the officially no-Curios environment: callers run their equipment-slot branches and the
 * curios branches never fire. Armor-path behavior is unaffected (officially the armor branches run before/independently of
 * the curios guard). The original NeoForge+Curios implementation is preserved in this file's git history at the baseline
 * import commit (4646f509) and must be restored as the body of the real port once the artifact is pinned.
 */
public final class CuriosIntegration {

    private CuriosIntegration() {
    }

    public static boolean isLoaded() {
        return false;
    }

    @NotNull
    public static ItemStack findFirstCurio(@NotNull LivingEntity entity, Predicate<ItemStack> filter) {
        //Unreachable while isLoaded() is false; keeps the official call shape compiling at the guarded call sites.
        return ItemStack.EMPTY;
    }

    /**
     * Returns the accessory-slot stacks currently equipped by the entity, in slot order. Empty while the integration is
     * unbound; the real port must return the same worn stacks the official Curios inventory iteration exposed.
     */
    @NotNull
    public static List<ItemStack> getEquippedStacks(@NotNull LivingEntity entity) {
        return List.of();
    }
}
