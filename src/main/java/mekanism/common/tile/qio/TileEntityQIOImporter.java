package mekanism.common.tile.qio;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import mekanism.api.Action;
import mekanism.api.SerializationConstants;
import mekanism.api.functions.ConstantPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.integration.computer.ComputerException;
import mekanism.common.integration.computer.annotation.ComputerMethod;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityQIOImporter extends TileEntityQIOFilterHandler {

    //Fabric port delta: the excluded entrypoint's logger is replaced by the same SLF4J category the rest of the port logs under
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");

    private static final int MAX_DELAY = MekanismUtils.TICKS_PER_HALF_SECOND;

    private final Predicate<ItemStack> FILTER_ENABLED = stack -> getFilterManager().anyEnabledMatch(stack, (filter, s) -> filter.getFinder().test(s));

    private BlockApiCache<Storage<ItemVariant>, Direction> backInventory;
    private int delay = 0;
    private boolean importWithoutFilter = true;

    public TileEntityQIOImporter(BlockPos pos, BlockState state) {
        super(MekanismBlocks.QIO_IMPORTER, pos, state);
    }

    @Override
    protected boolean onUpdateServer(@Nullable QIOFrequency frequency) {
        boolean needsUpdate = super.onUpdateServer(frequency);
        if (frequency != null && canFunction()) {
            if (delay > 0) {
                delay--;
            } else {
                tryImport(frequency);
                delay = MAX_DELAY;
            }
        }
        return needsUpdate;
    }

    @Override
    protected void invalidateDirectionCaches(Direction newDirection) {
        super.invalidateDirectionCaches(newDirection);
        backInventory = null;
    }

    private void tryImport(QIOFrequency freq) {
        if (backInventory == null) {
            Direction direction = getDirection();
            //Fabric port delta: the neighbor is queried through the Fabric item lookup; the per-find cache replaces the NeoForge capability cache
            backInventory = BlockApiCache.create(ItemStorage.SIDED, (ServerLevel) level, worldPosition.relative(direction.getOpposite()));
        }
        Storage<ItemVariant> inventory = backInventory.find(getDirection());
        if (inventory == null) {//Not an item storage
            return;
        }

        Predicate<ItemStack> canFilter;
        if (getFilterManager().hasEnabledFilters()) {
            canFilter = FILTER_ENABLED;
        } else if (importWithoutFilter) {
            // return true if we don't have any enabled filters installed, and we allow for filterless importing
            canFilter = ConstantPredicates.alwaysTrue();
        } else {
            //If we don't have any enabled filters installed, and we don't allow filterless importing
            return;
        }
        Set<HashedItem> typesAdded = new HashSet<>();
        int maxTypes = getMaxTransitTypes(), maxCount = getMaxTransitCount(), countAdded = 0;

        //Indexed storages preserve the upstream reverse-slot priority; a general storage has no slot indices and
        //therefore retains its provider-defined view order.
        for (StorageView<ItemVariant> view : importViews(inventory)) {
            if (view.isResourceBlank()) {
                continue;
            }
            ItemVariant variant = view.getResource();
            ItemStack stack;
            try (Transaction simulation = Transaction.openOuter()) {
                long simulated = view.extract(variant, maxCount - countAdded, simulation);
                stack = simulated <= 0 ? ItemStack.EMPTY : variant.toStack((int) simulated);
            }
            if (stack.isEmpty()) {
                continue;
            }
            HashedItem type = HashedItem.create(stack);
            // if we don't have room for another item type, skip
            if (!typesAdded.contains(type) && typesAdded.size() == maxTypes) {
                continue;
            }
            // if we can't filter this item type, skip
            if (!canFilter.test(stack)) {
                continue;
            }
            long capacity = freq.massInsert(stack, stack.getCount(), Action.SIMULATE);
            if (capacity <= 0) {
                continue;
            }
            long extracted;
            try (Transaction transfer = Transaction.openOuter()) {
                extracted = view.extract(variant, capacity, transfer);
                if (extracted < 0 || extracted > capacity) {
                    LOGGER.error("QIO insertion error: item storage at {} in {} extracted {} items after a request for {}",
                          worldPosition.relative(getOppositeDirection()), level.dimension().location(), extracted, capacity);
                    continue;
                }
                if (extracted == 0) {
                    continue;
                }
                long inserted = freq.insertItem(stack, extracted, transfer);
                if (inserted != extracted) {
                    LOGGER.error("QIO insertion error: frequency accepted {} of {} extracted items at {} in {}; rolling back",
                          inserted, extracted, worldPosition.relative(getOppositeDirection()), level.dimension().location());
                    continue;
                }
                transfer.commit();
            }
            typesAdded.add(type);
            countAdded += (int) extracted;
        }
    }

    static Iterable<? extends StorageView<ItemVariant>> importViews(Storage<ItemVariant> inventory) {
        if (inventory instanceof SlottedStorage<ItemVariant> slotted) {
            return slotted.getSlots().reversed();
        }
        return inventory;
    }

    @ComputerMethod
    public boolean getImportWithoutFilter() {
        return importWithoutFilter;
    }

    public void toggleImportWithoutFilter() {
        importWithoutFilter = !importWithoutFilter;
        markForSave();
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(this::getImportWithoutFilter, value -> importWithoutFilter = value));
    }

    @Override
    public void writeSustainedData(HolderLookup.Provider provider, CompoundTag dataMap) {
        super.writeSustainedData(provider, dataMap);
        dataMap.putBoolean(SerializationConstants.AUTO, importWithoutFilter);
    }

    @Override
    public void readSustainedData(HolderLookup.Provider provider, @NotNull CompoundTag dataMap) {
        super.readSustainedData(provider, dataMap);
        NBTUtils.setBooleanIfPresent(dataMap, SerializationConstants.AUTO, value -> importWithoutFilter = value);
    }

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(MekanismDataComponents.AUTO.get(), importWithoutFilter);
    }

    @Override
    protected void applyImplicitComponents(@NotNull BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        importWithoutFilter = input.getOrDefault(MekanismDataComponents.AUTO.get(), importWithoutFilter);
    }

    //Methods relating to IComputerTile
    @ComputerMethod(requiresPublicSecurity = true)
    void setImportsWithoutFilter(boolean value) throws ComputerException {
        validateSecurityIsPublic();
        if (importWithoutFilter != value) {
            toggleImportWithoutFilter();
        }
    }
    //End methods IComputerTile
}
