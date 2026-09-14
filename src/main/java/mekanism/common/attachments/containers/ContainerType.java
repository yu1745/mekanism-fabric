package mekanism.common.attachments.containers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.google.common.base.Suppliers;
import mekanism.api.INBTSerializable;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.fabric.item.FabricComponentItemLookup;
import java.util.function.Predicate;
import java.util.function.Supplier;
import mekanism.api.DataHandlerUtils;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.chemical.AttachedChemicals;
import mekanism.common.attachments.containers.chemical.ComponentBackedChemicalHandler;
import mekanism.common.attachments.containers.creator.IContainerCreator;
import mekanism.common.attachments.containers.energy.AttachedEnergy;
import mekanism.common.attachments.containers.energy.ComponentBackedEnergyHandler;
import mekanism.common.attachments.containers.fluid.AttachedFluids;
import mekanism.common.attachments.containers.fluid.ComponentBackedFluidHandler;
import mekanism.common.attachments.containers.heat.AttachedHeat;
import mekanism.common.attachments.containers.heat.ComponentBackedHeatHandler;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.attachments.containers.item.ComponentBackedItemHandler;
import mekanism.common.config.IMekanismConfig;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class ContainerType<CONTAINER extends INBTSerializable<CompoundTag>, ATTACHED extends IAttachedContainers<?, ATTACHED>,
      HANDLER extends ComponentBackedHandler<?, CONTAINER, ATTACHED>> {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Mekanism");
    private static final List<ContainerType<?, ?, ?>> TYPES_INTERNAL = new ArrayList<>();
    public static final List<ContainerType<?, ?, ?>> TYPES = Collections.unmodifiableList(TYPES_INTERNAL);

    public static final ContainerType<IEnergyContainer, AttachedEnergy, ComponentBackedEnergyHandler> ENERGY = new ContainerType<>(MekanismDataComponents.ATTACHED_ENERGY,
          SerializationConstants.ENERGY_CONTAINERS, SerializationConstants.CONTAINER, ComponentBackedEnergyHandler::new, FabricComponentItemLookup::registerEnergy, AttachedEnergy.EMPTY,
          TileEntityMekanism::getEnergyContainers, TileEntityMekanism::collectEnergyContainers, TileEntityMekanism::applyEnergyContainers, TileEntityMekanism::canHandleEnergy);
    public static final ContainerType<IInventorySlot, AttachedItems, ComponentBackedItemHandler> ITEM = new ContainerType<>(MekanismDataComponents.ATTACHED_ITEMS,
          SerializationConstants.ITEMS, SerializationConstants.SLOT, ComponentBackedItemHandler::new, FabricComponentItemLookup::registerItems, AttachedItems.EMPTY,
          TileEntityMekanism::getInventorySlots, TileEntityMekanism::collectInventorySlots, TileEntityMekanism::applyInventorySlots, TileEntityMekanism::hasInventory);
    public static final ContainerType<IExtendedFluidTank, AttachedFluids, ComponentBackedFluidHandler> FLUID = new ContainerType<>(MekanismDataComponents.ATTACHED_FLUIDS,
          SerializationConstants.FLUID_TANKS, SerializationConstants.TANK, ComponentBackedFluidHandler::new, FabricComponentItemLookup::registerFluid, AttachedFluids.EMPTY,
          TileEntityMekanism::getFluidTanks, TileEntityMekanism::collectFluidTanks, TileEntityMekanism::applyFluidTanks, TileEntityMekanism::canHandleFluid);

    public static final ContainerType<IChemicalTank, AttachedChemicals, ComponentBackedChemicalHandler> CHEMICAL = new ContainerType<>(MekanismDataComponents.ATTACHED_CHEMICALS,
          SerializationConstants.CHEMICAL_TANKS, SerializationConstants.TANK, ComponentBackedChemicalHandler::new, FabricComponentItemLookup::registerChemical, AttachedChemicals.EMPTY,
          TileEntityMekanism::getChemicalTanks, TileEntityMekanism::collectChemicalTanks, TileEntityMekanism::applyChemicalTanks, TileEntityMekanism::canHandleChemicals) {
        @SuppressWarnings("removal")
        @Override//TODO - 1.22: remove backcompat, incl getLegacyX methods
        public void readFrom(HolderLookup.Provider provider, CompoundTag tag, TileEntityMekanism tile) {
            if (tag.contains(getTag(), Tag.TAG_LIST)) {
                //has already saved with new format
                super.readFrom(provider, tag, getContainers(tile));
            } else {
                if (tag.contains(SerializationConstants.GAS_TANKS)) {
                    read(provider, tile.getLegacyGasTanks(), tag.getList(SerializationConstants.GAS_TANKS, Tag.TAG_COMPOUND));
                }
                if (tag.contains(SerializationConstants.INFUSION_TANKS)) {
                    read(provider, tile.getLegacyInfuseTanks(), tag.getList(SerializationConstants.INFUSION_TANKS, Tag.TAG_COMPOUND));
                }
                if (tag.contains(SerializationConstants.PIGMENT_TANKS)) {
                    read(provider, tile.getLegacyPigmentTanks(), tag.getList(SerializationConstants.PIGMENT_TANKS, Tag.TAG_COMPOUND));
                }
                if (tag.contains(SerializationConstants.SLURRY_TANKS)) {
                    read(provider, tile.getLegacySlurryTanks(), tag.getList(SerializationConstants.SLURRY_TANKS, Tag.TAG_COMPOUND));
                }
            }
        }

        @SuppressWarnings("removal")
        @Override//TODO - 1.22: remove backcompat. this one only for NON TileEntityMekanism
        public void readFrom(HolderLookup.Provider provider, CompoundTag tag, List<IChemicalTank> containers) {
            if (tag.contains(getTag(), Tag.TAG_LIST)) {
                //has already saved with new format
                super.readFrom(provider, tag, containers);
            } else {
                //this should be safe, as only one of them should have data per type
                read(provider, containers, tag.getList(SerializationConstants.GAS_TANKS, Tag.TAG_COMPOUND));
                read(provider, containers, tag.getList(SerializationConstants.INFUSION_TANKS, Tag.TAG_COMPOUND));
                read(provider, containers, tag.getList(SerializationConstants.PIGMENT_TANKS, Tag.TAG_COMPOUND));
                read(provider, containers, tag.getList(SerializationConstants.SLURRY_TANKS, Tag.TAG_COMPOUND));
            }
        }
    };

    public static final ContainerType<IHeatCapacitor, AttachedHeat, ComponentBackedHeatHandler> HEAT = new ContainerType<>(MekanismDataComponents.ATTACHED_HEAT,
          SerializationConstants.HEAT_CAPACITORS, SerializationConstants.CONTAINER, ComponentBackedHeatHandler::new, null, AttachedHeat.EMPTY,
          TileEntityMekanism::getHeatCapacitors, TileEntityMekanism::collectHeatCapacitors, TileEntityMekanism::applyHeatCapacitors, TileEntityMekanism::canHandleHeat);

    //TODO - 1.20.5: Re-evaluate this codec implementation
    public static final Codec<ContainerType<?, ?, ?>> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().comapFlatMap(componentType -> {
        for (ContainerType<?, ?, ?> type : TYPES) {
            if (type.component.get() == componentType) {
                return DataResult.success(type);
            }
        }
        return DataResult.error(() -> "Data Component type " + BuiltInRegistries.DATA_COMPONENT_TYPE.wrapAsHolder(componentType).getRegisteredName() +
                                      " does not have a corresponding container type");
    }, containerType -> containerType.component.get());

    private final Map<Item, Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>>> knownDefaultCreators = new Reference2ObjectOpenHashMap<>();
    //Same guarded provider used for Fabric exposure and Mekanism's internal item-owned operations (gear spending, HUD).
    private final Map<Item, Function<ItemStack, HANDLER>> registeredProviders = new Reference2ObjectOpenHashMap<>();
    private final HandlerConstructor<HANDLER> handlerConstructor;
    private final BiFunction<TileEntityMekanism, @Nullable Direction, List<CONTAINER>> containersFromTile;
    private final CopyFromTile<CONTAINER, ATTACHED> copyFromTile;
    private final CopyToTile<CONTAINER, ATTACHED> copyToTile;
    private final MekanismDeferredHolder<DataComponentType<?>, DataComponentType<ATTACHED>> component;
    @Nullable
    private final BiConsumer<Item, Function<ItemStack, HANDLER>> capability;
    private final Predicate<TileEntityMekanism> canHandle;
    private final ATTACHED emptyAttachment;
    private final String containerTag;
    protected final String containerKey;

    private ContainerType(MekanismDeferredHolder<DataComponentType<?>, DataComponentType<ATTACHED>> component, String containerTag, String containerKey,
          HandlerConstructor<HANDLER> handlerConstructor, @Nullable BiConsumer<Item, Function<ItemStack, HANDLER>> capability, ATTACHED emptyAttachment,
          BiFunction<TileEntityMekanism, @Nullable Direction, List<CONTAINER>> containersFromTile, CopyFromTile<CONTAINER, ATTACHED> copyFromTile,
          CopyToTile<CONTAINER, ATTACHED> copyToTile, Predicate<TileEntityMekanism> canHandle) {
        TYPES_INTERNAL.add(this);
        this.component = component;
        this.containerTag = containerTag;
        this.containerKey = containerKey;
        this.emptyAttachment = emptyAttachment;
        this.handlerConstructor = handlerConstructor;
        this.containersFromTile = containersFromTile;
        this.copyFromTile = copyFromTile;
        this.copyToTile = copyToTile;
        this.capability = capability;
        this.canHandle = canHandle;
    }

    public MekanismDeferredHolder<DataComponentType<?>, DataComponentType<ATTACHED>> getComponentType() {
        return component;
    }

    public ResourceLocation getComponentName() {
        return component.getId();
    }

    public String getTag() {
        return containerTag;
    }

    /**
     * Adds default containers, optionally registering their Fabric item exposure guarded by the given live configs.
     */
    public void addDefaultCreators(boolean exposeCapability, Item item, Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>> defaultCreator,
          IMekanismConfig... requiredConfigs) {
        knownDefaultCreators.put(item, Suppliers.memoize(defaultCreator::get));
        if (exposeCapability) registerItemCapabilities(item, false, requiredConfigs);
    }

    public void registerItemCapabilities(Item item, boolean exposeWhenStacked, IMekanismConfig... requiredConfigs) {
        if (capability != null) {
            Function<ItemStack, HANDLER> provider = getCapabilityProvider(exposeWhenStacked, requiredConfigs);
            if (registeredProviders.containsKey(item)) {
                throw new IllegalStateException("Duplicate " + containerTag + " item provider: " + item);
            }
            capability.accept(item, provider);
            registeredProviders.put(item, provider);
        }
    }

    /** Internal domain access for the owner of this stack, retaining the actual registration/count/live-config guard.
     * External transfers must use Fabric's transactional item context, never mutate this live handler.
     */
    @Nullable
    public HANDLER getRegisteredHandler(ItemStack stack) {
        Function<ItemStack, HANDLER> provider = registeredProviders.get(stack.getItem());
        return stack.isEmpty() || provider == null ? null : provider.apply(stack);
    }

    //TODO - 1.21: Do we want to have create in the name instead of get
    public List<CONTAINER> getAttachmentContainersIfPresent(ItemStack stack) {
        HANDLER handler = createHandlerIfData(stack);
        return handler == null ? Collections.emptyList() : handler.getContainers();
    }

    public int getContainerCount(ItemStack stack) {
        ATTACHED attached = getOrEmpty(stack);
        if (attached.isEmpty()) {
            Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>> containerCreator = knownDefaultCreators.get(stack.getItem());
            return containerCreator == null ? 0 : containerCreator.get().totalContainers();
        }
        //TODO - 1.21: Do we need to look it up in case the max size changed since we were last saved?
        return attached.size();
    }

    @Nullable//TODO - 1.21: Re-evaluate
    public HANDLER createHandlerIfData(ItemStack stack) {
        ATTACHED attached = getOrEmpty(stack);
        //TODO - 1.21: Do we need to look it up in case the max size changed since we were last saved?
        return attached.isEmpty() ? null : handlerConstructor.create(stack, attached.size());
    }

    @Nullable
    public HANDLER createHandler(ItemStack stack) {
        //TODO - 1.21: Do we want local callers to just directly access the handler constructor as we wouldn't be exposing the cap
        // if we didn't have any creators?
        int count = getContainerCount(stack);
        if (count == 0) {
            return null;
        }
        return handlerConstructor.create(stack, count);
    }

    public ATTACHED createNewAttachment(ItemStack stack) {
        //TODO - 1.1: Do we want local callers to just directly access the handler constructor as we wouldn't be exposing the cap
        // if we didn't have any creators?
        Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>> lazy = knownDefaultCreators.get(stack.getItem());
        if (lazy == null) {
            return emptyAttachment;
        }
        IContainerCreator<? extends CONTAINER, ATTACHED> containerCreator = lazy.get();
        int count = containerCreator.totalContainers();
        if (count == 0) {
            return emptyAttachment;
        }
        return containerCreator.initStorage(count);
    }

    public ATTACHED getOrEmpty(ItemStack stack) {
        return stack.getOrDefault(component.get(), emptyAttachment);
    }

    //TODO - 1.21: Re-evaluate usages and see if they should be going via capability instead?
    public CONTAINER createContainer(ItemStack attachedTo, int containerIndex) {
        Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>> creator = knownDefaultCreators.get(attachedTo.getItem());
        if (creator != null) {
            return creator.get().create(this, attachedTo, containerIndex);
        }
        throw new IllegalArgumentException("No known containers for item " + attachedTo.getItem());
    }

    protected Function<ItemStack, HANDLER> getCapabilityProvider(boolean exposeWhenStacked, IMekanismConfig... requiredConfigs) {
        if (exposeWhenStacked) return getCapabilityProvider(requiredConfigs);
        return stack -> stack.getCount() == 1 && hasRequiredConfigs(requiredConfigs) ? createHandler(stack) : null;
    }

    protected Function<ItemStack, HANDLER> getCapabilityProvider(IMekanismConfig... requiredConfigs) {
        // Applied on every lookup and every item-context query/transfer, not captured as a startup boolean.
        return stack -> hasRequiredConfigs(requiredConfigs) ? createHandler(stack) : null;
    }

    private static boolean hasRequiredConfigs(IMekanismConfig... requiredConfigs) {
        for (IMekanismConfig requiredConfig : requiredConfigs) {
            if (!requiredConfig.isLoaded()) {
                return false;
            }
        }
        return true;
    }

    public boolean supports(ItemStack stack) {
        return stack.has(component.get()) || knownDefaultCreators.containsKey(stack.getItem());
    }

    public void addDefault(Holder<Item> item, DataComponentPatch.Builder builder) {
        addDefault(item, builder::set);
    }

    public void addDefault(Holder<Item> item, DataComponentMap.Builder builder) {
        addDefault(item, builder::set);
    }

    private void addDefault(Holder<Item> item, BiConsumer<DataComponentType<ATTACHED>, ATTACHED> setter) {
        Supplier<? extends IContainerCreator<? extends CONTAINER, ATTACHED>> lazy = knownDefaultCreators.get(item.value());
        if (lazy != null) {
            //Supports the type
            IContainerCreator<? extends CONTAINER, ATTACHED> containerCreator = lazy.get();
            int count = containerCreator.totalContainers();
            if (count > 0) {
                setter.accept(component.get(), containerCreator.initStorage(count));
            }
        }
    }

    public static boolean anySupports(Holder<Item> item) {
        for (ContainerType<?, ?, ?> type : TYPES) {
            if (type.knownDefaultCreators.containsKey(item.value())) {
                return true;
            }
        }
        return false;
    }

    private ListTag save(HolderLookup.Provider provider, List<CONTAINER> containers) {
        return DataHandlerUtils.writeContents(provider, containers, containerKey);
    }

    protected void read(HolderLookup.Provider provider, List<CONTAINER> containers, @Nullable ListTag storedContainers) {
        if (storedContainers != null) {
            DataHandlerUtils.readContents(provider, containers, storedContainers, containerKey);
        }
    }

    public void saveTo(HolderLookup.Provider provider, CompoundTag tag, TileEntityMekanism tile) {
        saveTo(provider, tag, getContainers(tile));
    }

    public void saveTo(HolderLookup.Provider provider, CompoundTag tag, List<CONTAINER> containers) {
        ListTag serialized = save(provider, containers);
        if (!serialized.isEmpty()) {
            tag.put(containerTag, serialized);
        }
    }

    public void readFrom(HolderLookup.Provider provider, CompoundTag tag, TileEntityMekanism tile) {
        readFrom(provider, tag, getContainers(tile));
    }

    public void readFrom(HolderLookup.Provider provider, CompoundTag tag, List<CONTAINER> containers) {
        read(provider, containers, tag.getList(containerTag, Tag.TAG_COMPOUND));
    }

    public void copyToStack(HolderLookup.Provider provider, List<CONTAINER> containers, ItemStack stack) {
        HANDLER handler = createHandler(stack);
        if (handler != null) {
            read(provider, handler.getContainers(), save(provider, containers));
            //TODO - 1.21: FIX the getattached here?
            stack.set(component.get(), handler.getAttached());
            if (stack.getCount() > 1) {
                LOGGER.error("Copied {} to a stack ({}). This might lead to duplication of data.", getComponentName(), stack);
            }
        }
    }

    public void copyToTile(TileEntityMekanism tile, BlockEntity.DataComponentInput input) {
        ATTACHED attachedData = input.get(component.get());
        if (attachedData != null) {
            copyToTile.copy(tile, input, getContainers(tile), attachedData);
        }
    }

    public void copyFromStack(HolderLookup.Provider provider, ItemStack stack, List<CONTAINER> containers) {
        HANDLER handler = createHandler(stack);
        if (handler != null) {
            read(provider, containers, save(provider, handler.getContainers()));
        }
    }

    public void copyFromTile(TileEntityMekanism tile, DataComponentMap.Builder builder) {
        List<CONTAINER> containers = getContainers(tile);
        if (!containers.isEmpty()) {
            ATTACHED attachedData = copyFromTile.copy(tile, builder, containers);
            if (attachedData != null) {
                builder.set(component.get(), attachedData);
            }
        }
    }

    public boolean canHandle(TileEntityMekanism tile) {
        return canHandle.test(tile);
    }

    public List<CONTAINER> getContainers(TileEntityMekanism tile) {
        return containersFromTile.apply(tile, null);
    }

    @FunctionalInterface
    private interface HandlerConstructor<HANDLER extends ComponentBackedHandler<?, ?, ?>> {

        HANDLER create(ItemStack attachedTo, int totalContainers);
    }

    @FunctionalInterface
    public interface CopyToTile<CONTAINER extends INBTSerializable<CompoundTag>, ATTACHED extends IAttachedContainers<?, ATTACHED>> {

        void copy(TileEntityMekanism tile, BlockEntity.DataComponentInput input, List<CONTAINER> containers, ATTACHED attachedData);
    }

    @FunctionalInterface
    public interface CopyFromTile<CONTAINER extends INBTSerializable<CompoundTag>, ATTACHED extends IAttachedContainers<?, ATTACHED>> {

        @Nullable
        ATTACHED copy(TileEntityMekanism tile, DataComponentMap.Builder builder, List<CONTAINER> containers);
    }
}