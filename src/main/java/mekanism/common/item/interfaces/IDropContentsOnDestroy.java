package mekanism.common.item.interfaces;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Fabric port delta: vanilla calls {@code Item#onDestroyed(ItemEntity)} without the destroying damage source, while
 * NeoForge passes it so contained items can be security-checked against the destroying player. The item-entity mixin
 * forwards the real source to this interface.
 */
public interface IDropContentsOnDestroy {

    void onDestroyed(ItemEntity item, DamageSource damageSource);
}
