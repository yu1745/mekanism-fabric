package mekanism.common.content.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mekanism.api.MekanismIMC;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GameplayModuleEnchantmentsTest {
    private static ItemMekaTool tool;
    private static ItemMekaSuitArmor suit;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        tool = new ItemMekaTool(new Item.Properties());
        suit = new ItemMekaSuitArmor(ArmorItem.Type.BOOTS, new Item.Properties());
        MekanismIMC.addModuleContainer(Holder.direct(tool), "enchantment_test_tool");
        MekanismIMC.addModuleContainer(Holder.direct(suit), "enchantment_test_suit");
        ModuleHelper.get().processIMC(MekanismIMC.finishModuleRegistration());
    }

    private static Holder<Enchantment> enchantment(String name) {
        var cost = Enchantment.constantCost(1);
        return Holder.direct(new Enchantment(Component.literal(name),
              Enchantment.definition(HolderSet.direct(), 1, 5, cost, cost, 1, EquipmentSlotGroup.MAINHAND),
              HolderSet.direct(), DataComponentMap.EMPTY));
    }

    @Test
    void gameplayQueriesMergeModuleAndStoredLevelsWithoutChangingComponents() throws Exception {
        Holder<Enchantment> shared = enchantment("shared");
        Holder<Enchantment> moduleOnly = enchantment("module-only");
        ItemEnchantments.Mutable actual = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        actual.set(shared, 2);
        ItemEnchantments.Mutable virtual = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        virtual.set(shared, 4);
        virtual.set(moduleOnly, 3);
        ItemStack stack = new ItemStack(tool);
        stack.set(DataComponents.ENCHANTMENTS, actual.toImmutable());
        stack.set(MekanismDataComponents.MODULE_CONTAINER.get(), new ModuleContainer(new LinkedHashMap<>(), virtual.toImmutable()));

        assertEquals(4, EnchantmentHelper.getItemEnchantmentLevel(shared, stack));
        assertEquals(3, EnchantmentHelper.getItemEnchantmentLevel(moduleOnly, stack));
        assertEquals(2, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(shared));
        assertEquals(0, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(moduleOnly));

        Map<Holder<Enchantment>, Integer> oneItem = new LinkedHashMap<>();
        Class<?> visitorType = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper$EnchantmentVisitor");
        Object visitor = Proxy.newProxyInstance(visitorType.getClassLoader(), new Class<?>[]{visitorType}, (proxy, method, args) -> {
            if (method.getName().equals("accept")) oneItem.put((Holder<Enchantment>) args[0], (Integer) args[1]);
            return null;
        });
        var itemMethod = EnchantmentHelper.class.getDeclaredMethod("runIterationOnItem", ItemStack.class, visitorType);
        itemMethod.setAccessible(true);
        itemMethod.invoke(null, stack, visitor);
        assertEquals(Map.of(shared, 4, moduleOnly, 3), oneItem);

        TileLevelStub level = TileLevelStub.create();
        Player player = new Player(level, net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Map<Holder<Enchantment>, Integer> equipped = new LinkedHashMap<>();
        Class<?> equippedType = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper$EnchantmentInSlotVisitor");
        Object equippedVisitor = Proxy.newProxyInstance(equippedType.getClassLoader(), new Class<?>[]{equippedType}, (proxy, method, args) -> {
            if (method.getName().equals("accept")) equipped.put((Holder<Enchantment>) args[0], (Integer) args[1]);
            return null;
        });
        var equippedMethod = EnchantmentHelper.class.getDeclaredMethod("runIterationOnItem", ItemStack.class, EquipmentSlot.class, LivingEntity.class, equippedType);
        equippedMethod.setAccessible(true);
        equippedMethod.invoke(null, stack, EquipmentSlot.MAINHAND, player, equippedVisitor);
        assertEquals(Map.of(shared, 4, moduleOnly, 3), equipped);

        stack.remove(DataComponents.ENCHANTMENTS);
        equipped.clear();
        equippedMethod.invoke(null, stack, EquipmentSlot.MAINHAND, player, equippedVisitor);
        assertEquals(Map.of(shared, 4, moduleOnly, 3), equipped);
        ItemStack unrelated = new ItemStack(new Item(new Item.Properties()));
        unrelated.set(DataComponents.ENCHANTMENTS, actual.toImmutable());
        unrelated.set(MekanismDataComponents.MODULE_CONTAINER.get(), new ModuleContainer(new LinkedHashMap<>(), virtual.toImmutable()));
        assertEquals(2, EnchantmentHelper.getItemEnchantmentLevel(shared, unrelated));
        assertEquals(0, EnchantmentHelper.getItemEnchantmentLevel(moduleOnly, unrelated));
        ItemStack suitStack = new ItemStack(suit);
        suitStack.set(MekanismDataComponents.MODULE_CONTAINER.get(), new ModuleContainer(new LinkedHashMap<>(), virtual.toImmutable()));
        assertEquals(4, EnchantmentHelper.getItemEnchantmentLevel(shared, suitStack));
        assertEquals(3, EnchantmentHelper.getItemEnchantmentLevel(moduleOnly, suitStack));
        ItemStack unregisteredTool = new ItemStack(new ItemMekaTool(new Item.Properties()));
        unregisteredTool.set(MekanismDataComponents.MODULE_CONTAINER.get(), new ModuleContainer(new LinkedHashMap<>(), virtual.toImmutable()));
        assertEquals(0, EnchantmentHelper.getItemEnchantmentLevel(shared, unregisteredTool));
    }
}
