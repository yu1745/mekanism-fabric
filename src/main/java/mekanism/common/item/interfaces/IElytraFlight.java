package mekanism.common.item.interfaces;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta: vanilla decides fall flying from {@code Items.ELYTRA} alone, while NeoForge exposes the
 * {@code IItemExtension#canElytraFly}/{@code #elytraFlightTick} hooks. Fabric-side mixins consult this surface at the
 * same decision points so alternative elytra providers keep working.
 */
public interface IElytraFlight {

    boolean canElytraFly(ItemStack stack, LivingEntity entity);

    /**
     * Called once per tick while fall flying; returning {@code false} ends the flight.
     */
    boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks);
}
