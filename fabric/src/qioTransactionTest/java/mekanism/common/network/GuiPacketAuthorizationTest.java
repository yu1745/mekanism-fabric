package mekanism.common.network;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import mekanism.common.inventory.container.ISecurityContainer;
import mekanism.common.inventory.container.entity.IEntityContainer;
import mekanism.test.TileLevelStub;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuiPacketAuthorizationTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void guiTargetsMustBelongToTheCurrentValidAuthorizedMenu() {
        Level level = TileLevelStub.create();
        TestPlayer player = new TestPlayer(level);
        TestEntity entity = new TestEntity(level);
        assertNull(PacketUtils.guiTile(player, BlockPos.ZERO));
        assertNull(PacketUtils.guiEntity(player, entity.getId()));
        TestMenu menu = new TestMenu(entity);
        player.containerMenu = menu;
        assertSame(entity, PacketUtils.guiEntity(player, entity.getId()));
        assertNull(PacketUtils.guiEntity(player, entity.getId() + 1));
        menu.authorized = false;
        assertNull(PacketUtils.guiEntity(player, entity.getId()));
        menu.authorized = true;
        menu.valid = false;
        assertNull(PacketUtils.guiEntity(player, entity.getId()));
        assertNull(PacketUtils.guiTile(player, BlockPos.ZERO));
    }

    private static final class TestMenu extends AbstractContainerMenu implements IEntityContainer<Entity>, ISecurityContainer {
        private final Entity entity;
        boolean authorized = true;
        boolean valid = true;
        TestMenu(Entity entity) { super(null, 1); this.entity = entity; }
        @Override public Entity getEntity() { return entity; }
        @Override public boolean canPlayerAccess(Player player) { return authorized; }
        @Override public boolean stillValid(Player player) { return valid; }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static final class TestPlayer extends Player {
        TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "MekTest")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }

    private static final class TestEntity extends Entity {
        @SuppressWarnings("unchecked")
        TestEntity(Level level) { super((EntityType<? extends Entity>) (EntityType<?>) EntityType.ARROW, level); }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { }
        @Override protected void readAdditionalSaveData(CompoundTag tag) { }
        @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    }
}
