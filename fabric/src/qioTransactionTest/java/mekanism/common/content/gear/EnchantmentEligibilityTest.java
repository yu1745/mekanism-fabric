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
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EnchantmentEligibilityTest {
    private static ItemMekaTool tool;
    private static ItemScubaMask mask;
    private static ItemElectricBow bow;
    private static Holder<Enchantment> aqua;
    private static Holder<Enchantment> flame;
    private static Holder<Enchantment> compatible;
    private static RegistryAccess registryAccess;

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
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(enchantments));
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
    void fireModeAddsVirtualFlameToLevelAndEquipmentQueriesWithoutChangingStoredEnchantments() throws Exception {
        ItemStack stack = new ItemStack(bow);
        assertEquals(0, EnchantmentHelper.getItemEnchantmentLevel(flame, stack));
        stack.set(MekanismDataComponents.ELECTRIC_BOW_MODE.get(), true);
        assertEquals(1, EnchantmentHelper.getItemEnchantmentLevel(flame, stack));
        assertEquals(0, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(flame));
        Player player = new Player(TileLevelStub.create(), net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
            @Override public RegistryAccess registryAccess() { return registryAccess; }
        };
        Class<?> visitorType = Class.forName("net.minecraft.world.item.enchantment.EnchantmentHelper$EnchantmentInSlotVisitor");
        Map<Holder<Enchantment>, Integer> observed = new LinkedHashMap<>();
        Object visitor = Proxy.newProxyInstance(visitorType.getClassLoader(), new Class<?>[]{visitorType}, (proxy, method, args) -> {
            if (method.getName().equals("accept")) observed.put((Holder<Enchantment>) args[0], (Integer) args[1]);
            return null;
        });
        var method = EnchantmentHelper.class.getDeclaredMethod("runIterationOnItem", ItemStack.class, EquipmentSlot.class, LivingEntity.class, visitorType);
        method.setAccessible(true);
        method.invoke(null, stack, EquipmentSlot.MAINHAND, player, visitor);
        assertEquals(Map.of(flame, 1), observed);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(flame, 2);
        stack.set(DataComponents.ENCHANTMENTS, stored.toImmutable());
        assertEquals(2, EnchantmentHelper.getItemEnchantmentLevel(flame, stack));
        observed.clear();
        method.invoke(null, stack, EquipmentSlot.MAINHAND, player, visitor);
        assertEquals(Map.of(flame, 2), observed);
    }

    @Test
    void fireModeProjectileReceivesFlameDespiteVirtualLevelQuery() throws Exception {
        TileLevelStub level = TileLevelStub.createWithRegistry(registryAccess.registryOrThrow(Registries.ENCHANTMENT));
        Player player = new Player(level, net.minecraft.core.BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        class CapturingArrow extends ArrowItem {
            ItemStack weapon;
            CapturingArrow() { super(new Item.Properties()); }
            @Override public AbstractArrow createArrow(Level world, ItemStack ammo, LivingEntity shooter, ItemStack weapon) {
                this.weapon = weapon.copy();
                return super.createArrow(world, ammo, shooter, weapon);
            }
        }
        CapturingArrow arrow = new CapturingArrow();
        ItemStack stack = new ItemStack(bow);
        stack.set(MekanismDataComponents.ELECTRIC_BOW_MODE.get(), true);
        var method = ItemElectricBow.class.getDeclaredMethod("createProjectile", Level.class, LivingEntity.class, ItemStack.class, ItemStack.class, boolean.class);
        method.setAccessible(true);
        method.invoke(bow, level, player, stack, new ItemStack(arrow), false);
        assertEquals(1, arrow.weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(flame));
        assertEquals(0, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(flame));
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
