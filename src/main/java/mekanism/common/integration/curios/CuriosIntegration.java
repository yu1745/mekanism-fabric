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
 * accessory-slot lookups. The previously named 9.5.1+1.21.1 artifact is NeoForge-only, not a verified Fabric dependency.
 * An optional Accessories integration is authorized for investigation; slot semantics, data and rendering must be audited
 * before binding it. Until then this seam reports {@link #isLoaded()} as {@code false}, so guarded accessory branches never
 * run. The original NeoForge+Curios implementation is preserved in the baseline import commit (4646f509).
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
