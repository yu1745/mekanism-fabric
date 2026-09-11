package mekanism.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class RelativeSideTest {
    @Test
    void namesTranslationsAndOrdinalsRemainStable() {
        List<String> names = List.of("front", "left", "right", "back", "top", "bottom");
        assertEquals(names, List.of(RelativeSide.values()).stream().map(RelativeSide::getSerializedName).toList());
        for (int i = 0; i < RelativeSide.values().length; i++) {
            RelativeSide side = RelativeSide.values()[i];
            assertEquals("side.mekanism." + names.get(i), side.getTranslationKey());
            assertEquals(Component.translatable(side.getTranslationKey()), side.getTranslatedName());
        }
    }

    @Test
    void byIdAndCodecsPreserveOfficialWireAndDataNames() {
        assertSame(RelativeSide.BOTTOM, RelativeSide.BY_ID.apply(-1));
        assertSame(RelativeSide.FRONT, RelativeSide.BY_ID.apply(6));
        assertSame(RelativeSide.LEFT, RelativeSide.BY_ID.apply(7));

        for (RelativeSide side : RelativeSide.values()) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RelativeSide.STREAM_CODEC.encode(buffer, side);
                assertSame(side, RelativeSide.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
            assertEquals(new JsonPrimitive(side.getSerializedName()),
                  RelativeSide.CODEC.encodeStart(JsonOps.INSTANCE, side).getOrThrow());
            assertSame(side, RelativeSide.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(side.getSerializedName())).getOrThrow());
        }
    }

    @Test
    void everyFacingUsesTheOfficialFixedProjectionTable() {
        // Values are in RelativeSide enum order: front, left, right, back, top, bottom.
        Map<Direction, List<Direction>> expected = Map.of(
              Direction.DOWN, List.of(Direction.DOWN, Direction.EAST, Direction.WEST, Direction.UP, Direction.NORTH, Direction.SOUTH),
              Direction.UP, List.of(Direction.UP, Direction.EAST, Direction.WEST, Direction.DOWN, Direction.SOUTH, Direction.NORTH),
              Direction.NORTH, List.of(Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.UP, Direction.DOWN),
              Direction.SOUTH, List.of(Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.NORTH, Direction.UP, Direction.DOWN),
              Direction.WEST, List.of(Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.UP, Direction.DOWN),
              Direction.EAST, List.of(Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.UP, Direction.DOWN)
        );
        for (Direction facing : Direction.values()) {
            List<Direction> physicalSides = expected.get(facing);
            for (int i = 0; i < RelativeSide.values().length; i++) {
                RelativeSide relative = RelativeSide.values()[i];
                Direction physical = physicalSides.get(i);
                assertSame(physical, relative.getDirection(facing), () -> "facing=" + facing + " relative=" + relative);
                assertSame(relative, RelativeSide.fromDirections(facing, physical),
                      () -> "facing=" + facing + " physical=" + physical);
            }
        }
    }
}
