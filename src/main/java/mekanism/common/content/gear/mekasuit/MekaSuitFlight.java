package mekanism.common.content.gear.mekasuit;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric port delta: NeoForge exposes a {@code creative_flight} attribute that the gravitational modulating unit adds while the
 * chest armor is installed and powered; vanilla has no such attribute, so this owned seam grants and revokes
 * {@link net.minecraft.world.entity.player.Abilities#mayfly} instead. The decision is re-evaluated every server player tick:
 * flight is granted when the energized module is installed and revoked only when this seam was the one that granted it, so
 * creative/spectator flight and other mods' flight are never cleared.
 */
public final class MekaSuitFlight {

    private static final Set<UUID> GRANTED = new ObjectOpenHashSet<>();

    private MekaSuitFlight() {
    }

    public static void update(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        UUID uuid = player.getUUID();
        boolean provides = providesFlight(player);
        if (provides) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                GRANTED.add(uuid);
            }
        } else if (GRANTED.remove(uuid)) {
            //Only revoke flight this seam granted; vanilla stops active flight itself once mayfly is false
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
    }

    public static void clear() {
        GRANTED.clear();
    }

    private static boolean providesFlight(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty() || !chest.is(MekanismItems.MEKASUIT_BODYARMOR)) {
            return false;
        }
        IModule<ModuleGravitationalModulatingUnit> module = IModuleHelper.INSTANCE.getIfEnabled(chest, MekanismModules.GRAVITATIONAL_MODULATING_UNIT);
        return module != null && module.hasEnoughEnergy(chest, MekanismConfig.gear.mekaSuitEnergyUsageGravitationalModulation);
    }
}
