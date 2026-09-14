package mekanism.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * Project-owned Fabric replacement for the NeoForge {@code ItemAbility} token type.
 * <p>
 * Fabric port delta (recorded in {@code docs/API_DELTAS.md}): NeoForge exposes item abilities as tokens queried through the patched
 * {@code ItemStack#canPerformAction} method. Fabric has no equivalent patched vanilla method, so Mekanism owns the token type and the dispatch. Tokens are interned by
 * name and compared by identity/name, matching the NeoForge {@code ItemAbility.get} semantics. Items that expose abilities implement
 * {@link IItemAbilityProvider}, and callers use {@link MekanismItemAbilities#canPerformAction(ItemStack, ItemAbility)} instead of the patched vanilla method.
 *
 * @since 10.7.0 (Fabric port)
 */
public final class ItemAbility {

    private static final Map<String, ItemAbility> CACHE = new ConcurrentHashMap<>();

    private final String name;

    private ItemAbility(String name) {
        this.name = name;
    }

    /**
     * Gets (or creates and caches) the item ability for the given name.
     *
     * @param name Name of the ability.
     *
     * @return The interned ability token.
     */
    public static ItemAbility get(String name) {
        return CACHE.computeIfAbsent(name, ItemAbility::new);
    }

    /**
     * {@return the name of this ability}
     */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof ItemAbility other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "ItemAbility[" + name + "]";
    }

    /**
     * Mekanism-owned dispatch surface replacing the NeoForge {@code IItemExtension#canPerformAction(ItemStack, ItemAbility)} patched Item method. Items that want to
     * declare support for {@link ItemAbility ItemAbilities} implement this interface.
     *
     * @since 10.7.0 (Fabric port)
     */
    @FunctionalInterface
    public interface IItemAbilityProvider {

        /**
         * @param stack  Stack the ability check is being performed for.
         * @param ability Ability to check.
         *
         * @return {@code true} if the given stack can perform the given ability.
         */
        boolean canPerformAction(ItemStack stack, ItemAbility ability);
    }
}
