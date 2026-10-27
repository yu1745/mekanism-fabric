package mekanism.common.content.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.item.gear.ItemFreeRunners;
import mekanism.common.item.interfaces.IFreeRunnerItem.FreeRunnerMode;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GearAttributeActivationTest {
    private static final ResourceLocation FREE_RUNNERS = ResourceLocation.fromNamespaceAndPath("mekanism", "free_runners");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
    }

    @Test
    void realFreeRunnersContributeOnlyInNormalModeAndCorrectSlot() {
        ItemStack stack = new ItemStack(new ItemFreeRunners(MekanismArmorMaterials.FREE_RUNNERS, new Item.Properties()));
        List<AttributeModifier> seen = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> {
            if (attribute.equals(Attributes.MOVEMENT_EFFICIENCY) && modifier.id().equals(FREE_RUNNERS)) seen.add(modifier);
        });
        assertEquals(1, seen.size());
        assertEquals(1, seen.getFirst().amount());
        seen.clear();
        stack.forEachModifier(EquipmentSlotGroup.FEET, (attribute, modifier) -> {
            if (attribute.equals(Attributes.MOVEMENT_EFFICIENCY) && modifier.id().equals(FREE_RUNNERS)) seen.add(modifier);
        });
        assertEquals(1, seen.size());
        stack.set(MekanismDataComponents.FREE_RUNNER_MODE.get(), FreeRunnerMode.SAFETY);
        seen.clear();
        stack.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> {
            if (attribute.equals(Attributes.MOVEMENT_EFFICIENCY) && modifier.id().equals(FREE_RUNNERS)) seen.add(modifier);
        });
        assertEquals(0, seen.size());
    }
}
