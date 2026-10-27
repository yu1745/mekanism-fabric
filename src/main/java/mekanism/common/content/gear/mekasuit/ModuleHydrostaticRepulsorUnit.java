package mekanism.common.content.gear.mekasuit;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.common.config.MekanismConfig;
import mekanism.common.item.gear.ItemMekaSuitArmor;
import mekanism.common.registries.MekanismModules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import mekanism.api.gear.GearAttributeContext;

@ParametersAreNotNullByDefault
public record ModuleHydrostaticRepulsorUnit(boolean swimBoost) implements ICustomModule<ModuleHydrostaticRepulsorUnit> {

    private static final ResourceLocation WATER_MOVEMENT = ResourceLocation.fromNamespaceAndPath("mekanism", "water_movement");
    public static final ResourceLocation SWIM_BOOST = ResourceLocation.fromNamespaceAndPath("mekanism", "swim_boost");
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
    }

    @Override
    public void tickServer(IModule<ModuleHydrostaticRepulsorUnit> module, IModuleContainer moduleContainer, ItemStack stack, Player player) {
        if (isSwimBoost(module, stack) && intersectsFluid(player)) {
            module.useEnergy(player, stack, MekanismConfig.gear.mekaSuitEnergyUsageHydrostaticRepulsion.get());
        }
    }

    private boolean isSwimBoost(IModule<ModuleHydrostaticRepulsorUnit> module, ItemStack stack) {
        return swimBoost && module.getInstalledCount() >= BOOST_STACKS && module.hasEnoughEnergy(stack, MekanismConfig.gear.mekaSuitEnergyUsageHydrostaticRepulsion);
    }

    /** The swim-speed modifier is active only for an enabled module in worn MekaSuit leggings. */
    public static boolean hasActiveSwimBoost(Player player) {
        ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!(leggings.getItem() instanceof ItemMekaSuitArmor)) return false;
        IModule<ModuleHydrostaticRepulsorUnit> module = IModuleHelper.INSTANCE.getIfEnabled(leggings, MekanismModules.HYDROSTATIC_REPULSOR_UNIT);
        return module != null && module.getCustomInstance().isSwimBoost(module, leggings);
    }

    /** Inspect fluid surfaces intersecting the entity, without loading chunks, rather than only its feet block. */
    static boolean intersectsFluid(Player player) {
        Level level = player.level();
        AABB loaded = player.getBoundingBox().inflate(1);
        if (!level.hasChunksAt(Mth.floor(loaded.minX), Mth.floor(loaded.minZ), Mth.ceil(loaded.maxX), Mth.ceil(loaded.maxZ))) return false;
        AABB box = player.getBoundingBox().deflate(0.001);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(box.minX); x < Mth.ceil(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y < Mth.ceil(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z < Mth.ceil(box.maxZ); z++) {
                    pos.set(x, y, z);
                    FluidState fluid = level.getFluidState(pos);
                    if (!fluid.isEmpty() && y + fluid.getHeight(level, pos) >= box.minY) return true;
                }
            }
        }
        return false;
    }
}