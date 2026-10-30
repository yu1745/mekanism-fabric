package mekanism.fabric.recipe;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Optional child condition: decode failures retain the official false fallback. */
public record FabricConditionExistsCondition(Optional<ResourceCondition> value) implements ResourceCondition {
    //Fabric uses "condition" as its discriminator, so the official child field maps to "value".
    private static final MapCodec<FabricConditionExistsCondition> CODEC = ResourceCondition.CODEC
          .optionalFieldOf("value").orElse(Optional.empty())
          .xmap(FabricConditionExistsCondition::new, FabricConditionExistsCondition::value);
    private static final ResourceConditionType<FabricConditionExistsCondition> TYPE = ResourceConditionType.create(
          ResourceLocation.fromNamespaceAndPath("mekanism", "condition_exists"), CODEC);

    public static void register() {
        ResourceConditions.register(TYPE);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(@Nullable HolderLookup.Provider provider) {
        return value.map(condition -> condition.test(provider)).orElse(false);
    }
}
