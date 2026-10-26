package mekanism.common.util;

import java.util.Collection;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.FluidStack;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.content.network.distribution.FluidSendTransaction;
import mekanism.fabric.client.FabricFluidColors;
import mekanism.fabric.fluid.ItemFluidContainer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FluidUtils {

    /** Vanilla-neutral millibucket-per-bucket volume (the official code used NeoForge's {@code FluidType.BUCKET_VOLUME}). */
    public static final int BUCKET_VOLUME = 1000;

    //Connection status (accurate, do not overstate): TileComponentEjector (FLUID branch), TileEntityFluidTank (DOWN output) and TileEntityElectricPump (UP output)
    // now perform direct per-operation FluidStorage.SIDED.find lookups on the physical opposite face and route their real-tank sends through the sender-owned
    // FluidSendTransaction overloads below; unloaded neighbors are skipped and no Storage is retained across ticks.
    // Still on the OLD lookups (explicitly open, NOT migrated): none for fluid pipes - MechanicalPipe now uses FluidAcceptorCache (fresh per-pipe
    // FluidStorage.SIDED.find, continuation08) and FluidNetwork uses the derived FluidNetworkAcceptorCache snapshot; pipe pulls and network sends both run
    // through the sender-owned FluidSendTransaction path. QE/InventoryFrequency collects fluid through QE's typed getFluidStorage getter (continuation7).
    // Energy/chemical acceptor caches and item routing remain on the OLD lookups (not approved in this substep).

    private FluidUtils() {
    }

    public static ItemStack getFilledVariant(Holder<Item> toFill, Holder<Fluid> fluid) {
        return getFilledVariant(new ItemStack(toFill), fluid);
    }

    public static ItemStack getFilledVariant(ItemStack toFill, Holder<Fluid> fluid) {
        IMekanismFluidHandler attachment = ContainerType.FLUID.createHandler(toFill);
        if (attachment != null) {
            for (IExtendedFluidTank fluidTank : attachment.getFluidTanks(null)) {
                fluidTank.setStack(new FluidStack(fluid, fluidTank.getCapacity()));
            }
        }
        //The item is now filled return it for convenience
        return toFill;
    }

    public static int getRGBDurabilityForDisplay(ItemStack stack) {
        return getRGBDurabilityForDisplay(StorageUtils.getFirstFluidFromAttachment(stack));
    }

    public static int getRGBDurabilityForDisplay(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0xFFFFFFFF;
        }
        //TODO: Technically doesn't support things where the color is part of the texture such as lava
        // for chemicals it is supported via allowing people to override getColorRepresentation in their
        // chemicals
        if (stack.getFluid().isSame(Fluids.LAVA)) {//Special case lava
            return 0xFFDB6B19;
        } else if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            //Note: We can only return an accurate result on the client side. This method should never be called from the server
            // but in case it is make sure we only run on the client side
            //Fabric port delta: the registered render handler replaces the NeoForge client fluid-type extension
            int tint = FabricFluidColors.getTintColor(stack);
            return tint == FabricFluidColors.NO_TINT ? 0xFFFFFFFF : tint;
        }
        return 0xFFFFFFFF;
    }

    public static void emit(Collection<Storage<FluidVariant>> targets, IExtendedFluidTank tank) {
        emit(targets, tank, tank.getCapacity());
    }

    public static void emit(Collection<Storage<FluidVariant>> targets, IExtendedFluidTank tank, int maxOutput) {
        //Sender-owned transaction: snapshot tank, distribute fairly over real Fabric storages, debit exactly, commit atomically
        FluidSendTransaction.send(targets, tank, maxOutput);
    }

    /**
     * Emits a caller-owned offered stack from a central block by splitting it among the sides given.
     *
     * @param targets - the storages to output to
     * @param stack   - the stack to output; caller-owned, so it is NOT snapshotted or debited here
     *
     * @return the amount of fluid emitted; the caller must consume exactly this amount from its actual source
     */
    public static int emit(Collection<Storage<FluidVariant>> targets, @NotNull FluidStack stack) {
        return FluidSendTransaction.send(targets, stack);
    }

    /**
     * Fabric port delta: the official NeoForge {@code IFluidHandlerItem} flow is expressed through the port's
     * {@link ItemFluidContainer} detached-item seam, which owns the Fabric {@code FluidStorage.ITEM} lookup and the
     * simulate/execute transaction split. The tank interaction order, creative-mode simulation, and container
     * replacement/shrink/drop rules are unchanged.
     */
    public static boolean handleTankInteraction(Player player, InteractionHand hand, ItemStack itemStack, IExtendedFluidTank fluidTank) {
        ItemStack copyStack = itemStack.copyWithCount(1);
        ItemFluidContainer container = ItemFluidContainer.find(copyStack);
        if (container == null) {
            //If the stack doesn't have a fluid storage just exit. There may be cases like our fluid tank where it will have one
            // if the stack size is one, but not when the stack size is greater
            return false;
        }
        FluidStack fluidInItem;
        if (fluidTank.isEmpty()) {
            //If we don't have a fluid stored try draining in general
            fluidInItem = container.extract(Integer.MAX_VALUE, Action.SIMULATE);
        } else {
            //Otherwise, try draining the same type of fluid we have stored
            // We do this to better support multiple tanks in case the fluid we have stored could pull out of a block's
            // second tank but just asking to drain a specific amount
            fluidInItem = container.extract(fluidTank.getFluid().copyWithAmount(Integer.MAX_VALUE), Action.SIMULATE);
        }
        if (fluidInItem.isEmpty()) {
            if (!fluidTank.isEmpty()) {
                int filled = container.insert(fluidTank.getFluid().copy(), player.isCreative() ? Action.SIMULATE : Action.EXECUTE);
                ItemStack containerStack = container.getContainer();
                if (filled > 0) {
                    if (itemStack.getCount() == 1) {
                        player.setItemInHand(hand, containerStack);
                    } else if (itemStack.getCount() > 1 && player.getInventory().add(containerStack)) {
                        itemStack.shrink(1);
                    } else {
                        player.drop(containerStack, false, true);
                        itemStack.shrink(1);
                    }
                    fluidTank.extract(filled, Action.EXECUTE, AutomationType.MANUAL);
                    return true;
                }
            }
        } else {
            FluidStack simulatedRemainder = fluidTank.insert(fluidInItem, Action.SIMULATE, AutomationType.MANUAL);
            int remainder = simulatedRemainder.getAmount();
            int storedAmount = fluidInItem.getAmount();
            if (remainder < storedAmount) {
                boolean filled = false;
                FluidStack drained = container.extract(fluidInItem.copyWithAmount(storedAmount - remainder),
                      player.isCreative() ? Action.SIMULATE : Action.EXECUTE);
                if (!drained.isEmpty()) {
                    ItemStack containerStack = container.getContainer();
                    if (player.isCreative()) {
                        filled = true;
                    } else if (!containerStack.isEmpty()) {
                        if (itemStack.getCount() == 1) {
                            player.setItemInHand(hand, containerStack);
                            filled = true;
                        } else if (player.getInventory().add(containerStack)) {
                            itemStack.shrink(1);
                            filled = true;
                        }
                    } else {
                        itemStack.shrink(1);
                        if (itemStack.isEmpty()) {
                            player.setItemInHand(hand, ItemStack.EMPTY);
                        }
                        filled = true;
                    }
                    if (filled) {
                        fluidTank.insert(drained, Action.EXECUTE, AutomationType.MANUAL);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
