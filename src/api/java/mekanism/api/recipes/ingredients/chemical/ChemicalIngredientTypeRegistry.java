package mekanism.api.recipes.ingredients.chemical;

import com.mojang.serialization.MapCodec;
import java.util.Objects;
import mekanism.api.chemical.Chemical;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-neutral ownership seam for the canonical, synchronized {@link ChemicalIngredient} type registry: the six official type IDs, their codecs, and the
 * reverse codec identity used when serializing ingredients back to their typed map form.
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
 * <p>The authoritative store is a single vanilla {@link MappedRegistry}, which keeps the native identity value map (exact codec identity, no silent default) and
 * the native id map (duplicate ID rejection). Production installs the root-published, synchronized instance through its Fabric construction owner
 * ({@code FabricChemicalIngredientTypeRegistryLifecycle}) before any recipe decoding; the seam never allocates a substitute registry by itself, so a caller that
 * forgets the construction owner fails loudly instead of silently using a private store. Registration happens once per process; duplicate IDs, duplicate codec
 * identities, late registration after {@link #freeze()}, and freezing without all built-ins present all fail.</p>
 *
 * @since 10.7.12
 */
public final class ChemicalIngredientTypeRegistry {

    /**
     * The id of the canonical chemical ingredient type registry.
     */
    public static final ResourceLocation REGISTRY_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_ingredient_type");
    /**
     * The key of the canonical chemical ingredient type registry. This is the genuine registry identity a future {@code MekanismAPI} alias must expose.
     */
    public static final ResourceKey<Registry<MapCodec<? extends ChemicalIngredient>>> REGISTRY_KEY = ResourceKey.createRegistryKey(REGISTRY_ID);

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

    private static final ResourceLocation[] BUILT_IN_TYPES = new ResourceLocation[]{COMPOUND, DIFFERENCE, EMPTY, INTERSECTION, SINGLE, TAG};

    private static MappedRegistry<MapCodec<? extends ChemicalIngredient>> registry;
    private static volatile boolean frozen;

    private ChemicalIngredientTypeRegistry() {
    }

    /**
     * Installs the canonical registry instance. The exact instance must be the plain, non-defaulted, non-intrusive vanilla registry with the canonical key; a
     * second, different installation fails. Re-installing the same instance is a no-op.
     */
    public static synchronized void install(MappedRegistry<MapCodec<? extends ChemicalIngredient>> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (registry != null) {
            if (registry != candidate) {
                throw new IllegalStateException("Chemical ingredient type registry already installed");
            }
            return;
        }
        if (candidate.key() != REGISTRY_KEY || candidate instanceof DefaultedRegistry<?>) {
            throw new IllegalArgumentException("Chemical ingredient type registry must be the canonical plain registry " + REGISTRY_KEY.location());
        }
        registry = candidate;
    }

    /**
     * Gets the installed canonical registry.
     *
     * @throws IllegalStateException if no registry has been installed.
     */
    public static synchronized MappedRegistry<MapCodec<? extends ChemicalIngredient>> registry() {
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> value = registry;
        if (value == null) {
            throw new IllegalStateException("Chemical ingredient type registry has not been installed");
        }
        return value;
    }

    /**
     * Registers the codec for the given chemical ingredient type.
     *
     * @throws IllegalStateException if the registry is frozen, the ID is already registered, or the exact codec instance is already registered under another ID.
     */
    public static synchronized void register(ResourceLocation id, MapCodec<? extends ChemicalIngredient> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> value = registry();
        if (frozen) {
            throw new IllegalStateException("Chemical ingredient type registration is frozen: " + id);
        }
        if (value.containsKey(id)) {
            throw new IllegalStateException("Duplicate chemical ingredient type registration for " + id);
        }
        ResourceLocation existing = value.getKey(codec);
        if (existing != null) {
            throw new IllegalStateException("Chemical ingredient codec already registered under " + existing);
        }
        value.register(ResourceKey.create(REGISTRY_KEY, id), codec, RegistrationInfo.BUILT_IN);
    }

    /**
     * Freezes the registry, rejecting any further registration and verifying that all six built-in types are present. Freezing an already frozen registry
     * re-validates the built-ins and is otherwise a no-op.
     */
    public static synchronized void freeze() {
        MappedRegistry<MapCodec<? extends ChemicalIngredient>> value = registry();
        for (ResourceLocation builtIn : BUILT_IN_TYPES) {
            if (!value.containsKey(builtIn)) {
                throw new IllegalStateException("Missing built-in chemical ingredient type " + builtIn);
            }
        }
        if (frozen) {
            return;
        }
        value.freeze();
        frozen = true;
    }

    /**
     * Gets whether this owner has frozen the registry.
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
        Objects.requireNonNull(id, "id");
        return registry().getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown chemical ingredient type " + id));
    }

    /**
     * Gets the type ID a codec is registered under, for serializing ingredients back to their typed form.
     *
     * @return the ID, or {@code null} if the codec is not registered.
     */
    @Nullable
    public static synchronized ResourceLocation idOf(MapCodec<? extends ChemicalIngredient> codec) {
        return registry().getKey(Objects.requireNonNull(codec, "codec"));
    }
}
