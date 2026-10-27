package mekanism.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TechnicalDamageTagResourceTest {

    @Test
    void packagedTagRetainsNeoForgeDefaultsAndOptionalForgeExtension() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/neoforge/tags/damage_type/is_technical.json")) {
            assertNotNull(stream, "technical damage tag must be present on the runtime resource path");
            var values = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
            assertEquals(4, values.size());
            assertEquals("minecraft:generic_kill", values.get(0).getAsString());
            assertEquals("minecraft:outside_border", values.get(1).getAsString());
            assertEquals("minecraft:out_of_world", values.get(2).getAsString());
            var extension = values.get(3).getAsJsonObject();
            assertEquals("#forge:is_technical", extension.get("id").getAsString());
            assertFalse(extension.get("required").getAsBoolean());
        }
    }
}
