package mekanism.common.util;

import java.util.Collection;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.distribution.FluidSendTransaction;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import mekanism.api.fluid.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FluidUtils {

    //REVIEW_05 connection status (accurate, do not overstate): the sender-owned FluidSendTransaction overloads exist and the real-tank
    // emit(targets, tank, maxOutput) overload routes through them, but the immediate cache-owning callers of the LOOKUP side are NOT yet
    // migrated to the accepted Fabric lookup/cache identities: TileComponentEjector, TileEntityFluidTank and ElectricPump still build
    // their target collections from the old caches, InventoryFrequency gets external handlers from QE's old capability lookup, and
    // MechanicalPipe.createAcceptorCache still registers Capabilities.FLUID.block(). Connecting those needs accepted Fabric lookup
    // identities and physical side lifecycle — not casts or reverse NeoForge facades — and remains open WIP, not a completed migration.

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
        } else if (FMLEnvironment.dist.isClient()) {
            //Note: We can only return an accurate result on the client side. This method should never be called from the server
            // but in case it is make sure we only run on the client side
            return IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
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

    public static boolean handleTankInteraction(Player player, InteractionHand hand, ItemStack itemStack, IExtendedFluidTank fluidTank) {
        if (Capabilities.FLUID.getCapability(itemStack) == null) {
            //If the stack doesn't have a capability just exit. There may be cases like our fluid tank where it will have a capability
            // if the stack size is one, but not when the stack size is greater
            return false;
        }
        ItemStack copyStack = itemStack.copyWithCount(1);
        IFluidHandlerItem handler = Capabilities.FLUID.getCapability(copyStack);
        if (handler != null) {
            FluidStack fluidInItem;
            if (fluidTank.isEmpty()) {
                //If we don't have a fluid stored try draining in general
                fluidInItem = handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE);
            } else {
                //Otherwise, try draining the same type of fluid we have stored
                // We do this to better support multiple tanks in case the fluid we have stored we could pull out of a block's
                // second tank but just asking to drain a specific amount
                fluidInItem = handler.drain(fluidTank.getFluid().copyWithAmount(Integer.MAX_VALUE), FluidAction.SIMULATE);
            }
            if (fluidInItem.isEmpty()) {
                if (!fluidTank.isEmpty()) {
                    int filled = handler.fill(fluidTank.getFluid().copy(), player.isCreative() ? FluidAction.SIMULATE : FluidAction.EXECUTE);
                    ItemStack container = handler.getContainer();
                    if (filled > 0) {
                        if (itemStack.getCount() == 1) {
                            player.setItemInHand(hand, container);
                        } else if (itemStack.getCount() > 1 && player.getInventory().add(container)) {
                            itemStack.shrink(1);
                        } else {
                            player.drop(container, false, true);
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
                    FluidStack drained = handler.drain(fluidInItem.copyWithAmount(storedAmount - remainder), player.isCreative() ? FluidAction.SIMULATE : FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        ItemStack container = handler.getContainer();
                        if (player.isCreative()) {
                            filled = true;
                        } else if (!container.isEmpty()) {
                            if (itemStack.getCount() == 1) {
                                player.setItemInHand(hand, container);
                                filled = true;
                            } else if (player.getInventory().add(container)) {
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
        }
        return false;
    }
}