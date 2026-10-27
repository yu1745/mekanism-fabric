package mekanism.client;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.radial.RadialData;
import mekanism.client.gui.GuiRadialSelector;
import mekanism.client.key.MekKeyHandler;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.hud.MekanismStatusOverlay;
import mekanism.client.render.lib.ScrollIncrementer;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.gear.mekasuit.ModuleVisionEnhancementUnit;
import mekanism.common.item.gear.ItemHDPEElytra;
import mekanism.client.sound.GeigerSound;
import mekanism.client.sound.SoundHandler;
import mekanism.common.base.holiday.HolidayManager;
import mekanism.common.base.PlayerState;
import mekanism.common.base.KeySync;
import mekanism.common.item.gear.ItemJetpack;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.item.gear.ItemScubaTank;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import mekanism.common.item.interfaces.IModeItem;
import mekanism.common.lib.frequency.Frequency.FrequencyIdentity;
import mekanism.common.lib.radial.IGenericRadialModeItem;
import mekanism.common.lib.radiation.ClientRadiation;
import mekanism.common.lib.radiation.RadiationScale;
import mekanism.common.network.PacketUtils;
import mekanism.common.network.to_server.PacketModeChange;
import mekanism.common.network.to_server.PacketPortableTeleporterTeleport;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismModules;
import mekanism.common.util.MekanismUtils;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmorStandModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FogType;

/**
 * Client-side tick handler for Mekanism. Used mainly for the update check upon startup.
 *
 * @author AidanBrady
 */
public class ClientTickHandler {

    /** Fabric port: the platform registers and drives this single instance (NeoForge instantiated it via the bus). */
    public static final ClientTickHandler INSTANCE = new ClientTickHandler();

    private static final Minecraft minecraft = Minecraft.getInstance();
    private static final RandomSource rand = RandomSource.create();
    private static final Map<Player, TeleportData> portableTeleports = new Object2ObjectArrayMap<>(1);
    private static final ScrollIncrementer scrollIncrementer = new ScrollIncrementer(true);

    private static boolean visionEnhancement = false;

    public static void reset() {
        portableTeleports.clear();
        visionEnhancement = false;
    }

    public static boolean isJetpackInUse(Player player, ItemStack jetpack) {
        if (!player.isSpectator() && !jetpack.isEmpty()) {
            JetpackMode mode = ((IJetpackItem) jetpack.getItem()).getJetpackMode(jetpack);
            boolean guiOpen = minecraft.screen != null;
            boolean ascending = minecraft.player.input.jumping;
            boolean rising = ascending && !guiOpen;
            if (mode == JetpackMode.NORMAL || mode == JetpackMode.VECTOR) {
                return rising;
            } else if (mode == JetpackMode.HOVER) {
                boolean descending = minecraft.player.input.shiftKeyDown;
                if (!rising || descending) {
                    return !CommonPlayerTickHandler.isOnGroundOrSleeping(player);
                }
                return true;
            }
        }
        return false;
    }

    public static boolean isScubaMaskOn(Player player) {
        if (player != minecraft.player) {
            return PlayerState.INSTANCE.isScubaMaskOn(player);
        }
        return CommonPlayerTickHandler.isScubaMaskOn(player, player.getItemBySlot(EquipmentSlot.CHEST));
    }

    public static boolean isGravitationalModulationOn(Player player) {
        if (player != minecraft.player) {
            return PlayerState.INSTANCE.isGravitationalModulationOn(player);
        }
        return CommonPlayerTickHandler.isGravitationalModulationOn(player);
    }

    public static boolean isVisionEnhancementOn(Player player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!player.getCooldowns().isOnCooldown(head.getItem())) {
            IModuleContainer container = IModuleHelper.INSTANCE.getModuleContainer(head);
            if (container != null) {
                IModule<ModuleVisionEnhancementUnit> module = container.getIfEnabled(MekanismModules.VISION_ENHANCEMENT_UNIT);
                return module != null && module.hasEnoughEnergy(head, MekanismConfig.gear.mekaSuitEnergyUsageVisionEnhancement);
            }
        }
        return false;
    }

    public static void portableTeleport(Player player, InteractionHand hand, FrequencyIdentity identity) {
        //TODO - 1.21: Handle and validate the delay takes place server side
        int delay = MekanismConfig.gear.portableTeleporterDelay.get();
        if (delay == 0) {
            PacketUtils.sendToServer(new PacketPortableTeleporterTeleport(hand, identity));
        } else {
            portableTeleports.put(player, new TeleportData(hand, identity, minecraft.level.getGameTime() + delay));
        }
    }

    //Fabric port: the connection lifecycle handlers moved to the fabric bootstrap events (JOIN/DISCONNECT/world
    //change) and the entity-tracking handler to ClientEntityEvents.ENTITY_LOAD - all three wired.

    public void onTick() {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean tickingNormally = MekanismRenderer.isRunningNormally();
        //Holiday greetings once a day, refreshed every 1_200 ticks (the official notify cadence).
        if (!initHoliday || MekanismClient.ticksPassed % 1_200 == 0) {
            HolidayManager.notify(minecraft.player);
            initHoliday = true;
        }
        //SoundHandler.restartSounds belongs to the sound lane (flamethrower/geiger sounds) and stays deferred with it.
        //Radiation pre-tick particles are owned by FabricRadiationClientLifecycle.

        MekanismClient.ticksPassed++;

        UUID playerUUID = minecraft.player.getUUID();
        // Update player's state for various items; this also automatically notifies server if something changed and
        // kicks off sounds as necessary
        ItemStack jetpack = IJetpackItem.getActiveJetpack(minecraft.player);
        boolean jetpackInUse = isJetpackInUse(minecraft.player, jetpack);
        //Mekanism.playerState resolves to the admitted base.PlayerState singleton on Fabric (Mekanism.rl precedent).
        PlayerState.INSTANCE.setJetpackState(playerUUID, jetpackInUse, true);
        PlayerState.INSTANCE.setScubaMaskState(playerUUID, isScubaMaskOn(minecraft.player), true);
        PlayerState.INSTANCE.setGravitationalModulationState(playerUUID, isGravitationalModulationOn(minecraft.player), true);

        if (tickingNormally) {
            //Note: If we aren't ticking normally we can skip adding the particles or checking if the time matches as we know the time hasn't changed
            //Reboot player sounds if needed (the holiday notify above stays deferred with the holiday lane)
            SoundHandler.restartSounds();
            for (Iterator<Entry<Player, TeleportData>> iter = portableTeleports.entrySet().iterator(); iter.hasNext(); ) {
                Entry<Player, TeleportData> entry = iter.next();
                Player player = entry.getKey();
                for (int i = 0; i < 100; i++) {
                    double x = player.getX() + rand.nextDouble() - 0.5D;
                    double y = player.getY() + rand.nextDouble() * 2 - 2D;
                    double z = player.getZ() + rand.nextDouble() - 0.5D;
                    minecraft.level.addParticle(ParticleTypes.PORTAL, x, y, z, 0, 1, 0);
                }
                TeleportData data = entry.getValue();
                if (minecraft.level.getGameTime() == data.teleportTime) {
                    PacketUtils.sendToServer(new PacketPortableTeleporterTeleport(data.hand, data.identity));
                    iter.remove();
                }
            }
        }

        if (!jetpack.isEmpty()) {
            ItemStack primaryJetpack = IJetpackItem.getPrimaryJetpack(minecraft.player);
            if (!primaryJetpack.isEmpty()) {
                JetpackMode primaryMode = ((IJetpackItem) primaryJetpack.getItem()).getJetpackMode(primaryJetpack);
                JetpackMode mode = IJetpackItem.getPlayerJetpackMode(minecraft.player, primaryMode, p -> p.input.jumping);
                MekanismClient.updateKey(minecraft.player.input.jumping, KeySync.ASCEND);
                double jetpackThrust = ((IJetpackItem) primaryJetpack.getItem()).getJetpackThrust(primaryJetpack);
                if (jetpackInUse && IJetpackItem.handleJetpackMotion(minecraft.player, mode, jetpackThrust, p -> p.input.jumping)) {
                    minecraft.player.resetFallDistance();
                }
            }
        }

        if (isScubaMaskOn(minecraft.player) && minecraft.player.getAirSupply() == minecraft.player.getMaxAirSupply()) {
            for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
                if (MekanismUtils.shouldSpeedUpEffect(effect)) {
                    for (int i = 0; i < 9; i++) {
                        MekanismUtils.speedUpEffectSafely(minecraft.player, effect);
                    }
                }
            }
        }

        if (isVisionEnhancementOn(minecraft.player)) {
            visionEnhancement = true;
            // adds if it doesn't exist, otherwise tops off duration to 220. equal or less than 200 will make vision flickers
            minecraft.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 11 * SharedConstants.TICKS_PER_SECOND, 0, false, false, false));
        } else if (visionEnhancement) {
            visionEnhancement = false;
            MobEffectInstance effect = minecraft.player.getEffect(MobEffects.NIGHT_VISION);
            if (effect != null && effect.getDuration() <= 11 * SharedConstants.TICKS_PER_SECOND) {
                //Only remove it if it is our effect and not one that has a longer remaining duration
                minecraft.player.removeEffect(MobEffects.NIGHT_VISION);
            }
        }

        if (minecraft.screen == null || minecraft.screen instanceof GuiRadialSelector) {
            if (!MekKeyHandler.isRadialPressed() || (!updateSelectorRenderer(EquipmentSlot.MAINHAND) && !updateSelectorRenderer(EquipmentSlot.OFFHAND))) {
                if (minecraft.screen != null) {
                    //If we currently have a radial selector gui open but shouldn't close it
                    minecraft.setScreen(null);
                }
            }
        }

        if (MekanismConfig.client.enablePlayerSounds.get()) {
            RadiationScale scale = ClientRadiation.getClientScale();
            if (scale != RadiationScale.NONE && !SoundHandler.radiationSoundMap.containsKey(scale)) {
                GeigerSound sound = GeigerSound.create(minecraft.player, scale);
                SoundHandler.radiationSoundMap.put(scale, sound);
                SoundHandler.playSound(sound);
            }
        }
    }

    private boolean initHoliday;

    private boolean updateSelectorRenderer(EquipmentSlot slot) {
        if (minecraft.player != null) {
            ItemStack stack = minecraft.player.getItemBySlot(slot);
            if (stack.getItem() instanceof IGenericRadialModeItem item) {
                RadialData<?> radialData = item.getRadialData(stack);
                if (radialData != null) {
                    if (!(minecraft.screen instanceof GuiRadialSelector screen) || !screen.hasMatchingData(slot, radialData)) {
                        GuiRadialSelector newSelector = new GuiRadialSelector(slot, radialData, () -> minecraft.player);
                        newSelector.tryInheritCurrentPath(minecraft.screen);
                        minecraft.setScreen(newSelector);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** Fabric port: returns whether the scroll was consumed as a mode change (the caller cancels vanilla scrolling). */
    public boolean onMouseEvent(double scrollDeltaY) {
        if (MekanismConfig.client.allowModeScroll.get() && minecraft.player != null && minecraft.player.isShiftKeyDown()) {
            if (scrollDeltaY != 0 && IModeItem.isModeItem(minecraft.player, EquipmentSlot.MAINHAND)) {
                int shift = scrollIncrementer.scroll(scrollDeltaY);
                if (shift != 0) {
                    MekanismStatusOverlay.INSTANCE.setTimer();
                    PacketUtils.sendToServer(new PacketModeChange(EquipmentSlot.MAINHAND, shift));
                }
                return true;
            }
        }
        return false;
    }

    /** Fabric port: folds the official ComputeFogColor tint into the FogRenderer's static color fields. */
    public static void onFogLighting() {
        if (visionEnhancement) {
            float oldRatio = 0.1F;
            float newRatio = 1 - oldRatio;
            FogRenderer.fogRed = oldRatio * FogRenderer.fogRed + newRatio * 0.4F;
            FogRenderer.fogGreen = oldRatio * FogRenderer.fogGreen + newRatio * 0.8F;
            FogRenderer.fogBlue = oldRatio * FogRenderer.fogBlue + newRatio * 0.4F;
        }
    }

    /** Fabric port: the official RenderFog near-plane override (-8.0F, the spectator lava/snow near plane). */
    public static float onFogStart(float vanillaStart) {
        return activeVisionModule() != null ? -8.0F : vanillaStart;
    }

    /** Fabric port: the official RenderFog far-plane transform and the per-module distance scaling. */
    public static float onFogEnd(float farPlaneDistance) {
        IModule<ModuleVisionEnhancementUnit> module = activeVisionModule();
        if (module == null) {
            return farPlaneDistance;
        }
        float end = farPlaneDistance;
        if (end < 20) {
            float scalar;
            if (isLavaFog()) {
                //Special handling for lava which is usually either at 1 or 3
                scalar = 24 * end;
            } else {
                //Shortly before 27 this ends up being 192, but we want to get it beforehand, so we just allow numbers below 20
                scalar = 5 + 2.5F * (float) Math.pow(Math.E, 0.16F * end);
            }
            //192 is roughly equivalent to what spectators have lava, powdered snow, and a couple other bounds for fog are,
            // so we want to make sure we don't go above that
            end = Math.min(192, scalar);
        }
        //Scale the distance based on the number of installed modules
        return end * (((float) Math.pow(module.getInstalledCount(), 1.25)) / module.getUntypedData().getMaxStackSize());
    }

    private static IModule<ModuleVisionEnhancementUnit> activeVisionModule() {
        if (visionEnhancement && Minecraft.getInstance().getCameraEntity() instanceof Player player) {
            return IModuleHelper.INSTANCE.getIfEnabled(player, EquipmentSlot.HEAD, MekanismModules.VISION_ENHANCEMENT_UNIT);
        }
        return null;
    }

    private static boolean isLavaFog() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() == FogType.LAVA;
    }

    /** Fabric port: rides the recipe-sync packet instead of the NeoForge event (same note applies). */
    public static void onRecipesUpdated() {
        //Note: Dedicated servers first connection the server sends recipes then tags, and on reload sends tags then recipes.
        // We ignore this fact and only clear the cache in the recipes updated event however, as the cache should already be
        // empty on our initial connection, and even if it isn't the client has no way to query the recipes and cause the
        // caches to be initialized before the tags are then received as we lazily initialize our recipe caches.
        MekanismRecipeType.clearCache();
    }

    /** Fabric port: the official RenderLivingEvent.Pre handler, driven by the living-renderer mixin. */
    public static void onRenderEntityPre(LivingEntity entity, EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> humanoidModel) {
            //If the entity has a biped model, then see if it is wearing a meka suit, in which case we want to hide various parts of the model
            setModelVisibility(entity, humanoidModel, false);
        }
    }

    /** Fabric port: the official RenderLivingEvent.Post handler, driven by the living-renderer mixin. */
    public static void onRenderEntityPost(LivingEntity entity, EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> humanoidModel) {
            //Undo model visibility changes we made to ensure that other entities of the same type are properly visible
            setModelVisibility(entity, humanoidModel, true);
        }
    }

    private static void setModelVisibility(LivingEntity entity, HumanoidModel<?> entityModel, boolean showModel) {
        if (entity.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ItemMekaSuitArmor) {
            entityModel.head.visible = showModel;
            entityModel.hat.visible = showModel;
            if (entityModel instanceof PlayerModel<?> playerModel) {
                playerModel.ear.visible = showModel;
            }
        }
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof ItemMekaSuitArmor) {
            entityModel.body.visible = showModel;
            if (!(entity instanceof ArmorStand)) {
                //Don't adjust arms for armor stands as the model will end up changing them anyway and then we may incorrectly activate them
                entityModel.leftArm.visible = showModel;
                entityModel.rightArm.visible = showModel;
            }
            if (entityModel instanceof PlayerModel<?> playerModel) {
                playerModel.cloak.visible = showModel;
                playerModel.jacket.visible = showModel;
                playerModel.leftSleeve.visible = showModel;
                playerModel.rightSleeve.visible = showModel;
            } else if (entityModel instanceof ArmorStandModel armorStandModel) {
                armorStandModel.rightBodyStick.visible = showModel;
                armorStandModel.leftBodyStick.visible = showModel;
                armorStandModel.shoulderStick.visible = showModel;
            }
        } else if (itemHidesCape(chest.getItem()) && entityModel instanceof PlayerModel<?> playerModel) {
            //Hide the player's cape if they have an HDPE elytra as it will be part of the elytra's layer and shouldn't be rendered
            playerModel.cloak.visible = showModel;
        }
        if (entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof ItemMekaSuitArmor) {
            entityModel.leftLeg.visible = showModel;
            entityModel.rightLeg.visible = showModel;
            if (entityModel instanceof PlayerModel<?> playerModel) {
                playerModel.leftPants.visible = showModel;
                playerModel.rightPants.visible = showModel;
            }
        }
    }

    private static boolean itemHidesCape(Item item) {
        return item instanceof ItemHDPEElytra || item instanceof ItemJetpack || item instanceof ItemScubaTank;
    }

    private record TeleportData(InteractionHand hand, FrequencyIdentity identity, long teleportTime) {
    }
}