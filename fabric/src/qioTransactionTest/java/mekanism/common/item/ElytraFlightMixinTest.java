package mekanism.common.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.UUID;
import mekanism.common.item.gear.ItemHDPEElytra;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.interfaces.IElytraFlight;
import mekanism.common.registries.MekanismArmorMaterials;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElytraFlightMixinTest {
    private static Method updateFallFlying;

    @BeforeAll
    static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        MekanismArmorMaterials.ARMOR_MATERIALS.registerAll(BuiltInRegistries.ARMOR_MATERIAL);
        updateFallFlying = LivingEntity.class.getDeclaredMethod("updateFallFlying");
        updateFallFlying.setAccessible(true);
    }

    @Test
    void actualMekaSuitWithoutEnabledElytraStopsFlying() throws Exception {
        TestPlayer player = new TestPlayer(TileLevelStub.create());
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(new ItemMekaSuitArmor(ArmorItem.Type.CHESTPLATE, new Item.Properties())));
        player.startFallFlying();
        updateFallFlying.invoke(player);
        assertFalse(player.isFallFlying());
        assertEquals(0, player.events);
    }

    @Test
    void customFlightChecksEligibilityBeforeTickAndDoesNotDuplicateEvent() throws Exception {
        TestPlayer player = new TestPlayer(TileLevelStub.create());
        TestFlight item = new TestFlight();
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(item));
        player.startFallFlying();
        player.setFlightTicks(9);
        updateFallFlying.invoke(player);
        assertTrue(player.isFallFlying());
        assertEquals(1, item.ticks);
        assertEquals(1, player.events);
        item.enabled = false;
        updateFallFlying.invoke(player);
        assertFalse(player.isFallFlying());
        assertEquals(1, item.ticks);
        assertEquals(1, player.events);
    }

    @Test
    void hdpeAndVanillaElytraStartAndKeepVanillaEvent() throws Exception {
        for (Item item : new Item[]{Items.ELYTRA, new ItemHDPEElytra(new Item.Properties().durability(432))}) {
            TestPlayer player = new TestPlayer(TileLevelStub.create());
            ItemStack stack = new ItemStack(item);
            player.setItemSlot(EquipmentSlot.CHEST, stack);
            assertTrue(player.tryToStartFallFlying());
            player.setFlightTicks(19);
            updateFallFlying.invoke(player);
            assertTrue(player.isFallFlying());
            // The test level is not ServerLevel, so vanilla's damage path intentionally does not mutate the stack.
            assertEquals(0, stack.getDamageValue());
            assertEquals(1, player.events);
        }
    }

    private static final class TestFlight extends Item implements IElytraFlight {
        boolean enabled = true;
        int ticks;
        TestFlight() { super(new Item.Properties().stacksTo(1)); }
        @Override public boolean canElytraFly(ItemStack stack, LivingEntity entity) { return enabled; }
        @Override public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int ticksFlying) {
            ticks++;
            if ((ticksFlying + 1) % 10 == 0) entity.gameEvent(GameEvent.ELYTRA_GLIDE);
            return true;
        }
    }

    private static final class TestPlayer extends Player {
        int events;
        TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "ElytraTest")); }
        void setFlightTicks(int ticks) { fallFlyTicks = ticks; }
        @Override public void gameEvent(Holder<GameEvent> event) { if (event == GameEvent.ELYTRA_GLIDE) events++; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }
}
