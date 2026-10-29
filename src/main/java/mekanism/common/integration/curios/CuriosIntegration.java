package mekanism.common.integration.curios;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional Accessories integration. The external API is confined to a nested class so the common entry points remain
 * loadable without the optional mod. See TASK052_ADJUDICATION_GEAR_CATALOG.md for the audited slot semantics and WIP status.
 */
public final class CuriosIntegration {

    /** Stable direct-slot address; the server must resolve it again against the sending player. */
    public record EquippedSlot(String slotType, int index, ItemStack stack) {
    }

    private CuriosIntegration() {
    }

    public static boolean isLoaded() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("accessories");
    }

    @NotNull
    public static ItemStack findFirstCurio(@NotNull LivingEntity entity, Predicate<ItemStack> filter) {
        EquippedSlot slot = findFirstSlot(entity, filter);
        return slot == null ? ItemStack.EMPTY : slot.stack();
    }

    @Nullable
    public static EquippedSlot findFirstSlot(@NotNull LivingEntity entity, Predicate<ItemStack> filter) {
        if (!isLoaded()) {
            return null;
        }
        for (EquippedSlot slot : AccessoriesAccess.directSlots(entity)) {
            if (filter.test(slot.stack())) {
                return slot;
            }
        }
        return null;
    }

    @NotNull
    public static List<ItemStack> getEquippedStacks(@NotNull LivingEntity entity) {
        if (!isLoaded()) {
            return List.of();
        }
        return AccessoriesAccess.directSlots(entity).stream().map(EquippedSlot::stack).toList();
    }

    public static List<EquippedSlot> getEquippedSlots(@NotNull LivingEntity entity) {
        return isLoaded() ? AccessoriesAccess.directSlots(entity) : List.of();
    }

    public static ItemStack getSlotStack(@NotNull LivingEntity entity, String slotType, int index) {
        return isLoaded() ? AccessoriesAccess.getSlotStack(entity, slotType, index) : ItemStack.EMPTY;
    }

    public static void setSlotStack(@NotNull LivingEntity entity, String slotType, int index, ItemStack stack) {
        if (isLoaded()) {
            AccessoriesAccess.setSlotStack(entity, slotType, index, stack);
        }
    }

    /** Only this class has optional API references; never use recursive or cosmetic slot enumeration. */
    private static final class AccessoriesAccess {
        private static List<EquippedSlot> directSlots(LivingEntity entity) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            if (capability == null) {
                return List.of();
            }
            List<EquippedSlot> slots = new ArrayList<>();
            for (var entry : capability.getContainers().entrySet()) {
                var inventory = entry.getValue().getAccessories();
                for (int index = 0; index < inventory.getContainerSize(); index++) {
                    ItemStack stack = inventory.getItem(index);
                    if (!stack.isEmpty()) {
                        slots.add(new EquippedSlot(entry.getKey(), index, stack));
                    }
                }
            }
            return slots;
        }

        private static ItemStack getSlotStack(LivingEntity entity, String slotType, int index) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            var container = capability == null ? null : capability.getContainers().get(slotType);
            if (container == null || index < 0 || index >= container.getAccessories().getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return container.getAccessories().getItem(index);
        }

        private static void setSlotStack(LivingEntity entity, String slotType, int index, ItemStack stack) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.get(entity);
            var container = capability == null ? null : capability.getContainers().get(slotType);
            if (container != null && index >= 0 && index < container.getAccessories().getContainerSize()) {
                container.getAccessories().setItem(index, stack);
            }
        }
    }
}
