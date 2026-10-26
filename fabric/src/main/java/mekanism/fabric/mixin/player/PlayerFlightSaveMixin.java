package mekanism.fabric.mixin.player;

import mekanism.common.content.gear.mekasuit.MekaSuitFlight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerList.saveAll precedes disconnect at shutdown: filter the saved ability fields, without changing live abilities. */
@Mixin(Player.class)
abstract class PlayerFlightSaveMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void mekanism$saveWithoutTemporaryFlight(CompoundTag tag, CallbackInfo ci) {
        MekaSuitFlight.filterSavedAbilities((Player) (Object) this, tag);
    }
}
