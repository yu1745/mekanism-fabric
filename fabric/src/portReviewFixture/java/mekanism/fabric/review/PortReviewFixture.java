package mekanism.fabric.review;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import mekanism.api.SerializationConstants;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.CommonWorldTickHandler;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.base.MekFakePlayer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.ModuleContainer;
import mekanism.common.content.gear.mekasuit.MekaSuitFlight;
import mekanism.common.entity.EntityRobit;
import mekanism.common.item.gear.ItemElectricBow;
import mekanism.common.lib.chunkloading.ChunkLoaderTickets;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismEntityTypes;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.tile.TileEntityChargepad;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import mekanism.common.util.WorldUtils;
import mekanism.fabric.config.FabricMekanismConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.reborn.energy.api.EnergyStorage;

/** Eight bounded regression groups against actual packaged Minecraft/Fabric owners; no production test hooks. */
public final class PortReviewFixture implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism/PortReview");
    private static final ChunkPos TICKET_CHUNK = new ChunkPos(200, 200);
    private static final BlockPos OWNER_A = TICKET_CHUNK.getMiddleBlockPosition(120);
    private static final BlockPos OWNER_B = OWNER_A.above();
    private static final BlockPos BRINE = new BlockPos(12, 100, 0);
    private static final BlockPos WATER = new BlockPos(12, 100, 4);
    private static int randomTicks;
    private static int ticks;
    private static int age;
    private static int passed;
    private static boolean finished;
    private static Block tickingBlock;
    private static Item replaceableEnergyItem;
    private static FabricMekanismConfigManager fixtureGear;

    @Override
    public void onInitialize() {
        tickingBlock = Registry.register(BuiltInRegistries.BLOCK, id("tick_probe"), new Block(BlockBehaviour.Properties.of().randomTicks()) {
            @Override
            protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
                randomTicks++;
            }
        });
        replaceableEnergyItem = Registry.register(BuiltInRegistries.ITEM, id("energy_item"), new Item(new Item.Properties().stacksTo(1)));
        EnergyStorage.ITEM.registerForItems((stack, context) -> new EnergyStorage() {
            @Override
            public long insert(long maxAmount, TransactionContext transaction) {
                return maxAmount >= 8 && context.exchange(ItemVariant.of(Items.REDSTONE), 1, transaction) == 1 ? 8 : 0;
            }
            @Override public long extract(long maxAmount, TransactionContext transaction) { return 0; }
            @Override public long getAmount() { return 0; }
            @Override public long getCapacity() { return 8; }
        }, replaceableEnergyItem);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (fixtureGear != null) fixtureGear.unregister(MekanismConfig.gear);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!finished) {
                try {
                    tick(server);
                } catch (Throwable failure) {
                    finished = true;
                    LOGGER.error("PORT_REVIEW_FAIL", failure);
                    result("FAIL: " + failure);
                    server.halt(false);
                }
            }
        });
    }

    private static void tick(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        check(++age <= 600, "review fixture exceeded its bounded server-tick budget");
        ticks++;
        if (ticks == 2 && !level.getChunkSource().isPositionTicking(TICKET_CHUNK.toLong())) {
            //A level-31 region ticket loads the surrounding ticking dependencies asynchronously.
            //Start measuring behavior only once vanilla's ticking future is complete.
            ticks--;
            return;
        }
        if (ticks == 1) {
            level.setChunkForced(0, 0, true);
            //Explicit prerequisite: production has not ported this SERVER configuration owner yet.
            //Use its real spec and world path in this disposable fixture, never bypass provider guards.
            check(!MekanismConfig.gear.isLoaded(), "fixture must not create a second production gear writer");
            fixtureGear = new FabricMekanismConfigManager(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig"));
            fixtureGear.registerAndLoad(MekanismConfig.gear);
            LOGGER.info("PORT_REVIEW_PREREQUISITE fixture_only_gear_config=true production_gear_owner_unported=true");
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "PortReview"), ClientInformation.createDefault());
            player.connection = MekFakePlayer.setupFakePlayer(level).connection;
            //This detached server-side probe has no login ticks; put it past vanilla's 60-tick spawn immunity.
            var spawnImmunity = ServerPlayer.class.getDeclaredField(FabricLoader.getInstance().getMappingResolver()
                  .mapFieldName("intermediary", "net.minecraft.class_3222", "field_13998", "I"));
            spawnImmunity.setAccessible(true);
            spawnImmunity.setInt(player, 0);
            player.setGameMode(GameType.SURVIVAL);
            robitAndDamageGuards(level, player);
            protectionProbe(level);
            itemEnergy(level);
            toolsAndBow(level, player);
            flight(level, player);
            chunkVersion(level);
            prepareFluids(level);
            //A full section guarantees every random-tick sample hits the counter block, without probabilistic crop assertions.
            for (int x = TICKET_CHUNK.getMinBlockX(); x <= TICKET_CHUNK.getMaxBlockX(); x++) {
                for (int z = TICKET_CHUNK.getMinBlockZ(); z <= TICKET_CHUNK.getMaxBlockZ(); z++) {
                    for (int y = 96; y < 112; y++) {
                        level.setBlock(new BlockPos(x, y, z), tickingBlock.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            check(!level.getForcedChunks().contains(TICKET_CHUNK.toLong()), "fixture chunk must not start globally forced");
            ChunkLoaderTickets.setTicket(level, OWNER_A, TICKET_CHUNK.x, TICKET_CHUNK.z, true, false);
        } else if (ticks == 10) {
            check(randomTicks == 0, "ordinary owner must not enable remote random ticking");
            ChunkLoaderTickets.setTicket(level, OWNER_B, TICKET_CHUNK.x, TICKET_CHUNK.z, true, true);
        } else if (ticks == 20) {
            check(ChunkLoaderTickets.shouldForceTicks(level, TICKET_CHUNK), "the second owner must request forced ticking");
            check(randomTicks > 0, "forceTicks owner must actually tick a remote section: ready=" +
                  level.getChunkSource().isPositionTicking(TICKET_CHUNK.toLong()) + " blocks=" + level.shouldTickBlocksAt(TICKET_CHUNK.toLong()));
            ChunkLoaderTickets.setTicket(level, OWNER_A, TICKET_CHUNK.x, TICKET_CHUNK.z, false, false);
        } else if (ticks == 40) {
            check(level.isPositionEntityTicking(OWNER_A), "removing A must preserve B's entity-ticking ticket");
            check(ChunkLoaderTickets.shouldForceTicks(level, TICKET_CHUNK), "B's forced-tick request must survive A");
            check(!level.getForcedChunks().contains(TICKET_CHUNK.toLong()), "owned region tickets must not enter /forceload data");
            level.setChunkForced(TICKET_CHUNK.x, TICKET_CHUNK.z, true);
            ChunkLoaderTickets.setTicket(level, OWNER_B, TICKET_CHUNK.x, TICKET_CHUNK.z, false, true);
        } else if (ticks == 60) {
            check(level.isPositionEntityTicking(OWNER_A), "removing B must preserve vanilla /forceload");
            check(level.getForcedChunks().contains(TICKET_CHUNK.toLong()), "owned release must leave administrator's flag intact");
            level.setChunkForced(TICKET_CHUNK.x, TICKET_CHUNK.z, false);
        } else if (ticks == 80) {
            check(!level.isPositionEntityTicking(OWNER_A), "all removed region tickets must stop entity ticking");
            ChunkLoaderTickets.setTicket(level, OWNER_A, TICKET_CHUNK.x, TICKET_CHUNK.z, true, false);
            ChunkLoaderTickets data = ChunkLoaderTickets.get(level);
            data.setDirty(false);
            ChunkLoaderTickets.validate(server);
            check(data.isDirty(), "invalid-owner cleanup must dirty an empty saved-data result");
            check(data.save(new CompoundTag(), level.registryAccess()).getList("owners", Tag.TAG_COMPOUND).isEmpty(), "invalid owner must be removed from persisted data");
            pass("chunk-owner-overlap-forceload-forceTicks-persistence");
            check(!level.getFluidState(BRINE.east()).isEmpty() && !level.getFluidState(BRINE.east()).isSource(), "two brine sources must not create a third source");
            check(level.getFluidState(WATER.east()).isSource(), "vanilla water control must actually form a source");
            pass("fluid-source-and-defaults");
            check(passed == 8, "exactly eight independent review groups must complete");
            result("PASS groups=" + passed);
            finished = true;
            server.halt(false);
        }
    }

    private static void robitAndDamageGuards(ServerLevel level, ServerPlayer player) {
        EntityRobit robit = MekanismEntityTypes.ROBIT.get().create(level);
        check(robit != null, "registered Robit must construct");
        IEnergyContainer energy = robit.getEnergyContainers(null).getFirst();
        energy.setEnergy(100_000);
        robit.hurt(level.damageSources().generic(), 10);
        check(robit.isAlive() && !robit.isDeadOrDying() && robit.getHealth() == robit.getMaxHealth(), "lethal-size damage must not kill the 1 HP Robit");
        check(energy.getEnergy() == 90_000, "Robit must pay 10,000 J for ten actual damage, without a 1 HP clamp");
        ItemStack armor = StorageUtils.getFilledEnergyVariant(MekanismItems.MEKASUIT_BODYARMOR);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        IEnergyContainer suitEnergy = StorageUtils.getEnergyContainer(armor, 0);
        long before = suitEnergy.getEnergy();
        check(!player.hurt(level.damageSources().inFire(), 10), "fire resistance must reject fire damage");
        check(suitEnergy.getEnergy() == before, "rejected fire damage must not drain suit energy");
        player.removeAllEffects();
        player.setItemSlot(EquipmentSlot.HEAD, StorageUtils.getFilledEnergyVariant(MekanismItems.MEKASUIT_HELMET));
        player.setItemSlot(EquipmentSlot.LEGS, StorageUtils.getFilledEnergyVariant(MekanismItems.MEKASUIT_PANTS));
        player.setItemSlot(EquipmentSlot.FEET, StorageUtils.getFilledEnergyVariant(MekanismItems.MEKASUIT_BOOTS));
        long totalBefore = suitEnergy(player);
        var source = level.damageSources().generic();
        check(!player.hurt(source, 1), "full suit must absorb the first hit");
        long afterFirst = suitEnergy(player);
        check(!player.hurt(source, 1), "full suit must absorb the next identical hit");
        check(afterFirst < totalBefore && suitEnergy(player) < afterFirst, "cancelled identical hits must each pay energy: before=" + totalBefore + " first=" + afterFirst + " second=" + suitEnergy(player));
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        pass("robit-and-damage-rejection");
    }

    private static void protectionProbe(ServerLevel level) throws Exception {
        BlockPos minerPos = new BlockPos(0, 100, 0);
        BlockPos denied = minerPos.east();
        level.setBlockAndUpdate(minerPos, MekanismBlocks.DIGITAL_MINER.get().defaultBlockState());
        level.setBlockAndUpdate(denied, Blocks.STONE.defaultBlockState());
        TileEntityDigitalMiner miner = (TileEntityDigitalMiner) level.getBlockEntity(minerPos);
        UUID owner = UUID.randomUUID();
        miner.getSecurity().setOwnerUUID(owner);
        boolean[] observed = {false};
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (pos.equals(denied)) {
                observed[0] = owner.equals(player.getUUID());
                return false;
            }
            return true;
        });
        Method canMine = TileEntityDigitalMiner.class.getDeclaredMethod("canMine", BlockState.class, BlockPos.class);
        canMine.setAccessible(true);
        check(!(boolean) canMine.invoke(miner, level.getBlockState(denied), denied), "miner's real probe must honor Fabric veto");
        check(observed[0], "break callback must observe the miner owner UUID");
        MekFakePlayer fake = MekFakePlayer.setupFakePlayer(level);
        check(!WorldUtils.canBreakBlock(level, fake, denied, level.getBlockState(denied)), "shared cardboard replacement probe must honor veto too");
        fake.cleanupFakePlayer(level);
        pass("owner-protection-probe");
    }

    private static void itemEnergy(ServerLevel level) throws Exception {
        BlockPos pos = new BlockPos(4, 100, 0);
        level.setBlockAndUpdate(pos, MekanismBlocks.CHARGEPAD.get().defaultBlockState());
        TileEntityChargepad pad = (TileEntityChargepad) level.getBlockEntity(pos);
        IEnergyContainer energy = pad.getEnergyContainers(null).getFirst();
        energy.setEnergy(100_000);
        SimpleContainer inventory = new SimpleContainer(new ItemStack(MekanismItems.ELECTRIC_BOW.get()));
        Method charge = TileEntityChargepad.class.getDeclaredMethod("chargeHandler", net.minecraft.world.Container.class);
        charge.setAccessible(true);
        long before = energy.getEnergy();
        check((boolean) charge.invoke(pad, inventory), "chargepad must charge a registered Mekanism item");
        long gained = ContainerType.ENERGY.getRegisteredHandler(inventory.getItem(0)).getEnergy(0);
        check(gained > 0 && before - energy.getEnergy() == gained, "charged item and machine must conserve strict Joules");
        inventory.setItem(0, new ItemStack(replaceableEnergyItem));
        before = energy.getEnergy();
        check((boolean) charge.invoke(pad, inventory), "chargepad must accept a native transactional replacement");
        check(inventory.getItem(0).is(Items.REDSTONE), "actual inventory must install the native provider's replacement item");
        check(before - energy.getEnergy() == EnergyUnit.FORGE_ENERGY.convertFrom(8), "native transfer must conserve converted energy");
        pass("chargepad-real-inventory-and-native-replacement");
    }

    private static void toolsAndBow(ServerLevel level, ServerPlayer player) throws Exception {
        for (Item item : List.of(MekanismItems.MEKA_TOOL.get(), MekanismItems.ATOMIC_DISASSEMBLER.get())) {
            ItemStack stack = new ItemStack(item);
            check(stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), "default universal tool must retain correct drops");
            stack.set(DataComponents.TOOL, new Tool(List.of(), 1, 0));
            check(!stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), "replacement empty TOOL must remain authoritative");
            stack.remove(DataComponents.TOOL);
            check(!stack.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), "removed TOOL must not get a hidden catch-all");
        }
        ItemElectricBow bow = MekanismItems.ELECTRIC_BOW.get();
        ItemStack stack = new ItemStack(bow);
        bow.setMode(stack, player, true);
        Method create = java.util.Arrays.stream(ItemElectricBow.class.getDeclaredMethods()).filter(method ->
              java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{Level.class, LivingEntity.class, ItemStack.class, ItemStack.class, boolean.class})).findFirst().orElseThrow();
        create.setAccessible(true);
        Projectile arrow = (Projectile) create.invoke(bow, level, player, stack, new ItemStack(Items.ARROW), false);
        check(arrow.isOnFire(), "electric-bow fire mode must apply the real Flame projectile effect");
        check(stack.getEnchantments().isEmpty(), "virtual Flame must not mutate the stored bow enchantments");
        pass("tool-components-and-electric-bow");
    }

    private static void flight(ServerLevel level, ServerPlayer player) {
        ItemStack armor = StorageUtils.getFilledEnergyVariant(MekanismItems.MEKASUIT_BODYARMOR);
        ModuleContainer.EMPTY.addModule(level.registryAccess(), armor, MekanismModules.GRAVITATIONAL_MODULATING_UNIT, 1);
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        MekaSuitFlight.update(player);
        check(player.getAbilities().mayfly, "powered gravitational module must grant flight");
        player.getAbilities().flying = true;
        CompoundTag playerSave = player.saveWithoutId(new CompoundTag());
        check(!playerSave.getCompound("abilities").getBoolean("mayfly") && !playerSave.getCompound("abilities").getBoolean("flying"),
              "temporary flight must not persist through saveAll before disconnect");
        check(player.getAbilities().mayfly && player.getAbilities().flying, "saving must not mutate live flight abilities");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        MekaSuitFlight.update(player);
        check(!player.getAbilities().mayfly && !player.getAbilities().flying, "revoking module must end both permission and active flying");
        player.setItemSlot(EquipmentSlot.CHEST, armor);
        MekaSuitFlight.update(player);
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        MekaSuitFlight.update(player);
        check(player.getAbilities().mayfly, "creative flight must survive removal of a previously owned module");
        player.setGameMode(GameType.SURVIVAL);
        MekaSuitFlight.release(player);
        pass("flight-grant-revoke-and-creative");
    }

    private static void chunkVersion(ServerLevel level) {
        int original = MekanismConfig.world.userGenVersion.get();
        try {
            MekanismConfig.world.userGenVersion.set(original + 1);
            LevelChunk chunk = level.getChunk(0, 0);
            chunk.setAttached(CommonWorldTickHandler.WORLD_GEN_VERSION, original);
            CompoundTag saved = ChunkSerializer.write(level, chunk);
            check(saved.getInt(SerializationConstants.WORLD_GEN_VERSION) == original, "saving before retrogen must retain the old root NBT version");
            saved.remove(SerializationConstants.WORLD_GEN_VERSION);
            ChunkAccess loaded = ChunkSerializer.read(level, level.getPoiManager(), new RegionStorageInfo("port_review", level.dimension(), "chunk"), chunk.getPos(), saved);
            if (loaded instanceof ImposterProtoChunk wrapped) loaded = wrapped.getWrapped();
            check(Integer.valueOf(0).equals(loaded.getAttached(CommonWorldTickHandler.WORLD_GEN_VERSION)), "legacy missing root field must load as version zero");
            chunk.setAttached(CommonWorldTickHandler.WORLD_GEN_VERSION, original + 1);
            check(ChunkSerializer.write(level, chunk).getInt(SerializationConstants.WORLD_GEN_VERSION) == original + 1, "completed retrogen version must be persisted explicitly");
        } finally {
            MekanismConfig.world.userGenVersion.set(original);
        }
        pass("chunk-version-root-nbt");
    }

    private static void prepareFluids(ServerLevel level) {
        for (BlockPos start : List.of(BRINE, WATER)) {
            for (int x = -1; x <= 3; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(start.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(start.offset(x, 0, z), x == -1 || x == 3 || z != 0 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(BRINE, MekanismFluids.BRINE.getBlock().defaultBlockState());
        level.setBlockAndUpdate(BRINE.east(2), MekanismFluids.BRINE.getBlock().defaultBlockState());
        level.setBlockAndUpdate(WATER, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(WATER.east(2), Blocks.WATER.defaultBlockState());
        var fluid = MekanismFluids.BRINE.get().defaultFluidState();
        check(fluid.getExplosionResistance() == 1, "official mod-fluid resistance must remain one");
        check(!fluid.canBeReplacedWith(level, BRINE, MekanismFluids.BRINE.getFlowingFluid().get(), Direction.DOWN), "same-family fluid must not replace itself");
        check(fluid.canBeReplacedWith(level, BRINE, Fluids.WATER, Direction.DOWN), "different downward fluid retains replacement behavior");
    }

    private static long suitEnergy(ServerPlayer player) {
        long total = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            IEnergyContainer energy = StorageUtils.getEnergyContainer(stack, 0);
            if (energy != null) total += energy.getEnergy();
        }
        return total;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("mekanism_port_review", path); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static void pass(String group) { passed++; LOGGER.info("PORT_REVIEW_PASS {}", group); }
    private static void result(String message) {
        try {
            Files.writeString(Path.of(System.getProperty("mekanism.portReview.result")), message + "\n");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
