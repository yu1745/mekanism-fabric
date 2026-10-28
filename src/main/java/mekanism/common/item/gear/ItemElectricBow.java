package mekanism.common.item.gear;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.text.EnumColor;
import mekanism.common.MekanismLang;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.interfaces.IItemHUDProvider;
import mekanism.common.item.interfaces.IModeItem.IAttachmentBasedModeItem;
import mekanism.common.registration.impl.CreativeTabDeferredRegister.ICustomCreativeTabContents;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.text.BooleanStateDisplay.OnOff;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ItemElectricBow extends BowItem implements IItemHUDProvider, ICustomCreativeTabContents, IAttachmentBasedModeItem<Boolean> {

    public ItemElectricBow(Properties properties) {
        super(properties.rarity(Rarity.RARE).stacksTo(1).component(MekanismDataComponents.ELECTRIC_BOW_MODE.get(), false));
    }

    @Override
    public boolean allowContinuingBlockBreaking(Player player, ItemStack oldStack, ItemStack newStack) {
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        StorageUtils.addStoredEnergy(stack, tooltip, true);
        tooltip.add(MekanismLang.FIRE_MODE.translateColored(EnumColor.PINK, OnOff.of(getMode(stack))));
    }

    @Override
    public void releaseUsing(@NotNull ItemStack bow, @NotNull Level world, @NotNull LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player && !player.isCreative()) {
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(bow, 0);
            long energyNeeded = getMode(bow) ? MekanismConfig.gear.electricBowEnergyUsageFire.get() : MekanismConfig.gear.electricBowEnergyUsage.get();
            if (energyContainer == null || energyContainer.extract(energyNeeded, Action.SIMULATE, AutomationType.MANUAL) < energyNeeded) {
                return;
            }
        }
        super.releaseUsing(bow, world, entity, timeLeft);
    }

    @Override
    protected void shoot(@NotNull ServerLevel world, @NotNull LivingEntity entity, @NotNull InteractionHand hand, @NotNull ItemStack bow,
          @NotNull List<ItemStack> potentialAmmo, float velocity, float inaccuracy, boolean critical, @Nullable LivingEntity target) {
        super.shoot(world, entity, hand, bow, potentialAmmo, velocity, inaccuracy, critical, target);
        if (entity instanceof Player player && !player.isCreative() && !potentialAmmo.isEmpty()) {
            IEnergyContainer energyContainer = StorageUtils.getEnergyContainer(bow, 0);
            if (energyContainer != null) {
                //Use energy
                long energyNeeded = getMode(bow) ? MekanismConfig.gear.electricBowEnergyUsageFire.get() : MekanismConfig.gear.electricBowEnergyUsage.get();
                energyContainer.extract(energyNeeded, Action.EXECUTE, AutomationType.MANUAL);
            }
        }
    }

    @NotNull
    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack bow, ItemStack ammo, boolean critical) {
        if (getMode(bow)) {
            //Preserve the virtual Flame enchantment at the real projectile owner. Resolve the live enchantment,
            //so datapack-defined effects still apply, without mutating the player's bow or its stored enchantments.
            var flame = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.FLAME);
            if (flame.isPresent() && bow.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(flame.get()) == 0) {
                ItemStack enchanted = bow.copy();
                enchanted.enchant(flame.get(), 1);
                return super.createProjectile(level, shooter, enchanted, ammo, critical);
            }
        }
        return super.createProjectile(level, shooter, bow, ammo, critical);
    }

    @NotNull
    @Override
    public DataComponentType<Boolean> getModeDataType() {
        return MekanismDataComponents.ELECTRIC_BOW_MODE.get();
    }

    @Override
    public Boolean getDefaultMode() {
        return Boolean.FALSE;
    }

    @Override
    public void addHUDStrings(List<Component> list, Player player, ItemStack stack, EquipmentSlot slotType) {
        list.add(MekanismLang.FIRE_MODE.translateColored(EnumColor.PINK, OnOff.of(getMode(stack))));
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return StorageUtils.getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return MekanismConfig.client.energyColor.get();
    }

    @Override
    public void addItems(Holder<Item> item, Consumer<ItemStack> tabOutput) {
        tabOutput.accept(StorageUtils.getFilledEnergyVariant(item));
    }

    @Override
    public void changeMode(@NotNull Player player, @NotNull ItemStack stack, int shift, DisplayChange displayChange) {
        if (Math.abs(shift) % 2 == 1) {
            //We are changing by an odd amount, so toggle the mode
            boolean newState = !getMode(stack);
            setMode(stack, player, newState);
            displayChange.sendMessage(player, newState, s -> MekanismLang.FIRE_MODE.translate(OnOff.of(s, true)));
        }
    }

    @NotNull
    @Override
    public Component getScrollTextComponent(@NotNull ItemStack stack) {
        return MekanismLang.FIRE_MODE.translateColored(EnumColor.PINK, OnOff.of(getMode(stack), true));
    }

}