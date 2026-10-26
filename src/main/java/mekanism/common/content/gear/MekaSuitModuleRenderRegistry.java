package mekanism.common.content.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import mekanism.api.gear.ModuleData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Platform seam for the MekaSuit module renderer.
 *
 * <p>The official {@code IModuleHelper} implementation forwards these registrations straight into the client renderer
 * ({@code MekanismModelCache} and {@code MekaSuitArmor}). Those classes are built on the NeoForge model-geometry API
 * ({@code ModelEvent.BakingCompleted}, {@code IGeometryBakingContext}, {@code ModelData}) and have no Fabric
 * counterpart yet, so the renderer vertical is not ported. Addons can still register their module models during
 * common initialization: the registrations are retained here, in order, and replayed into the client renderer when it
 * installs a {@link Handler}. Until then this seam holds the real data instead of dropping it.
 */
public final class MekaSuitModuleRenderRegistry {

    /** Receives every retained registration, both at install time and for later addon calls. */
    public interface Handler {

        void addModuleModel(ResourceLocation location);

        void addModuleModelSpec(ModelSpec spec);
    }

    public record ModelSpec(String name, Holder<ModuleData<?>> moduleData, EquipmentSlot slotType, Predicate<LivingEntity> isActive) {
    }

    private static final List<ResourceLocation> MODEL_LOCATIONS = new ArrayList<>();
    private static final List<ModelSpec> MODEL_SPECS = new ArrayList<>();
    @Nullable
    private static Handler handler;

    private MekaSuitModuleRenderRegistry() {
    }

    public static synchronized void addModuleModel(ResourceLocation location) {
        MODEL_LOCATIONS.add(location);
        if (handler != null) {
            handler.addModuleModel(location);
        }
    }

    public static synchronized void addModuleModelSpec(String name, Holder<ModuleData<?>> moduleData, EquipmentSlot slotType,
          Predicate<LivingEntity> isActive) {
        ModelSpec spec = new ModelSpec(name, moduleData, slotType, isActive);
        MODEL_SPECS.add(spec);
        if (handler != null) {
            handler.addModuleModelSpec(spec);
        }
    }

    /** Installs the client renderer and replays everything registered before it was available. */
    public static synchronized void install(Handler newHandler) {
        handler = newHandler;
        for (ResourceLocation location : MODEL_LOCATIONS) {
            newHandler.addModuleModel(location);
        }
        for (ModelSpec spec : MODEL_SPECS) {
            newHandler.addModuleModelSpec(spec);
        }
    }

    public static synchronized List<ResourceLocation> getModelLocations() {
        return Collections.unmodifiableList(MODEL_LOCATIONS);
    }

    public static synchronized List<ModelSpec> getModelSpecs() {
        return Collections.unmodifiableList(MODEL_SPECS);
    }
}
