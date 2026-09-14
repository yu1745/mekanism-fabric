package mekanism.fabric.client.render.obj;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import mekanism.fabric.client.render.obj.FabricTransmitterAssets.ObjAssets;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Real Fabric transmitter model: the concrete {@link UnbakedModel} the transmitter resolver returns for a transmitter
 * base and for every model that inherits from one, and the producer of the actual {@link BakedModel}.
 *
 * <p>Why a {@link BlockModel} subclass: shipped item models declare a transmitter tier model as their {@code "parent"},
 * and vanilla's parent resolution hard-requires every parent to be a {@link BlockModel}. Extending BlockModel keeps that
 * chain - and therefore inherited display transforms and texture aliases - working exactly as vanilla.</p>
 *
 * <p>The superclass texture map is rebuilt from this model's own JSON because vanilla's texture walk reads the
 * {@code textureMap} <em>field</em> directly (there is no accessor, and the field is final so a delegate's map cannot be
 * adopted). Without this, an item model's walk would stop at this instance's empty map and never reach the tier model's
 * real aliases - a real defect this class must not have. The parse mirrors vanilla's own rule exactly: a {@code #name}
 * value is a reference to another alias, anything else is a {@link Material} in the block atlas.</p>
 */
public final class FabricTransmitterBlockModel extends BlockModel {

    private final ObjAssets internal;
    private final ObjAssets glass;
    private final Collection<ResourceLocation> extraDependencies;
    private final BooleanSupplier opaqueTransmitters;

    FabricTransmitterBlockModel(String name, String json, ObjAssets internal, ObjAssets glass,
          Collection<ResourceLocation> extraDependencies, BooleanSupplier opaqueTransmitters) {
        super(parentId(json), List.<BlockElement>of(), parseTextures(json), null, null, parseTransforms(json), List.<ItemOverride>of());
        this.internal = internal;
        this.glass = glass;
        this.extraDependencies = extraDependencies;
        this.opaqueTransmitters = opaqueTransmitters;
        //Keep the vanilla debug name populated the way the vanilla loader does.
        this.name = name;
    }

    /**
     * Rebuilds the {@code textures} map with vanilla's exact rule. Delegating to another BlockModel instance would not
     * work, because {@code BlockModel#findTextureEntry} reads the field and never calls {@link #getMaterial}.
     */
    private static Map<String, Either<Material, String>> parseTextures(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Either<Material, String>> textures = new LinkedHashMap<>();
        if (root.has("textures") && root.get("textures").isJsonObject()) {
            JsonObject declared = root.getAsJsonObject("textures");
            for (String name : declared.keySet()) {
                String value = declared.get(name).getAsString();
                if (value.startsWith("#")) {
                    textures.put(name, Either.right(value.substring(1)));
                } else {
                    textures.put(name, Either.left(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(value))));
                }
            }
        }
        return textures;
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        LinkedHashSet<ResourceLocation> dependencies = new LinkedHashSet<>(super.getDependencies());
        dependencies.addAll(extraDependencies);
        return List.copyOf(dependencies);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        //The real vanilla chain: parents resolve through the ordinary resolver, which is what makes inherited aliases and
        //display transforms work for this model's own textureMap walk.
        super.resolveParents(resolver);
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        TransmitterObjGeometry internalGeometry = TransmitterObjGeometry.parse(internal.objSource(), internal.mtlSources(), internal.flipV());
        TransmitterObjGeometry glassGeometry = glass == null ? null
              : TransmitterObjGeometry.parse(glass.objSource(), glass.mtlSources(), glass.flipV());
        //Alias resolution runs through this model's real material chain, so it sees this model's own aliases plus
        //everything inherited from its transmitter base and block/block, exactly like the official configuration.
        return new FabricTransmitterBakedModel(internalGeometry, glassGeometry, alias -> spriteGetter.apply(getMaterial(alias)),
              opaqueTransmitters.getAsBoolean(), getTransforms());
    }

    @Nullable
    private static ResourceLocation parentId(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("parent") || !root.get("parent").isJsonPrimitive() || !root.getAsJsonPrimitive("parent").isString()) {
            return null;
        }
        return ResourceLocation.tryParse(root.get("parent").getAsString());
    }

    /**
     * Display transforms parsed through vanilla's own deserializer (the same path a normal block model uses), so the
     * values and their defaults are identical. A missing {@code display} block is the real
     * {@link ItemTransforms#NO_TRANSFORMS}; a declared one (the shipped models declare one, inherited from
     * {@code block/block}) is preserved. Inheritance is completed later by the real parent walk in
     * {@link #resolveParents}, because this instance owns its own transforms field and parent pointer.
     */
    private static ItemTransforms parseTransforms(String json) {
        return BlockModel.fromString(json).getTransforms();
    }
}
