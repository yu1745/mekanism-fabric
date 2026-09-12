package mekanism.api.text;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

@MethodsReturnNonnullByDefault
public interface IHasTranslationKey {

    /**
     * Gets the translation key for this object.
     */
    String getTranslationKey();

    /**
     * Helper interface for enums that expose their translated display name.
     * @since 10.7.3
     */
    interface IHasEnumNameTranslationKey extends IHasTranslationKey {

        @NotNull
        default Component getTranslatedName() {
            return TextComponentUtil.translate(getTranslationKey());
        }
    }
}