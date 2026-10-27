package mekanism.api.recipes.ingredients;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mekanism.api.SerializerHelper;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation for how Mekanism handle's ItemStack Ingredients.
 * <p>
 * Create instances of this using {@link mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess#item()}.
 *
 * @implNote Unlike the NeoForge original which wraps {@code SizedIngredient}, the Fabric port owns a vanilla {@link Ingredient} plus a positive count directly. Union
 * forms decoded from the official {@code neoforge:compound} typed JSON (including the legacy {@code children} field alias) are carried as an explicit compound child
 * list, and component predicates created from stacks with non-default data components are carried as a non-strict {@link DataComponentPredicate}.
 */
@NothingNullByDefault
public final class ItemStackIngredient implements InputIngredient<@NotNull ItemStack> {

    private static final Logger LOGGER = LoggerFactory.getLogger("mekanism.api.MekanismAPI");
    private static final String COMPOUND_TYPE = "neoforge:compound";
    private static final String DIFFERENCE_TYPE = "neoforge:difference";
    private static final String INTERSECTION_TYPE = "neoforge:intersection";

    private static final byte MODE_SIMPLE = 0;
    private static final byte MODE_COMPOUND = 1;
    private static final byte MODE_DIFFERENCE = 2;
    private static final byte MODE_INTERSECTION = 3;

    /**
     * A codec which can (de)encode item stack ingredients.
     *
     * @implNote Flat composition matching the official sized ingredient format: the item/tag fields (or a {@code neoforge:compound} union form) live inline next to the
     * {@code count} field, which defaults to one when absent and is always written.
     *
     * @since 10.6.0
     */
    public static final Codec<ItemStackIngredient> CODEC = new FlatCodec();
    /**
     * A stream codec which can be used to encode and decode item stack ingredients over the network.
     *
     * @since 10.6.0
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStackIngredient> STREAM_CODEC = new NetworkCodec();

    /**
     * Creates an Item Stack Ingredient that matches a given ingredient and amount. Prefer calling via
     * {@link mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess#item()} and
     * {@link mekanism.api.recipes.ingredients.creator.IItemStackIngredientCreator#from(Ingredient, int)}.
     *
     * @param ingredient Ingredient to match.
     * @param amount     Amount needed.
     *
     * @throws NullPointerException     if the given instance is null.
     * @throws IllegalArgumentException if the given ingredient is empty or the amount is not positive.
     * @since 10.6.0
     */
    public static ItemStackIngredient of(Ingredient ingredient, int amount) {
        Objects.requireNonNull(ingredient, "ItemStackIngredients cannot be created from a null ingredient.");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("ItemStackIngredients cannot be created using the empty ingredient.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("ItemStackIngredients require a positive count.");
        }
        return new ItemStackIngredient(ingredient, amount, null, null, null, null);
    }

    /**
     * Creates an Item Stack Ingredient that matches the union of the given ingredients and amount.
     *
     * @param children Ingredients whose union to match.
     * @param amount   Amount needed.
     *
     * @throws NullPointerException     if the given instance is null.
     * @throws IllegalArgumentException if no children are given or the amount is not positive.
     *
     * @since 10.7.12
     */
    public static ItemStackIngredient of(List<Ingredient> children, int amount) {
        Objects.requireNonNull(children, "ItemStackIngredients cannot be created from a null ingredient list.");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("ItemStackIngredients cannot be created using no ingredients.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("ItemStackIngredients require a positive count.");
        }
        if (children.size() == 1) {
            //Match the official compound ingredient behavior of collapsing single element unions
            return of(children.getFirst(), amount);
        }
        return new ItemStackIngredient(children.getFirst(), amount, null, List.copyOf(children), null, null);
    }

    /**
     * Creates an Item Stack Ingredient that matches the given ingredient, additionally requiring the given non-strict data component predicate. Used to implement the
     * component construction of the official data component ingredient.
     *
     * @param ingredient  Ingredient to match.
     * @param amount      Amount needed.
     * @param components Non-strict component predicate to require.
     *
     * @throws NullPointerException     if any given instance is null.
     * @throws IllegalArgumentException if the given ingredient is empty or the amount is not positive.
     *
     * @since 10.7.12
     */
    @Internal
    public static ItemStackIngredient of(Ingredient ingredient, int amount, DataComponentPredicate components) {
        Objects.requireNonNull(ingredient, "ItemStackIngredients cannot be created from a null ingredient.");
        Objects.requireNonNull(components, "Component predicates cannot be null; use the overloads without one.");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("ItemStackIngredients cannot be created using the empty ingredient.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("ItemStackIngredients require a positive count.");
        }
        return new ItemStackIngredient(ingredient, amount, components, null, null, null);
    }

    private final Ingredient ingredient;
    private final int count;
    @Nullable
    private final DataComponentPredicate components;
    @Nullable
    private final List<Ingredient> compoundChildren;
    @Nullable
    private final Ingredient subtracted;
    @Nullable
    private final List<Ingredient> intersectionChildren;
    @Nullable
    private List<ItemStack> representations;

    /**
     * Creates an Item Stack Ingredient that matches the given ingredient minus any stacks matching the subtracted ingredient. Used to decode the official
     * {@code neoforge:difference} typed JSON.
     *
     * @param ingredient Ingredient whose matches to start from.
     * @param subtracted Ingredient whose matches to exclude.
     * @param amount     Amount needed.
     *
     * @throws NullPointerException     if either ingredient instance is null.
     * @throws IllegalArgumentException if the base ingredient is empty or the amount is not positive.
     *
     * @since 10.7.12
     */
    public static ItemStackIngredient ofDifference(Ingredient ingredient, Ingredient subtracted, int amount) {
        Objects.requireNonNull(ingredient, "ItemStackIngredients cannot be created from a null ingredient.");
        Objects.requireNonNull(subtracted, "Difference ItemStackIngredients cannot be created from a null subtracted ingredient.");
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("ItemStackIngredients cannot be created using the empty ingredient.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("ItemStackIngredients require a positive count.");
        }
        return new ItemStackIngredient(ingredient, amount, null, null, subtracted, null);
    }

    /**
     * Creates an Item Stack Ingredient that matches the intersection of the given ingredients and amount. Used to decode the official
     * {@code neoforge:intersection} typed JSON.
     *
     * @param children Ingredients whose intersection to match.
     * @param amount   Amount needed.
     *
     * @throws NullPointerException     if the children instance is null.
     * @throws IllegalArgumentException if no children are given or the amount is not positive.
     *
     * @since 10.7.12
     */
    public static ItemStackIngredient ofIntersection(List<Ingredient> children, int amount) {
        Objects.requireNonNull(children, "ItemStackIngredients cannot be created from a null ingredient list.");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("Intersection ItemStackIngredients cannot be created from no ingredients.");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("ItemStackIngredients require a positive count.");
        }
        return new ItemStackIngredient(children.getFirst(), amount, null, null, null, List.copyOf(children));
    }

    private ItemStackIngredient(Ingredient ingredient, int count, @Nullable DataComponentPredicate components, @Nullable List<Ingredient> compoundChildren,
          @Nullable Ingredient subtracted, @Nullable List<Ingredient> intersectionChildren) {
        this.ingredient = ingredient;
        this.count = count;
        this.components = components;
        this.compoundChildren = compoundChildren;
        this.subtracted = subtracted;
        this.intersectionChildren = intersectionChildren;
    }

    @Override
    public boolean test(ItemStack stack) {
        Objects.requireNonNull(stack);
        return testType(stack) && stack.getCount() >= count;
    }

    @Override
    public boolean testType(ItemStack stack) {
        Objects.requireNonNull(stack);
        if (!matchesType(stack)) {
            return false;
        }
        return components == null || components.test(stack);
    }

    private boolean matchesType(ItemStack stack) {
        if (compoundChildren != null) {
            for (Ingredient child : compoundChildren) {
                if (child.test(stack)) {
                    return true;
                }
            }
            return false;
        }
        if (intersectionChildren != null) {
            for (Ingredient child : intersectionChildren) {
                if (!child.test(stack)) {
                    return false;
                }
            }
            return true;
        }
        if (subtracted != null && subtracted.test(stack)) {
            return false;
        }
        return ingredient.test(stack);
    }

    @Override
    public ItemStack getMatchingInstance(ItemStack stack) {
        return test(stack) ? stack.copyWithCount(count) : ItemStack.EMPTY;
    }

    @Override
    public long getNeededAmount(ItemStack stack) {
        return testType(stack) ? count : 0;
    }

    @Override
    public boolean hasNoMatchingInstances() {
        if (compoundChildren != null) {
            for (Ingredient child : compoundChildren) {
                if (!hasNoItems(child)) {
                    return false;
                }
            }
            return true;
        }
        if (intersectionChildren != null) {
            for (Ingredient child : intersectionChildren) {
                if (hasNoItems(child)) {
                    return true;
                }
            }
        }
        if (hasNoItems(ingredient)) {
            return true;
        }
        if (subtracted != null || intersectionChildren != null) {
            //A nonempty base does not guarantee a nonempty difference or intersection.
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty() && matchesType(stack)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Vanilla replacement for the platform {@code hasNoItems} check: an ingredient has no matching instances when it is explicitly empty, or when the only
     * representation it produced is the barrier stack vanilla substitutes for empty tags.
     */
    private static boolean hasNoItems(Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return true;
        }
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return true;
        }
        if (items.length == 1) {
            ItemStack item = items[0];
            return item.is(Items.BARRIER) && item.getHoverName() instanceof MutableComponent hoverName && hoverName.getString().startsWith("Empty Tag: ");
        }
        return false;
    }

    @Override
    public void logMissingTags() {
        if (hasNoMatchingInstances()) {
            //Note: Vanilla ingredients cannot be introspected back into their values, so unlike the original which reported the exact empty tag value,
            // the Fabric port reports the stringified ingredient (the official fallback used for non-simple ingredients)
            for (Ingredient unsized : ingredients()) {
                LOGGER.error("Empty ItemStackIngredient: {}", SerializerHelper.stringify(Ingredient.CODEC, unsized));
            }
        }
    }

    @Override
    public List<@NotNull ItemStack> getRepresentations() {
        if (this.representations == null) {
            //Note: The official implementation returned one stack per representation with the needed count applied
            List<ItemStack> stacks = new ArrayList<>();
            for (Ingredient unsized : ingredients()) {
                for (ItemStack item : unsized.getItems()) {
                    if (!item.isEmpty() && matchesType(item) && stacks.stream().noneMatch(existing -> ItemStack.isSameItemSameComponents(existing, item))) {
                        stacks.add(item.copyWithCount(count));
                    }
                }
            }
            this.representations = List.copyOf(stacks);
        }
        return representations;
    }

    /**
     * For use in recipe input caching. Gets the internal vanilla ingredient.
     *
     * @apiNote For ingredients decoded from compound union forms, this returns the first child rather than the union; use {@link #ingredients()} for cache mapping.
     *
     * @since 10.6.0
     */
    @Internal
    public Ingredient ingredient() {
        return ingredient;
    }

    /**
     * For use in recipe input caching. Gets every vanilla ingredient this ingredient matches through: a singleton of the primary ingredient for plain forms, or the
     * compound children for union forms.
     *
     * @since 10.7.12
     */
    @Internal
    public List<Ingredient> ingredients() {
        if (compoundChildren != null) {
            return compoundChildren;
        }
        if (intersectionChildren != null) {
            //The intersection's cache surface is its membership; the primary ingredient is the first child
            return intersectionChildren;
        }
        return List.of(ingredient);
    }

    /** Whether cache candidates require a full type check rather than per-item membership. */
    @Internal
    public boolean requiresComplexItemLookup() {
        return components != null || subtracted != null || intersectionChildren != null;
    }

    /**
     * For use in recipe input caching. Checks if this ingredient applies a non-strict data component predicate, which makes quick per-item lookup invalid.
     *
     * @since 10.7.12
     */
    @Internal
    public boolean hasComponentPredicate() {
        return components != null;
    }

    /**
     * For use in recipe input caching. Gets the needed count.
     *
     * @since 10.7.12
     */
    @Internal
    public int count() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ItemStackIngredient other = (ItemStackIngredient) o;
        return count == other.count && ingredient.equals(other.ingredient) && Objects.equals(components, other.components)
              && Objects.equals(compoundChildren, other.compoundChildren);
    }

    @Override
    public int hashCode() {
        int result = ingredient.hashCode();
        result = 31 * result + count;
        result = 31 * result + (components == null ? 0 : components.hashCode());
        result = 31 * result + (compoundChildren == null ? 0 : compoundChildren.hashCode());
        return result;
    }

    @Override
    public String toString() {
        if (compoundChildren != null) {
            return count + "x " + compoundChildren;
        }
        return count + "x " + SerializerHelper.stringify(Ingredient.CODEC, ingredient);
    }

    private static final class FlatCodec implements Codec<ItemStackIngredient> {

        @Override
        public <T> DataResult<Pair<ItemStackIngredient, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<ItemStackIngredient> decoded = decodeTyped(ops, input);
            return decoded.map(ingredient -> Pair.of(ingredient, input));
        }

        private <T> DataResult<ItemStackIngredient> decodeTyped(DynamicOps<T> ops, T input) {
            T typeValue = ops.get(input, SerializationConstants.TYPE).result().orElse(null);
            if (typeValue != null) {
                return ops.getStringValue(typeValue).flatMap(type -> {
                    if (COMPOUND_TYPE.equals(type)) {
                        return decodeCompound(ops, input);
                    }
                    if (DIFFERENCE_TYPE.equals(type)) {
                        return decodeDifference(ops, input);
                    }
                    if (INTERSECTION_TYPE.equals(type)) {
                        return decodeIntersection(ops, input);
                    }
                    return DataResult.error(() -> "Unknown custom item ingredient type: '" + type + "' is not supported");
                });
            }
            return Ingredient.CODEC_NONEMPTY.decode(ops, input)
                  .flatMap(ingredient -> decodeCount(ops, input).map(count -> ItemStackIngredient.of(ingredient.getFirst(), count)));
        }

        private <T> DataResult<ItemStackIngredient> decodeCompound(DynamicOps<T> ops, T input) {
            boolean hasChildren = ops.get(input, SerializationConstants.CHILDREN).result().isPresent();
            //Note: Accept the documented legacy child-field alias only when the canonical field is absent; malformed canonical input must retain its real error.
            DataResult<List<Ingredient>> children = decodeChildren(ops, input,
                  hasChildren ? SerializationConstants.CHILDREN : SerializationConstants.INGREDIENTS);
            return children.flatMap(list -> {
                if (list.isEmpty()) {
                    return DataResult.error(() -> "Compound item ingredients must have at least one child");
                }
                return decodeCount(ops, input).map(count -> ItemStackIngredient.of(list, count));
            });
        }

        private <T> DataResult<List<Ingredient>> decodeChildren(DynamicOps<T> ops, T input, String field) {
            T value = ops.get(input, field).result().orElse(null);
            if (value == null) {
                return DataResult.error(() -> "No " + field + " field found");
            }
            return Ingredient.CODEC_NONEMPTY.listOf().decode(ops, value).map(Pair::getFirst);
        }

        private <T> DataResult<ItemStackIngredient> decodeDifference(DynamicOps<T> ops, T input) {
            T baseValue = ops.get(input, SerializationConstants.BASE).result().orElse(null);
            if (baseValue == null) {
                return DataResult.error(() -> "No " + SerializationConstants.BASE + " field found");
            }
            T subtractedValue = ops.get(input, SerializationConstants.SUBTRACTED).result().orElse(null);
            if (subtractedValue == null) {
                return DataResult.error(() -> "No " + SerializationConstants.SUBTRACTED + " field found");
            }
            return Ingredient.CODEC_NONEMPTY.decode(ops, baseValue).flatMap(base -> Ingredient.CODEC_NONEMPTY.decode(ops, subtractedValue)
                  .flatMap(subtracted -> decodeCount(ops, input).map(count -> ItemStackIngredient.ofDifference(base.getFirst(), subtracted.getFirst(), count))));
        }

        private <T> DataResult<ItemStackIngredient> decodeIntersection(DynamicOps<T> ops, T input) {
            DataResult<List<Ingredient>> children = decodeChildren(ops, input, SerializationConstants.CHILDREN);
            return children.flatMap(list -> {
                if (list.isEmpty()) {
                    return DataResult.error(() -> "Intersection item ingredients must have at least one child");
                }
                return decodeCount(ops, input).map(count -> ItemStackIngredient.ofIntersection(list, count));
            });
        }

        private <T> DataResult<Integer> decodeCount(DynamicOps<T> ops, T input) {
            return Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf(SerializationConstants.COUNT, 1).codec().decode(ops, input).map(Pair::getFirst);
        }

        @Override
        public <T> DataResult<T> encode(ItemStackIngredient value, DynamicOps<T> ops, T prefix) {
            if (value.subtracted != null) {
                return encodeDifference(value, ops, prefix);
            }
            if (value.intersectionChildren != null) {
                return encodeIntersection(value, ops, prefix);
            }
            List<Ingredient> ingredients = value.ingredients();
            if (ingredients.size() > 1) {
                return encodeCompound(value, ops, prefix, ingredients);
            }
            return Ingredient.CODEC_NONEMPTY.encodeStart(ops, ingredients.getFirst()).flatMap(serialized -> {
                Optional<MapLike<T>> map = ops.getMap(serialized).result();
                if (map.isPresent()) {
                    return ops.mergeToMap(serialized, ops.createString(SerializationConstants.COUNT), ops.createInt(value.count));
                }
                //Multi-value ingredients serialize to list form, which cannot carry an inline count; wrap them in the typed compound form instead
                return encodeCompound(value, ops, prefix, ingredients);
            });
        }

        private <T> DataResult<T> encodeDifference(ItemStackIngredient value, DynamicOps<T> ops, T prefix) {
            return Ingredient.CODEC_NONEMPTY.encodeStart(ops, value.ingredient).flatMap(base -> Ingredient.CODEC_NONEMPTY.encodeStart(ops, value.subtracted)
                  .flatMap(subtracted -> {
                      RecordBuilder<T> builder = ops.mapBuilder();
                      builder.add(SerializationConstants.TYPE, ops.createString(DIFFERENCE_TYPE));
                      builder.add(SerializationConstants.BASE, base);
                      builder.add(SerializationConstants.SUBTRACTED, subtracted);
                      builder.add(SerializationConstants.COUNT, ops.createInt(value.count));
                      return builder.build(prefix);
                  }));
        }

        private <T> DataResult<T> encodeIntersection(ItemStackIngredient value, DynamicOps<T> ops, T prefix) {
            return Ingredient.CODEC_NONEMPTY.listOf().encodeStart(ops, value.intersectionChildren).flatMap(children -> {
                RecordBuilder<T> builder = ops.mapBuilder();
                builder.add(SerializationConstants.TYPE, ops.createString(INTERSECTION_TYPE));
                builder.add(SerializationConstants.CHILDREN, children);
                builder.add(SerializationConstants.COUNT, ops.createInt(value.count));
                return builder.build(prefix);
            });
        }

        private <T> DataResult<T> encodeCompound(ItemStackIngredient value, DynamicOps<T> ops, T prefix, List<Ingredient> ingredients) {
            return Ingredient.CODEC_NONEMPTY.listOf().encodeStart(ops, ingredients).flatMap(children -> {
                RecordBuilder<T> builder = ops.mapBuilder();
                builder.add(SerializationConstants.TYPE, ops.createString(COMPOUND_TYPE));
                builder.add(SerializationConstants.CHILDREN, children);
                builder.add(SerializationConstants.COUNT, ops.createInt(value.count));
                return builder.build(prefix);
            });
        }
    }

    private static final class NetworkCodec implements StreamCodec<RegistryFriendlyByteBuf, ItemStackIngredient> {

        @Override
        public ItemStackIngredient decode(RegistryFriendlyByteBuf buffer) {
            int count = ByteBufCodecs.VAR_INT.decode(buffer);
            int mode = buffer.readByte();
            Ingredient primary;
            List<Ingredient> children = null;
            Ingredient subtracted = null;
            if (mode == MODE_COMPOUND) {
                int size = ByteBufCodecs.VAR_INT.decode(buffer);
                if (size < 1) {
                    throw new IllegalStateException("Compound item ingredient wire payload must have at least one child");
                }
                List<Ingredient> decoded = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    decoded.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
                }
                children = decoded;
                primary = decoded.getFirst();
            } else if (mode == MODE_DIFFERENCE) {
                primary = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                subtracted = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            } else if (mode == MODE_INTERSECTION) {
                int size = ByteBufCodecs.VAR_INT.decode(buffer);
                if (size < 1) {
                    throw new IllegalStateException("Intersection item ingredient wire payload must have at least one child");
                }
                List<Ingredient> decoded = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    decoded.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
                }
                children = decoded;
                primary = decoded.getFirst();
            } else if (mode == MODE_SIMPLE) {
                primary = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            } else {
                throw new IllegalStateException("Unknown item ingredient wire mode: " + mode);
            }
            boolean hasComponents = buffer.readBoolean();
            if (hasComponents) {
                if (mode != MODE_SIMPLE) {
                    throw new IllegalStateException("Item ingredient wire payload cannot combine a shaped form with component sensitivity");
                }
                DataComponentPredicate components = DataComponentPredicate.STREAM_CODEC.decode(buffer);
                return ItemStackIngredient.of(primary, count, components);
            }
            if (subtracted != null) {
                return ItemStackIngredient.ofDifference(primary, subtracted, count);
            }
            if (children != null && children.size() > 1) {
                return mode == MODE_INTERSECTION ? ItemStackIngredient.ofIntersection(children, count) : ItemStackIngredient.of(children, count);
            }
            return ItemStackIngredient.of(primary, count);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemStackIngredient value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.count);
            if (value.subtracted != null) {
                buffer.writeByte(MODE_DIFFERENCE);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, value.ingredient);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, value.subtracted);
            } else if (value.intersectionChildren != null) {
                buffer.writeByte(MODE_INTERSECTION);
                ByteBufCodecs.VAR_INT.encode(buffer, value.intersectionChildren.size());
                for (Ingredient child : value.intersectionChildren) {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, child);
                }
            } else {
                List<Ingredient> ingredients = value.ingredients();
                boolean compound = ingredients.size() > 1;
                buffer.writeByte(compound ? MODE_COMPOUND : MODE_SIMPLE);
                if (compound) {
                    ByteBufCodecs.VAR_INT.encode(buffer, ingredients.size());
                    for (Ingredient child : ingredients) {
                        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, child);
                    }
                } else {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredients.getFirst());
                }
                boolean hasComponents = value.hasComponentPredicate();
                buffer.writeBoolean(hasComponents);
                if (hasComponents) {
                    DataComponentPredicate.STREAM_CODEC.encode(buffer, value.components);
                }
                return;
            }
            boolean hasComponents = value.hasComponentPredicate();
            buffer.writeBoolean(hasComponents);
            if (hasComponents) {
                throw new IllegalStateException("Item ingredient wire payload cannot combine a shaped form with component sensitivity");
            }
        }
    }
}
