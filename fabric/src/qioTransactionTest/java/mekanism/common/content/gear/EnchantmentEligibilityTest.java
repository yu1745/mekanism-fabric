package mekanism.common.content.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.item.gear.ItemMekaTool;
import mekanism.common.item.gear.ItemScubaMask;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnchantmentEligibilityTest {
    private static ItemMekaTool tool;
    private static ItemScubaMask mask;
    private static ItemElectricBow bow;
    private static Holder<Enchantment> aqua;
    private static Holder<Enchantment> flame;
    private static Holder<Enchantment> compatible;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        tool = new ItemMekaTool(new Item.Properties());
        mask = new ItemScubaMask(new Item.Properties());
        bow = new ItemElectricBow(new Item.Properties());
        var enchantments = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        aqua = register(enchantments, Enchantments.AQUA_AFFINITY, HolderSet.direct(Items.IRON_HELMET.builtInRegistryHolder()));
        flame = register(enchantments, Enchantments.FLAME, HolderSet.direct(bow.builtInRegistryHolder()));
        compatible = register(enchantments, Enchantments.UNBREAKING, HolderSet.direct(tool.builtInRegistryHolder(), bow.builtInRegistryHolder()));
        // Exercise transformation of the other real owner classes even where this test does not invoke their full runtime.
        Class.forName("net.minecraft.server.commands.EnchantCommand");
        Class.forName("net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction");
    }

    private static Holder<Enchantment> register(Registry<Enchantment> registry, ResourceKey<Enchantment> key, HolderSet<Item> supported) {
        var definition = Enchantment.definition(supported, 1, 1, Enchantment.constantCost(0), Enchantment.constantCost(10), 1, EquipmentSlotGroup.ANY);
        return Registry.registerForHolder(registry, key, new Enchantment(Component.literal(key.location().toString()), definition, HolderSet.direct(), DataComponentMap.EMPTY));
    }

    private static boolean offered(ItemStack stack, Holder<Enchantment> enchantment) {
        return !EnchantmentHelper.getAvailableEnchantmentResults(1, stack, Stream.of(enchantment)).isEmpty();
    }

    @Test
    void tableUsesItemSpecificPrimaryRules() {
        assertTrue(offered(new ItemStack(mask), aqua));
        assertFalse(offered(new ItemStack(bow), flame));
        assertTrue(offered(new ItemStack(bow), compatible));
        assertFalse(offered(new ItemStack(tool), compatible));
    }

    @Test
    void commandRejectsNonenchantableToolEvenWhenTheEnchantmentSupportsIt() throws Exception {
        Player player = new Player(TileLevelStub.create(), net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        player.getInventory().setItem(0, new ItemStack(tool));
        var method = net.minecraft.server.commands.EnchantCommand.class.getDeclaredMethod("enchant", net.minecraft.commands.CommandSourceStack.class,
              Collection.class, Holder.class, int.class);
        method.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> method.invoke(null, null, List.of(player), compatible, 1));
        assertInstanceOf(CommandSyntaxException.class, failure.getCause());
    }

    @Test
    void anvilAppliesMaskExceptionAndRejectsBooksForNonenchantableGearEvenCreative() {
        Player player = new Player(TileLevelStub.create(), net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        player.getAbilities().instabuild = false;
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(aqua, 1);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        ItemStack maskStack = new ItemStack(mask);
        maskStack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        menu.getSlot(0).set(maskStack);
        menu.getSlot(1).set(book);
        menu.createResult();
        assertEquals(1, EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem()).getLevel(aqua));

        enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(flame, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        ItemStack bowStack = new ItemStack(bow);
        bowStack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        menu.getSlot(0).set(bowStack);
        menu.getSlot(1).set(book);
        menu.createResult();
        assertEquals(1, EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem()).getLevel(flame));

        enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(compatible, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
        ItemStack toolStack = new ItemStack(tool);
        toolStack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        menu.getSlot(0).set(toolStack);
        menu.getSlot(1).set(book);
        menu.createResult();
        assertTrue(menu.getSlot(2).getItem().isEmpty());
        player.getAbilities().instabuild = true;
        menu.createResult();
        assertTrue(menu.getSlot(2).getItem().isEmpty());
    }
}
