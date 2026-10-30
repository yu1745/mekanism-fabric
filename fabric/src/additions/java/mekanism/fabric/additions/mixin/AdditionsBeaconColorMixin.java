package mekanism.fabric.additions.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import mekanism.additions.common.block.plastic.BlockPlasticTransparent;
import mekanism.additions.common.block.plastic.BlockPlasticTransparentSlab;
import mekanism.additions.common.block.plastic.BlockPlasticTransparentStairs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity.BeaconBeamSection;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend the actual beacon scan for the three original plastic color providers, preserving its section and height owner. */
@Mixin(BeaconBlockEntity.class)
abstract class AdditionsBeaconColorMixin {
    @Shadow private List<BeaconBeamSection> checkingBeamSections;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getBlock()Lnet/minecraft/world/level/block/Block;"))
    private static void mekanismadditions$color(Level level, BlockPos beaconPos, BlockState beaconState, BeaconBlockEntity beacon, CallbackInfo ci,
          @Local(ordinal = 1) BlockState state, @Local(ordinal = 1) BlockPos pos, @Local LocalRef<BeaconBeamSection> current,
          @Share("mekanismadditions$colored") LocalBooleanRef colored) {
        Integer color = null;
        var block = state.getBlock();
        if (block instanceof BlockPlasticTransparent plastic) color = plastic.getBeaconColorMultiplier(state, level, pos, beaconPos);
        else if (block instanceof BlockPlasticTransparentSlab plastic) color = plastic.getBeaconColorMultiplier(state, level, pos, beaconPos);
        else if (block instanceof BlockPlasticTransparentStairs plastic) color = plastic.getBeaconColorMultiplier(state, level, pos, beaconPos);
        colored.set(color != null);
        if (color == null) return;
        var sections = ((AdditionsBeaconColorMixin) (Object) beacon).checkingBeamSections;
        var section = current.get();
        if (sections.size() <= 1) {
            section = new BeaconBeamSection(color);
            sections.add(section);
        } else if (section != null) {
            if (section.getColor() == color) section.increaseHeight();
            else {
                section = new BeaconBeamSection(FastColor.ARGB32.average(section.getColor(), color));
                sections.add(section);
            }
        }
        current.set(section);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getLightBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I"))
    private static int mekanismadditions$coloredBlockPasses(BlockState state, BlockGetter level, BlockPos pos, Operation<Integer> original,
          @Share("mekanismadditions$colored") LocalBooleanRef colored) {
        return colored.get() ? 0 : original.call(state, level, pos);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BeaconBlockEntity$BeaconBeamSection;increaseHeight()V", ordinal = 1))
    private static void mekanismadditions$height(BeaconBeamSection section, Operation<Void> original,
          @Share("mekanismadditions$colored") LocalBooleanRef colored) {
        if (!colored.get()) original.call(section);
    }
}
