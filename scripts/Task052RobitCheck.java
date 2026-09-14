import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import mekanism.api.robit.*;
import mekanism.common.registries.MekanismRobitSkins;
import mekanism.fabric.robit.RobitSkinMappedRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

/** Focused domain/default-registry checks. Not a substitute for executing the RegistryDataLoader mixin under Knot. */
public class Task052RobitCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var serializers = new MappedRegistry<MapCodec<? extends RobitSkin>>(RobitSkinRegistry.SERIALIZER_KEY, Lifecycle.stable(), false);
        RobitSkinRegistry.install(serializers);
        var direct = RobitSkinSerializationHelper.DIRECT_CODEC;
        require(RobitSkinSerializationHelper.REFERENCE_CODEC != null && RobitSkinSerializationHelper.LIST_CODEC != null, "complete helper initialization");
        MekanismRobitSkins.ROBIT_SKINS.registerAll(serializers);
        serializers.freeze();
        require(serializers.size() == 2 && serializers.get(ResourceLocation.parse("mekanism:basic")) == RobitSkinSerializationHelper.NETWORK_CODEC
              && serializers.get(ResourceLocation.parse("mekanism:advancement_based")) == RobitSkinSerializationHelper.ADVANCEMENT_BASED_ROBIT_SKIN_CODEC,
              "canonical exact serializer codecs");
        int count = 0;
        int advancementCount = 0;
        try (var paths = Files.list(Path.of(args[0]))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(path));
                RobitSkin decoded = direct.parse(JsonOps.INSTANCE, json).getOrThrow();
                RobitSkin roundTrip = direct.parse(JsonOps.INSTANCE, direct.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow();
                require(decoded.equals(roundTrip), "data round trip " + path);
                var network = RobitSkinSerializationHelper.NETWORK_CODEC.codec();
                RobitSkin client = network.parse(JsonOps.INSTANCE, network.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow()).getOrThrow();
                require(client instanceof BasicRobitSkin && client.textures().equals(decoded.textures())
                      && java.util.Objects.equals(client.customModel(), decoded.customModel()), "network skin data " + path);
                if (decoded instanceof AdvancementBasedRobitSkin) advancementCount++;
                count++;
            }
        }
        require(count == 13 && advancementCount > 0, "all 13 shipped skins, including advancement unlock");
        var skins = new RobitSkinMappedRegistry(Lifecycle.stable());
        var base = new BasicRobitSkin(List.of(ResourceLocation.parse("mekanism:robit")));
        skins.register(RobitSkinRegistry.BASE, base, RegistrationInfo.BUILT_IN);
        skins.freeze();
        var missing = ResourceLocation.parse("mekanism:missing");
        require(skins.get(missing) == base && skins.getOptional(missing).isEmpty() && skins.byId(-1) == base, "native default versus optional lookup");
        require(skins.getId(new BasicRobitSkin(List.of(missing))) == skins.getId(base), "native missing reverse ID fallback");
        try {
            new RobitSkinMappedRegistry(Lifecycle.stable()).freeze();
            throw new AssertionError("missing BASE accepted");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("mekanism:robit"), "diagnostic missing BASE failure");
        }
        System.out.println("TASK052_ROBIT_CHECK_PASS serializers=2 assets=13 distinct_network_codec=true defaulted_semantics=true missing_base_rejected=true mixin_execution=NOT_TESTED");
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
