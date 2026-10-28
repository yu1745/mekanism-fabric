package mekanism.fabric.gear;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

/** Restricted support for the fixed upstream tools' neoforge:any block rule. */
public final class BlockToolRuleCodecs {
    private static final Codec<String> TYPE = Codec.STRING.fieldOf("type").codec();
    private static final String ANY = "neoforge:any";
    private static final int ANY_WIRE_MARKER = -1;

    private static final Codec<HolderSet<Block>> ANY_SET = new Codec<>() {
        @Override
        public <T> DataResult<Pair<HolderSet<Block>, T>> decode(DynamicOps<T> ops, T input) {
            return TYPE.decode(ops, input).flatMap(pair -> {
                if (!ANY.equals(pair.getFirst())) {
                    return DataResult.error(() -> "Not the Mekanism any-block set");
                }
                if (!(ops instanceof RegistryOps<?> registryOps)) {
                    return DataResult.error(() -> "Not registry ops");
                }
                return registryOps.owner(Registries.BLOCK)
                      .filter(owner -> owner instanceof HolderLookup.RegistryLookup<?>)
                      .map(owner -> DataResult.success(Pair.of((HolderSet<Block>) new BlockToolAnySet((HolderLookup.RegistryLookup<Block>) owner), pair.getSecond())))
                      .orElseGet(() -> DataResult.error(() -> "Block owner is not a registry lookup"));
            });
        }

        @Override
        public <T> DataResult<T> encode(HolderSet<Block> input, DynamicOps<T> ops, T prefix) {
            if (!(input instanceof BlockToolAnySet)) {
                return DataResult.error(() -> "Not the Mekanism any-block set");
            }
            return TYPE.encode(ANY, ops, prefix);
        }
    };

    private BlockToolRuleCodecs() {
    }

    public static Codec<Tool.Rule> persistent(Codec<Tool.Rule> original) {
        Codec<Tool.Rule> anyRule = RecordCodecBuilder.create(instance -> instance.group(
              ANY_SET.fieldOf("blocks").forGetter(Tool.Rule::blocks),
              ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("speed").forGetter(Tool.Rule::speed),
              Codec.BOOL.optionalFieldOf("correct_for_drops").forGetter(Tool.Rule::correctForDrops)
        ).apply(instance, Tool.Rule::new));
        Codec<Tool.Rule> combined = Codec.either(anyRule, original).xmap(either -> either.map(rule -> rule, rule -> rule),
              rule -> rule.blocks() instanceof BlockToolAnySet ? Either.left(rule) : Either.right(rule));
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<Tool.Rule, T>> decode(DynamicOps<T> ops, T input) {
                return combined.decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(Tool.Rule input, DynamicOps<T> ops, T prefix) {
                if (!(ops instanceof RegistryOps<?> registryOps) || registryOps.owner(Registries.BLOCK).isEmpty()) {
                    return original.encode(input, ops, prefix);
                }
                return combined.encode(input, ops, prefix);
            }
        };
    }

    public static StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> network(StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> original) {
        StreamCodec<RegistryFriendlyByteBuf, HolderSet<Block>> set = new StreamCodec<>() {
            @Override
            public HolderSet<Block> decode(RegistryFriendlyByteBuf buffer) {
                buffer.readVarInt(); // The enclosing rule codec checked the marker.
                return new BlockToolAnySet(buffer.registryAccess().lookupOrThrow(Registries.BLOCK));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, HolderSet<Block> value) {
                BlockToolAnySet any = (BlockToolAnySet) value;
                if (!any.lookup().key().equals(Registries.BLOCK)) {
                    throw new IllegalStateException("Any-block rule has the wrong registry");
                }
                buffer.writeVarInt(ANY_WIRE_MARKER);
            }
        };
        StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> anyRule = StreamCodec.composite(set, Tool.Rule::blocks,
              ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional), Tool.Rule::speed,
              ByteBufCodecs.BOOL.apply(ByteBufCodecs::optional), Tool.Rule::correctForDrops, Tool.Rule::new);
        return new StreamCodec<>() {
            @Override
            public Tool.Rule decode(RegistryFriendlyByteBuf buffer) {
                int index = buffer.readerIndex();
                int marker = buffer.readVarInt();
                buffer.readerIndex(index);
                return marker == ANY_WIRE_MARKER ? anyRule.decode(buffer) : original.decode(buffer);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Tool.Rule value) {
                if (value.blocks() instanceof BlockToolAnySet) {
                    anyRule.encode(buffer, value);
                } else {
                    original.encode(buffer, value);
                }
            }
        };
    }
}
