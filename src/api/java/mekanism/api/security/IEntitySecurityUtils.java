package mekanism.api.security;

import mekanism.api.MekanismServices;
import net.fabricmc.fabric.api.lookup.v1.entity.EntityApiLookup;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for interacting with Mekanism's security system when applied to entities.
 *
 * @see IEntitySecurityUtils#INSTANCE
 * @since 10.5.0
 */
public interface IEntitySecurityUtils extends ITypedSecurityUtils<Entity> {

    /**
     * Provides access to Mekanism's implementation of {@link IEntitySecurityUtils}.
     *
     * @since 10.5.0
     */
    IEntitySecurityUtils INSTANCE = MekanismServices.load(IEntitySecurityUtils.class);

    /**
     * {@return the entity capability representing owner objects}
     */
    EntityApiLookup<IOwnerObject, Void> ownerCapability();

    @Nullable
    @Override
    default IOwnerObject ownerCapability(@Nullable Entity entity) {
        return entity == null ? null : ownerCapability().find(entity, null);
    }

    /**
     * {@return the entity capability representing security objects}
     */
    EntityApiLookup<ISecurityObject, Void> securityCapability();

    @Nullable
    @Override
    default ISecurityObject securityCapability(@Nullable Entity entity) {
        return entity == null ? null : securityCapability().find(entity, null);
    }
}