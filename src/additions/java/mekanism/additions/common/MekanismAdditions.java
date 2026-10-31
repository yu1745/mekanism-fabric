package mekanism.additions.common;

import mekanism.additions.client.AdditionsClient;
import mekanism.additions.common.block.BlockObsidianTNT;
import mekanism.additions.common.config.MekanismAdditionsConfig;
import mekanism.additions.common.entity.EntityBalloon;
import mekanism.additions.common.item.ItemBalloon;
import mekanism.additions.common.registries.AdditionsBiomeModifierSerializers;
import mekanism.additions.common.registries.AdditionsBlocks;
import mekanism.additions.common.registries.AdditionsCreativeTabs;
import mekanism.additions.common.registries.AdditionsDataComponents;
import mekanism.additions.common.registries.AdditionsEntityTypes;
import mekanism.additions.common.registries.AdditionsItems;
import mekanism.additions.common.registries.AdditionsSounds;
import mekanism.additions.common.registries.AdditionsStructureModifierSerializers;
import mekanism.additions.common.voice.VoiceServerManager;
import mekanism.api.MekanismAPI;
import mekanism.common.base.IModModule;
import mekanism.common.lib.Version;
import mekanism.common.registration.impl.ItemRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.loader.api.FabricLoader;
import mekanism.common.base.ModModuleState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

public class MekanismAdditions implements IModModule, ModInitializer {

    public static final String MODID = "mekanismadditions";

    public static MekanismAdditions instance;

    /**
     * MekanismTools version number
     */
    public final Version versionNumber;

    /**
     * The VoiceServer manager for walkie-talkies
     */
    public static VoiceServerManager voiceManager;

    public MekanismAdditions() {
        instance = this;
        versionNumber = Version.fromFabricVersion(FabricLoader.getInstance().getModContainer(MODID).orElseThrow()
              .getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public void onInitialize() {
        ModModuleState.addModule(this);
        MekanismAdditionsConfig.registerConfigs();
        AdditionsDataComponents.DATA_COMPONENTS.registerAll(BuiltInRegistries.DATA_COMPONENT_TYPE);
        AdditionsEntityTypes.ENTITY_TYPES.registerAll(BuiltInRegistries.ENTITY_TYPE);
        AdditionsBlocks.BLOCKS.registerAll(BuiltInRegistries.BLOCK, BuiltInRegistries.ITEM);
        AdditionsItems.ITEMS.registerAll(BuiltInRegistries.ITEM);
        AdditionsSounds.SOUND_EVENTS.registerAll(BuiltInRegistries.SOUND_EVENT);
        AdditionsCreativeTabs.CREATIVE_TABS.registerAll(BuiltInRegistries.CREATIVE_MODE_TAB);
        for (var tab : java.util.List.of(CreativeModeTabs.COLORED_BLOCKS, CreativeModeTabs.FUNCTIONAL_BLOCKS,
              CreativeModeTabs.REDSTONE_BLOCKS, CreativeModeTabs.COMBAT, CreativeModeTabs.SPAWN_EGGS)) {
            ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> AdditionsCreativeTabs.CREATIVE_TABS.getAddToExistingTabs()
                  .addToDisplay(tab, entries, entries.getContext()));
        }
        mekanism.fabric.worldgen.FabricBiomeModifiers.registerModuleFinalizer(mekanism.fabric.additions.AdditionsSpawnModifiers::load);
        net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().add(AdditionsBlocks.OBSIDIAN_TNT.get(), 15, 75);
        commonSetup();
        ServerLifecycleEvents.SERVER_STARTED.register(this::serverStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::serverStopping);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public Version getVersion() {
        return versionNumber;
    }

    @Override
    public String getName() {
        return "Additions";
    }

    @Override
    public void resetClient() {
        AdditionsClient.reset();
    }

    @Override
    public void launchClient(Connection connection) {
        AdditionsClient.launch(connection);
    }

    private void commonSetup() {
            //Dispenser behavior
            DispenserBlock.registerBehavior(AdditionsBlocks.OBSIDIAN_TNT, new DefaultDispenseItemBehavior() {
                @NotNull
                @Override
                protected ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
                    BlockPos blockpos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    if (BlockObsidianTNT.createAndAddEntity(source.level(), blockpos, null)) {
                        source.level().gameEvent(null, GameEvent.ENTITY_PLACE, blockpos);
                        stack.shrink(1);
                        return stack;
                    }
                    //Otherwise, if something went very wrong, eject it as a normal item
                    return super.execute(source, stack);
                }
            });
            DefaultDispenseItemBehavior balloonBehavior = new DefaultDispenseItemBehavior() {
                @NotNull
                @Override
                protected ItemStack execute(@NotNull BlockSource source, @NotNull ItemStack stack) {
                    ServerLevel level = source.level();
                    Position position = DispenserBlock.getDispensePosition(source, 1, new Vec3(-0.5, -3.5, -0.5));
                    EntityBalloon balloon = EntityBalloon.create(level, position.x(), position.y(), position.z(), ((ItemBalloon) stack.getItem()).getColor());
                    if (balloon == null) {
                        //Otherwise, if something went very wrong, eject it as a normal item
                        return super.execute(source, stack);
                    }
                    stack.shrink(1);
                    level.addFreshEntity(balloon);
                    level.gameEvent(null, GameEvent.ENTITY_PLACE, new Vec3(position.x(), position.y(), position.z()));
                    return stack;
                }
            };
            for (ItemRegistryObject<ItemBalloon> balloon : AdditionsItems.BALLOONS.values()) {
                DispenserBlock.registerBehavior(balloon, balloonBehavior);
            }
        MekanismAPI.logger.info("Loaded 'Mekanism: Additions' module.");
    }

    private void serverStarting(MinecraftServer server) {
        if (MekanismAdditionsConfig.additions.voiceServerEnabled.get()) {
            if (voiceManager == null) {
                voiceManager = new VoiceServerManager(server);
            }
            voiceManager.start();
        }
    }

    private void serverStopping(MinecraftServer server) {
        if (voiceManager != null) {
            voiceManager.stop();
            voiceManager = null;
        }
    }
}