package mekanism.common.content.qio;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import mekanism.api.Action;
import mekanism.api.SerializationConstants;
import mekanism.api.inventory.IHashedItem;
import mekanism.api.inventory.qio.IQIOFrequency;
import mekanism.api.math.MathUtils;
import mekanism.api.security.SecurityMode;
import mekanism.api.text.EnumColor;
import mekanism.common.recipe.lookup.cache.RecipeCacheContext;
import mekanism.common.base.TagCache;
import mekanism.common.content.qio.QIODriveData.QIODriveKey;
import mekanism.common.inventory.container.QIOItemViewerContainer;
import mekanism.common.lib.WildcardMatcher;
import mekanism.common.lib.collection.BiMultimap;
import mekanism.common.lib.frequency.Frequency;
import mekanism.common.lib.frequency.FrequencyType;
import mekanism.common.lib.frequency.IColorableFrequency;
import mekanism.common.lib.inventory.HashedItem;
import mekanism.common.lib.inventory.HashedItem.UUIDAwareHashedItem;
import mekanism.common.lib.security.SecurityFrequency;
import mekanism.common.network.to_client.qio.PacketQIOItemViewerKill;
import mekanism.common.network.to_client.qio.PacketUpdateItemViewer;
import mekanism.common.util.MekanismUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.SharedConstants;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class QIOFrequency extends Frequency implements IColorableFrequency, IQIOFrequency {

    private static final RandomSource rand = RandomSource.create();
    public static final Codec<QIOFrequency> CODEC = RecordCodecBuilder.create(instance -> baseCodec(instance)
          .and(EnumColor.CODEC.fieldOf(SerializationConstants.COLOR).forGetter(QIOFrequency::getColor))
          .apply(instance, (name, owner, securityMode, color) -> {
              QIOFrequency frequency = new QIOFrequency(name, owner.orElse(null), securityMode);
              frequency.color = color;
              return frequency;
          }));
    public static final StreamCodec<ByteBuf, QIOFrequency> STREAM_CODEC = StreamCodec.composite(
          baseStreamCodec(QIOFrequency::new), Function.identity(),
          ByteBufCodecs.VAR_LONG, QIOFrequency::getTotalItemCount,
          ByteBufCodecs.VAR_LONG, QIOFrequency::getTotalItemCountCapacity,
          ByteBufCodecs.VAR_INT, freq -> freq.getTotalItemTypes(false),
          ByteBufCodecs.VAR_INT, QIOFrequency::getTotalItemTypeCapacity,
          EnumColor.STREAM_CODEC, QIOFrequency::getColor,
          (frequency, totalCount, totalCountCapacity, totalTypes, totalTypeCapacity, color) -> {
              frequency.totalCount = totalCount;
              frequency.totalCountCapacity = totalCountCapacity;
              frequency.clientTypes = totalTypes;
              frequency.totalTypeCapacity = totalTypeCapacity;
              frequency.color = color;
              return frequency;
          }
    );

    private final SequencedMap<QIODriveKey, QIODriveData> driveMap = new LinkedHashMap<>();
    private final SequencedMap<HashedItem, QIOItemTypeData> itemDataMap = new LinkedHashMap<>();
    // New types in an open insertion transaction get their globally persistent UUID only after the outer commit.
    private final Set<QIOItemTypeData> pendingInsertionUUIDs = new HashSet<>();
    private boolean insertionTransactionActive;

    /**
     * Stable frequency-level participant (Task052 R4 correction) for transaction-aware removal: snapshots the
     * ordered {@code itemDataMap} membership and the tag/mod/fuzzy lookup memberships that a removal can touch.
     * Nested propagation is the real {@link SnapshotParticipant} promotion, so an enclosing abort after a committed
     * nested removal restores the exact ordered identity/membership and shared totals/publication state.
     * Failed-wildcard sets are included because creating a type during transactional insertion clears them.
     */
    private final FrequencyRemoveParticipant frequencyRemoveTx = new FrequencyRemoveParticipant();

    private final class FrequencyRemoveParticipant extends SnapshotParticipant<FrequencyRemoveSnapshot> {

        @Override
        protected FrequencyRemoveSnapshot createSnapshot() {
            Map<HashedItem, QIOItemTypeData> items = new LinkedHashMap<>(itemDataMap);
            Map<String, Set<HashedItem>> tags = new LinkedHashMap<>();
            for (String tag : tagLookupMap.getAllKeys()) {
                tags.put(tag, new HashSet<>(tagLookupMap.getValues(tag)));
            }
            Map<String, Set<HashedItem>> mods = new LinkedHashMap<>();
            for (Map.Entry<String, Set<HashedItem>> entry : modIDLookupMap.entrySet()) {
                mods.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            Map<Item, Set<HashedItem>> fuzzy = new IdentityHashMap<>();
            for (Map.Entry<Item, Set<HashedItem>> entry : fuzzyItemLookupMap.entrySet()) {
                fuzzy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return new FrequencyRemoveSnapshot(items, tags, mods, fuzzy, totalCount, isDirty, needsUpdate,
                  new HashSet<>(updatedItems), HashMultimap.create(tagWildcardCache), HashMultimap.create(modIDWildcardCache),
                  new HashSet<>(failedWildcardTags), new HashSet<>(failedWildcardModIDs), new HashSet<>(pendingInsertionUUIDs), insertionTransactionActive);
        }

        @Override
        protected void readSnapshot(FrequencyRemoveSnapshot snapshot) {
            itemDataMap.clear();
            itemDataMap.putAll(snapshot.items);
            tagLookupMap.clear();
            snapshot.tags.forEach((tag, items) -> items.forEach(item -> tagLookupMap.put(tag, item)));
            modIDLookupMap.clear();
            snapshot.mods.forEach((mod, items) -> modIDLookupMap.put(mod, new HashSet<>(items)));
            fuzzyItemLookupMap.clear();
            snapshot.fuzzy.forEach((item, items) -> fuzzyItemLookupMap.put(item, new HashSet<>(items)));
            totalCount = snapshot.totalCount;
            isDirty = snapshot.isDirty;
            needsUpdate = snapshot.needsUpdate;
            updatedItems.clear();
            updatedItems.addAll(snapshot.updatedItems);
            tagWildcardCache.clear();
            tagWildcardCache.putAll(snapshot.tagWildcards);
            modIDWildcardCache.clear();
            modIDWildcardCache.putAll(snapshot.modWildcards);
            failedWildcardTags.clear();
            failedWildcardTags.addAll(snapshot.failedTags);
            failedWildcardModIDs.clear();
            failedWildcardModIDs.addAll(snapshot.failedMods);
            pendingInsertionUUIDs.clear();
            pendingInsertionUUIDs.addAll(snapshot.pendingUUIDs);
            insertionTransactionActive = snapshot.insertionTransactionActive;
        }

        @Override
        protected void onFinalCommit() {
            insertionTransactionActive = false;
            for (QIOItemTypeData data : pendingInsertionUUIDs) {
                if (itemDataMap.get(data.itemType) == data) {
                    data.itemUUID = QIOGlobalItemLookup.INSTANCE.getOrTrackUUID(data.itemType);
                    setNeedsUpdate(data.itemType);
                }
            }
            pendingInsertionUUIDs.clear();
        }
    }

    private record FrequencyRemoveSnapshot(Map<HashedItem, QIOItemTypeData> items, Map<String, Set<HashedItem>> tags,
          Map<String, Set<HashedItem>> mods, Map<Item, Set<HashedItem>> fuzzy, long totalCount, boolean isDirty,
          boolean needsUpdate, Set<UUID> updatedItems, SetMultimap<String, String> tagWildcards,
          SetMultimap<String, String> modWildcards, Set<String> failedTags, Set<String> failedMods,
          Set<QIOItemTypeData> pendingUUIDs, boolean insertionTransactionActive) {
    }

    private final Set<IQIODriveHolder> driveHolders = new HashSet<>();
    // efficiently keep track of the tags utilized by the items stored
    private final BiMultimap<String, HashedItem> tagLookupMap = new BiMultimap<>();
    // efficiently keep track of the modids utilized by the items stored
    private final Map<String, Set<HashedItem>> modIDLookupMap = new HashMap<>();
    // efficiently keep track of the items for use in fuzzy lookup utilized by the items stored
    private final Map<Item, Set<HashedItem>> fuzzyItemLookupMap = new IdentityHashMap<>();
    // a sensitive cache for wildcard tag lookups (wildcard -> [matching tags])
    private final SetMultimap<String, String> tagWildcardCache = HashMultimap.create();
    private final Set<String> failedWildcardTags = new HashSet<>();
    // a sensitive cache for wildcard modid lookups (wildcard -> [matching modids])
    private final SetMultimap<String, String> modIDWildcardCache = HashMultimap.create();
    private final Set<String> failedWildcardModIDs = new HashSet<>();

    private final Set<UUID> updatedItems = new HashSet<>();
    private final Set<ServerPlayer> playersViewingItems = new HashSet<>();

    /** If we need to send a packet to viewing clients with changed item data. */
    private boolean needsUpdate;
    /** If we have new item changes that haven't been saved. */
    private boolean isDirty;

    private long totalCount, totalCountCapacity;
    private int totalTypeCapacity;
    // only used on client side, for server side we can just look at itemDataMap.size()
    private int clientTypes;

    private EnumColor color = EnumColor.INDIGO;

    /**
     * @param uuid Should only be null if we have incomplete data that we are loading
     */
    public QIOFrequency(String n, @Nullable UUID uuid, SecurityMode securityMode) {
        super(FrequencyType.QIO, n, uuid, securityMode);
    }

    private QIOFrequency(String name, @Nullable UUID owner, String ownerName, SecurityMode securityMode) {
        super(FrequencyType.QIO, name, owner, ownerName, securityMode);
    }

    /**
     * Dangerous function. Don't mess with this map.
     *
     * @return core item data map, tracking item types + their respective counts and containing drives
     */
    public Map<HashedItem, QIOItemTypeData> getItemDataMap() {
        return itemDataMap;
    }

    @Override
    public void forAllStored(ObjLongConsumer<ItemStack> consumer) {
        for (Entry<HashedItem, QIOItemTypeData> entry : itemDataMap.entrySet()) {
            consumer.accept(entry.getKey().createStack(1), entry.getValue().getCount());
        }
    }

    @Override
    public void forAllHashedStored(ObjLongConsumer<IHashedItem> consumer) {
        for (Entry<HashedItem, QIOItemTypeData> entry : itemDataMap.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getCount());
        }
    }

    @Override
    public long massInsert(ItemStack stack, long amount, Action action) {
        if (action.execute()) {
            requireNoInsertionTransaction();
        }
        if (stack.isEmpty() || amount <= 0) {
            return 0;
        }
        HashedItem type = action.execute() ? HashedItem.create(stack) : HashedItem.raw(stack);
        // these checks are extremely important; they prevent us from wasting CPU searching for a place to put the new items,
        // and they also prevent us from adding a ghost type to the itemDataMap if nothing is inserted
        if (totalCount == totalCountCapacity || (!itemDataMap.containsKey(type) && itemDataMap.size() == totalTypeCapacity)) {
            return 0;
        }
        // at this point we're guaranteed at least part of the input stack will be inserted
        QIOItemTypeData data;
        if (action.execute()) {
            data = itemDataMap.computeIfAbsent(type, this::createTypeDataForAbsent);
        } else {
            //If we are simulating, look it up
            data = itemDataMap.get(type);
            if (data == null) {
                // if it doesn't already have that type, fall back to a new item type data that doesn't actually get added
                data = new QIOItemTypeData(type);
            }
        }
        return amount - data.add(amount, action);
    }

    /** Inserts into the real QIO drives under the caller's transfer transaction. */
    public long insertItem(ItemStack stack, long amount, TransactionContext transaction) {
        java.util.Objects.requireNonNull(transaction, "transaction");
        long accepted = massInsert(stack, amount, Action.SIMULATE);
        if (accepted <= 0) {
            return 0;
        }
        HashedItem type = HashedItem.create(stack);
        frequencyRemoveTx.updateSnapshots(transaction);
        insertionTransactionActive = true;
        QIOItemTypeData data = itemDataMap.get(type);
        if (data == null) {
            data = createTypeDataForAbsent(type, true);
            itemDataMap.put(type, data);
        }
        return accepted - data.add(accepted, Action.EXECUTE, transaction);
    }

    void requireNoInsertionTransaction() {
        if (insertionTransactionActive) {
            throw new IllegalStateException("Cannot mutate QIO frequency outside its active insertion transaction");
        }
    }

    public ItemStack addItem(ItemStack stack) {
        requireNoInsertionTransaction();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        } else if (totalCount == totalCountCapacity) {
            //This check and the pre-check in the computeIfAbsent are extremely important; they prevent us from wasting CPU searching for
            // a place to put the new items, and they also prevent us from adding a ghost type to the itemDataMap if nothing is inserted
            return stack;
        }
        HashedItem type = HashedItem.create(stack);
        QIOItemTypeData data = itemDataMap.get(type);
        if (data == null) {
            if (itemDataMap.size() == totalTypeCapacity) {
                //Don't add any ghost item types if there is no room for new ones. We do this inside of a computeIfAbsent
                // so that we don't have to check if the map contains it twice
                //Failed to insert
                return stack;
            } else {
                // at this point we're guaranteed at least part of the input stack will be inserted
                data = createTypeDataForAbsent(type);
                itemDataMap.put(type, data);
            }
        }
        return type.createStack(MathUtils.clampToInt(data.add(stack.getCount(), Action.EXECUTE)));
    }

    private QIOItemTypeData createTypeDataForAbsent(HashedItem type) {
        return createTypeDataForAbsent(type, false);
    }

    private QIOItemTypeData createTypeDataForAbsent(HashedItem type, boolean deferUUID) {
        ItemStack stack = type.getInternalStack();
        List<String> tags = TagCache.getItemTags(stack);
        if (!tags.isEmpty()) {
            boolean hasAllKeys = tagLookupMap.hasAllKeys(tags);
            if (tagLookupMap.putAll(tags, type) && !hasAllKeys) {
                //If we added any tag item combinations, and we didn't have all the keys for tags this item has,
                // then we need to clear our wildcard cache as our new tags may be valid for some of our wildcards
                tagWildcardCache.clear();
                failedWildcardTags.clear();
            }
        }
        String modID = MekanismUtils.getModId(stack);
        Set<HashedItem> modItems = modIDLookupMap.get(modID);
        if (modItems == null) {
            //If we added a new modid to the lookup map we also want to make sure that we clear our modid wildcard cache
            // as our new modid may be valid for some of our wildcards
            modIDWildcardCache.clear();
            failedWildcardModIDs.clear();
            modItems = new HashSet<>();
            modIDLookupMap.put(modID, modItems);
        }
        modItems.add(type);
        //Fuzzy item lookup has no wildcard cache related to it
        fuzzyItemLookupMap.computeIfAbsent(stack.getItem(), item -> new HashSet<>()).add(type);
        QIOItemTypeData data = new QIOItemTypeData(type);
        // Global UUID allocation persists across worlds; an aborted insertion must not publish a new one.
        if (deferUUID) {
            data.itemUUID = QIOGlobalItemLookup.INSTANCE.getUUIDForType(type);
            if (data.itemUUID == null) {
                pendingInsertionUUIDs.add(data);
            }
        } else {
            data.getItemUUID();
        }
        return data;
    }

    @Override
    public long massExtract(ItemStack stack, long amount, Action action) {
        if (action.execute()) {
            requireNoInsertionTransaction();
        }
        if (amount <= 0 || stack.isEmpty() || itemDataMap.isEmpty()) {
            return 0;
        }
        HashedItem type = HashedItem.raw(stack);
        QIOItemTypeData data = itemDataMap.get(type);
        if (data == null) {
            return 0;
        }
        long removed = data.remove(amount, action);
        // remove this item type if it's now empty
        if (action.execute() && data.count == 0) {
            removeItemData(data.itemType);
        }
        return removed;
    }

    public ItemStack removeItem(int amount) {
        return removeByType(null, amount);
    }

    public ItemStack removeItem(ItemStack stack, int amount) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return removeByType(HashedItem.raw(stack), amount);
    }

    public ItemStack removeByType(@Nullable HashedItem itemType, int amount) {
        return removeByType(itemType, amount, (TransactionContext) null);
    }

    /**
     * Transaction-aware removal (Task052 continuation09). Snapshots are taken defensively before any mutation and
     * all touched state is restored on abort (own or later root abort): the original {@link QIOItemTypeData} identity
     * and ordered itemDataMap membership, type count/containingDrives, per-drive itemMap/count, totalCount, the
     * tag/mod/fuzzy index memberships and the physical metadata on the captured actual drive stacks. The holder
     * {@code dataUpdate} notifications are deferred to successful outer commit by the per-drive journals. Wildcard
     * caches are restored to their prior state on rollback; failed-wildcard sets are never touched
     * by removal. There is no remove-then-add compensation: rollback restores the exact prior state.
     */
    public ItemStack removeByType(@Nullable HashedItem itemType, int amount, @Nullable TransactionContext tx) {
        if (tx == null) {
            requireNoInsertionTransaction();
        }
        if (itemDataMap.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        QIOItemTypeData data;
        if (itemType == null) {
            Map.Entry<HashedItem, QIOItemTypeData> entry = itemDataMap.firstEntry();
            itemType = entry.getKey();
            data = entry.getValue();
        } else {
            data = itemDataMap.get(itemType);
            if (data == null) {
                return ItemStack.EMPTY;
            }
        }

        if (tx == null) {
            //Legacy immediate path for unrelated callers
            ItemStack removed = data.remove(amount);
            // remove this item type if it's now empty
            if (data.count == 0) {
                removeItemData(data.itemType);
            }
            return removed;
        }

        final HashedItem type = itemType;
        //Join the frequency-level state this removal can touch to the caller transaction BEFORE any mutation
        //(stable owner participant; R4): ordered itemDataMap membership and the tag/mod/fuzzy index memberships
        frequencyRemoveTx.updateSnapshots(tx);
        long removedAmount = data.remove(amount, Action.EXECUTE, tx);
        // remove this item type if it's now empty
        if (data.count == 0) {
            removeItemData(data.itemType);
        }
        //Join the frequency-level state this removal can touch to the caller transaction BEFORE any of the
        // mutations above reached it (the participant was joined at the top of the tx branch)
        return removedAmount == 0 ? ItemStack.EMPTY : type.createStack(MathUtils.clampToInt(removedAmount));
    }

    private void removeItemData(HashedItem type) {
        itemDataMap.remove(type);
        //Note: We need to copy the tags to a new collection as otherwise when we start removing them from the lookup
        // they will also get removed from this view
        Set<String> tags = new HashSet<>(tagLookupMap.getKeys(type));
        if (tagLookupMap.removeValue(type) && !tagLookupMap.hasAllKeys(tags)) {
            //If we completely removed any tags clear our wildcard cache as it may have some wildcards that are
            // matching a tag that is no longer stored
            tagWildcardCache.clear();
            //Note: We don't need to clear the failed wildcard tags as if we are removing tags they still won't have any matches
        }
        ItemStack stack = type.getInternalStack();
        String modID = MekanismUtils.getModId(stack);
        Set<HashedItem> itemsForMod = modIDLookupMap.get(modID);
        //In theory if we are removing an item, and it existed we should have a set corresponding to it,
        // but double check that it is not null just in case
        // Next if we removed the item successfully, check if the items for that mod is now empty, and if they are
        // remove the modid from the lookup map, and clear our wildcard cache as it may have some wildcards that are
        // matching a modid that is no longer stored
        if (itemsForMod != null && itemsForMod.remove(type) && itemsForMod.isEmpty()) {
            modIDLookupMap.remove(modID);
            modIDWildcardCache.clear();
            //Note: We don't need to clear the failed wildcard modids as if we are removing tags they still won't have any matches
        }
        Item item = stack.getItem();
        Set<HashedItem> itemsByFuzzy = fuzzyItemLookupMap.get(item);
        //In theory if we are removing an item, and it existed we should have a set corresponding to it,
        // but double check that it is not null just in case
        // Next if we removed the item successfully, check if the "fuzzy" items for that item is now empty, and if they are
        // remove the item completely from the lookup map
        if (itemsByFuzzy != null && itemsByFuzzy.remove(type) && itemsByFuzzy.isEmpty()) {
            fuzzyItemLookupMap.remove(item);
        }
    }

    public Set<HashedItem> getTypesForItem(Item item) {
        return fuzzyItemLookupMap.getOrDefault(item, Collections.emptySet());
    }

    public Object2LongMap<HashedItem> getStacksByItem(Item item) {
        return getStacksWithCounts(fuzzyItemLookupMap.get(item));
    }

    public Object2LongMap<HashedItem> getStacksByTag(String tag) {
        return getStacksWithCounts(tagLookupMap.getValues(tag));
    }

    public Object2LongMap<HashedItem> getStacksByModID(String modID) {
        return getStacksWithCounts(modIDLookupMap.get(modID));
    }

    private Object2LongMap<HashedItem> getStacksWithCounts(@Nullable Set<HashedItem> items) {
        if (items == null || items.isEmpty()) {
            return Object2LongMaps.emptyMap();
        }
        Object2LongMap<HashedItem> ret = new Object2LongOpenHashMap<>();
        for (HashedItem item : items) {
            ret.put(item, getStoredByHash(item));
        }
        return ret;
    }

    public Object2LongMap<HashedItem> getStacksByTagWildcard(String wildcard) {
        if (hasMatchingElements(tagWildcardCache, failedWildcardTags, wildcard, tagLookupMap::getAllKeys)) {
            Object2LongMap<HashedItem> ret = new Object2LongOpenHashMap<>();
            ToLongFunction<HashedItem> storedFunction = this::getStoredByHash;
            for (String match : tagWildcardCache.get(wildcard)) {
                for (HashedItem item : tagLookupMap.getValues(match)) {
                    //If our return map doesn't already have the stored value in it, calculate it.
                    // The case where it may have the stored value in it is if an item has multiple
                    // tags that all match the wildcard
                    ret.computeIfAbsent(item, storedFunction);
                }
            }
            return ret;
        }
        return Object2LongMaps.emptyMap();
    }

    public Object2LongMap<HashedItem> getStacksByModIDWildcard(String wildcard) {
        if (hasMatchingElements(modIDWildcardCache, failedWildcardModIDs, wildcard, modIDLookupMap::keySet)) {
            Object2LongMap<HashedItem> ret = new Object2LongOpenHashMap<>();
            for (String match : modIDWildcardCache.get(wildcard)) {
                for (HashedItem item : modIDLookupMap.get(match)) {
                    //Note: Unlike in getStacksByTagWildcard, we don't use computeLongIfAbsent here because
                    // each stack only has one modid, so while we may have multiple modids that match our
                    // wildcard, the stacks that correspond to said modids will be unique
                    ret.put(item, getStoredByHash(item));
                }
            }
            return ret;
        }
        return Object2LongMaps.emptyMap();
    }

    private boolean hasMatchingElements(SetMultimap<String, String> wildcardCache, Set<String> failedWildcards, String wildcard, Supplier<Set<String>> entriesSupplier) {
        if (failedWildcards.contains(wildcard)) {
            //If we already know this wildcard has no matching things, fail fast
            return false;
        }
        //If we don't have a cached value for the given wildcard, try to build up the corresponding cache
        if (!wildcardCache.containsKey(wildcard) && !buildWildcardMapping(wildcardCache, wildcard, entriesSupplier.get())) {
            // If we don't actually have any matches, mark that the wildcard failed, and return false
            failedWildcards.add(wildcard);
            return false;
        }
        return true;
    }

    /**
     * @return {@code true} if any wildcards were added.
     */
    private boolean buildWildcardMapping(SetMultimap<String, String> wildcardCache, String wildcard, Set<String> entries) {
        boolean added = false;
        for (String entry : entries) {
            if (WildcardMatcher.matches(wildcard, entry)) {
                added |= wildcardCache.put(wildcard, entry);
            }
        }
        return added;
    }

    public void openItemViewer(ServerPlayer player) {
        playersViewingItems.add(player);
    }

    public void closeItemViewer(ServerPlayer player) {
        playersViewingItems.remove(player);
    }

    @Override
    public EnumColor getColor() {
        return color;
    }

    @Override
    public void setColor(EnumColor color) {
        if (this.color != color) {
            this.color = color;
            this.dirty = true;
        }
    }

    // utility methods for accessing descriptors
    public long getTotalItemCount() {
        return totalCount;
    }

    public long getTotalItemCountCapacity() {
        return totalCountCapacity;
    }

    public int getTotalItemTypes(boolean remote) {
        return remote ? clientTypes : itemDataMap.size();
    }

    public int getTotalItemTypeCapacity() {
        return totalTypeCapacity;
    }

    @Override
    public long getStored(ItemStack type) {
        return type.isEmpty() ? 0 : getStoredByHash(HashedItem.raw(type));
    }

    public long getStoredByHash(HashedItem itemType) {
        QIOItemTypeData data = itemDataMap.get(itemType);
        return data == null ? 0 : data.count;
    }

    public boolean isStoring(HashedItem itemType) {
        return getStoredByHash(itemType) > 0;
    }

    public QIODriveData getDriveData(QIODriveKey key) {
        return driveMap.get(key);
    }

    /**
     * This is mainly for use by things that need to do simulation, and should not have any of the values of the drive get changed directly.
     */
    public Collection<QIODriveData> getAllDrives() {
        return driveMap.values();
    }

    @Override
    public boolean tick(boolean tickingNormally) {
        requireNoInsertionTransaction();
        boolean superDirty = super.tick(tickingNormally);

        if (getSecurity() == SecurityMode.TRUSTED && !playersViewingItems.isEmpty()) {
            //TODO - 1.20.4: Only perform every so often?
            SecurityFrequency security = FrequencyType.SECURITY.getManager(null, SecurityMode.PUBLIC).getFrequency(getOwner());
            if (security != null) {
                for (ServerPlayer player : new HashSet<>(playersViewingItems)) {
                    if (!ownerMatches(player.getUUID()) && !security.isTrusted(player.getUUID()) && player.containerMenu instanceof QIOItemViewerContainer) {
                        player.closeContainer();
                        closeItemViewer(player);
                    }
                }
            }
        }

        if (!updatedItems.isEmpty() || needsUpdate) {
            //Only calculate the packet and the update map if there are actually players viewing this frequency,
            // otherwise we can just skip looking up UUIDs and counts
            //Fabric port delta: NeoForge's Lazy.of memoization is replaced by a memoizing supplier with identical
            // once-per-needed-update semantics (the packet is not reconstructed per recipient)
            java.util.function.Supplier<PacketUpdateItemViewer> lazyPacket = memoize(() -> {
                Object2LongMap<UUIDAwareHashedItem> map = new Object2LongOpenHashMap<>(updatedItems.size());
                for (UUID uuid : updatedItems) {
                    HashedItem type = QIOGlobalItemLookup.INSTANCE.getTypeByUUID(uuid);
                    if (type != null) {//The type should never be null as we create a UUID if there isn't one before adding but validate it
                        QIOItemTypeData data = itemDataMap.get(type);
                        map.put(new UUIDAwareHashedItem(type, uuid), data == null ? 0 : data.count);
                    }
                }
                return new PacketUpdateItemViewer(totalCountCapacity, totalTypeCapacity, map);
            });
            for (Iterator<ServerPlayer> viewingIterator = playersViewingItems.iterator(); viewingIterator.hasNext(); ) {
                ServerPlayer player = viewingIterator.next();
                if (player.containerMenu instanceof QIOItemViewerContainer) {
                    //Fabric port delta: PacketDistributor.sendToPlayer becomes the Fabric play S2C send
                    ServerPlayNetworking.send(player, lazyPacket.get());
                } else {
                    //flush players that somehow didn't send a container close packet
                    viewingIterator.remove();
                }
            }
            updatedItems.clear();
            needsUpdate = false;
        }
        // if something has changed, we'll subsequently randomly run a save operation in the next 100 ticks.
        // the random factor helps us avoid bogging down the CPU by saving all QIO frequencies at once
        // this isn't a fully necessary operation, but it'll help avoid all item data getting lost if the server
        // is forcibly shut down.
        if (isDirty && rand.nextInt(5 * SharedConstants.TICKS_PER_SECOND) == 0) {
            //Note: We don't have this affect our super dirty value as this is for if the drives are dirty,
            // not for if the frequency is dirty
            saveAll();
            isDirty = false;
        }

        if (RecipeCacheContext.isInvalidationPending()) {
            //Note: We only need to clear tags here as the modids cannot change just because a reload happened
            tagLookupMap.clear();
            tagWildcardCache.clear();
            for (QIOItemTypeData item : itemDataMap.values()) {
                tagLookupMap.putAll(TagCache.getItemTags(item.itemType.getInternalStack()), item.itemType);
            }
        }
        return superDirty;
    }

    @Override
    public boolean onDeactivate(BlockEntity tile) {
        if (tile instanceof IQIODriveHolder) {
            requireNoInsertionTransaction();
        }
        boolean changedData = super.onDeactivate(tile);
        if (tile instanceof IQIODriveHolder holder) {
            for (int i = 0, size = holder.getDriveSlots().size(); i < size; i++) {
                QIODriveKey key = new QIODriveKey(holder, i);
                removeDrive(key, true);
            }
            //Uncache the holder when it stops being part of the frequency
            driveHolders.remove(holder);
        }
        return changedData;
    }

    @Override
    public boolean update(BlockEntity tile) {
        if (tile instanceof IQIODriveHolder) {
            requireNoInsertionTransaction();
        }
        boolean changedData = super.update(tile);
        if (tile instanceof IQIODriveHolder holder && driveHolders.add(holder)) {
            for (int i = 0, slots = holder.getDriveSlots().size(); i < slots; i++) {
                addDrive(new QIODriveKey(holder, i));
            }
        }
        return changedData;
    }

    @Override
    public void onRemove() {
        requireNoInsertionTransaction();
        super.onRemove();
        // copy keys to avoid CME
        Set<QIODriveKey> keys = new HashSet<>(driveMap.keySet());
        for (QIODriveKey key : keys) {
            removeDrive(key, false);
        }
        driveMap.clear();
        for (ServerPlayer player : playersViewingItems) {
            //Fabric port delta: Mekanism.packetHandler().killItemViewer becomes a direct send of the extracted
            // no-data kill payload through the Fabric play S2C channel
            ServerPlayNetworking.send(player, PacketQIOItemViewerKill.INSTANCE);
        }
    }

    @Override
    public int getSyncHash() {
        int code = super.getSyncHash();
        code = 31 * code + Long.hashCode(totalCount);
        code = 31 * code + Long.hashCode(totalCountCapacity);
        code = 31 * code + itemDataMap.size();
        code = 31 * code + totalTypeCapacity;
        code = 31 * code + color.ordinal();
        return code;
    }

    public void addDrive(QIODriveKey key) {
        requireNoInsertionTransaction();
        if (key.getDriveStack().getItem() instanceof IQIODriveItem) {
            // if a drive in this position is already in the system, we remove it before adding this one
            if (driveMap.containsKey(key)) {
                removeDrive(key, true);
            }
            // add drive and capacity info to core tracking
            QIODriveData data = new QIODriveData(key);
            totalCountCapacity += data.getCountCapacity();
            totalTypeCapacity += data.getTypeCapacity();
            driveMap.put(key, data);
            for (ObjectIterator<Object2LongMap.Entry<HashedItem>> iterator = Object2LongMaps.fastIterator(data.getItemMap()); iterator.hasNext(); ) {
                Object2LongMap.Entry<HashedItem> entry = iterator.next();
                HashedItem storedKey = entry.getKey();
                itemDataMap.computeIfAbsent(storedKey, this::createTypeDataForAbsent).addFromDrive(data, entry.getLongValue());
                markForUpdate(storedKey);
            }
            setNeedsUpdate();
        }
    }

    public void removeDrive(QIODriveKey key, boolean updateItemMap) {
        requireNoInsertionTransaction();
        if (!driveMap.containsKey(key)) {
            return;
        }
        QIODriveData data = driveMap.get(key);
        if (updateItemMap) {
            for (ObjectIterator<Object2LongMap.Entry<HashedItem>> iterator = Object2LongMaps.fastIterator(data.getItemMap()); iterator.hasNext(); ) {
                Object2LongMap.Entry<HashedItem> entry = iterator.next();
                HashedItem storedKey = entry.getKey();
                long value = entry.getLongValue();
                QIOItemTypeData itemData = itemDataMap.get(storedKey);
                if (itemData != null) {
                    itemData.containingDrives.remove(key);
                    itemData.count -= value;
                    totalCount -= value;
                    markForUpdate(storedKey);
                    // remove this entry from the item data map if it's now empty
                    if (itemData.containingDrives.isEmpty() || itemData.count == 0) {
                        removeItemData(storedKey);
                    }
                }
            }
            setNeedsUpdate();
        }
        // remove drive and capacity info from core tracking
        totalCountCapacity -= data.getCountCapacity();
        totalTypeCapacity -= data.getTypeCapacity();
        driveMap.remove(key);
        // save the item list onto the physical drive
        key.updateMetadata(data);
        key.save(data);
    }

    public void saveAll() {
        for (Entry<QIODriveKey, QIODriveData> entry : driveMap.entrySet()) {
            QIODriveKey key = entry.getKey();
            QIODriveData value = entry.getValue();
            key.updateMetadata(value);
            key.save(value);
        }
    }

    private void setNeedsUpdate(@Nullable HashedItem changedItem) {
        isDirty = true;
        if (!playersViewingItems.isEmpty()) {//Skip marking for update if there are no players viewing the items
            needsUpdate = true;
            if (changedItem != null) {
                UUID uuid = QIOGlobalItemLookup.INSTANCE.getUUIDForType(changedItem);
                if (uuid != null) {
                    updatedItems.add(uuid);
                }
            }
        }
    }

    private void markForUpdate(HashedItem changedItem) {
        if (!playersViewingItems.isEmpty()) {//Skip marking for update if there are no players viewing the items
            UUID uuid = QIOGlobalItemLookup.INSTANCE.getUUIDForType(changedItem);
            if (uuid != null) {
                updatedItems.add(uuid);
            }
        }
    }

    private void setNeedsUpdate() {
        setNeedsUpdate(null);
    }

    private static <T> java.util.function.Supplier<T> memoize(java.util.function.Supplier<T> delegate) {
        return new java.util.function.Supplier<>() {
            private T value;
            private boolean resolved;

            @Override
            public synchronized T get() {
                if (!resolved) {
                    value = delegate.get();
                    resolved = true;
                }
                return value;
            }
        };
    }

    public class QIOItemTypeData {

        private final Set<QIODriveKey> containingDrives = new HashSet<>();
        private final HashedItem itemType;

        @Nullable
        private UUID itemUUID;
        private long count = 0;

        public QIOItemTypeData(HashedItem itemType) {
            this.itemType = itemType;
        }

        private void addFromDrive(QIODriveData data, long toAdd) {
            count += toAdd;
            totalCount += toAdd;
            containingDrives.add(data.getKey());
            setNeedsUpdate();
        }

        private long add(long amount, Action action) {
            return add(amount, action, null);
        }

        private long add(long amount, Action action, @Nullable TransactionContext tx) {
            if (action.execute() && tx != null) {
                frequencyRemoveTx.updateSnapshots(tx);
                typeRemoveTx.updateSnapshots(tx);
            }
            long toAdd = amount;
            // first we try to add the items to an already-containing drive
            for (QIODriveKey key : containingDrives) {
                toAdd = addItemsToDrive(toAdd, driveMap.get(key), action, tx);
                if (toAdd == 0) {
                    break;
                }
            }
            // next, we add the items to any drive that will take it
            if (toAdd > 0) {
                for (QIODriveData data : driveMap.values()) {
                    if (!containingDrives.contains(data.getKey())) {
                        toAdd = addItemsToDrive(toAdd, data, action, tx);
                        if (toAdd == 0) {
                            break;
                        }
                    }
                }
            }
            if (action.execute()) {
                // update internal/core values
                count += amount - toAdd;
                totalCount += amount - toAdd;
                setNeedsUpdate(itemType);
            }
            return toAdd;
        }

        private long addItemsToDrive(long toAdd, QIODriveData data, Action action, @Nullable TransactionContext tx) {
            long rejects = data.add(itemType, toAdd, action, tx);
            if (action.execute() && rejects < toAdd) {
                containingDrives.add(data.getKey());
            }
            return rejects;
        }

        private long remove(long amount, Action action) {
            return remove(amount, action, null);
        }

        /**
         * Transaction-aware removal (Task052 continuation09): journals count, totalCount, containingDrives and the
         * per-drive removals (which carry their own metadata/dataUpdate journals). Exact per-drive iteration order is
         * unchanged; the needsUpdate marking is never erased on rollback (it only marks an update, and an aborted
         * removal must not wipe earlier changes).
         */
        private long remove(long amount, Action action, @Nullable TransactionContext tx) {
            long removed = 0;
            if (tx != null) {
                frequencyRemoveTx.updateSnapshots(tx);
                typeRemoveTx.updateSnapshots(tx);
            }
            for (Iterator<QIODriveKey> iter = containingDrives.iterator(); iter.hasNext(); ) {
                QIODriveKey key = iter.next();
                QIODriveData data = driveMap.get(key);
                if (tx != null && (data == null || !data.getKey().equals(key))) {
                    throw new IllegalStateException("QIO type membership no longer matches its drive owner");
                }
                removed += data.remove(itemType, amount - removed, action, tx);
                // remove this drive from containingDrives if it doesn't have this item anymore
                if (action.execute() && data.getStored(itemType) == 0) {
                    iter.remove();
                }
                // break early if we found enough items
                if (removed == amount) {
                    break;
                }
            }
            if (action.execute()) {
                count -= removed;
                totalCount -= removed;
                setNeedsUpdate(itemType);
            }
            return removed;
        }

        /**
         * Stable per-type participant for transaction-aware removal: snapshots this type's count and
         * containingDrives only. Shared state belongs exclusively to frequencyRemoveTx; rollback does not
         * depend on the ordering of distinct type participants.
         */
        private final TypeRemoveParticipant typeRemoveTx = new TypeRemoveParticipant();

        private final class TypeRemoveParticipant extends SnapshotParticipant<TypeRemoveSnapshot> {

            @Override
            protected TypeRemoveSnapshot createSnapshot() {
                return new TypeRemoveSnapshot(count, new HashSet<>(containingDrives), itemUUID);
            }

            @Override
            protected void readSnapshot(TypeRemoveSnapshot snapshot) {
                count = snapshot.count;
                containingDrives.clear();
                containingDrives.addAll(snapshot.containingDrives);
                itemUUID = snapshot.itemUUID;
            }
        }

        private record TypeRemoveSnapshot(long count, Set<QIODriveKey> containingDrives, @Nullable UUID itemUUID) {
        }

        private ItemStack remove(int amount) {
            int removed = MathUtils.clampToInt(remove(amount, Action.EXECUTE));
            return removed == 0 ? ItemStack.EMPTY : itemType.createStack(removed);
        }

        public long getCount() {
            return count;
        }

        public UUID getItemUUID() {
            if (itemUUID == null) {
                if (pendingInsertionUUIDs.contains(this)) {
                    throw new IllegalStateException("Cannot publish a provisional QIO item UUID before the transaction commits");
                }
                //Lazily cache what the uuid for the stack is
                itemUUID = QIOGlobalItemLookup.INSTANCE.getOrTrackUUID(itemType);
            }
            return itemUUID;
        }

        public HashedItem getItemType() {
            return itemType;
        }
    }
}
