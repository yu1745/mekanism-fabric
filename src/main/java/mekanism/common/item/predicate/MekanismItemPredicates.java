package mekanism.common.item.predicate;

import mekanism.api.MekanismAPI;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.advancements.critereon.ItemSubPredicate.Type;
import net.minecraft.core.registries.Registries;

/**
 * Item sub-predicate types used by the generated advancement conditions. The official class used NeoForge's
 * {@code DeferredRegister}; this port uses the canonical Mekanism deferred register against the same vanilla registry.
 */
public class MekanismItemPredicates {

    public static final MekanismDeferredRegister<Type<?>> PREDICATES = new MekanismDeferredRegister<>(Registries.ITEM_SUB_PREDICATE_TYPE,
          MekanismAPI.MEKANISM_MODID);

    public static final MekanismDeferredHolder<Type<?>, Type<FullCanteenItemPredicate>> FULL_CANTEEN = PREDICATES.register("full_canteen",
          () -> FullCanteenItemPredicate.TYPE);
    public static final MekanismDeferredHolder<Type<?>, Type<MaxedModuleContainerItemPredicate>> MAXED_MODULE_CONTAINER_ITEM = PREDICATES.register(
          "maxed_module_container", () -> MaxedModuleContainerItemPredicate.TYPE);
}
