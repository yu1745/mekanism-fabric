package mekanism.api;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.IntFunction;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.text.IHasTranslationKey.IHasEnumNameTranslationKey;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

@NothingNullByDefault
public enum RelativeSide implements IHasEnumNameTranslationKey, StringRepresentable {
    FRONT("front"),
    LEFT("left"),
    RIGHT("right"),
    BACK("back"),
    TOP("top"),
    BOTTOM("bottom");

    /**
     * Codec for serializing sides based on their name.
     *
     * @since 10.6.0
     */
    public static final Codec<RelativeSide> CODEC = StringRepresentable.fromEnum(RelativeSide::values);
    /**
     * Gets a side by index, wrapping for out of bounds indices.
     *
     * @since 10.6.0
     */
    public static final IntFunction<RelativeSide> BY_ID = ByIdMap.continuous(RelativeSide::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
    /**
     * Stream codec for syncing sides by index.
     *
     * @since 10.6.0
     */
    public static final StreamCodec<ByteBuf, RelativeSide> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, RelativeSide::ordinal);

    private final String serializedName;
    private final String translationKey;

    RelativeSide(String path) {
        this.serializedName = name().toLowerCase(Locale.ROOT);
        this.translationKey = Util.makeDescriptionId("side",
              ResourceLocation.fromNamespaceAndPath("mekanism", path));
    }

    @Override
    public String getTranslationKey() {
        return translationKey;
    }

    /**
     * Gets the {@link Direction} from the block based on what side it is facing.
     *
     * @param facing The direction the block is facing.
     *
     * @return The direction representing which side of the block this RelativeSide is actually representing based on the direction it is facing.
     */
    public Direction getDirection(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing == Direction.DOWN || facing == Direction.UP ? Direction.EAST : facing.getClockWise();
            case RIGHT -> facing == Direction.DOWN || facing == Direction.UP ? Direction.WEST : facing.getCounterClockWise();
            case TOP -> switch (facing) {
                case DOWN -> Direction.NORTH;
                case UP -> Direction.SOUTH;
                default -> Direction.UP;
            };
            case BOTTOM -> switch (facing) {
                case DOWN -> Direction.SOUTH;
                case UP -> Direction.NORTH;
                default -> Direction.DOWN;
            };
        };
    }

    /**
     * Gets the {@link RelativeSide} based on a side, and the facing direction of a block.
     *
     * @param facing The direction the block is facing.
     * @param side   The side of the block we want to know what {@link RelativeSide} it is.
     *
     * @return the {@link RelativeSide} based on a side, and the facing direction of a block.
     *
     * @apiNote The calculations for what side is what when facing upwards or downwards, is done as if it was facing NORTH and rotated around the X-axis
     */
    public static RelativeSide fromDirections(Direction facing, Direction side) {
        if (side == facing) {
            return FRONT;
        } else if (side == facing.getOpposite()) {
            return BACK;
        } else if (facing == Direction.DOWN || facing == Direction.UP) {
            return switch (side) {
                case NORTH -> facing == Direction.DOWN ? TOP : BOTTOM;
                case SOUTH -> facing == Direction.DOWN ? BOTTOM : TOP;
                case WEST -> RIGHT;
                case EAST -> LEFT;
                default -> throw new IllegalStateException("Case should have been caught earlier.");
            };
        } else if (side == Direction.DOWN) {
            return BOTTOM;
        } else if (side == Direction.UP) {
            return TOP;
        } else if (side == facing.getCounterClockWise()) {
            return RIGHT;
        } else if (side == facing.getClockWise()) {
            return LEFT;
        }
        //Fall back to front, should never get here
        return FRONT;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}