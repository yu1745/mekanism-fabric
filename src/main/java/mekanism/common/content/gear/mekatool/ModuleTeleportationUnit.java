package mekanism.common.content.gear.mekatool;

import mekanism.api.annotations.ParametersAreNotNullByDefault;
import mekanism.api.gear.ICustomModule;
import mekanism.api.gear.IModule;
import net.minecraft.resources.ResourceLocation;

@ParametersAreNotNullByDefault
public record ModuleTeleportationUnit(boolean requiresBlockTarget) implements ICustomModule<ModuleTeleportationUnit> {

    public static final ResourceLocation REQUIRE_TARGET = ResourceLocation.fromNamespaceAndPath("mekanism", "teleportation_requires_block");

    public ModuleTeleportationUnit(IModule<ModuleTeleportationUnit> module) {
        this(module.getBooleanConfigOrFalse(REQUIRE_TARGET));
    }
}