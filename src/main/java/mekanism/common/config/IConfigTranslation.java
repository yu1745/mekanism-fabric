package mekanism.common.config;

import mekanism.fabric.config.MekanismConfigSpec;
import org.jetbrains.annotations.Nullable;

public interface IConfigTranslation {

    String getTranslationKey();

    String title();

    String tooltip();

    @Nullable
    default String button() {
        return null;
    }

    default MekanismConfigSpec.Builder applyToBuilder(MekanismConfigSpec.Builder builder) {
        return builder.translation(getTranslationKey()).comment(tooltip());
    }

    @Nullable
    static String getSectionTitle(String title, boolean isSection) {
        return isSection ? "Edit " + title : null;
    }

    record ConfigTranslation(String getTranslationKey, String title, String tooltip, @Nullable String button) implements IConfigTranslation {

        public ConfigTranslation(String getTranslationKey, String title, String tooltip) {
            this(getTranslationKey, title, tooltip, null);
        }

        public ConfigTranslation(String getTranslationKey, String title, String tooltip, boolean isSection) {
            this(getTranslationKey, title, tooltip, getSectionTitle(title, isSection));
        }
    }
}
