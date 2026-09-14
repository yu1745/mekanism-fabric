package mekanism.api.inventory;

import mekanism.api.annotations.NothingNullByDefault;

/**
 * Exposed by inventory slots that have a <b>creative</b> mode with specific non-finite transfer semantics: while
 * creative, extraction never depletes the slot and external (automated) insertion voids instead of accumulating -
 * in particular an empty creative slot does <b>not</b> acquire an item type from external insertion.
 *
 * <p>Platform adapters use this to apply the creative policy <b>per operation and only while the slot actually
 * reports being creative</b>, instead of bypassing their finite read-back validation globally. Slots that are not
 * creative, or creative slots being interacted with in a way that does change their state (for example a player
 * manually setting the type of an empty creative bin through the container, which never goes through a sided
 * adapter), keep the strict finite validation.
 *
 * @since 10.7.20
 */
@NothingNullByDefault
public interface ICreativeInventorySlot {

    /**
     * Whether this slot is currently in creative mode.
     *
     * @return {@code true} if extraction is non-depleting and external insertion voids.
     */
    boolean isCreative();
}
