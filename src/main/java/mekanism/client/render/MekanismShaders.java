package mekanism.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback.RegistrationContext;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

public class MekanismShaders {

    static final ShaderTracker MEKASUIT = new ShaderTracker();
    //Merge of position_color_tex and rendertype_lightning
    static final ShaderTracker SPS = new ShaderTracker();
    //Copy of position_color_tex with support for fog
    static final ShaderTracker FLAME = new ShaderTracker();

    public static void shaderRegistry(RegistrationContext context) throws IOException {
        registerShader(context, ResourceLocation.fromNamespaceAndPath("mekanism", "rendertype_flame"), DefaultVertexFormat.POSITION_TEX_COLOR, FLAME);
        registerShader(context, ResourceLocation.fromNamespaceAndPath("mekanism", "rendertype_mekasuit"), DefaultVertexFormat.NEW_ENTITY, MEKASUIT);
        registerShader(context, ResourceLocation.fromNamespaceAndPath("mekanism", "rendertype_sps"), DefaultVertexFormat.POSITION_TEX_COLOR, SPS);
    }

    private static void registerShader(RegistrationContext context, ResourceLocation shaderLocation, VertexFormat vertexFormat, ShaderTracker tracker) throws IOException {
        context.register(shaderLocation, vertexFormat, tracker::setInstance);
    }

    static class ShaderTracker implements Supplier<ShaderInstance> {

        private ShaderInstance instance;
        final ShaderStateShard shard = new ShaderStateShard(this);

        private ShaderTracker() {
        }

        private void setInstance(ShaderInstance instance) {
            this.instance = instance;
        }

        @Override
        public ShaderInstance get() {
            return instance;
        }
    }
}