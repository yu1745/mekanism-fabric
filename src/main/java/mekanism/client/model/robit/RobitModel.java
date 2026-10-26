package mekanism.client.model.robit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism model loader that gets automatically wrapped into a robit baked model
 *
 * <p>Fabric port: the NeoForge {@code IGeometryLoader}/{@code ElementsModel} pair becomes a plain element reader over
 * the vanilla block-model Gson (access widened); the fabric model plugin bakes the elements (missingno placeholder
 * textures, replaced per skin at draw time) and the robit item renders through the builtin renderer with the robit
 * atlas, mirroring the official render-type forcing through the reshaped {@link RobitBakedModel}.</p>
 */
public class RobitModel {

    public static final RobitModel INSTANCE = new RobitModel();

    private RobitModel() {
    }

    @NotNull
    public List<BlockElement> read(@NotNull JsonObject modelContents) {
        if (!modelContents.has("elements")) {
            throw new JsonParseException("An element model must have an \"elements\" member.");
        }
        List<BlockElement> elements = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(modelContents, "elements")) {
            elements.add(BlockModel.GSON.fromJson(element, BlockElement.class));
        }
        return elements;
    }
}
