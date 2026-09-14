package mekanism.common.lib;

import java.util.function.Supplier;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismServerLifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

public abstract class MekanismSavedData extends SavedData {

    public abstract void load(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider);

    /**
     * Note: This should only be called from the server side
     */
    public static <DATA extends MekanismSavedData> DATA createSavedData(Supplier<DATA> createFunction, String name) {
        //Fabric port delta: consult the real lifecycle-bound server owner (captured on SERVER_STARTING, released post-save on SERVER_STOPPED)
        // instead of NeoForge's ServerLifecycleHooks global; world data storage is only touched once the overworld actually exists
        MinecraftServer currentServer = MekanismServerLifecycle.getServer();
        if (currentServer == null) {
            throw new IllegalStateException("Current server is null");
        }
        DimensionDataStorage dataStorage = currentServer.overworld().getDataStorage();
        return createSavedData(dataStorage, new Factory<>(createFunction, (tag, provider) -> {
            DATA handler = createFunction.get();
            handler.load(tag, provider);
            return handler;
        }, null), name); //Fabric object-builder's native null-fixer hook retains the original domain-owned NBT migrations.
    }

    /**
     * Note: This should only be called from the server side
     */
    public static <DATA extends MekanismSavedData> DATA createSavedData(DimensionDataStorage dataStorage, SavedData.Factory<DATA> factory, String name) {
        return dataStorage.computeIfAbsent(factory, MekanismAPI.MEKANISM_MODID + "_" + name);
    }
}
