package mekanism.common.content.gear.mekasuit;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.common.config.MekanismConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import mekanism.api.gear.GearAttributeContext;

@ParametersAreNotNullByDefault
public record ModuleHydrostaticRepulsorUnit(boolean swimBoost) implements ICustomModule<ModuleHydrostaticRepulsorUnit> {

    private static final ResourceLocation WATER_MOVEMENT = ResourceLocation.fromNamespaceAndPath("mekanism", "water_movement");
    public static final ResourceLocation SWIM_BOOST = ResourceLocation.fromNamespaceAndPath("mekanism", "swim_boost");
    private static final AttributeModifier SWIM_BOOST_MODIFIER = new AttributeModifier(SWIM_BOOST, 1, AttributeModifier.Operation.ADD_VALUE);
    public static final int BOOST_STACKS = 4;

    public ModuleHydrostaticRepulsorUnit(IModule<ModuleHydrostaticRepulsorUnit> module) {
        this(module.getBooleanConfigOrFalse(SWIM_BOOST));
    }

    @Override
    public void adjustAttributes(IModule<ModuleHydrostaticRepulsorUnit> module, GearAttributeContext event) {
        //Clamp out at a max efficiency of one (at three installed units)
        //Note: Value copied from default for depth strider
        AttributeModifier modifier = new AttributeModifier(WATER_MOVEMENT, Math.min(1, 0.33333334F * module.getInstalledCount()), AttributeModifier.Operation.ADD_VALUE);
        event.addModifier(Attributes.WATER_MOVEMENT_EFFICIENCY, modifier, EquipmentSlotGroup.LEGS);
        if (isSwimBoost(module, event.getItemStack())) {
            //Fabric port delta: NeoForge's swim_speed attribute does not exist on Fabric; the swim boost maps onto the
            // vanilla water-movement-efficiency attribute, which is the vanilla swim-speed surface.
            event.addModifier(Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_BOOST_MODIFIER, EquipmentSlotGroup.LEGS);
        }
    }

    @Override
    public void tickServer(IModule<ModuleHydrostaticRepulsorUnit> module, IModuleContainer moduleContainer, ItemStack stack, Player player) {
        //Fabric port delta: vanilla has no "fluid type at the entity's max height" query; being inside any non-empty
        // fluid block at the player's position is the vanilla-equivalent submerged test the official hook reported.
        if (isSwimBoost(module, stack) && !player.level().getFluidState(player.blockPosition()).isEmpty()) {
            module.useEnergy(player, stack, MekanismConfig.gear.mekaSuitEnergyUsageHydrostaticRepulsion.get());
        }
    }

    private boolean isSwimBoost(IModule<ModuleHydrostaticRepulsorUnit> module, ItemStack stack) {
        return swimBoost && module.getInstalledCount() >= BOOST_STACKS && module.hasEnoughEnergy(stack, MekanismConfig.gear.mekaSuitEnergyUsageHydrostaticRepulsion);
    }
}