package mekanism.fabric.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mekanism.api.SerializationConstants;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Fabric port of the official {@code ModVersionLoadedCondition} datapack condition: the compat recipes shipped by
 * {@code transformCompatRecipes} reference the mekanism-namespaced condition, and the fabric condition registry keys
 * off the same mekanism id, so the packaged form stays the official decision surface.
 *
 * <p>Retains the official Maven version ordering, including qualifier aliases and release normalization.</p>
 */
public record FabricModVersionLoadedCondition(String modid, String minVersion) implements ResourceCondition {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism/FabricCompatConditions");

    public static final MapCodec<FabricModVersionLoadedCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
          Codec.STRING.fieldOf(SerializationConstants.MODID).forGetter(FabricModVersionLoadedCondition::modid),
          Codec.STRING.fieldOf(SerializationConstants.VERSION).forGetter(FabricModVersionLoadedCondition::minVersion)
    ).apply(instance, FabricModVersionLoadedCondition::new));

    private static final ResourceConditionType<FabricModVersionLoadedCondition> TYPE =
          ResourceConditionType.create(ResourceLocation.fromNamespaceAndPath("mekanism", "mod_version_loaded"), MAP_CODEC);

    /** Registers during mod init so the condition is available before any datapack load. */
    public static void register() {
        ResourceConditions.register(TYPE);
        LOGGER.info("MEKANISM_FABRIC_COMPAT_CONDITION_PROOF mod_version_loaded=registered");
    }

    @Override
    public @NotNull ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(@NotNull net.minecraft.core.HolderLookup.Provider lookupProvider) {
        return FabricLoader.getInstance().getModContainer(modid)
              .map(container -> new ComparableVersion(minVersion).compareTo(
                    new ComparableVersion(container.getMetadata().getVersion().getFriendlyString())) <= 0)
              .orElse(false);
    }
}
