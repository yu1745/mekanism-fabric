package mekanism.fabric.config;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import mekanism.api.radiation.RadiationValidation;
import mekanism.common.config.ChemicalConfigAccess;
import mekanism.common.config.CommonConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Sole owners for complete common.toml and the preserving world general.toml fragment. */
public final class FabricChemicalConfig {
    private static final CommonConfig COMMON = new CommonConfig();
    private static final Map<MinecraftServer, GeneralFragment> SERVERS = new IdentityHashMap<>();
    private static final Set<Path> CLAIMS = new HashSet<>();
    private static boolean commonInitialized;
    private static boolean callbacksInitialized;

    private FabricChemicalConfig() {
    }

    public static synchronized void initializeCommon(FabricMekanismConfigManager manager) {
        if (commonInitialized) return;
        manager.registerAndLoad(COMMON);
        ChemicalConfigAccess.publishCommon(COMMON);
        commonInitialized = true;
    }

    public static synchronized void initializeCallbacks() {
        if (callbacksInitialized) return;
        ServerLifecycleEvents.SERVER_STARTING.register(FabricChemicalConfig::start);
        ServerLifecycleEvents.SERVER_STOPPED.register(FabricChemicalConfig::stop);
        callbacksInitialized = true;
    }

    private static synchronized void start(MinecraftServer server) {
        requireThread(server);
        if (SERVERS.containsKey(server)) throw new IllegalStateException("Duplicate server chemical config owner");
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/Mekanism/general.toml").toAbsolutePath().normalize();
        if (!CLAIMS.add(file)) throw new IllegalStateException("World general config path is already owned: " + file);
        GeneralFragment fragment = new GeneralFragment(file);
        try {
            fragment.load();
            SERVERS.put(server, fragment);
            ChemicalConfigAccess.publishServerPolicy(fragment.policy());
            RadiationValidation.publish(ChemicalConfigAccess::radiationEnabled);
        } catch (Throwable failure) {
            CLAIMS.remove(file);
            fragment.close();
            throw failure;
        }
    }

    private static synchronized void stop(MinecraftServer server) {
        requireThread(server);
        GeneralFragment fragment = SERVERS.remove(server);
        if (fragment == null) return; // a failure before this callback's start is permitted
        fragment.close();
        if (!CLAIMS.remove(fragment.file)) throw new IllegalStateException("World general config claim mismatch");
        ChemicalConfigAccess.resetServerPolicy();
        RadiationValidation.reset();
    }

    public static synchronized ChemicalConfigAccess.ServerPolicy policy(MinecraftServer server) {
        GeneralFragment fragment = SERVERS.get(server);
        if (fragment == null) throw new IllegalStateException("No world chemical config for server");
        return fragment.policy();
    }

    private static void requireThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("World chemical config must run on server thread");
    }

    static final class GeneralFragment implements AutoCloseable {
        private final Path file;
        final MekanismConfigSpec spec;
        private final MekanismConfigSpec.ConfigValue<Boolean> radiationEnabled;
        private final MekanismConfigSpec.ConfigValue<Double> feConversionRate;
        private final MekanismConfigSpec.ConfigValue<Boolean> blacklistForge;

        GeneralFragment(Path file) {
            this.file = file;
            MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();
            builder.push("radiation");
            radiationEnabled = builder.define("enabled", true);
            builder.pop();
            builder.push("energy_conversion");
            feConversionRate = builder.defineInRange("feConversionRate", 2.5, 0.0001, 10_000);
            blacklistForge = builder.worldRestart().define("blacklistForge", false);
            builder.pop();
            spec = builder.build();
        }

        void load() { spec.load(file); }
        ChemicalConfigAccess.ServerPolicy policy() {
            return new ChemicalConfigAccess.ServerPolicy(radiationEnabled.get(), feConversionRate.get(), blacklistForge.get());
        }
        @Override public void close() { spec.unload(); }
    }
}
