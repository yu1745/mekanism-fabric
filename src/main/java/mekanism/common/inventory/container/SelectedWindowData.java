package mekanism.common.inventory.container;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import mekanism.common.config.ClientWindowConfigAccess;
import mekanism.common.config.IConfigTranslation;
import mekanism.common.config.value.CachedBooleanValue;
import mekanism.common.config.value.CachedIntValue;
import mekanism.common.util.text.TextUtils;
import net.minecraft.Util;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ByIdMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SelectedWindowData {

    public static final SelectedWindowData UNSPECIFIED = new SelectedWindowData(WindowType.UNSPECIFIED);

    @NotNull
    public final WindowType type;
    public final byte extraData;

    public SelectedWindowData(@NotNull WindowType type) {
        this(type, (byte) 0);
    }

    /**
     * It is expected to only call this with a piece of extra data that is valid. If it is not valid this end up treating it as zero instead.
     */
    public SelectedWindowData(@NotNull WindowType type, byte extraData) {
        this.type = Objects.requireNonNull(type);
        this.extraData = this.type.isValid(extraData) ? extraData : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SelectedWindowData other = (SelectedWindowData) o;
        return extraData == other.extraData && type == other.type;
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + extraData;
    }

    /**
     * @apiNote Only call this on the client.
     */
    public void updateLastPosition(int x, int y, boolean pinned) {
        String saveName = type.getSaveName(extraData);
        if (saveName != null) {
            ClientWindowConfigAccess.update(saveName, x, y, pinned, type.canPin());
        }
    }

    public boolean wasPinned() {
        return getLastPosition().pinned();
    }

    /**
     * @apiNote Only call this on the client.
     */
    public WindowPosition getLastPosition() {
        String saveName = type.getSaveName(extraData);
        if (saveName != null) {
            ClientWindowConfigAccess.Position position = ClientWindowConfigAccess.get(saveName);
            return new WindowPosition(position.x(), position.y(), position.pinned());
        }
        return new WindowPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, false);
    }

    public record CachedWindowPosition(CachedIntValue x, CachedIntValue y, @Nullable CachedBooleanValue pinned) {
    }

    public record WindowPosition(int x, int y, boolean pinned) {
    }

    public enum WindowType {
        COLOR("color", false),
        CONFIRMATION("confirmation", false),
        CRAFTING("crafting", true, (byte) 3),
        MEKA_SUIT_HELMET("mekasuit_helmet", false),
        RENAME("rename", false),
        SKIN_SELECT("skin_select", false),
        SIDE_CONFIG("side_config", true),
        TRANSPORTER_CONFIG("transporter_config", true),
        UPGRADE("upgrade", true),
        /**
         * For use by windows that don't actually have any server side specific logic required, or don't persist their position.
         */
        UNSPECIFIED(null, false);

        public static final IntFunction<WindowType> BY_ID = ByIdMap.continuous(WindowType::ordinal, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
        public static final StreamCodec<ByteBuf, WindowType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, WindowType::ordinal);

        /**
         * @implNote This name needs to be lower case
         */
        @Nullable
        private final String saveName;
        private final boolean canPin;
        private final byte maxData;

        WindowType(@Nullable String saveName, boolean canPin) {
            this(saveName, canPin, (byte) 1);
        }

        WindowType(@Nullable String saveName, boolean canPin, byte maxData) {
            this.saveName = saveName;
            this.maxData = maxData;
            this.canPin = canPin;
        }

        @Nullable
        String getSaveName(byte extraData) {
            return maxData == 1 ? saveName : saveName + extraData;
        }

        public List<ConfigSaveData> getSavePaths() {
            if (saveName == null) {
                return Collections.emptyList();
            }
            List<ConfigSaveData> savePaths = new ArrayList<>(maxData);
            for (int i = 0; i < maxData; i++) {
                savePaths.add(ConfigSaveData.create(saveName, i, maxData, canPin));
            }
            return savePaths;
        }

        public boolean canPin() {
            return canPin;
        }

        public boolean isValid(byte extraData) {
            return extraData >= 0 && extraData < maxData;
        }

        public record ConfigSaveData(String savePath, String title, String tooltip, @Nullable String button, String getTranslationKey) implements IConfigTranslation {

            private static ConfigSaveData create(String savePath, int index, int size, boolean canPin) {
                String saveName = TextUtils.formatAndCapitalize(savePath);
                String title = saveName + " Window Position";
                String tooltip = "The last position the " + saveName + " window was in when it was closed";
                if (size > 1) {
                    savePath += index;
                    title += " " + (index + 1);
                }
                if (canPin) {
                    tooltip += ", and whether it was pinned";
                }
                return new ConfigSaveData(savePath, title, tooltip + ".", "Edit " + saveName + " Position",
                      Util.makeDescriptionId("configuration", ResourceLocation.fromNamespaceAndPath("mekanism", "client.last_window_positions." + savePath))
                );
            }
        }
    }
}