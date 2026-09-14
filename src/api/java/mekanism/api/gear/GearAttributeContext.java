package mekanism.api.gear;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Per-query Mekanism gear attribute editing, backed by vanilla component entries (never mutates the stack). */
public final class GearAttributeContext {
    private final ItemStack stack;
    private final boolean showInTooltip;
    private final List<ItemAttributeModifiers.Entry> entries;

    public GearAttributeContext(ItemStack stack, ItemAttributeModifiers original) {
        this.stack = stack;
        this.showInTooltip = original.showInTooltip();
        this.entries = new ArrayList<>(original.modifiers());
    }

    public ItemStack getItemStack() {
        return stack;
    }

    /** Attribute plus modifier ID is the uniqueness key, independent of the slot group. */
    public boolean addModifier(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        if (entries.stream().anyMatch(entry -> entry.attribute().equals(attribute) && entry.modifier().id().equals(modifier.id()))) return false;
        entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
        return true;
    }

    public void replaceModifier(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        entries.removeIf(entry -> entry.attribute().equals(attribute) && entry.modifier().id().equals(modifier.id()));
        entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
    }

    public ItemAttributeModifiers build() {
        return new ItemAttributeModifiers(List.copyOf(entries), showInTooltip);
    }
}
