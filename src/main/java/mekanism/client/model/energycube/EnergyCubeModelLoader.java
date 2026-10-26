package mekanism.client.model.energycube;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mekanism.api.RelativeSide;
import mekanism.common.util.EnumUtils;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Mekanism model loader that properly loads models in a specific to energy cube format
 *
 * <p>Fabric port: the NeoForge {@code IGeometryLoader} surface becomes a plain reader over the vanilla block-model
 * {@code Gson} ({@code BlockModel.GSON} rides the access widener); the fabric model plugin parses the shipped JSONs
 * with this reader and drives the bake itself.</p>
 */
public class EnergyCubeModelLoader {

    public static final EnergyCubeModelLoader INSTANCE = new EnergyCubeModelLoader();

    private EnergyCubeModelLoader() {
    }

    @NotNull
    public EnergyCubeGeometry read(@NotNull JsonObject jsonObject) {
        List<BlockElement> frame = readElements(jsonObject, "frame");
        Map<RelativeSide, List<BlockElement>> leds = new EnumMap<>(RelativeSide.class);
        Map<RelativeSide, List<BlockElement>> ports = new EnumMap<>(RelativeSide.class);
        for (RelativeSide side : EnumUtils.SIDES) {
            String name = side.name().toLowerCase(Locale.ROOT);
            leds.put(side, readElements(jsonObject, name + "LEDs"));
            ports.put(side, readElements(jsonObject, name + "Port"));
        }
        return new EnergyCubeGeometry(frame, leds, ports);
    }

    private List<BlockElement> readElements(JsonObject jsonObject, String key) {
        List<BlockElement> elements = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(jsonObject, key)) {
            elements.add(BlockModel.GSON.fromJson(element, BlockElement.class));
        }
        if (elements.isEmpty()) {
            throw new JsonParseException("Energy cube models requires a \"" + key + "\" element with at least one element.");
        }
        return elements;
    }
}
