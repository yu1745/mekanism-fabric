package mekanism.api;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Iterator;
import java.util.ServiceLoader;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.gear.ModuleRegistry;
import mekanism.api.robit.RobitSkinRegistry;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredientTypeRegistry;
import mekanism.fabric.chemical.FabricChemicalRegistryLifecycle;
import mekanism.fabric.chemical.FabricChemicalIngredientTypeRegistryLifecycle;
import mekanism.fabric.gear.FabricModuleRegistry;
import mekanism.fabric.robit.FabricRobitRegistries;
import mekanism.api.gear.ModuleData;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.robit.RobitSkin;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.registration.MekanismDeferredRegister;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.slf4j.Logger;

@NothingNullByDefault
public class MekanismAPI {

    private MekanismAPI() {
    }

    /**
     * The version of the api classes - may not always match the mod's version
     */
    public static final String API_VERSION = "10.7.15";
    /**
     * Mekanism's Mod ID
     */
    public static final String MEKANISM_MODID = "mekanism";
    /**
     * Mekanism debug mode
     */
    public static boolean debug = false;
    /**
     * Logger for use in Mekanism's API classes
     */
    public static final Logger logger = LogUtils.getLogger();

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MEKANISM_MODID, path);
    }

    private static <T> ResourceKey<Registry<T>> registryKey(@SuppressWarnings("unused") Class<T> compileTimeTypeValidator, String path) {
        return ResourceKey.createRegistryKey(rl(path));
    }

    private static <T> ResourceKey<Registry<MapCodec<? extends T>>> codecRegistryKey(@SuppressWarnings("unused") Class<T> compileTimeTypeValidator, String path) {
        return ResourceKey.createRegistryKey(rl(path));
    }

    /**
     * Gets the {@link ResourceKey} representing the name of the Registry for {@link Chemical chemicals}.
     *
     * @apiNote When registering {@link Chemical chemicals} using {@link DeferredRegister}, use this field to get access to the {@link ResourceKey}.
     * @since 10.7.0
     */
    public static final ResourceKey<Registry<Chemical>> CHEMICAL_REGISTRY_NAME = ChemicalRegistry.REGISTRY_KEY;

    /**
     * Constant location representing the name all empty chemicals will be registered under.
     *
     * @since 10.6.0
     */
    public static final ResourceKey<Chemical> EMPTY_CHEMICAL_KEY = ChemicalRegistry.EMPTY_KEY;

    /**
     * Constant location representing the name all empty chemicals will be registered under.
     *
     * @since 10.6.0
     *
     * @deprecated Use {@link #EMPTY_CHEMICAL_KEY} instead
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public static final ResourceLocation EMPTY_CHEMICAL_NAME = EMPTY_CHEMICAL_KEY.location();

    /**
     * Gets the {@link ResourceKey} representing the name of the Registry for {@link ChemicalIngredient} ingredient type serializers.
     *
     * @apiNote When registering chemical ingredient types using {@link DeferredRegister}, use this field to get access to the {@link ResourceKey}.
     * @since 10.7.0
     */
    public static final ResourceKey<Registry<MapCodec<? extends ChemicalIngredient>>> CHEMICAL_INGREDIENT_TYPE_REGISTRY_NAME = ChemicalIngredientTypeRegistry.REGISTRY_KEY;

    /**
     * Gets the {@link ResourceKey} representing the name of the Registry for {@link ModuleData modules}.
     *
     * @apiNote When registering {@link ModuleData modules} using {@link DeferredRegister}, use this field to get access to the {@link ResourceKey}.
     * @since 10.4.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final ResourceKey<Registry<ModuleData<?>>> MODULE_REGISTRY_NAME = ModuleRegistry.KEY;
    /**
     * Gets the {@link ResourceKey} representing the name of the Datapack Registry for {@link RobitSkin robit skins}.
     *
     * @since 10.4.0
     */
    public static final ResourceKey<Registry<RobitSkin>> ROBIT_SKIN_REGISTRY_NAME = RobitSkinRegistry.SKIN_KEY;
    /**
     * Gets the {@link ResourceKey} representing the name of the Registry for {@link RobitSkin robit skin} serializers.
     *
     * @apiNote When registering {@link RobitSkin robit skin} serializers using {@link DeferredRegister}, use this field to get access to the {@link ResourceKey}.
     * @since 10.4.0
     */
    public static final ResourceKey<Registry<MapCodec<? extends RobitSkin>>> ROBIT_SKIN_SERIALIZER_REGISTRY_NAME = RobitSkinRegistry.SERIALIZER_KEY;

    /**
     * Gets the Registry for {@link Chemical}.
     *
     * @see #CHEMICAL_REGISTRY_NAME
     * @since 10.7.0
     */
    public static final DefaultedRegistry<Chemical> CHEMICAL_REGISTRY = FabricChemicalRegistryLifecycle.allocate();

    /**
     * Gets the Registry for {@link ChemicalIngredient} type serializers.
     *
     * @see #CHEMICAL_INGREDIENT_TYPE_REGISTRY_NAME
     * @since 10.7.0
     */
    public static final Registry<MapCodec<? extends ChemicalIngredient>> CHEMICAL_INGREDIENT_TYPES = FabricChemicalIngredientTypeRegistryLifecycle.allocate();

    /**
     * Gets the Registry for {@link ModuleData}.
     *
     * @see #MODULE_REGISTRY_NAME
     * @since 10.5.0
     */
    public static final Registry<ModuleData<?>> MODULE_REGISTRY = FabricModuleRegistry.allocate();
    /**
     * Gets the Registry for {@link RobitSkin} serializers.
     *
     * @see #ROBIT_SKIN_SERIALIZER_REGISTRY_NAME
     * @since 10.5.0
     */
    public static final Registry<MapCodec<? extends RobitSkin>> ROBIT_SKIN_SERIALIZER_REGISTRY = FabricRobitRegistries.allocate();

    /**
     * Empty Chemical instance.
     *
     * @since 10.7.0
     *
     * @deprecated Use {@link #EMPTY_CHEMICAL_HOLDER}
     */
    @Deprecated(forRemoval = true, since = "10.7.11")//TODO - 1.22: Inline creation instead of creating the instance in the API
    public static final Chemical EMPTY_CHEMICAL = ChemicalRegistry.empty();
    /**
     * Holder for the empty Chemical instance.
     *
     * @since 10.7.11
     */
    public static final Holder<Chemical> EMPTY_CHEMICAL_HOLDER = ChemicalRegistry.emptyHolder();

    @Internal
    private static final ClassLoader SERVICE_CL = MekanismAPI.class.getClassLoader();

    /**
     * Loads a Mekanism service from ServiceLoader, ensuring that the correct classloader is used instead of relying on the context classloader, which may not be correct
     *
     * @param serviceClass the interface class to search for
     *
     * @return the concrete implementation
     *
     * @throws IllegalStateException when an implementation is not found
     */
    @Internal
    public static <SERVICE> SERVICE getService(Class<SERVICE> serviceClass) {
        Iterator<SERVICE> service = ServiceLoader.load(serviceClass, SERVICE_CL).iterator();
        if (service.hasNext()) {
            return service.next();
        }

        IllegalStateException illegalStateException = new IllegalStateException("No valid ServiceImpl for " + serviceClass.getSimpleName() + " found");
        MekanismAPI.logger.error("Failed to load service", illegalStateException);
        MekanismAPI.logger.error("CL: {} CCL: {}", SERVICE_CL, Thread.currentThread().getContextClassLoader());
        throw illegalStateException;

    }

}