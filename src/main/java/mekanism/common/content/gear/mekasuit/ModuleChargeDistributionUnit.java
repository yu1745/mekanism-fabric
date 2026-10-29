package mekanism.common.content.gear.mekasuit;

import java.util.function.Consumer;
import mekanism.api.AutomationType;
import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.content.network.distribution.EnergySaveTarget;
import mekanism.common.content.network.distribution.EnergySaveTarget.DelegateSaveHandler;
import mekanism.common.integration.curios.CuriosIntegration;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.StorageUtils;
import mekanism.fabric.energy.ItemEnergyContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@ParametersAreNotNullByDefault
public record ModuleChargeDistributionUnit(boolean chargeSuit, boolean chargeInventory) implements ICustomModule<ModuleChargeDistributionUnit> {

    public static final ResourceLocation CHARGE_SUIT = ResourceLocation.fromNamespaceAndPath("mekanism", "charge_suit");
    public static final ResourceLocation CHARGE_INVENTORY = ResourceLocation.fromNamespaceAndPath("mekanism", "charge_inventory");

    public ModuleChargeDistributionUnit(IModule<ModuleChargeDistributionUnit> module) {
        this(module.getBooleanConfigOrFalse(CHARGE_SUIT), module.getBooleanConfigOrFalse(CHARGE_INVENTORY));
    }

    @Override
    public void tickServer(IModule<ModuleChargeDistributionUnit> module, IModuleContainer moduleContainer, ItemStack stack, Player player) {
        // charge inventory first
        if (chargeInventory) {
            IEnergyContainer energyContainer = module.getEnergyContainer(stack);
            if (energyContainer != null) {
                chargeInventory(energyContainer, player);
            }
        }
        // distribute suit charge next, so that if we used power from the suit to charge an item, then we can balance across the suit properly
        if (chargeSuit) {
            chargeSuit(player);
        }
    }

    private void chargeSuit(Player player) {
        EnergySaveTarget<DelegateSaveHandler> saveTarget = new EnergySaveTarget<>(4);
        for (ItemStack stack : player.getArmorSlots()) {
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(stack, 0);
            if (energyContainer != null) {
                saveTarget.addHandler(new DelegateSaveHandler(energyContainer));
            }
        }
        if (saveTarget.getHandlerCount() > 1) {
            //If we only have one handler we can skip charging as it will all just go back into the chest piece
            long stored = saveTarget.getStored();
            EmitUtils.sendToAcceptors(saveTarget, stored, EnergyNetwork.ENERGY);
            saveTarget.save();
        }
    }

    private void chargeInventory(IEnergyContainer energyContainer, Player player) {
        //Only try to charge up to how much energy we actually have stored
        long toCharge = Math.min(MekanismConfig.gear.mekaSuitInventoryChargeRate.get(), energyContainer.getEnergy());
        if (toCharge == 0L) {
            return;
        }
        // first try to charge mainhand/offhand item
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        toCharge = charge(energyContainer, mainHand, toCharge, result -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
            player.getInventory().setChanged();
        });
        toCharge = charge(energyContainer, offHand, toCharge, result -> {
            player.setItemInHand(InteractionHand.OFF_HAND, result);
            player.getInventory().setChanged();
        });
        if (toCharge > 0L) {
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                ItemStack stack = player.getInventory().items.get(slot);
                if (slot != player.getInventory().selected) {
                    int ownedSlot = slot;
                    toCharge = charge(energyContainer, stack, toCharge, result -> {
                        player.getInventory().setItem(ownedSlot, result);
                        player.getInventory().setChanged();
                    });
                    if (toCharge == 0L) {
                        return;
                    }
                }
            }
            if (CuriosIntegration.isLoaded()) {
                for (CuriosIntegration.EquippedSlot slot : CuriosIntegration.getEquippedSlots(player)) {
                    toCharge = charge(energyContainer, slot.stack(), toCharge,
                          result -> CuriosIntegration.setSlotStack(player, slot.slotType(), slot.index(), result));
                    if (toCharge == 0L) {
                        return;
                    }
                }
            }
        }
    }

    private long charge(IEnergyContainer energyContainer, ItemStack stack, long amount, Consumer<ItemStack> install) {
        ItemEnergyContainer itemEnergy = ItemEnergyContainer.find(stack);
        if (itemEnergy == null) {
            return amount;
        }
        long moved = itemEnergy.transfer(energyContainer, false, amount, AutomationType.MANUAL);
        if (moved > 0) {
            install.accept(itemEnergy.getContainer());
        }
        return amount - moved;
    }
}