package mekanism.api.providers;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import mekanism.api.fluid.FluidStack;

@SuppressWarnings("removal")
@MethodsReturnNonnullByDefault
@Deprecated(forRemoval = true, since = "10.7.11")
public interface IFluidProvider extends IBaseProvider {

    /**
     * Gets the fluid this provider represents.
     */
    Fluid getFluid();

    /**
     * Creates a fluid stack of the given size using the fluid this provider represents.
     *
     * @param size Size of the stack.
     */
    default FluidStack getFluidStack(int size) {
        return new FluidStack(getFluid(), size);
    }

    /**
     * {@return the registry name of the fluid this provider represents}
     */
    @Override
    default ResourceLocation getRegistryName() {
        return BuiltInRegistries.FLUID.getKey(getFluid());
    }

    /**
     * {@return the display name of this provider's fluid}
     *
     * @implNote The Fabric port derives the name from the fluid's default state like {@link FluidStack#getHoverName()} does; arbitrary platform fluid type
     * description overrides are not available and are not promised.
     */
    @Override
    default Component getTextComponent() {
        return getFluidStack(1).getHoverName();
    }

    /**
     * {@return the translation key of this provider's fluid}
     *
     * @implNote The Fabric port derives the key from the fluid's default state like {@link FluidStack#getTranslationKey()} does; arbitrary platform fluid type
     * description overrides are not available and are not promised.
     */
    @Override
    default String getTranslationKey() {
        return getFluidStack(1).getTranslationKey();
    }
}
