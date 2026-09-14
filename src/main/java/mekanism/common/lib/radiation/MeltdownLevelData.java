package mekanism.common.lib.radiation;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.ParametersAreNonnullByDefault;
import mekanism.api.INBTSerializable;
import mekanism.fabric.radiation.FabricRadiationAttachments;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MeltdownLevelData implements INBTSerializable<ListTag> {

    //Retain the original list of meltdown compounds and invalid-position skip. Native persistence writes an empty
    //list after the final meltdown expires, so a previously saved meltdown cannot revive on the next world load.
    public static final Codec<MeltdownLevelData> CODEC = CompoundTag.CODEC.listOf().xmap(tags -> {
        MeltdownLevelData data = new MeltdownLevelData();
        for (CompoundTag tag : tags) {
            Meltdown meltdown = Meltdown.load(tag);
            if (meltdown != null) {
                data.meltdowns.add(meltdown);
            }
        }
        return data;
    }, data -> data.meltdowns.stream().map(Meltdown::write).toList());

    private final List<Meltdown> meltdowns = new ArrayList<>();

    public void createMeltdown(BlockPos minPos, BlockPos maxPos, double magnitude, double chance, float radius, UUID multiblockID) {
        meltdowns.add(new Meltdown(minPos, maxPos, magnitude, chance, radius, multiblockID));
    }

    public static void tickWorld(ServerLevel world) {
        MeltdownLevelData existingData = FabricRadiationAttachments.getMeltdownData(world);
        if (existingData != null) {
            existingData.tick(world);
        }
    }

    public void tick(ServerLevel world) {
        if (meltdowns.isEmpty()) {
            return;
        }
        //noinspection Java8CollectionRemoveIf - We can't replace it with removeIf as it has a capturing lambda
        for (Iterator<Meltdown> iterator = meltdowns.iterator(); iterator.hasNext(); ) {
            Meltdown meltdown = iterator.next();
            if (meltdown.update(world)) {
                iterator.remove();
            }
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, ListTag meltdownList) {
        for (int i = 0; i < meltdownList.size(); i++) {
            Meltdown meltdown = Meltdown.load(meltdownList.getCompound(i));
            if (meltdown != null) {
                this.meltdowns.add(meltdown);
            }
        }
    }

    @Override
    @Nullable
    public ListTag serializeNBT(HolderLookup.Provider provider) {
        if (meltdowns.isEmpty()) {
            return null;
        }

        ListTag list = new ListTag();
        for (Meltdown meltdown : meltdowns) {
            list.add(meltdown.write());
        }
        return list;
    }

}
