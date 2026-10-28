package mekanism.common.content.gear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.common.item.ItemEnergized;
import mekanism.common.item.ItemPortableQIODashboard;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.item.gear.ItemFlamethrower;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.fabric.client.GearInteractionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GearInteractionPolicyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
    }

    @Test
    void policyMatchesTheOriginalDifferentReequipAndBreakCategories() {
        ItemStack energy = new ItemStack(new ItemEnergized(new Item.Properties()));
        ItemStack bow = new ItemStack(new ItemElectricBow(new Item.Properties()));
        ItemStack suit = new ItemStack(new ItemMekaSuitArmor(ArmorItem.Type.BOOTS, new Item.Properties()));
        for (ItemStack stack : new ItemStack[]{energy, bow, suit}) {
            assertTrue(GearInteractionPolicy.ignoresReequipComponents(stack));
            assertTrue(stack.getItem().allowContinuingBlockBreaking(null, stack, stack));
        }
        ItemStack flame = new ItemStack(new ItemFlamethrower(new Item.Properties()));
        ItemStack dashboard = new ItemStack(new ItemPortableQIODashboard(new Item.Properties()));
        for (ItemStack stack : new ItemStack[]{flame, dashboard}) {
            assertTrue(GearInteractionPolicy.ignoresReequipComponents(stack));
            assertFalse(stack.getItem().allowContinuingBlockBreaking(null, stack, stack));
        }
        assertFalse(GearInteractionPolicy.ignoresReequipComponents(new ItemStack(Items.STONE)));
        ItemStack stone = new ItemStack(Items.STONE);
        assertFalse(stone.getItem().allowContinuingBlockBreaking(null, stone, stone));
        assertFalse(GearInteractionPolicy.ignoresReequipComponents(ItemStack.EMPTY));
    }
}
