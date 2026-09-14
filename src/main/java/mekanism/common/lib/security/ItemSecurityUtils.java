package mekanism.common.lib.security;

import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.functions.TriConsumer;
import mekanism.api.security.IItemSecurityUtils;
import mekanism.api.security.IOwnerObject;
import mekanism.api.security.ISecurityObject;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.network.to_client.security.PacketSyncSecurity;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.text.OwnerDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.lookup.v1.item.ItemApiLookup;
import mekanism.fabric.network.FabricSecurityNetworking;

/**
 * @apiNote Do not instantiate this class directly as it will be done via the service loader. Instead, access instances of this via {@link IItemSecurityUtils#INSTANCE}
 */
@NothingNullByDefault
public class ItemSecurityUtils implements IItemSecurityUtils {

    private static final ItemApiLookup<IOwnerObject, Void> OWNER_CAPABILITY = ItemApiLookup.get(ResourceLocation.fromNamespaceAndPath("mekanism", "owner_object"), IOwnerObject.class, Void.class);
    private static final ItemApiLookup<ISecurityObject, Void> SECURITY_CAPABILITY = ItemApiLookup.get(ResourceLocation.fromNamespaceAndPath("mekanism", "security_object"), ISecurityObject.class, Void.class);

    public static ItemSecurityUtils get() {
        return (ItemSecurityUtils) INSTANCE;
    }

    @Override
    public ItemApiLookup<IOwnerObject, Void> ownerCapability() {
        return OWNER_CAPABILITY;
    }

    @Override
    public ItemApiLookup<ISecurityObject, Void> securityCapability() {
        return SECURITY_CAPABILITY;
    }

    @Override
    public void addOwnerTooltip(ItemStack stack, List<Component> tooltip) {
        Objects.requireNonNull(stack, "Stack to add tooltip for may not be null.");
        Objects.requireNonNull(tooltip, "List of tooltips to add to may not be null.");
        IOwnerObject ownerObject = ownerCapability(stack);
        if (ownerObject != null) {
            tooltip.add(OwnerDisplay.of(MekanismUtils.tryGetClientPlayer(), ownerObject.getOwnerUUID()).getTextComponent());
        }
    }

    @Override
    public void addSecurityTooltip(ItemStack stack, List<Component> tooltip) {
        addOwnerTooltip(stack, tooltip);
        ISecurityObject security = securityCapability(stack);
        if (security != null) {
            SecurityData data = SecurityUtils.get().getFinalData(security, true);
            tooltip.add(MekanismLang.SECURITY.translateColored(EnumColor.GRAY, data.mode()));
            if (data.override()) {
                tooltip.add(MekanismLang.SECURITY_OVERRIDDEN.translateColored(EnumColor.RED));
            }
        }
    }

    public InteractionResultHolder<ItemStack> claimOrOpenGui(Level level, Player player, InteractionHand hand, TriConsumer<ServerPlayer, InteractionHand, ItemStack> openGui) {
        ItemStack stack = player.getItemInHand(hand);
        if (!tryClaimItem(level, player, stack)) {
            if (!INSTANCE.canAccessOrDisplayError(player, stack)) {
                return InteractionResultHolder.fail(stack);
            } else if (stack.getCount() > 1) {
                //If the item is currently stacked, don't allow opening the GUI
                return InteractionResultHolder.pass(stack);
            } else if (!level.isClientSide) {
                openGui.accept((ServerPlayer) player, hand, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public boolean tryClaimItem(Level level, Player player, ItemStack stack) {
        IOwnerObject ownerObject = ownerCapability(stack);
        if (ownerObject != null && ownerObject.getOwnerUUID() == null) {
            if (!level.isClientSide) {
                ownerObject.setOwnerUUID(player.getUUID());
                FabricSecurityNetworking.broadcast(new PacketSyncSecurity(player.getUUID()));
                player.sendSystemMessage(MekanismUtils.logFormat(MekanismLang.NOW_OWN));
            }
            return true;
        }
        return false;
    }
}