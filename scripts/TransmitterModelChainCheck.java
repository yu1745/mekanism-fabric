// Focused Task052 check 3: run the REAL Fabric transmitter model chain against the shipped resource pack.
// Run through scripts/task052-transmitter-check.sh (it supplies the pinned Loom-resolved classpath).
//
// Sprite-free by construction: producing a TextureAtlasSprite needs LWJGL natives and a live client, so this check
// covers everything up to a sprite, which is exactly the platform-independent half of the owner:
//   * FabricTransmitterAssets: real ResourceManager reads of the loader JSON, OBJ and MTL;
//   * the glass pass' mtl_override dispatch (transporter.json -> transmitter_glass_large.mtl);
//   * declared mtllib resolution against the OBJ's own directory;
//   * FabricTransmitterModels.discover: bases and their tier/item descendants found from the pack alone;
//   * the real vanilla BlockModel parent chain, and the inherited tier texture aliases + display transforms;
//   * the dependency set the loader will schedule;
//   * geometry + tint material selection from the shipped MTLs.
//
// Baking a FabricTransmitterBakedModel requires sprites, so quad production is verified by check 1 (real assets) plus
// the compiled owner; it is NOT claimed as runtime-verified here.
package mekanism.fabric.client.render.obj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import mekanism.fabric.client.render.obj.FabricTransmitterAssets.Assets;
import mekanism.fabric.client.render.obj.FabricTransmitterAssets.ObjAssets;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Runs the real transmitter model chain over the repo's shipped assets. */
public final class TransmitterModelChainCheck {

    private static final String NS = "mekanism";
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path assets = Path.of("src/main/resources/assets");
        DirResourceManager resources = new DirResourceManager(assets);
        TransmitterModelConfig.install(() -> false);

        ResourceLocation base = id("block/transmitter/large/large");
        ResourceLocation tier = id("block/transmitter/large/logistical_transporter/basic");
        ResourceLocation smallBase = id("block/transmitter/small/small");
        ResourceLocation item = id("item/basic_logistical_transporter");
        // The glass pass is declared by the logistical transporter base, not by large.json, so its assets are a second
        // real loader JSON with its own OBJ/MTL reads.
        ResourceLocation glassBase = id("block/transmitter/large/logistical_transporter/transporter");

        // --- discovery from the pack alone -------------------------------------------------
        Map<ResourceLocation, ResourceLocation> discovered = FabricTransmitterModels.discover(resources);
        check("discovers the large transmitter base", base.equals(discovered.get(base)));
        check("discovers the small transmitter base", smallBase.equals(discovered.get(smallBase)));
        check("discovers the glass-declaring transporter base", glassBase.equals(discovered.get(glassBase)));
        check("tier block model maps to its glass base", glassBase.equals(discovered.get(tier)));
        check("item model maps through its tier parent to the base", glassBase.equals(discovered.get(item)));
        check("discovers more than one transmitter base", discovered.values().stream().distinct().count() > 1);

        // --- real resource reads -----------------------------------------------------------
        Assets assetsForBase = FabricTransmitterAssets.read(resources, base);
        check("obj source read from the pack", assetsForBase.obj().objSource().contains("mtllib transmitter_large.mtl"));
        check("mtl library read under its declared name", assetsForBase.obj().mtlSources().containsKey("transmitter_large.mtl"));
        check("large base declares no glass pass", assetsForBase.glass() == null);
        check("dependencies include the obj", assetsForBase.resources().contains(assetsForBase.obj().objId()));

        Assets assetsForGlass = FabricTransmitterAssets.read(resources, glassBase);
        check("glass pass read through mtl_override", assetsForGlass.glass() != null
              && assetsForGlass.glass().mtlSources().values().stream().anyMatch(mtl -> mtl.contains("#side_glass")));
        check("dependencies include the resolved mtl override",
              assetsForGlass.glass() != null && assetsForGlass.resources().containsAll(
                    FabricTransmitterAssets.declaredLibraries(assetsForGlass.glass().objId(), assetsForGlass.glass().objSource(), null)
                          .stream().map(FabricTransmitterAssets.DeclaredLibrary::resolvedId).toList()));

        // --- geometry + tint material selection from the real MTLs -------------------------
        TransmitterObjGeometry internal = TransmitterObjGeometry.parse(assetsForBase.obj().objSource(), assetsForBase.obj().mtlSources(), assetsForBase.obj().flipV());
        check("internal geometry has the 24 transmitter parts", internal.groups().size() == 24);
        check("internal materials carry tint -1 (no tint)", internal.materials().values().stream().allMatch(m -> m.tintIndex() == -1));
        TransmitterObjGeometry glass = TransmitterObjGeometry.parse(assetsForGlass.glass().objSource(), assetsForGlass.glass().mtlSources(), assetsForGlass.glass().flipV());
        check("glass materials carry tint index 1", glass.materials().values().stream().allMatch(m -> m.tintIndex() == 1));
        check("glass materials reference glass aliases",
              glass.materials().values().stream().anyMatch(m -> m.textureAlias().contains("glass")));

        // --- real model construction, parent chain, inherited textures and transforms -------
        FabricTransmitterBlockModel baseModel = model(resources, base, assetsForBase);
        FabricTransmitterBlockModel tierModel = model(resources, tier, assetsForGlass);
        FabricTransmitterBlockModel itemModel = model(resources, item, assetsForGlass);
        Map<ResourceLocation, UnbakedModel> byId = new LinkedHashMap<>();
        byId.put(base, baseModel);
        byId.put(tier, tierModel);
        byId.put(item, itemModel);
        tierModel.resolveParents(resolver(assets, byId));
        itemModel.resolveParents(resolver(assets, byId));

        check("tier alias 'side' resolves to a real texture", real(tierModel.getMaterial("side")));
        check("tier alias 'center_down' resolves to a real texture", real(tierModel.getMaterial("center_down")));
        check("tier alias 'center_opaque' resolves through the tier file", real(tierModel.getMaterial("center_opaque")));
        check("item model inherits the tier alias chain", real(itemModel.getMaterial("side")));
        check("inherited display transforms are real, not the identity placeholder",
              itemModel.getTransforms() != ItemTransforms.NO_TRANSFORMS);
        check("item model declares real geometry dependencies", !itemModel.getDependencies().isEmpty());

        // A transmitting model must expose its own alias map, not the vanilla missing texture, for every shipped alias.
        for (String alias : List.of("side", "side_opaque", "center_down", "center_opaque")) {
            check("alias '" + alias + "' resolves", real(tierModel.getMaterial(alias)));
        }

        if (failures == 0) {
            System.out.println("TRANSMITTER_MODEL_CHAIN_CHECK PASS discovered=" + discovered.size() + " bases="
                  + discovered.values().stream().distinct().count() + " tier_aliases=ok transforms=inherited");
        } else {
            System.out.println("TRANSMITTER_MODEL_CHAIN_CHECK FAILURES=" + failures);
            System.exit(1);
        }
    }

    private static boolean real(Material material) {
        return material != null && !material.texture().getPath().contains("missingno");
    }

    private static FabricTransmitterBlockModel model(ResourceManager resources, ResourceLocation modelId, Assets assets) throws IOException {
        String json = FabricTransmitterAssets.tryReadJsonText(resources, modelId);
        return new FabricTransmitterBlockModel(modelId.toString(), json, assets.obj(), assets.glass(), assets.resources(),
              () -> false);
    }

    /** A real recursive resolver over the pack, so vanilla's parent requirement (BlockModel) is genuinely exercised. */
    private static java.util.function.Function<ResourceLocation, UnbakedModel> resolver(Path assets, Map<ResourceLocation, UnbakedModel> known) {
        return new java.util.function.Function<>() {
            @Override
            public UnbakedModel apply(ResourceLocation id) {
                UnbakedModel model = known.get(id);
                if (model != null) {
                    return model;
                }
                Path file = assets.resolve(id.getNamespace()).resolve("models/" + id.getPath() + ".json");
                if (Files.isRegularFile(file)) {
                    try {
                        return BlockModel.fromString(Files.readString(file));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                //An absent parent (e.g. block/block is shipped) still needs a BlockModel so the vanilla chain runs.
                return new BlockModel(null, List.of(), Map.of(), null, null, ItemTransforms.NO_TRANSFORMS, List.of());
            }
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    /** Minimal real ResourceManager over the repo's shipped assets (no client runtime needed). */
    private static final class DirResourceManager implements ResourceManager {
        private final Path assetsRoot;

        DirResourceManager(Path assetsRoot) {
            this.assetsRoot = assetsRoot;
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of(NS);
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation id) {
            Resource resource = resource(id);
            return resource == null ? List.of() : List.of(resource);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
            Path root = assetsRoot.resolve(NS).resolve(path);
            if (!Files.isDirectory(root)) {
                return result;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String relative = assetsRoot.resolve(NS).relativize(file).toString().replace('\\', '/');
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NS, relative);
                    if (filter.test(id)) {
                        result.put(id, resource(id));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return result;
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
            Map<ResourceLocation, List<Resource>> result = new LinkedHashMap<>();
            listResources(path, filter).forEach((id, resource) -> result.put(id, List.of(resource)));
            return result;
        }

        @Override
        public java.util.stream.Stream<PackResources> listPacks() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation id) {
            return Optional.ofNullable(resource(id));
        }

        private Resource resource(ResourceLocation id) {
            Path file = assetsRoot.resolve(id.getNamespace()).resolve(id.getPath());
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return new Resource(null, () -> {
                try {
                    return Files.newInputStream(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }
}
