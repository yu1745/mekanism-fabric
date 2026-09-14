package mekanism.api.security;

import java.util.List;
import mekanism.api.MekanismServices;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for interacting with Mekanism's security system when applied to items.
 *
 * @see IItemSecurityUtils#INSTANCE
 * @since 10.5.0
 */
public interface IItemSecurityUtils extends ITypedSecurityUtils<ItemStack> {

    /**
     * Provides access to Mekanism's implementation of {@link IItemSecurityUtils}.
     *
     * @since 10.5.0
     */
    IItemSecurityUtils INSTANCE = MekanismServices.load(IItemSecurityUtils.class);

    /**
     * {@return the item capability representing security objects}
     */
    ItemApiLookup<ISecurityObject, Void> securityCapability();

    @Nullable
    @Override
    default ISecurityObject securityCapability(@Nullable ItemStack stack) {
        return stack == null ? null : securityCapability().find(stack, null);
    }

    /**
     * {@return the item capability representing owner objects}
     */
    ItemApiLookup<IOwnerObject, Void> ownerCapability();

    @Nullable
    @Override
    default IOwnerObject ownerCapability(@Nullable ItemStack stack) {
        return stack == null ? null : ownerCapability().find(stack, null);
    }

    /**
     * Adds any owner data that the given stack has to the passed in list of tooltips. If the stack does not expose an owner then the corresponding data will not be
     * added.
     *
     * @param stack   Stack to retrieve the owner data from.
     * @param tooltip List of components to add extra tooltips to.
     *
     * @apiNote While this method won't crash if called on the server it won't render quite right due to not having access to the player, so it is best to only call this
     * on the client.
     * @see #addSecurityTooltip(ItemStack, List)
     */
    void addOwnerTooltip(ItemStack stack, List<Component> tooltip);

    /**
     * Adds any owner and security data that the given stack has to the passed in list of tooltips. If the stack does not expose an owner or any security data then the
     * corresponding data will not be added.
     *
     * @param stack   Stack to retrieve the owner and any security data from.
     * @param tooltip List of components to add extra tooltips to.
     *
     * @apiNote While this method won't crash if called on the server it won't render quite right due to not having access to the player, so it is best to only call this
     * on the client.
     * @see #addOwnerTooltip(ItemStack, List)
     */
    void addSecurityTooltip(ItemStack stack, List<Component> tooltip);
}