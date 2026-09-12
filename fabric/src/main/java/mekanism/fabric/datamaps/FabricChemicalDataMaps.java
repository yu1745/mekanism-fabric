package mekanism.fabric.datamaps;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalDataContext;
import mekanism.api.chemical.ChemicalRegistry;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.MekaSuitAbsorption;
import mekanism.api.datamaps.MekanismDataMapType;
import mekanism.api.datamaps.chemical.ChemicalSolidTag;
import mekanism.api.datamaps.chemical.attribute.ChemicalFuel;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.datamaps.chemical.attribute.CooledCoolant;
import mekanism.api.datamaps.chemical.attribute.HeatedCoolant;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Complete Fabric-backed Mekanism data-map service and server chemical epoch owner. */
public final class FabricChemicalDataMaps implements IMekanismDataMapTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/FabricChemicalDataMaps");
    public static final MekanismDataMapType<Chemical, ChemicalSolidTag> CHEMICAL_SOLID_TAG = new MekanismDataMapType<>(
          ChemicalRegistry.REGISTRY_KEY, ChemicalSolidTag.ID, ChemicalSolidTag.CODEC, stream(
          (buffer, value) -> ResourceLocation.STREAM_CODEC.encode(buffer, value.solidRepresentation().location()),
          buffer -> new ChemicalSolidTag(TagKey.create(Registries.ITEM, ResourceLocation.STREAM_CODEC.decode(buffer)))));
    public static final MekanismDataMapType<Chemical, ChemicalFuel> CHEMICAL_FUEL = new MekanismDataMapType<>(
          ChemicalRegistry.REGISTRY_KEY, ChemicalFuel.ID, ChemicalFuel.CODEC, stream(
          (buffer, value) -> { buffer.writeVarInt(value.burnTicks()); buffer.writeVarLong(value.energyPerTick()); },
          buffer -> new ChemicalFuel(buffer.readVarInt(), buffer.readVarLong())));
    public static final MekanismDataMapType<Chemical, ChemicalRadioactivity> CHEMICAL_RADIOACTIVITY = new MekanismDataMapType<>(
          ChemicalRegistry.REGISTRY_KEY, ChemicalRadioactivity.ID, ChemicalRadioactivity.CODEC, stream(
          (buffer, value) -> buffer.writeDouble(value.radioactivity()),
          buffer -> new ChemicalRadioactivity(buffer.readDouble())));
    public static final MekanismDataMapType<Chemical, CooledCoolant> COOLED_COOLANT = new MekanismDataMapType<>(
          ChemicalRegistry.REGISTRY_KEY, CooledCoolant.ID, CooledCoolant.CODEC, stream(
          (buffer, value) -> { writeChemicalName(buffer, value.otherVariant()); buffer.writeDouble(value.thermalEnthalpy()); buffer.writeDouble(value.conductivity()); },
          buffer -> new CooledCoolant(readChemicalName(buffer), buffer.readDouble(), buffer.readDouble())));
    public static final MekanismDataMapType<Chemical, HeatedCoolant> HEATED_COOLANT = new MekanismDataMapType<>(
          ChemicalRegistry.REGISTRY_KEY, HeatedCoolant.ID, HeatedCoolant.CODEC, stream(
          (buffer, value) -> { writeChemicalName(buffer, value.otherVariant()); buffer.writeDouble(value.thermalEnthalpy()); buffer.writeDouble(value.conductivity()); buffer.writeDouble(value.temperature()); },
          buffer -> new HeatedCoolant(readChemicalName(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
    public static final List<MekanismDataMapType<Chemical, ?>> CHEMICAL_TYPES = List.of(
          CHEMICAL_SOLID_TAG, CHEMICAL_FUEL, CHEMICAL_RADIOACTIVITY, COOLED_COOLANT, HEATED_COOLANT);

    private static final Map<net.minecraft.server.MinecraftServer, FabricChemicalDataMapSession> SESSIONS = new IdentityHashMap<>();
    private static final Set<net.minecraft.server.MinecraftServer> FAILED_STARTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean callbacksInitialized;

    public FabricChemicalDataMaps() {
    }

    private interface Encoder<T> { void encode(RegistryFriendlyByteBuf buffer, T value); }
    private interface Decoder<T> { T decode(RegistryFriendlyByteBuf buffer); }
    private static <T> StreamCodec<RegistryFriendlyByteBuf, T> stream(Encoder<T> encoder, Decoder<T> decoder) {
        return new StreamCodec<>() {
            @Override public T decode(RegistryFriendlyByteBuf buffer) { return decoder.decode(buffer); }
            @Override public void encode(RegistryFriendlyByteBuf buffer, T value) { encoder.encode(buffer, value); }
        };
    }

    private static void writeChemicalName(RegistryFriendlyByteBuf buffer, Holder<Chemical> holder) {
        ResourceKey<Chemical> key = holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Direct coolant chemical holder"));
        if (!key.isFor(ChemicalRegistry.REGISTRY_KEY)) throw new IllegalArgumentException("Coolant holder belongs to another registry");
        ResourceLocation.STREAM_CODEC.encode(buffer, key.location());
    }

    private static Holder<Chemical> readChemicalName(RegistryFriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
        ResourceKey<Chemical> key = ResourceKey.create(ChemicalRegistry.REGISTRY_KEY, id);
        return buffer.registryAccess().lookupOrThrow(ChemicalRegistry.REGISTRY_KEY).getOrThrow(key);
    }

    public static synchronized void initializeCallbacks() {
        if (callbacksInitialized) return;
        FabricDataMapLoader.validateCatalog(allTypes());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            requireThread(server);
            FabricChemicalDataMapSession session;
            synchronized (FabricChemicalDataMaps.class) {
                if (SESSIONS.containsKey(server) || FAILED_STARTS.contains(server)) throw new IllegalStateException("Duplicate chemical map session");
                session = new FabricChemicalDataMapSession(Thread.currentThread());
                SESSIONS.put(server, session);
            }
            try {
                ChemicalDataContext context = session.load(server.getResourceManager(), server.reloadableRegistries().get(), CHEMICAL_TYPES);
                LOGGER.info("MEKANISM_FABRIC_CHEMICAL_DATAMAP_PROOF phase=startup epoch={} maps=5", context.epoch());
            } catch (Throwable failure) {
                synchronized (FabricChemicalDataMaps.class) { SESSIONS.remove(server); FAILED_STARTS.add(server); }
                session.close();
                throw failure;
            }
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (!success) return;
            requireThread(server);
            FabricChemicalDataMapSession session = requireSession(server);
            try {
                ChemicalDataContext context = session.load(resources, server.reloadableRegistries().get(), CHEMICAL_TYPES);
                LOGGER.info("MEKANISM_FABRIC_CHEMICAL_DATAMAP_PROOF phase=reload epoch={} maps=5", context.epoch());
                mekanism.fabric.network.FabricChemicalNetworking.broadcastReload(server, context);
            } catch (Throwable failure) {
                LOGGER.error("Chemical data-map candidate failed; retaining previous complete epoch {}", session.context().epoch(), failure);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            requireThread(server);
            FabricChemicalDataMapSession session;
            synchronized (FabricChemicalDataMaps.class) { session = SESSIONS.remove(server); }
            if (session == null) {
                synchronized (FabricChemicalDataMaps.class) { if (FAILED_STARTS.remove(server)) return; }
                throw new IllegalStateException("Stopping chemical map owner without a session");
            }
            session.close();
        });
        callbacksInitialized = true;
    }

    public static synchronized FabricChemicalDataMapSession session(net.minecraft.server.MinecraftServer server) {
        return requireSession(server);
    }

    private static synchronized FabricChemicalDataMapSession requireSession(net.minecraft.server.MinecraftServer server) {
        FabricChemicalDataMapSession session = SESSIONS.get(server);
        if (session == null) throw new IllegalStateException("No active chemical map session");
        return session;
    }
    private static void requireThread(net.minecraft.server.MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Chemical map lifecycle must run on server thread");
    }

    public static List<MekanismDataMapType<?, ?>> allTypes() {
        return List.of(FabricMekanismDataMaps.MEKASUIT_ABSORPTION, CHEMICAL_SOLID_TAG, CHEMICAL_FUEL,
              CHEMICAL_RADIOACTIVITY, COOLED_COOLANT, HEATED_COOLANT);
    }

    @Override
    public <TYPE, DATA> @Nullable DATA getData(RegistryAccess access, ResourceKey<? extends Registry<? extends TYPE>> registryName,
          Holder<TYPE> holder, MekanismDataMapType<TYPE, DATA> type) {
        if (type == FabricMekanismDataMaps.MEKASUIT_ABSORPTION && registryName.location().equals(Registries.DAMAGE_TYPE.location())) {
            @SuppressWarnings("unchecked") DATA value = (DATA) FabricMekanismDataMaps.getCurrent(access, (Holder<DamageType>) holder);
            return value;
        }
        throw new IllegalArgumentException("Unsupported data-map descriptor " + type.id());
    }

    @Override
    public <DATA> @Nullable DATA getChemicalData(Holder<Chemical> holder, MekanismDataMapType<Chemical, DATA> type) {
        ResourceKey<Chemical> key;
        if (holder.kind() == Holder.Kind.REFERENCE) {
            key = holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Unbound chemical holder"));
            if (!key.isFor(ChemicalRegistry.REGISTRY_KEY)) throw new IllegalArgumentException("Chemical holder belongs to another registry");
        } else {
            key = ChemicalRegistry.registry().getResourceKey(holder.value())
                  .orElseThrow(() -> new IllegalArgumentException("Direct chemical holder is not registered"));
        }
        return ChemicalDataContext.current().get(key, type);
    }

    @Override public MekanismDataMapType<DamageType, MekaSuitAbsorption> mekaSuitAbsorption() { return FabricMekanismDataMaps.MEKASUIT_ABSORPTION; }
    @Override public MekanismDataMapType<Chemical, ChemicalSolidTag> chemicalSolidTag() { return CHEMICAL_SOLID_TAG; }
    @Override public MekanismDataMapType<Chemical, ChemicalFuel> chemicalFuel() { return CHEMICAL_FUEL; }
    @Override public MekanismDataMapType<Chemical, ChemicalRadioactivity> chemicalRadioactivity() { return CHEMICAL_RADIOACTIVITY; }
    @Override public MekanismDataMapType<Chemical, CooledCoolant> cooledChemicalCoolant() { return COOLED_COOLANT; }
    @Override public MekanismDataMapType<Chemical, HeatedCoolant> heatedChemicalCoolant() { return HEATED_COOLANT; }
}
