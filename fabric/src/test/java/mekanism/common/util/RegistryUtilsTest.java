package mekanism.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mekanism.api.SerializationConstants;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Fabric-local behavior proof for the narrowly vanilla-adapted official {@link RegistryUtils}. */
class RegistryUtilsTest {

    private static final ResourceLocation CHEST_ID = vanilla("chest");
    private static final ResourceLocation STONE_ID = vanilla("stone");

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void getBEHolderReturnsCanonicalBuiltInHolder() {
        Holder.Reference<BlockEntityType<?>> canonical = BuiltInRegistries.BLOCK_ENTITY_TYPE.getHolder(CHEST_ID).orElseThrow();

        Holder<BlockEntityType<?>> actual = RegistryUtils.getBEHolder(BlockEntityType.CHEST);

        assertSame(canonical, actual);
        assertEquals(canonical.unwrapKey(), actual.unwrapKey());
    }

    @Test
    void getHolderByIdRejectsAbsentInvalidAndUnknownIdsAndReturnsCanonicalReference() {
        assertTrue(RegistryUtils.getHolderById(null, BuiltInRegistries.BLOCK).isEmpty());
        assertTrue(RegistryUtils.getHolderById(new CompoundTag(), BuiltInRegistries.BLOCK).isEmpty());

        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt(SerializationConstants.ID, 1);
        assertTrue(RegistryUtils.getHolderById(wrongType, BuiltInRegistries.BLOCK).isEmpty());

        assertTrue(RegistryUtils.getHolderById(withId("minecraft:bad path"), BuiltInRegistries.BLOCK).isEmpty());
        assertTrue(RegistryUtils.getHolderById(withId("mekanism:registry_utils_missing"), BuiltInRegistries.BLOCK).isEmpty());

        Holder.Reference<Block> canonical = BuiltInRegistries.BLOCK.getHolder(STONE_ID).orElseThrow();
        assertSame(canonical, RegistryUtils.getHolderById(withId(STONE_ID.toString()), BuiltInRegistries.BLOCK).orElseThrow());
    }

    @Test
    void getPathUsesBuiltInBlockRegistryPath() {
        assertEquals("oak_log", RegistryUtils.getPath(Blocks.OAK_LOG));
    }

    @Test
    void registryAwareGetNameUsesReferenceKeyAndDirectHolderFallback() {
        Holder.Reference<Block> reference = BuiltInRegistries.BLOCK.getHolder(STONE_ID).orElseThrow();
        assertEquals(STONE_ID, RegistryUtils.getName(reference, BuiltInRegistries.BLOCK));
        assertEquals(vanilla("obsidian"), RegistryUtils.getName(Holder.direct(Blocks.OBSIDIAN), BuiltInRegistries.BLOCK));
    }

    @Test
    void nullableGetNameOnlyNamesReferenceHolders() {
        Holder.Reference<Block> reference = BuiltInRegistries.BLOCK.getHolder(STONE_ID).orElseThrow();
        assertEquals(STONE_ID, RegistryUtils.getName(reference));
        assertNull(RegistryUtils.getName(Holder.direct(Blocks.STONE)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void unboundReferencesPreserveNullableNeoForgeKeySemantics() {
        HolderOwner<Block> owner = new HolderOwner<>() {
        };
        ResourceKey<Block> stoneKey = ResourceKey.create(BuiltInRegistries.BLOCK.key(), STONE_ID);
        Holder.Reference<Block> standalone = Holder.Reference.createStandAlone(owner, stoneKey);
        Holder.Reference<Block> intrusive = Holder.Reference.createIntrusive(owner, Blocks.STONE);
        assertFalse(standalone.isBound());
        assertFalse(intrusive.isBound());

        // A standalone holder has a key before its value is bound; an intrusive holder is the inverse.
        assertEquals(STONE_ID, RegistryUtils.getName(standalone));
        assertEquals(STONE_ID, RegistryUtils.getNameGeneric(standalone));
        assertNull(RegistryUtils.getName(intrusive));
        assertNull(RegistryUtils.getNameGeneric(intrusive));
        assertEquals(STONE_ID, RegistryUtils.getName(intrusive, BuiltInRegistries.BLOCK));
    }

    @Test
    void genericGetNameHandlesHoldersRawValuesAndDirectHolderShortCircuit() {
        Holder.Reference<Block> reference = BuiltInRegistries.BLOCK.getHolder(STONE_ID).orElseThrow();
        assertEquals(STONE_ID, RegistryUtils.getNameGeneric(reference));
        assertEquals(STONE_ID, RegistryUtils.getNameGeneric(Blocks.STONE));
        assertNull(RegistryUtils.getNameGeneric(new Object()));

        // Holder handling returns immediately; do not scan its otherwise registered value.
        assertNull(RegistryUtils.getNameGeneric(Holder.direct(Blocks.STONE)));
    }

    private static CompoundTag withId(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString(SerializationConstants.ID, id);
        return tag;
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
