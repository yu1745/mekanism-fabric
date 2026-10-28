package mekanism.common.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import java.util.List;
import mekanism.common.item.gear.ItemAtomicDisassembler;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.fabric.gear.BlockToolAnySet;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ToolRuleCodecTest {
    private static RegistryAccess access;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        FabricChemicalRegistryLifecycle.initialize();
        MekanismDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void realToolRetainsExplicitDenyOnlyReplacement() {
        Item item = new ItemAtomicDisassembler(new Item.Properties());
        ItemStack stack = new ItemStack(item);
        Tool defaults = stack.get(DataComponents.TOOL);
        assertInstanceOf(BlockToolAnySet.class, defaults.rules().get(1).blocks());
        assertTrue(stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
        Tool custom = new Tool(List.of(defaults.rules().getFirst()), 1, 0);
        stack.set(DataComponents.TOOL, custom);
        assertEquals(custom, stack.get(DataComponents.TOOL));
        assertFalse(stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
        stack.remove(DataComponents.TOOL);
        assertFalse(stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void anySetObservesRegistryAfterConstruction() {
        MappedRegistry<Block> registry = new MappedRegistry<>(Registries.BLOCK, Lifecycle.stable());
        BlockToolAnySet any = new BlockToolAnySet(registry.asLookup());
        assertEquals(0, any.size());
        Registry.register(registry, ResourceLocation.fromNamespaceAndPath("test", "first"), Blocks.STONE);
        Holder<Block> first = registry.getHolder(ResourceLocation.fromNamespaceAndPath("test", "first")).orElseThrow();
        assertTrue(any.contains(first));
        assertFalse(any.contains(Holder.direct(Blocks.STONE)));
        Registry.register(registry, ResourceLocation.fromNamespaceAndPath("test", "second"), Blocks.DIRT);
        assertEquals(2, any.size());
    }

    @Test
    void ordinaryRuleKeepsVanillaWireEncoding() {
        Tool.Rule rule = Tool.Rule.minesAndDrops(List.of(Blocks.STONE), 2);
        RegistryFriendlyByteBuf actual = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        RegistryFriendlyByteBuf vanilla = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            Tool.Rule.STREAM_CODEC.encode(actual, rule);
            ByteBufCodecs.holderSet(Registries.BLOCK).encode(vanilla, rule.blocks());
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional).encode(vanilla, rule.speed());
            ByteBufCodecs.BOOL.apply(ByteBufCodecs::optional).encode(vanilla, rule.correctForDrops());
            assertEquals(vanilla, actual);
            assertEquals(rule, Tool.Rule.STREAM_CODEC.decode(actual));
        } finally {
            actual.release();
            vanilla.release();
        }
    }

    @Test
    void persistentAndNetworkRoundTripPreserveDynamicRule() {
        Item item = new ItemAtomicDisassembler(new Item.Properties());
        Tool original = item.components().get(DataComponents.TOOL);
        var ops = access.createSerializationContext(JsonOps.INSTANCE);
        var encoded = DataComponents.TOOL.codecOrThrow().encodeStart(ops, original).getOrThrow();
        assertTrue(encoded.toString().contains("neoforge:any"));
        Tool decoded = DataComponents.TOOL.codecOrThrow().parse(ops, encoded).getOrThrow();
        assertInstanceOf(BlockToolAnySet.class, decoded.rules().get(1).blocks());
        assertTrue(decoded.isCorrectForDrops(Blocks.STONE.defaultBlockState()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            DataComponents.TOOL.streamCodec().encode(buffer, original);
            Tool network = DataComponents.TOOL.streamCodec().decode(buffer);
            assertInstanceOf(BlockToolAnySet.class, network.rules().get(1).blocks());
            assertTrue(network.isCorrectForDrops(Blocks.STONE.defaultBlockState()));
        } finally {
            buffer.release();
        }
    }
}
