package mekanism.common.capabilities;

import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of the official same-id multi-type catalog entry for Mekanism-owned domain APIs, whose item-leg context is
 * empty ({@code Void}). The three NeoForge token legs become one {@link BlockApiLookup} sided leg and one {@link ItemApiLookup}
 * void-context leg; both are interned by Fabric per {@code (id, apiClass, contextClass)}, so a catalog entry constructed from
 * a name resolves to the same block identity that a handler manager declared with the same id.
 */
public record MultiTypeCapability<HANDLER>(BlockApiLookup<HANDLER, Direction> block, ItemApiLookup<HANDLER, Void> item)
      implements IMultiTypeCapability<HANDLER> {

    public MultiTypeCapability(ResourceLocation name, Class<HANDLER> handlerClass) {
        this(
              BlockApiLookup.get(name, handlerClass, Direction.class),
              ItemApiLookup.get(name, handlerClass, Void.class)
        );
    }

    @Override
    @Nullable
    public HANDLER getCapability(ItemStack stack) {
        //Note: Fabric performs the empty-stack rejection in the lookup implementation
        return item.find(stack, null);
    }
}
