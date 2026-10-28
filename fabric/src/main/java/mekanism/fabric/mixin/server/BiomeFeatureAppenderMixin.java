package mekanism.fabric.mixin.server;

import java.util.ArrayList;
import java.util.List;
import mekanism.fabric.worldgen.FabricBiomeModifiers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Append official holder sets through Fabric's real mutable generation context and its rebuild lifecycle. */
@Mixin(targets = "net.fabricmc.fabric.impl.biome.modification.BiomeModificationContextImpl$GenerationSettingsContextImpl", remap = false)
abstract class BiomeFeatureAppenderMixin implements FabricBiomeModifiers.FeatureAppender {
    @Shadow @Final private BiomeGenerationSettings generationSettings;
    @Shadow private boolean rebuildFeatures;

    @Override
    public void mekanism$appendFeature(GenerationStep.Decoration step, Holder<PlacedFeature> feature) {
        List<HolderSet<PlacedFeature>> stages = generationSettings.features();
        int index = step.ordinal();
        while (stages.size() <= index) {
            stages.add(HolderSet.direct(List.of()));
        }
        List<Holder<PlacedFeature>> features = new ArrayList<>(stages.get(index).stream().toList());
        // NeoForge's builder appends even if the same holder is already present.
        features.add(feature);
        stages.set(index, HolderSet.direct(features));
        rebuildFeatures = true;
    }
}
