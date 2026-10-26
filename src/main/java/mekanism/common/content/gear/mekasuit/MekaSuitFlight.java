package mekanism.common.content.gear.mekasuit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta: NeoForge exposes a {@code creative_flight} attribute that the gravitational modulating unit adds while the
 * chest armor is installed and powered; vanilla has no such attribute, so this owned seam grants and revokes
 * {@link net.minecraft.world.entity.player.Abilities#mayfly} instead. The decision is re-evaluated every server player tick:
 * flight is granted when the energized module is installed and revoked only when this seam was the one that granted it, so
 * creative/spectator flight is preserved. Other providers must compose through their own permission lifecycle; vanilla has no grant tokens.
 */
public final class MekaSuitFlight {

    private static final Set<Player> GRANTED = Collections.newSetFromMap(new IdentityHashMap<>());

    private MekaSuitFlight() {
    }

    public static void update(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            GRANTED.remove(player);
            return;
        }
        boolean provides = providesFlight(player);
        if (provides) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                GRANTED.add(player);
            }
        } else if (GRANTED.remove(player)) {
            //Revoke both abilities together, just as a vanilla game-mode change does.
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    /** Drop an outgoing entity's permission before its abilities can be saved or copied to a new player. */
    public static void release(Player player) {
        if (GRANTED.remove(player) && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
    }

    public static void respawn(Player oldPlayer, Player newPlayer) {
        if (GRANTED.contains(oldPlayer)) {
            release(oldPlayer);
            if (!newPlayer.isCreative() && !newPlayer.isSpectator()) {
                newPlayer.getAbilities().mayfly = false;
                newPlayer.getAbilities().flying = false;
                update(newPlayer);
                newPlayer.onUpdateAbilities();
            }
        }
    }

    /** Temporary module permission must not outlive its owner through a player save, including saveAll before disconnect. */
    public static void filterSavedAbilities(Player player, CompoundTag playerTag) {
        if (!player.level().isClientSide() && GRANTED.contains(player) && !player.isCreative() && !player.isSpectator()) {
            CompoundTag abilities = playerTag.getCompound("abilities");
            abilities.putBoolean("mayfly", false);
            abilities.putBoolean("flying", false);
        }
    }

    public static void clear() {
        GRANTED.clear();
    }

    private static boolean providesFlight(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !chest.is(MekanismItems.MEKASUIT_BODYARMOR.get())) {
            return false;
        }
        IModule<ModuleGravitationalModulatingUnit> module = IModuleHelper.INSTANCE.getIfEnabled(chest, MekanismModules.GRAVITATIONAL_MODULATING_UNIT);
        return module != null && module.hasEnoughEnergy(chest, MekanismConfig.gear.mekaSuitEnergyUsageGravitationalModulation);
    }
}
