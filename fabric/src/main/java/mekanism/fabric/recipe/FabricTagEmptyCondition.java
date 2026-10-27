package mekanism.fabric.recipe;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mekanism.api.SerializationConstants;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.impl.resource.conditions.ResourceConditionsImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagManager;
import org.jetbrains.annotations.Nullable;

/** NeoForge's item tag-empty condition evaluated against the tags prepared for the current data reload. */
public record FabricTagEmptyCondition(ResourceLocation tag) implements ResourceCondition {

    private static final MapCodec<FabricTagEmptyCondition> CODEC = ResourceLocation.CODEC
          .fieldOf(SerializationConstants.TAG).xmap(FabricTagEmptyCondition::new, FabricTagEmptyCondition::tag);
    private static final ResourceConditionType<FabricTagEmptyCondition> TYPE = ResourceConditionType.create(
          ResourceLocation.fromNamespaceAndPath("mekanism", "tag_empty"), CODEC);
    private static final ThreadLocal<Set<ResourceLocation>> NONEMPTY_ITEM_TAGS = new ThreadLocal<>();

    public static void register() {
        ResourceConditions.register(TYPE);
    }

    /** Mirrors Fabric's tag snapshot timing, keeping only ids and no holders across reloads. */
    public static void captureTags(List<TagManager.LoadResult<?>> results) {
        Set<ResourceLocation> populated = new HashSet<>();
        for (TagManager.LoadResult<?> result : results) {
            if (result.key().equals(Registries.ITEM)) {
                result.tags().forEach((id, values) -> {
                    if (!values.isEmpty()) {
                        populated.add(id);
                    }
                });
            }
        }
        NONEMPTY_ITEM_TAGS.set(Set.copyOf(populated));
    }

    public static void clearTags() {
        NONEMPTY_ITEM_TAGS.remove();
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(@Nullable HolderLookup.Provider provider) {
        Set<ResourceLocation> tags = NONEMPTY_ITEM_TAGS.get();
        if (ResourceConditionsImpl.LOADED_TAGS.get() == null || tags == null) {
            throw new IllegalStateException("Mekanism tag-empty condition evaluated outside tag reload");
        }
        return !tags.contains(tag);
    }
}
