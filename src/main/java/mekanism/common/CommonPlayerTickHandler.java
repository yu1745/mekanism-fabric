package mekanism.common;

import java.util.function.LongSupplier;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPITags;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.functions.FloatSupplier;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.math.MathUtils;
import mekanism.common.base.KeySync;
import mekanism.common.base.PlayerState;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.IBlastingItem;
import mekanism.common.content.gear.mekasuit.ModuleGravitationalModulatingUnit;
import mekanism.common.content.gear.mekasuit.ModuleHydraulicPropulsionUnit;
import mekanism.common.content.gear.mekasuit.MekaSuitFlight;
import mekanism.common.content.gear.mekasuit.ModuleLocomotiveBoostingUnit;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemScubaMask;
import mekanism.common.item.gear.ItemScubaTank;
import mekanism.common.item.interfaces.IFreeRunnerItem;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.lib.radiation.PlayerExposure;
import mekanism.common.registries.MekanismDamageTypes;
import mekanism.common.registries.MekanismGameEvents;
import mekanism.common.registries.MekanismModules;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class CommonPlayerTickHandler {

    public static boolean isOnGroundOrSleeping(Player player) {
        return player.onGround() || player.isSleeping() || player.getAbilities().flying;
    }

    public static boolean isScubaMaskOn(Player player, ItemStack tank) {
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        return !tank.isEmpty() && !mask.isEmpty() && tank.getItem() instanceof ItemScubaTank scubaTank &&
               mask.getItem() instanceof ItemScubaMask && ChemicalUtil.hasAnyChemical(tank) && scubaTank.getMode(tank);
    }

    public static float getStepBoost(Player player) {
        if (player.isShiftKeyDown()) {
            return 0;
        }
        ItemStack stack = player.getItemBySlot(EquipmentSlot.FEET);
        if (stack.isEmpty()) {
            return 0;
        }
        IModule<ModuleHydraulicPropulsionUnit> hydraulic = IModuleHelper.INSTANCE.getIfEnabled(stack, MekanismModules.HYDRAULIC_PROPULSION_UNIT);
        if (hydraulic != null) {
            return hydraulic.getCustomInstance().getStepHeight();
        }
        ItemStack primaryFreeRunners = IFreeRunnerItem.getPrimaryFreeRunners(player);
        if (!primaryFreeRunners.isEmpty() && ((IFreeRunnerItem) primaryFreeRunners.getItem()).getFreeRunnerMode(primaryFreeRunners).providesStepBoost()) {
            return 0.5F;
        }
        return 0;
    }

    /** Fabric port delta: the NeoForge player-tick event is replaced by the port's Player.tick mixin entry point. */
    public static void onPlayerTick(Player player) {
        //Note: Player's can't be frozen with the tick rate manager, so we don't have to check it here
        if (!player.level().isClientSide()) {
            tickEnd(player);
        }
    }

    private static void tickEnd(Player player) {
        PlayerState.INSTANCE.updateStepAssist(player);
        //Radiation is now owned by RadiationPlayerTickMixin at the actual native Player.tick tail.

        ItemStack jetpack = IJetpackItem.getActiveJetpack(player);
        if (!jetpack.isEmpty()) {
            ItemStack primaryJetpack = IJetpackItem.getPrimaryJetpack(player);
            if (!primaryJetpack.isEmpty()) {
                IJetpackItem jetpackItem = (IJetpackItem) primaryJetpack.getItem();
                JetpackMode primaryMode = jetpackItem.getJetpackMode(primaryJetpack);
                JetpackMode mode = IJetpackItem.getPlayerJetpackMode(player, primaryMode, p -> KeySync.INSTANCE.has(p.getUUID(), KeySync.ASCEND));
                if (mode != JetpackMode.DISABLED) {
                    double jetpackThrust = jetpackItem.getJetpackThrust(primaryJetpack);
                    if (IJetpackItem.handleJetpackMotion(player, mode, jetpackThrust, p -> KeySync.INSTANCE.has(p.getUUID(), KeySync.ASCEND))) {
                        player.resetFallDistance();
                        if (player instanceof ServerPlayer serverPlayer) {
                            serverPlayer.connection.aboveGroundTickCount = 0;
                        }
                    }
                    ((IJetpackItem) jetpack.getItem()).useJetpackFuel(jetpack);
                    if (player.level().getGameTime() % MekanismUtils.TICKS_PER_HALF_SECOND == 0) {
                        player.gameEvent(MekanismGameEvents.JETPACK_BURN);
                    }
                }
            }
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (isScubaMaskOn(player, chest)) {
            ItemScubaTank tank = (ItemScubaTank) chest.getItem();
            final int max = player.getMaxAirSupply();
            tank.useChemical(chest, 1);
            ChemicalStack received = tank.useChemical(chest, max - player.getAirSupply());
            if (!received.isEmpty()) {
                player.setAirSupply(player.getAirSupply() + (int) received.getAmount());
            }
            if (player.getAirSupply() == max) {
                for (MobEffectInstance effect : player.getActiveEffects()) {
                    if (MekanismUtils.shouldSpeedUpEffect(effect)) {
                        for (int i = 0; i < 9; i++) {
                            MekanismUtils.speedUpEffectSafely(player, effect);
                        }
                    }
                }
            }
        }
        //Fabric port delta: vanilla has no creative-flight attribute, so the gravitational unit's flight grant is maintained here
        MekaSuitFlight.update(player);
    }

    public static boolean isGravitationalModulationOn(Player player) {
        if (ModuleGravitationalModulatingUnit.shouldProcess(player)) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
            IModule<ModuleGravitationalModulatingUnit> module = IModuleHelper.INSTANCE.getIfEnabled(stack, MekanismModules.GRAVITATIONAL_MODULATING_UNIT);
            return module != null && module.hasEnoughEnergy(stack, MekanismConfig.gear.mekaSuitEnergyUsageGravitationalModulation);
        }
        return false;
    }

    /** Fabric port delta: called from the LivingEntity.die mixin instead of the NeoForge death event. */
    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (!entity.isOnFire() && !entity.fireImmune() && MekanismDamageTypes.FLAMETHROWER.is(source)) {
            //If they took damage from a flamethrower, set that they are on fire so that they drop cooked food
            entity.igniteForSeconds(1);
        }
    }

    /**
     * Fabric port delta: called from the LivingEntity.isInvulnerableTo mixin in place of the NeoForge invulnerability-check event;
     * returns whether this mod makes the entity invulnerable to the source.
     */
    public static boolean checkRadiationImmunity(LivingEntity entity, DamageSource source) {
        return MekanismDamageTypes.RADIATION.is(source) && entity.getType().is(MekanismAPITags.Entities.MEK_RADIATION_IMMUNE);
    }

    /**
     * Result of one incoming-damage reduction query. Cached per hurt call (the same thread computes it once, whether the
     * cancellation injector or the amount modifier reaches it first) so the armor energy is extracted exactly once.
     */
    public record DamageReduction(LivingEntity entity, DamageSource source, float original, float remaining, boolean cancel) {
    }

    private static final ThreadLocal<DamageReduction> DAMAGE_REDUCTION = new ThreadLocal<>();

    public static void clearIncomingDamage() {
        DAMAGE_REDUCTION.remove();
    }

    /**
     * Fabric port delta: replaces the NeoForge incoming-damage event. The damage-container rules are preserved: gas-mask
     * prevention cancels, and powered MekaSuit absorption reduces the raw amount (or cancels when fully absorbed).
     */
    public static DamageReduction reduceIncomingDamage(LivingEntity entity, DamageSource source, float damage) {
        DamageReduction cached = DAMAGE_REDUCTION.get();
        if (cached != null && cached.entity() == entity && cached.source() == source && cached.original() == damage) {
            return cached;
        }
        DamageReduction result = computeIncomingDamage(entity, source, damage);
        DAMAGE_REDUCTION.set(result);
        return result;
    }

    private static DamageReduction computeIncomingDamage(LivingEntity entity, DamageSource source, float damage) {
        if (damage <= 0 || !entity.isAlive()) {
            //If some mod does weird things and causes the damage value to be negative or zero then exit
            // as our logic assumes there is actually damage happening and can crash if someone tries
            // to use a negative number as the damage value. We also check to make sure that we don't do
            // anything if the entity is dead as living attack is still fired when the entity is dead
            // for things like fall damage if the entity dies before hitting the ground, and then energy
            // would be depleted regardless if keep inventory is on even if no damage was stopped as the
            // entity can't take damage while dead
            return new DamageReduction(entity, source, damage, damage, false);
        }
        //Gas Mask checks
        if (source.is(MekanismAPITags.DamageTypes.IS_PREVENTABLE_MAGIC)) {
            ItemStack headStack = entity.getItemBySlot(EquipmentSlot.HEAD);
            if (!headStack.isEmpty() && headStack.getItem() instanceof ItemScubaMask) {
                ItemStack chestStack = entity.getItemBySlot(EquipmentSlot.CHEST);
                if (!chestStack.isEmpty() && chestStack.getItem() instanceof ItemScubaTank tank && tank.getMode(chestStack) && ChemicalUtil.hasAnyChemical(chestStack)) {
                    return new DamageReduction(entity, source, damage, 0, true);
                }
            }
        }
        if (entity instanceof Player player) {
            float ratioAbsorbed = ItemMekaSuitArmor.getDamageAbsorbed(player, source, damage);
            if (ratioAbsorbed > 0) {
                float damageRemaining = damage * Math.max(0, 1 - ratioAbsorbed);
                if (damageRemaining <= 0) {
                    return new DamageReduction(entity, source, damage, 0, true);
                }
                return new DamageReduction(entity, source, damage, damageRemaining, false);
            }
        }
        return new DamageReduction(entity, source, damage, damage, false);
    }

    /**
     * Result of the fall-absorption query for one fall event. Cached per call so energy is extracted once even when both the
     * cancellation and distance injectors run.
     */
    public record FallResult(LivingEntity entity, float distance, float multiplicity, DamageSource source, float remainingDistance, boolean cancel) {
    }

    private static final ThreadLocal<FallResult> FALL_RESULT = new ThreadLocal<>();

    public static void clearFallDamage() {
        FALL_RESULT.remove();
    }

    /**
     * Fabric port delta: replaces the NeoForge living-fall event. A fully absorbed fall keeps the official behavior of playing the
     * landing step sound and cancelling the damage, while partial absorption returns the reduced fall distance.
     */
    public static FallResult reduceFallDamage(LivingEntity entity, float distance, float damageMultiplier, DamageSource source) {
        FallResult cached = FALL_RESULT.get();
        if (cached != null && cached.entity() == entity && cached.distance() == distance && cached.multiplicity() == damageMultiplier) {
            return cached;
        }
        FallResult result = computeFallDamage(entity, distance, damageMultiplier, source);
        FALL_RESULT.set(result);
        return result;
    }

    private static FallResult computeFallDamage(LivingEntity entity, float distance, float damageMultiplier, DamageSource source) {
        float safeFallDistance = (float) entity.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        float fallDistance = Math.max(distance - safeFallDistance, 0);
        if (fallDistance <= Mth.EPSILON) {
            return new FallResult(entity, distance, damageMultiplier, source, distance, false);
        }
        double multiplier = damageMultiplier * entity.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
        int fallDamage = Mth.ceil(fallDistance * multiplier);
        if (fallDamage <= 0) {//This may be the case for things like slime blocks that have a damage multiplier of zero
            return new FallResult(entity, distance, damageMultiplier, source, distance, false);
        }
        FallEnergyInfo info = getFallAbsorptionEnergyInfo(entity);
        if (info == null || info.container == null) {
            return new FallResult(entity, distance, damageMultiplier, source, distance, false);
        }
        float absorption = info.damageRatio.getAsFloat();
        float amount = fallDamage * absorption;
        long energyRequirement = MathUtils.ceilToLong(info.energyCost.getAsLong() * amount);
        float ratioAbsorbed;
        if (energyRequirement == 0L) {
            //No energy is actually needed to absorb the damage, either because of the config
            // or how small the amount to absorb is
            ratioAbsorbed = absorption;
        } else {
            long extracted = info.container.extract(energyRequirement, Action.EXECUTE, AutomationType.MANUAL);
            ratioAbsorbed = (float) (absorption * ((double) extracted / energyRequirement));
        }
        if (ratioAbsorbed <= 0) {
            return new FallResult(entity, distance, damageMultiplier, source, distance, false);
        }
        float damageRemaining = fallDamage * Math.max(0, 1 - ratioAbsorbed);
        if (damageRemaining <= Mth.EPSILON) {
            BlockPos posOn = entity.getOnPos();
            BlockState stateOn = entity.level().getBlockState(posOn);
            if (entity instanceof Player player) {
                player.playStepSound(posOn, stateOn);
            } else {
                //Fallback to default implementation
                //Fabric port delta: vanilla has no positional/entity-aware sound-type lookup (the NeoForge IBlockStateExtension
        // overload); the state sound type is the vanilla equivalent.
        SoundType soundtype = stateOn.getSoundType();
                entity.playSound(soundtype.getStepSound(), soundtype.getVolume() * 0.15F, soundtype.getPitch());
            }
            return new FallResult(entity, distance, damageMultiplier, source, 0, true);
        }
        float distanceRemaining = (float) (damageRemaining / multiplier);
        return new FallResult(entity, distance, damageMultiplier, source, distanceRemaining + safeFallDistance, false);
    }

    /** Fabric port delta: called from the LivingEntity.jumpFromGround mixin instead of the NeoForge living-jump event. */
    public static void onLivingJump(LivingEntity entity) {
        if (entity instanceof Player player) {
            ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
            IModule<ModuleHydraulicPropulsionUnit> propulsionModule = IModuleHelper.INSTANCE.getIfEnabled(boots, MekanismModules.HYDRAULIC_PROPULSION_UNIT);
            if (propulsionModule != null && KeySync.INSTANCE.has(player.getUUID(), KeySync.BOOST)) {
                float boost = propulsionModule.getCustomInstance().getBoost();
                long usage = MathUtils.ceilToLong(MekanismConfig.gear.mekaSuitBaseJumpEnergyUsage.get() * boost / 0.1F);
                if (propulsionModule.canUseEnergy(player, boots, usage)) {
                    // if we're sprinting with the boost module, limit the height
                    ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
                    IModule<ModuleLocomotiveBoostingUnit> boostModule = IModuleHelper.INSTANCE.getIfEnabled(legs, MekanismModules.LOCOMOTIVE_BOOSTING_UNIT);
                    if (boostModule != null && boostModule.getCustomInstance().canFunction(boostModule, legs, player)) {
                        boost = Mth.sqrt(boost);
                    }
                    player.addDeltaMovement(new Vec3(0, boost, 0));
                    propulsionModule.useEnergy(player, legs, usage, true);
                }
            }
        }
    }

    /**
     * @return null if free runners are not being worn, or they don't have an energy container for some reason
     */
    @Nullable
    private static FallEnergyInfo getFallAbsorptionEnergyInfo(LivingEntity base) {
        ItemStack feetStack = base.getItemBySlot(EquipmentSlot.FEET);
        if (!feetStack.isEmpty() && feetStack.getItem() instanceof ItemMekaSuitArmor) {
            return new FallEnergyInfo(StorageUtils.getEnergyContainer(feetStack, 0), MekanismConfig.gear.mekaSuitFallDamageRatio,
                  MekanismConfig.gear.mekaSuitEnergyUsageFall);
        }
        ItemStack freeRunners = IFreeRunnerItem.getActiveFreeRunners(base);
        if (!freeRunners.isEmpty()) {
            ItemStack primaryFreeRunners = IFreeRunnerItem.getPrimaryFreeRunners(base);
            if (!primaryFreeRunners.isEmpty() && ((IFreeRunnerItem) primaryFreeRunners.getItem()).getFreeRunnerMode(primaryFreeRunners).preventsFallDamage()) {
                return new FallEnergyInfo(((IFreeRunnerItem) freeRunners.getItem()).getRunnerEnergyContainer(freeRunners), MekanismConfig.gear.freeRunnerFallDamageRatio,
                      MekanismConfig.gear.freeRunnerFallEnergyCost);
            }
        }
        return null;
    }

    private record FallEnergyInfo(@Nullable IEnergyContainer container, FloatSupplier damageRatio, LongSupplier energyCost) {
    }

    /**
     * Fabric port delta: replaces the NeoForge break-speed event. The hook receives the vanilla destroy progress, which is linear in
     * the mining speed, so the official multiplicative adjustments (blasting hardest-block scaling and gyroscopic stabilization) are
     * applied to it unchanged; the position is always known here, so the blasting-tool scaling no longer depends on an optional
     * event position.
     */
    public static float modifyBreakProgress(Player player, BlockState state, BlockPos pos, float progress) {
        if (progress <= 0) {
            return progress;
        }
        float factor = 1;
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.getItem() instanceof IBlastingItem tool) {
            java.util.Map<BlockPos, BlockState> blocks = tool.getBlastedBlocks(player.level(), player, mainHand, pos, state);
            if (!blocks.isEmpty()) {
                // Scales mining speed based on hardest block
                // Does not take into account the tool check for those blocks or other mining speed changes that don't apply to the target block.
                float targetHardness = state.getDestroySpeed(player.level(), pos);
                float maxHardness = targetHardness;
                for (java.util.Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                    float destroySpeed = entry.getValue().getDestroySpeed(player.level(), entry.getKey());
                    maxHardness = Math.max(maxHardness, destroySpeed);
                }
                factor *= (targetHardness / maxHardness);
            }
        }
        //Gyroscopic stabilization check
        if (!player.onGround() && IModuleHelper.INSTANCE.isEnabled(player.getItemBySlot(EquipmentSlot.LEGS), MekanismModules.GYROSCOPIC_STABILIZATION_UNIT)) {
            factor *= 5.0F;
        }
        return progress * factor;
    }
}
