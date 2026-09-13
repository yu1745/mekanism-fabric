package mekanism.api.chemical;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mekanism.api.MekanismAPITags;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.attribute.ChemicalAttribute;
import mekanism.api.chemical.attribute.ChemicalAttributes.Radiation;
import mekanism.api.chemical.attribute.IChemicalAttributeContainer;
import mekanism.api.datamaps.IMekanismDataMapTypes;
import mekanism.api.datamaps.chemical.ChemicalSolidTag;
import mekanism.api.datamaps.chemical.attribute.ChemicalRadioactivity;
import mekanism.api.datamaps.chemical.attribute.IChemicalAttribute;
import mekanism.api.providers.IChemicalProvider;
import mekanism.api.radiation.RadiationValidation;
import mekanism.api.text.TextComponentUtil;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import mekanism.api.datamaps.MekanismDataMapType;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault//TODO - 1.22: Investigate making chemicals a datapack registry
public class Chemical implements IChemicalProvider, IChemicalAttributeContainer<Chemical> {

    private static final Logger LOGGER = LoggerFactory.getLogger("mekanism.api.MekanismAPI");

    /**
     * A codec which can (de)encode chemicals.
     *
     * @since 10.6.0
     * @deprecated Use {@link #HOLDER_CODEC} instead
     */
    @Deprecated(forRemoval = true, since = "!0.7.11")
    public static final Codec<Chemical> CODEC = ChemicalRegistry.registry().byNameCodec();
    /**
     * A codec which can (de)encode chemical holders.
     *
     * @since 10.7.11
     */
    public static final Codec<Holder<Chemical>> HOLDER_CODEC = ChemicalRegistry.registry().holderByNameCodec();//TODO - 1.22: Rename this to just CODEC
    /**
     * A stream codec which can be used to encode and decode chemicals over the network.
     *
     * @since 10.6.0
     * @deprecated Use {@link #HOLDER_STREAM_CODEC} instead
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public static final StreamCodec<RegistryFriendlyByteBuf, Chemical> STREAM_CODEC = ByteBufCodecs.registry(ChemicalRegistry.REGISTRY_KEY);
    /**
     * A stream codec which can be used to encode and decode chemical holders over the network.
     *
     * @since 10.7.9
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Chemical>> HOLDER_STREAM_CODEC = ByteBufCodecs.holderRegistry(ChemicalRegistry.REGISTRY_KEY);

    /**
     * Tries to parse a chemical.
     *
     * @since 10.7.0
     * @deprecated Use {@link #parseHolder(Provider, Tag)} instead
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public static Optional<Chemical> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(lookupProvider.createSerializationContext(NbtOps.INSTANCE), tag)
              .resultOrPartial(error -> LOGGER.error("Tried to load invalid chemical: '{}'", error));
    }


    /**
     * Tries to parse a chemical, defaulting to the empty chemical on parsing failure.
     *
     * @since 10.7.0
     * @deprecated Use {@link #parseOptionalHolder(Provider, String)} instead
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public static Chemical parseOptional(HolderLookup.Provider lookupProvider, String tag) {
        return parseOptionalHolder(lookupProvider, tag).value();
    }

    /**
     * Tries to parse a chemical holder.
     *
     * @since 10.7.11
     */
    public static Optional<Holder<Chemical>> parseHolder(HolderLookup.Provider lookupProvider, Tag tag) {
        return HOLDER_CODEC.parse(lookupProvider.createSerializationContext(NbtOps.INSTANCE), tag)
              .resultOrPartial(error -> LOGGER.error("Tried to load invalid chemical: '{}'", error));
    }

    /**
     * Tries to parse a chemical, defaulting to {@link MekanismAPI#EMPTY_CHEMICAL_HOLDER} on parsing failure.
     *
     * @since 10.7.11
     */
    public static Holder<Chemical> parseOptionalHolder(HolderLookup.Provider lookupProvider, String tag) {
        if (tag.isEmpty()) {
            return ChemicalRegistry.emptyHolder();
        }
        Optional<RegistryLookup<Chemical>> chemicalLookup = lookupProvider.lookup(ChemicalRegistry.REGISTRY_KEY);
        if (chemicalLookup.isPresent()) {
            ResourceLocation rl = ResourceLocation.tryParse(tag);
            if (rl != null) {
                Optional<Reference<Chemical>> chemicalReference = chemicalLookup.get().get(ResourceKey.create(ChemicalRegistry.REGISTRY_KEY, rl));
                if (chemicalReference.isPresent()) {
                    return chemicalReference.get();
                }
            }
        }
        return ChemicalRegistry.emptyHolder();
    }

    private final Holder.Reference<Chemical> builtInRegistryHolder = ChemicalRegistry.registry().createIntrusiveHolder(this);
    //TODO - 1.22: Figure out if we should we keep this cache or remove it?
    private final List<IChemicalAttribute> attributes = new ArrayList<>();
    private final List<IChemicalAttribute> attributesView = Collections.unmodifiableList(attributes);
    private final ResourceLocation iconLocation;
    private final int tint;
    private double radioactivity;
    private boolean hasAttributesWithValidation;

    @Nullable
    private String translationKey;

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    private Map<Class<? extends ChemicalAttribute>, ChemicalAttribute> legacyAttributeMap;
    @Nullable
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    private Map<Class<? extends ChemicalAttribute>, ChemicalAttribute> attributeMap;
    @Deprecated(forRemoval = true, since = "10.7.11")
    private double legacyRadioactivity;
    @Deprecated(forRemoval = true, since = "10.7.11")
    private boolean hasLegacyAttributesWithValidation;
    @Nullable
    @Deprecated(forRemoval = true, since = "10.7.11")
    private TagKey<Item> legacyOreTag;
    @Nullable
    @Deprecated(forRemoval = true, since = "10.7.11")
    private TagKey<Item> oreTag;
    @Deprecated(forRemoval = true, since = "10.7.11")
    private boolean isGaseous;

    public Chemical(ChemicalBuilder builder) {
        this.iconLocation = builder.getTexture();
        this.tint = builder.getTint();
        initLegacy(builder);
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    private void initLegacy(ChemicalBuilder builder) {
        //Copy the map to support addAttribute
        this.legacyAttributeMap = new HashMap<>(builder.getAttributeMap());
        for (ChemicalAttribute legacyAttribute : legacyAttributeMap.values()) {
            if (legacyAttribute instanceof Radiation radiation) {
                this.legacyRadioactivity = radiation.getRadioactivity();
                this.radioactivity = this.legacyRadioactivity;
            } else if (legacyAttribute.needsValidation()) {
                //Skip radioactive attributes when checking if we have any that need validation, so that we properly return false
                // when the radiation manager is disabled
                this.hasLegacyAttributesWithValidation = true;
                this.hasAttributesWithValidation = true;
            }
        }
        this.legacyOreTag = builder.getOreTag();
        this.oreTag = this.legacyOreTag;
        this.isGaseous = builder.isGaseous();
    }

    @Override
    public final String toString() {
        //Note: Similar to vanilla we look up the holder and registered name from teh registry
        return ChemicalRegistry.registry().wrapAsHolder(this).getRegisteredName();
    }

    @NotNull
    @Override
    public final Chemical getChemical() {
        return this;
    }

    @Override
    public String getTranslationKey() {
        if (translationKey == null) {
            translationKey = Util.makeDescriptionId("chemical", ChemicalRegistry.registry().getResourceKey(this).map(ResourceKey::location).orElse(null));
        }
        return translationKey;
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    private Map<Class<? extends ChemicalAttribute>, ChemicalAttribute> getAllAttributes() {
        List<IChemicalAttribute> modern = derived().attributes();
        if (modern.isEmpty()) return legacyAttributeMap;
        Map<Class<? extends ChemicalAttribute>, ChemicalAttribute> merged = new HashMap<>(legacyAttributeMap);
        for (IChemicalAttribute attribute : modern) {
            ChemicalAttribute legacyAttribute = attribute.toLegacyAttribute();
            merged.put(legacyAttribute.getClass(), legacyAttribute);
        }
        return merged;
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public boolean has(Class<? extends ChemicalAttribute> type) {
        return getAllAttributes().containsKey(type);
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public boolean hasLegacy(Class<? extends ChemicalAttribute> type) {
        return legacyAttributeMap.containsKey(type);
    }

    /**
     * Helper to check if this chemical is radioactive without having to look it up from the attributes.
     *
     * @return {@code true} if this chemical is radioactive.
     *
     * @since 10.5.15
     */
    public boolean isRadioactive() {
        return getRadioactivity() > 0;
    }

    /**
     * {@return radiation level of this chemical, or zero if it is not radioactive}
     *
     * @since 10.7.11
     */
    public double getRadioactivity() {//TODO - 1.22: Do we want this to return the baseline instead of zero if it is missing?
        return derived().radioactivity();
    }

    /**
     * Helper to check if this chemical has any attributes that need validation.
     *
     * @return {@code true} if this chemical doesn't fit for {@link mekanism.api.chemical.attribute.ChemicalAttributeValidator#DEFAULT}.
     *
     * @since 10.5.15
     */
    public boolean hasAttributesWithValidation() {
        //Note: We only treat radiation as needing validation if the radiation manager is enabled
        Derived derived = derived();
        return derived.needsValidation() || derived.radioactivity() > 0 && RadiationValidation.isEnabled();
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "removal"})
    @Deprecated(forRemoval = true, since = "10.7.11")
    public <ATTRIBUTE extends ChemicalAttribute> ATTRIBUTE get(Class<ATTRIBUTE> type) {
        return (ATTRIBUTE) getAllAttributes().get(type);
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "removal"})
    @Deprecated(forRemoval = true, since = "10.7.11")
    public <ATTRIBUTE extends ChemicalAttribute> ATTRIBUTE getLegacy(Class<ATTRIBUTE> type) {
        return (ATTRIBUTE) legacyAttributeMap.get(type);
    }

    /**
     * Adds an attribute to this chemical's attribute map. Will overwrite any existing attribute with the same type.
     *
     * @param attribute attribute to add to this chemical
     *
     * @deprecated Prefer adding attributes via datamaps.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public void addAttribute(ChemicalAttribute attribute) {
        legacyAttributeMap.put(attribute.getClass(), attribute);
        //Clear the merged cache if it has already been initialized, and just reinitialize it when needed
        attributeMap = null;
        if (attribute instanceof Radiation radiation) {
            legacyRadioactivity = radiation.getRadioactivity();
            radioactivity = legacyRadioactivity;
            //Note: We don't mark radiation as needing validation here, as we handle it separately, so that if the radiation manager is disabled
            // we return false for if we have any attributes that need validation
        } else if (attribute.needsValidation()) {
            this.hasLegacyAttributesWithValidation = true;
            this.hasAttributesWithValidation = true;
        }
    }

    /**
     * Gets an unmodifiable view of all attribute instances associated with this chemical type.
     *
     * @return collection of attribute instances.
     *
     * @implNote This method only returns the modern attributes and not any legacy ones that were defined in code.
     * @since 10.7.11
     */
    public List<IChemicalAttribute> getModernAttributes() {
        //TODO - 1.22: Rename this to getAttributes and maybe move it into IChemicalAttributeContainer? (Also Update/remove implNote)
        return derived().attributes();
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Collection<ChemicalAttribute> getAttributes() {
        return getAllAttributes().values();
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Collection<ChemicalAttribute> getLegacyAttributes() {
        return getAttributes();
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Collection<Class<? extends ChemicalAttribute>> getAttributeTypes() {
        return getAllAttributes().keySet();
    }

    @Override
    public Component getTextComponent() {
        return TextComponentUtil.translate(getTranslationKey());
    }

    /**
     * Gets the resource location of the icon associated with this Chemical.
     *
     * @return The resource location of the icon
     */
    public ResourceLocation getIcon() {
        return iconLocation;
    }

    /**
     * Get the tint for rendering the chemical
     *
     * @return int representation of color in RRGGBB format
     */
    public int getTint() {
        return tint;
    }

    /**
     * Get the color representation used for displaying in things like durability bars of chemical tanks.
     *
     * @return int representation of color in RRGGBB format
     */
    public int getColorRepresentation() {
        return getTint();
    }

    /**
     * Checks if this chemical is in a given tag.
     *
     * @param tag The tag to check.
     *
     * @return {@code true} if the chemical is in the tag, {@code false} otherwise.
     *
     * @deprecated Use {@link ChemicalStack#is(TagKey)} instead.
     */
    @Deprecated(forRemoval = true, since = "10.7.9")
    public boolean is(TagKey<Chemical> tag) {
        return getAsHolder().is(tag);
    }

    /**
     * Gets the tags that this chemical is a part of.
     *
     * @return All the tags this chemical is a part of.
     *
     * @deprecated Use {@link ChemicalStack#getTags()} instead
     */
    @Deprecated(forRemoval = true, since = "10.7.9")
    public Stream<TagKey<Chemical>> getTags() {
        return getAsHolder().tags();
    }

    /**
     * Helper method to get the intrusive holder for this chemical.
     *
     * @since 10.6.0
     * @deprecated If a holder is necessary get it from {@link ChemicalStack#getChemicalHolder()} or direct from the {@link MekanismAPI#CHEMICAL_REGISTRY}.
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Holder<Chemical> getAsHolder() {
        return builtInRegistryHolder;
    }

    /**
     * Gets whether this chemical is the empty instance.
     *
     * @return {@code true} if this chemical is the empty instance, {@code false} otherwise.
     *
     * @deprecated Prefer checking if against {@link MekanismAPI#EMPTY_CHEMICAL_KEY}
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true, since = "10.7.11")
    public boolean isEmptyType() {
        return this == ChemicalRegistry.empty();
    }

    /**
     * Gets the item tag representing the ore for this slurry.
     *
     * @return The tag for the item the slurry goes with. May be null.
     *
     * @deprecated Prefer checking against {@link IMekanismDataMapTypes#chemicalSolidTag()}, though note it may not contain entries from mods that haven't updated to
     * declaring via datamaps.
     */
    @Nullable
    @Deprecated(forRemoval = true, since = "10.7.11")
    public TagKey<Item> getOreTag() {
        return derived().oreTag();
    }

    /**
     * {@return whether this chemical should render as a gas or more like a fluid}
     *
     * @since 10.7.0
     * @deprecated Prefer checking against {@link MekanismAPITags.Chemicals#GASEOUS}, though note it may not contain entries from mods that haven't updated to declaring
     * via tags.
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public boolean isGaseous() {
        return isGaseousLegacy() || is(MekanismAPITags.Chemicals.GASEOUS);
    }

    /**
     * {@return whether this chemical declares in code that it should render as a gas or more like a fluid}
     *
     * @since 10.7.11
     * @deprecated Prefer checking against {@link MekanismAPITags.Chemicals#GASEOUS}, though note it may not contain entries from mods that haven't updated to declaring
     * via tags.
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public boolean isGaseousLegacy() {
        return isGaseous;
    }

    /**
     * Participates in deriving this chemical's modern attributes from the {@link ChemicalDataContext} bound to the
     * calling thread. Every context-dependent public query invokes this virtual method; overrides therefore remain
     * observable and must invoke {@code super} so that the standard five-map derivation is retained.
     *
     * <p>This is an internal derivation callback, not a public refresh operation. Calling it directly outside a
     * context-local query is unsupported and the base implementation throws {@link IllegalStateException}. An
     * override may make reentrant context-dependent queries on this same chemical: those queries observe the
     * immutable partial view accumulated so far and do not recursively invoke the override. Querying another
     * chemical derives a nested independent view and restores this derivation afterward.</p>
     *
     * @param holder canonical holder for this chemical
     *
     * @implNote Fabric deliberately re-derives a context-local view for each top-level query rather than mutating
     * shared {@code Chemical} state. Direct external invocation being invalid is an authorized Fabric delta from the
     * upstream cache-refresh lifecycle.
     */
    @Internal//TODO - 1.22: Evaluate if we want to get rid of this or if caching the state of some of this is useful from a performance standpoint
    @MustBeInvokedByOverriders
    public void updateFromDataMap(Holder<Chemical> holder) {
        DerivedBuilder builder = trackingBuilder();
        ChemicalSolidTag tag = IMekanismDataMapTypes.INSTANCE.getChemicalData(holder, IMekanismDataMapTypes.INSTANCE.chemicalSolidTag());
        builder.oreTag = tag == null ? legacyOreTag : tag.solidRepresentation();
        trackAttribute(holder, IMekanismDataMapTypes.INSTANCE.chemicalFuel());
        trackAttribute(holder, IMekanismDataMapTypes.INSTANCE.chemicalRadioactivity());
        trackAttribute(holder, IMekanismDataMapTypes.INSTANCE.cooledChemicalCoolant());
        trackAttribute(holder, IMekanismDataMapTypes.INSTANCE.heatedChemicalCoolant());
    }

    /**
     * Tracks an attribute if it is present, and update any related cached states.
     *
     * @param holder      The reference holder for this chemical.
     * @param dataMapType The type of the attribute to check for and track.
     *
     * @since 10.7.11
     */
    protected void trackAttribute(Holder<Chemical> holder, MekanismDataMapType<Chemical, ? extends IChemicalAttribute> dataMapType) {
        DerivedBuilder builder = trackingBuilder();
        IChemicalAttribute attribute = IMekanismDataMapTypes.INSTANCE.getChemicalData(holder, dataMapType);
        if (attribute != null) {
            builder.attributes.add(attribute);
            if (attribute instanceof ChemicalRadioactivity(double rads)) builder.radioactivity = rads;
            else builder.needsValidation |= attribute.needsValidation();
        }
    }

    private static final ThreadLocal<DerivationFrame> TRACKING = new ThreadLocal<>();

    private DerivedBuilder trackingBuilder() {
        DerivationFrame frame = TRACKING.get();
        if (frame == null || frame.chemical != this) {
            throw new IllegalStateException("Chemical data-map update requires this chemical's context-local derivation");
        }
        return frame.builder;
    }

    private Derived derived() {
        DerivationFrame previous = TRACKING.get();
        if (previous != null && previous.chemical == this) {
            return previous.builder.snapshot();
        }
        DerivedBuilder builder = new DerivedBuilder(legacyOreTag, legacyRadioactivity, hasLegacyAttributesWithValidation);
        TRACKING.set(new DerivationFrame(this, builder));
        try {
            updateFromDataMap(getAsHolder());
            return builder.snapshot();
        } finally {
            if (previous == null) TRACKING.remove();
            else TRACKING.set(previous);
        }
    }

    private record DerivationFrame(Chemical chemical, DerivedBuilder builder) {
    }

    private static final class DerivedBuilder {
        private TagKey<Item> oreTag;
        private final List<IChemicalAttribute> attributes = new ArrayList<>();
        private double radioactivity;
        private boolean needsValidation;
        private DerivedBuilder(TagKey<Item> oreTag, double radioactivity, boolean needsValidation) {
            this.oreTag = oreTag;
            this.radioactivity = radioactivity;
            this.needsValidation = needsValidation;
        }

        private Derived snapshot() {
            return new Derived(oreTag, List.copyOf(attributes), radioactivity, needsValidation);
        }
    }

    private record Derived(@Nullable TagKey<Item> oreTag, List<IChemicalAttribute> attributes,
                           double radioactivity, boolean needsValidation) {
    }

    /**
     * Saves this chemical to a new tag.
     *
     * @throws IllegalStateException if this chemical is empty
     * @since 10.7.0
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Tag save(HolderLookup.Provider lookupProvider) {
        if (isEmptyType()) {
            throw new IllegalStateException("Cannot encode empty Chemical");
        }
        return CODEC.encodeStart(lookupProvider.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
    }

    /**
     * Saves this chemical to a new tag. Empty chemicals are supported and will be saved as an empty tag.
     *
     * @since 10.7.0
     */
    @Deprecated(forRemoval = true, since = "10.7.11")
    public Tag saveOptional(HolderLookup.Provider lookupProvider) {
        return isEmptyType() ? new CompoundTag() : save(lookupProvider);
    }

    /**
     * Gathers any tooltips this chemical has, and adds them to the list.
     *
     * @param stack       Chemical stack.
     * @param context     Current tooltip context.
     * @param tooltips    List of tooltips to add to.
     * @param tooltipFlag Flag representing if advanced tooltips are to be shown.
     *
     * @see ChemicalStack#appendHoverText(TooltipContext, List, TooltipFlag)
     * @since 10.7.11
     */
    @SuppressWarnings("removal")
    protected void appendHoverText(ChemicalStack stack, TooltipContext context, List<Component> tooltips, TooltipFlag tooltipFlag) {
        for (IChemicalAttribute attribute : derived().attributes()) {
            attribute.collectTooltips(context, tooltips, tooltipFlag);
        }
        //TODO - 1.22: Remove this legacy handling
        if (!legacyAttributeMap.isEmpty()) {
            //Only loop the legacy attributes if we have any, don't bother looping the attribute map that contains the modern attributes as legacy ones
            Consumer<Component> tooltipAdder = tooltips::add;
            for (ChemicalAttribute attr : getLegacyAttributes()) {
                attr.collectTooltips(tooltipAdder);
            }
        }
    }
}