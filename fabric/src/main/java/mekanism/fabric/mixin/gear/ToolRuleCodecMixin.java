package mekanism.fabric.mixin.gear;

import com.mojang.serialization.Codec;
import mekanism.fabric.gear.BlockToolRuleCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.Tool;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tool.Rule.class)
public abstract class ToolRuleCodecMixin {
    @Shadow @Final @Mutable public static Codec<Tool.Rule> CODEC;
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mekanism$extendToolRuleCodecs(CallbackInfo ci) {
        CODEC = BlockToolRuleCodecs.persistent(CODEC);
        STREAM_CODEC = BlockToolRuleCodecs.network(STREAM_CODEC);
    }
}
