package mekanism.fabric.mixin.client;

import java.util.List;
import java.util.function.Function;
import mekanism.fabric.client.model.CompositeModelParentAccess;
import mekanism.fabric.client.model.FabricCompositeModel;
import mekanism.fabric.client.model.FabricObjModel;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restore custom composite geometry inherited by otherwise vanilla block and item models. */
@Mixin(BlockModel.class)
public abstract class BlockModelCompositeMixin implements CompositeModelParentAccess {
    @Shadow @Final private List<BlockElement> elements;
    @Shadow @Nullable protected BlockModel parent;

    @Override
    public @Nullable BlockModel mekanism$parentModel() {
        return parent;
    }

    @Override
    public boolean mekanism$hasOwnElements() {
        return !elements.isEmpty();
    }

    @Inject(method = "resolveParents", at = @At("RETURN"))
    private void mekanism$resolveInheritedCompositeChildren(Function<ResourceLocation, UnbakedModel> resolver, CallbackInfo info) {
        if ((Object) this instanceof FabricCompositeModel || !elements.isEmpty()) {
            return;
        }
        BlockModel current = parent;
        while (current != null) {
            if (current instanceof FabricCompositeModel composite) {
                composite.resolveParents(resolver);
                return;
            }
            CompositeModelParentAccess access = (CompositeModelParentAccess) current;
            if (access.mekanism$hasOwnElements()) {
                return;
            }
            current = access.mekanism$parentModel();
        }
    }

    @Inject(method = "bake(Lnet/minecraft/client/resources/model/ModelBaker;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;)Lnet/minecraft/client/resources/model/BakedModel;",
          at = @At("HEAD"), cancellable = true)
    private void mekanism$bakeInheritedComposite(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state,
          CallbackInfoReturnable<BakedModel> result) {
        if (!elements.isEmpty()) {
            return;
        }
        BlockModel current = parent;
        while (current != null) {
            if (current instanceof FabricCompositeModel composite) {
                result.setReturnValue(composite.bakeInherited((BlockModel) (Object) this, baker, sprites, state));
                return;
            } else if (current instanceof FabricObjModel obj) {
                result.setReturnValue(obj.bakeInherited((BlockModel) (Object) this, baker, sprites, state));
                return;
            }
            CompositeModelParentAccess access = (CompositeModelParentAccess) current;
            if (access.mekanism$hasOwnElements()) {
                return;
            }
            current = access.mekanism$parentModel();
        }
    }
}
