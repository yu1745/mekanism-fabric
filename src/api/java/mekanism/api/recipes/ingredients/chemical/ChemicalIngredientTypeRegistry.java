package mekanism.api.recipes.ingredients.chemical;

import com.mojang.serialization.MapCodec;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import mekanism.api.chemical.Chemical;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Narrow ownership of the built-in {@link ChemicalIngredient} type registry: the six official type IDs, their codecs, and the reverse codec identity used when
 * serializing ingredients back to their typed map form.
 *
 * <p>The six built-ins are exactly:</p>
 * <ul>
 * <li>{@code mekanism:compound}</li>
 * <li>{@code mekanism:difference}</li>
 * <li>{@code mekanism:empty}</li>
 * <li>{@code mekanism:intersection}</li>
 * <li>{@code mekanism:single}</li>
 * <li>{@code mekanism:tag}</li>
 * </ul>
 *
 * <p>Registration happens once per process before any recipe decoding; duplicate IDs, duplicate codec identities, late registration after
 * {@link #freeze()}, and freezing without all built-ins present all fail.</p>
 *
 * @since 10.7.12
 */
public final class ChemicalIngredientTypeRegistry {

    /**
     * The union chemical ingredient type.
     */
    public static final ResourceLocation COMPOUND = ResourceLocation.fromNamespaceAndPath("mekanism", "compound");
    /**
     * The set difference chemical ingredient type.
     */
    public static final ResourceLocation DIFFERENCE = ResourceLocation.fromNamespaceAndPath("mekanism", "difference");
    /**
     * The explicitly empty chemical ingredient type.
     */
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath("mekanism", "empty");
    /**
     * The intersection chemical ingredient type.
     */
    public static final ResourceLocation INTERSECTION = ResourceLocation.fromNamespaceAndPath("mekanism", "intersection");
    /**
     * The single chemical ingredient type.
     */
    public static final ResourceLocation SINGLE = ResourceLocation.fromNamespaceAndPath("mekanism", "single");
    /**
     * The chemical tag ingredient type.
     */
    public static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath("mekanism", "tag");

    private static final Map<ResourceLocation, MapCodec<? extends ChemicalIngredient>> TYPES = new LinkedHashMap<>();
    private static final Map<MapCodec<?>, ResourceLocation> CODEC_IDS = new IdentityHashMap<>();
    private static volatile boolean frozen;

    private ChemicalIngredientTypeRegistry() {
    }

    /**
     * Registers the codec for the given chemical ingredient type.
     *
     * @throws IllegalStateException if the registry is frozen, the ID is already registered, or the exact codec instance is already registered under another ID.
     */
    public static synchronized void register(ResourceLocation id, MapCodec<? extends ChemicalIngredient> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        if (frozen) {
            throw new IllegalStateException("Chemical ingredient type registration is frozen: " + id);
        }
        if (TYPES.containsKey(id)) {
            throw new IllegalStateException("Duplicate chemical ingredient type registration for " + id);
        }
        if (CODEC_IDS.containsKey(codec)) {
            throw new IllegalStateException("Chemical ingredient codec already registered under " + CODEC_IDS.get(codec));
        }
        TYPES.put(id, codec);
        CODEC_IDS.put(codec, id);
    }

    /**
     * Freezes the registry, rejecting any further registration and verifying that all six built-in types are present.
     */
    public static synchronized void freeze() {
        for (ResourceLocation builtIn : new ResourceLocation[]{COMPOUND, DIFFERENCE, EMPTY, INTERSECTION, SINGLE, TAG}) {
            if (!TYPES.containsKey(builtIn)) {
                throw new IllegalStateException("Missing built-in chemical ingredient type " + builtIn);
            }
        }
        frozen = true;
    }

    /**
     * Gets whether the registry has been frozen.
     */
    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /**
     * Gets the registered codec for the given type ID.
     *
     * @throws IllegalArgumentException if no codec is registered under the ID.
     */
    public static synchronized MapCodec<? extends ChemicalIngredient> codec(ResourceLocation id) {
        MapCodec<? extends ChemicalIngredient> codec = TYPES.get(Objects.requireNonNull(id, "id"));
        if (codec == null) {
            throw new IllegalArgumentException("Unknown chemical ingredient type " + id);
        }
        return codec;
    }

    /**
     * Gets the type ID a codec is registered under, for serializing ingredients back to their typed form.
     *
     * @return the ID, or {@code null} if the codec is not registered.
     */
    @Nullable
    public static synchronized ResourceLocation idOf(MapCodec<? extends ChemicalIngredient> codec) {
        return CODEC_IDS.get(Objects.requireNonNull(codec, "codec"));
    }
}
