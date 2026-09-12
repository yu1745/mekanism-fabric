package mekanism.api.chemical;

import java.util.Objects;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Platform-neutral ownership seam for the synchronized Mekanism chemical registry. */
public final class ChemicalRegistry {
    public static final ResourceLocation REGISTRY_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical");
    public static final ResourceKey<Registry<Chemical>> REGISTRY_KEY = ResourceKey.createRegistryKey(REGISTRY_ID);
    public static final ResourceKey<Chemical> EMPTY_KEY = ResourceKey.create(REGISTRY_KEY,
          ResourceLocation.fromNamespaceAndPath("mekanism", "empty"));
    private static MappedRegistry<Chemical> mutableRegistry;
    private static DefaultedRegistry<Chemical> registry;
    private static Holder.Reference<Chemical> emptyHolder;
    private static Chemical empty;

    private ChemicalRegistry() {
    }

    public static synchronized void install(MappedRegistry<Chemical> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (mutableRegistry != null) {
            if (mutableRegistry != candidate) throw new IllegalStateException("Chemical registry already installed");
            return;
        }
        if (candidate.key() != REGISTRY_KEY || !(candidate instanceof DefaultedRegistry<?> defaulted)) {
            throw new IllegalArgumentException("Chemical registry must be the canonical defaulted registry");
        }
        mutableRegistry = candidate;
        @SuppressWarnings("unchecked") DefaultedRegistry<Chemical> typed = (DefaultedRegistry<Chemical>) defaulted;
        registry = typed;
    }

    public static synchronized void publishEmpty(Chemical value, Holder.Reference<Chemical> holder) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(holder, "holder");
        if (empty != null) throw new IllegalStateException("Empty chemical already published");
        if (holder.value() != value || holder.key() != EMPTY_KEY || mutable().getHolder(EMPTY_KEY).orElse(null) != holder) {
            throw new IllegalStateException("Empty chemical is not canonical");
        }
        empty = value;
        emptyHolder = holder;
    }

    public static MappedRegistry<Chemical> mutable() {
        MappedRegistry<Chemical> value = mutableRegistry;
        if (value == null) throw new IllegalStateException("Chemical registry has not been installed");
        return value;
    }

    public static DefaultedRegistry<Chemical> registry() {
        DefaultedRegistry<Chemical> value = registry;
        if (value == null) throw new IllegalStateException("Chemical registry has not been installed");
        return value;
    }

    public static Holder.Reference<Chemical> emptyHolder() {
        Holder.Reference<Chemical> value = emptyHolder;
        if (value == null) throw new IllegalStateException("Empty chemical has not been registered");
        return value;
    }

    public static Chemical empty() {
        Chemical value = empty;
        if (value == null) throw new IllegalStateException("Empty chemical has not been registered");
        return value;
    }
}
