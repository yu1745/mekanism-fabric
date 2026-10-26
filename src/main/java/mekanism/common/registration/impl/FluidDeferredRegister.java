package mekanism.common.registration.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import mekanism.api.fluid.FluidStack;
import mekanism.api.MekanismAPI;
import mekanism.common.base.IChemicalConstant;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.MekanismDeferredRegister;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric port of the official fluid registry owner. NeoForge registers a {@code FluidType} per fluid and derives the
 * flowing implementation from {@code BaseFlowingFluid}; vanilla has neither concept, so this class owns the fluid
 * metadata ({@link MekanismFluidType}) as a plain Mekanism value and provides the {@link FlowingFluid} implementation
 * that vanilla expects. Bucket fill/empty sound actions from the NeoForge builder have no vanilla equivalent and are
 * dropped here; bucket pickup sounds come from {@link Fluid#getPickupSound()}.
 */
public class FluidDeferredRegister {

    private static final ResourceLocation OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay");
    private static final ResourceLocation RENDER_OVERLAY = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
    private static final ResourceLocation LIQUID = ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "liquid/liquid");
    private static final ResourceLocation LIQUID_FLOW = ResourceLocation.fromNamespaceAndPath(MekanismAPI.MEKANISM_MODID, "liquid/liquid_flow");
    //Copy of/based off of vanilla's lava/water bucket dispense behavior
    private static final DispenseItemBehavior BUCKET_DISPENSE_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @NotNull
        @Override
        public ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
            Level world = source.level();
            DispensibleContainerItem bucket = (DispensibleContainerItem) stack.getItem();
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (bucket.emptyContents(null, world, pos, null)) {
                bucket.checkExtraContent(null, world, stack, pos);
                return new ItemStack(Items.BUCKET);
            }
            return super.execute(source, stack);
        }
    };

    private final MekanismDeferredRegister<Fluid> fluidRegister;
    private final MekanismDeferredRegister<Block> blockRegister;
    private final ItemDeferredRegister itemRegister;
    private final List<FluidRegistryObject<?, ?, ?, ?>> registryObjects = new ArrayList<>();

    public FluidDeferredRegister(String modid) {
        blockRegister = new MekanismDeferredRegister<>(Registries.BLOCK, modid);
        fluidRegister = new MekanismDeferredRegister<>(Registries.FLUID, modid);
        //Note: We use our own deferred register so that we also can automatically attach any capability aware buckets we register
        itemRegister = new ItemDeferredRegister(modid);
    }

    public static MekanismFluidProperties getMekBaseBuilder() {
        return MekanismFluidProperties.create();
    }

    public FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BucketItem> registerLiquidChemical(IChemicalConstant constants) {
        int density = Math.round(constants.getDensity());
        return register(constants.getName(), properties -> properties
              .temperature(Math.round(constants.getTemperature()))
              .density(density)
              .viscosity(density)
              .lightLevel(constants.getLightLevel()), renderProperties -> renderProperties
              .tint(constants.getColor())
        );
    }

    public FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BucketItem> register(String name,
          UnaryOperator<FluidTypeRenderProperties> renderProperties) {
        return register(name, UnaryOperator.identity(), renderProperties);
    }

    public FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BucketItem> register(String name,
          UnaryOperator<MekanismFluidProperties> properties, UnaryOperator<FluidTypeRenderProperties> renderProperties) {
        return register(name, BucketItem::new, properties, renderProperties);
    }

    public <BUCKET extends BucketItem> FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BUCKET> register(
          String name, BucketCreator<BUCKET> bucketCreator, UnaryOperator<MekanismFluidProperties> fluidProperties,
          UnaryOperator<FluidTypeRenderProperties> renderProperties) {
        return register(name, fluidProperties.apply(getMekBaseBuilder()), renderProperties.apply(FluidTypeRenderProperties.builder()), bucketCreator);
    }

    public <BUCKET extends BucketItem> FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BUCKET> register(
          String name, MekanismFluidProperties properties, FluidTypeRenderProperties renderProperties, BucketCreator<BUCKET> bucketCreator) {
        //The official implementation relied on the block and fluid sharing a name for the description; keep that exact id
        String descriptionId = Util.makeDescriptionId("block", ResourceLocation.fromNamespaceAndPath(fluidRegister.getNamespace(), name));
        MekanismFluidType fluidType = new MekanismFluidType(descriptionId, properties, renderProperties);
        FluidDefinition definition = new FluidDefinition(fluidType);

        //Register the still variant first: the flowing variant, the bucket, and the block all resolve it lazily by holder
        MekanismDeferredHolder<Fluid, MekanismFlowingFluid.Source> stillFluid = fluidRegister.register(name,
              () -> new MekanismFlowingFluid.Source(definition));
        definition.still = stillFluid;
        MekanismDeferredHolder<Fluid, MekanismFlowingFluid.Flowing> flowingFluid = fluidRegister.register("flowing_" + name,
              () -> new MekanismFlowingFluid.Flowing(definition));
        definition.flowing = flowingFluid;
        ItemRegistryObject<BUCKET> bucket = itemRegister.register(name + "_bucket",
              () -> bucketCreator.create(stillFluid.get(), new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
        definition.bucket = bucket;
        MapColor color = getClosestColor(renderProperties.color);
        //Note: The block properties used here is a copy of the ones for water
        MekanismDeferredHolder<Block, LiquidBlock> block = blockRegister.register(name, () -> new LiquidBlock(flowingFluid.get(),
              BlockBehaviour.Properties.of().noCollission().strength(100.0F).noLootTable().replaceable().pushReaction(PushReaction.DESTROY)
                    .liquid().mapColor(color)));
        definition.block = block;
        FluidRegistryObject<MekanismFlowingFluid.Source, MekanismFlowingFluid.Flowing, LiquidBlock, BUCKET> registryObject =
              new FluidRegistryObject<>(fluidType, stillFluid, flowingFluid, bucket, block);
        registryObjects.add(registryObject);
        return registryObject;
    }

    /**
     * Commits the fluid, liquid block, and bucket batches in the order the flowing-fluid instances require: fluids first,
     * because the block factory resolves the registered flowing fluid, and buckets last, because the bucket factory
     * resolves the registered still fluid.
     */
    public void registerAll(Registry<Fluid> fluidRegistry, Registry<Block> blockRegistry, Registry<Item> itemRegistry) {
        fluidRegister.registerAll(fluidRegistry);
        this.blockRegister.registerAll(blockRegistry);
        itemRegister.registerAll(itemRegistry);
    }

    public Collection<? extends MekanismDeferredHolder<Fluid, ? extends Fluid>> getFluidEntries() {
        return fluidRegister.getEntries();
    }

    public Collection<? extends MekanismDeferredHolder<Block, ? extends Block>> getBlockEntries() {
        return blockRegister.getEntries();
    }

    public Collection<? extends Holder<Item>> getBucketEntries() {
        return itemRegister.getEntries();
    }

    /** Registered fluid families in declaration order; the client renderer uses these to bind textures and tints. */
    public List<FluidRegistryObject<?, ?, ?, ?>> getFluidRegistryObjects() {
        return Collections.unmodifiableList(registryObjects);
    }

    public void registerBucketDispenserBehavior() {
        for (Holder<Item> bucket : getBucketEntries()) {
            DispenserBlock.registerBehavior(bucket.value(), BUCKET_DISPENSE_BEHAVIOR);
        }
    }

    public static MapColor getClosestColor(int tint) {
        if (tint == 0xFFFFFFFF) {
            return MapColor.NONE;
        }
        int red = FastColor.ARGB32.red(tint);
        int green = FastColor.ARGB32.green(tint);
        int blue = FastColor.ARGB32.blue(tint);
        MapColor color = MapColor.NONE;
        double minDistance = Double.MAX_VALUE;
        for (MapColor toTest : MapColor.MATERIAL_COLORS) {
            if (toTest != null && toTest != MapColor.NONE) {
                int testRed = FastColor.ARGB32.red(toTest.col);
                int testGreen = FastColor.ARGB32.green(toTest.col);
                int testBlue = FastColor.ARGB32.blue(toTest.col);
                double distanceSquare = perceptualColorDistanceSquared(red, green, blue, testRed, testGreen, testBlue);
                if (distanceSquare < minDistance) {
                    minDistance = distanceSquare;
                    color = toTest;
                }
            }
        }
        return color;
    }

    /**
     * <a href="http://www.compuphase.com/cmetric.htm">Color Metric</a>
     * <a href="http://stackoverflow.com/a/6334454">Stack Overflow</a>
     * Returns 0 for equal colors, nonzero for colors that look different. The return value is farther from 0 the more different the colors look.
     */
    private static double perceptualColorDistanceSquared(int red1, int green1, int blue1, int red2, int green2, int blue2) {
        int redMean = (red1 + red2) >> 1;
        int r = red1 - red2;
        int g = green1 - green2;
        int b = blue1 - blue2;
        return (((512 + redMean) * r * r) >> 8) + 4 * g * g + (((767 - redMean) * b * b) >> 8);
    }

    @FunctionalInterface
    public interface BucketCreator<BUCKET extends BucketItem> {

        BUCKET create(Fluid fluid, Item.Properties builder);
    }

    /** Vanilla-facing flow parameters; the NeoForge defaults of {@code BaseFlowingFluid.Properties} are preserved. */
    static final class FluidDefinition {

        private final MekanismFluidType fluidType;
        private MekanismDeferredHolder<Fluid, ?> still;
        private MekanismDeferredHolder<Fluid, ?> flowing;
        private ItemRegistryObject<? extends BucketItem> bucket;
        private MekanismDeferredHolder<Block, ? extends LiquidBlock> block;

        private FluidDefinition(MekanismFluidType fluidType) {
            this.fluidType = fluidType;
        }
    }

    /**
     * Vanilla flowing-fluid implementation, replacing NeoForge's {@code BaseFlowingFluid}. Tick rate, slope distance,
     * drop-off, and explosion resistance use the values {@code BaseFlowingFluid.Properties} defaulted to.
     */
    public abstract static class MekanismFlowingFluid extends FlowingFluid {

        protected final FluidDefinition definition;

        private MekanismFlowingFluid(FluidDefinition definition) {
            this.definition = definition;
        }

        @Override
        public Fluid getFlowing() {
            return definition.flowing.get();
        }

        @Override
        public Fluid getSource() {
            return definition.still.get();
        }

        @Override
        public Item getBucket() {
            return definition.bucket.get();
        }

        @Override
        public boolean isSame(Fluid fluid) {
            return fluid == definition.still.get() || fluid == definition.flowing.get();
        }

        @Override
        protected boolean canConvertToSource(Level level) {
            //BaseFlowingFluid.Properties enables source conversion by default for modded fluids
            return true;
        }

        @Override
        protected void beforeDestroyingBlock(net.minecraft.world.level.LevelAccessor levelAccessor, BlockPos pos, BlockState state) {
            Block.dropResources(state, levelAccessor, pos, state.hasBlockEntity() ? levelAccessor.getBlockEntity(pos) : null);
        }

        @Override
        protected int getSlopeFindDistance(net.minecraft.world.level.LevelReader level) {
            return 4;
        }

        @Override
        protected int getDropOff(net.minecraft.world.level.LevelReader level) {
            return 1;
        }

        @Override
        public int getTickDelay(net.minecraft.world.level.LevelReader level) {
            return 5;
        }

        @Override
        protected float getExplosionResistance() {
            return 100.0F;
        }

        @Override
        protected boolean canBeReplacedWith(FluidState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Fluid fluid,
              net.minecraft.core.Direction direction) {
            return direction == net.minecraft.core.Direction.DOWN;
        }

        @Override
        protected BlockState createLegacyBlock(FluidState state) {
            return definition.block.get().defaultBlockState().setValue(LiquidBlock.LEVEL, getLegacyLevel(state));
        }

        public static class Flowing extends MekanismFlowingFluid {

            private Flowing(FluidDefinition definition) {
                super(definition);
            }

            @Override
            protected void createFluidStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Fluid, FluidState> builder) {
                super.createFluidStateDefinition(builder);
                builder.add(LEVEL);
            }

            @Override
            public int getAmount(FluidState state) {
                return state.getValue(LEVEL);
            }

            @Override
            public boolean isSource(FluidState state) {
                return false;
            }
        }

        public static class Source extends MekanismFlowingFluid {

            private Source(FluidDefinition definition) {
                super(definition);
            }

            @Override
            public int getAmount(FluidState state) {
                return 8;
            }

            @Override
            public boolean isSource(FluidState state) {
                return true;
            }
        }
    }

    public static class FluidTypeRenderProperties {

        private ResourceLocation stillTexture = LIQUID;
        private ResourceLocation flowingTexture = LIQUID_FLOW;
        //For now all our fluids use the same "overlay" for being against glass as vanilla water.
        private ResourceLocation overlayTexture = OVERLAY;
        private ResourceLocation renderOverlayTexture = RENDER_OVERLAY;
        private int color = 0xFFFFFFFF;

        private FluidTypeRenderProperties() {
        }

        public static FluidTypeRenderProperties builder() {
            return new FluidTypeRenderProperties();
        }

        public FluidTypeRenderProperties texture(ResourceLocation still, ResourceLocation flowing) {
            this.stillTexture = still;
            this.flowingTexture = flowing;
            return this;
        }

        public FluidTypeRenderProperties texture(ResourceLocation still, ResourceLocation flowing, ResourceLocation overlay) {
            this.stillTexture = still;
            this.flowingTexture = flowing;
            this.overlayTexture = overlay;
            return this;
        }

        public FluidTypeRenderProperties renderOverlay(ResourceLocation renderOverlay) {
            this.renderOverlayTexture = renderOverlay;
            return this;
        }

        public FluidTypeRenderProperties tint(int color) {
            this.color = color;
            return this;
        }
    }

    /** Vanilla-neutral replacement for NeoForge's {@code FluidType} metadata; NeoForge defaults are preserved. */
    public static class MekanismFluidType {

        private final String descriptionId;
        private final int temperature;
        private final int density;
        private final int viscosity;
        private final int lightLevel;
        public final ResourceLocation stillTexture;
        public final ResourceLocation flowingTexture;
        public final ResourceLocation overlayTexture;
        public final ResourceLocation renderOverlayTexture;
        public final int color;

        private MekanismFluidType(String descriptionId, MekanismFluidProperties properties, FluidTypeRenderProperties renderProperties) {
            this.descriptionId = descriptionId;
            this.temperature = properties.temperature;
            this.density = properties.density;
            this.viscosity = properties.viscosity;
            this.lightLevel = properties.lightLevel;
            this.stillTexture = renderProperties.stillTexture;
            this.flowingTexture = renderProperties.flowingTexture;
            this.overlayTexture = renderProperties.overlayTexture;
            this.renderOverlayTexture = renderProperties.renderOverlayTexture;
            this.color = renderProperties.color;
        }

        public String getDescriptionId() {
            return descriptionId;
        }

        public Component getDescription(FluidStack stack) {
            return Component.translatable(descriptionId);
        }

        public int getTemperature() {
            return temperature;
        }

        public int getDensity() {
            return density;
        }

        public int getViscosity() {
            return viscosity;
        }

        public int getLightLevel(FluidStack stack) {
            return lightLevel;
        }
    }

    /** Vanilla-neutral replacement for {@code FluidType.Properties}; defaults match NeoForge's builder defaults. */
    public static class MekanismFluidProperties {

        private int temperature = 300;
        private int density = 1000;
        private int viscosity = 1000;
        private int lightLevel = 0;

        private MekanismFluidProperties() {
        }

        public static MekanismFluidProperties create() {
            return new MekanismFluidProperties();
        }

        public MekanismFluidProperties temperature(int temperature) {
            this.temperature = temperature;
            return this;
        }

        public MekanismFluidProperties density(int density) {
            this.density = density;
            return this;
        }

        public MekanismFluidProperties viscosity(int viscosity) {
            this.viscosity = viscosity;
            return this;
        }

        public MekanismFluidProperties lightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
            return this;
        }
    }
}
