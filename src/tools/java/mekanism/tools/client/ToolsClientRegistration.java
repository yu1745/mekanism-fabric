package mekanism.tools.client;

import java.util.HashMap;
import java.util.List;
import mekanism.tools.client.render.item.RenderMekanismShieldItem;
import mekanism.tools.common.MekanismTools;
import mekanism.tools.common.registries.ToolsItems;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public class ToolsClientRegistration {
    private ToolsClientRegistration() {
    }

    public static void initialize() {
        for (var shield : List.of(ToolsItems.BRONZE_SHIELD, ToolsItems.LAPIS_LAZULI_SHIELD, ToolsItems.OSMIUM_SHIELD,
              ToolsItems.REFINED_GLOWSTONE_SHIELD, ToolsItems.REFINED_OBSIDIAN_SHIELD, ToolsItems.STEEL_SHIELD)) {
            ItemProperties.PROPERTIES.computeIfAbsent(shield.asItem(), ignored -> new HashMap<>()).put(MekanismTools.rl("blocking"),
                  (stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
            BuiltinItemRendererRegistry.INSTANCE.register(shield.asItem(), RenderMekanismShieldItem.RENDERER::renderByItem);
        }
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return MekanismTools.rl("shield_renderer"); }
            @Override public void onResourceManagerReload(ResourceManager resources) { RenderMekanismShieldItem.RENDERER.onResourceManagerReload(resources); }
        });
    }
}
