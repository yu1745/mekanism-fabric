package mekanism.common.content.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.UUID;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismIMC;
import mekanism.api.gear.ModuleData;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PickBlockModuleProtectionTest {
    private static ModuleContainer installed;
    private static ItemMekaTool toolItem;
    private static ItemMekaSuitArmor suitItem;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        ModuleData<?> data = new ModuleData<>(ModuleData.ModuleDataBuilder.marker(Holder.direct(Items.STONE)));
        Registry.register(MekanismAPI.MODULE_REGISTRY, ResourceLocation.fromNamespaceAndPath("mekanism_test", "marker"), data);
        Module<?> module = new Module<>(MekanismAPI.MODULE_REGISTRY.wrapAsHolder(data), 1);
        var modules = new LinkedHashMap<ModuleData<?>, Module<?>>();
        modules.put(data, module);
        installed = new ModuleContainer(modules, ItemEnchantments.EMPTY);
        toolItem = new ItemMekaTool(new Item.Properties());
        suitItem = new ItemMekaSuitArmor(ArmorItem.Type.BOOTS, new Item.Properties());
        MekanismIMC.addModuleContainer(Holder.direct(toolItem), "test_tool");
        MekanismIMC.addModuleContainer(Holder.direct(suitItem), "test_suit");
        ModuleHelper.get().processIMC(MekanismIMC.finishModuleRegistration());
    }

    @Test
    void selectedModuleGearIsAvoidedButFallbackAndOrdinaryItemsRemainVanilla() {
        TileLevelStub level = TileLevelStub.create();
        Player player = new Player(level, net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) inventory.setItem(i, new ItemStack(Items.DIRT));
        ItemStack tool = new ItemStack(toolItem);
        tool.set(MekanismDataComponents.MODULE_CONTAINER.get(), installed);
        inventory.setItem(0, tool);
        inventory.selected = 0;
        assertEquals(1, inventory.getSuitableHotbarSlot());

        ItemStack suit = new ItemStack(suitItem);
        suit.set(MekanismDataComponents.MODULE_CONTAINER.get(), installed);
        inventory.setItem(1, suit);
        assertEquals(2, inventory.getSuitableHotbarSlot());
        inventory.setItem(3, ItemStack.EMPTY);
        assertEquals(3, inventory.getSuitableHotbarSlot());

        for (int i = 0; i < 9; i++) inventory.setItem(i, tool.copy());
        assertEquals(0, inventory.getSuitableHotbarSlot());
        inventory.setItem(0, new ItemStack(toolItem));
        assertEquals(0, inventory.getSuitableHotbarSlot());
    }
}
