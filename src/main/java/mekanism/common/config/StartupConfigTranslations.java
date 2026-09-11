package mekanism.common.config;

import mekanism.common.config.IConfigTranslation.ConfigTranslation;

/** Startup-only translation metadata kept narrow until the full config translation table is portable. */
final class StartupConfigTranslations {
    static final IConfigTranslation GEAR = new ConfigTranslation("startup.gear", "Gear Settings",
          "Settings for configuring Mekanism's gear settings. This config is not synced automatically between client and server. It is highly "
          + "recommended to ensure you are using the same values for this config on the server and client.", true);

    static final IConfigTranslation FREE_RUNNERS_ARMORED = new ConfigTranslation("gear.free_runners.armored", "Armored Free Runners",
          "Settings for configuring Armored Free Runners", "Edit Free Runner Settings");
    static final IConfigTranslation FREE_RUNNERS_ARMOR = new ConfigTranslation("gear.free_runners.armored.armor", "Armor",
          "Armor value of the Armored Free Runners");
    static final IConfigTranslation FREE_RUNNERS_TOUGHNESS = new ConfigTranslation("gear.free_runners.armored.toughness", "Toughness",
          "Toughness value of the Armored Free Runners.");
    static final IConfigTranslation FREE_RUNNERS_KNOCKBACK_RESISTANCE = new ConfigTranslation(
          "gear.free_runners.armored.knockback_resistance", "Knockback Resistance",
          "Knockback resistance value of the Armored Free Runners.");

    static final IConfigTranslation JETPACK_ARMORED = new ConfigTranslation("gear.jetpack.armored", "Armored Jetpack",
          "Settings for configuring Armored Jetpacks", "Edit Jetpack Settings");
    static final IConfigTranslation JETPACK_ARMOR = new ConfigTranslation("gear.jetpack.armored.armor", "Armor",
          "Armor value of the Armored Jetpacks");
    static final IConfigTranslation JETPACK_TOUGHNESS = new ConfigTranslation("gear.jetpack.armored.toughness", "Toughness",
          "Toughness value of the Armored Jetpacks.");
    static final IConfigTranslation JETPACK_KNOCKBACK_RESISTANCE = new ConfigTranslation(
          "gear.jetpack.armored.knockback_resistance", "Knockback Resistance",
          "Knockback resistance value of the Armored Jetpacks.");

    static final IConfigTranslation MEKA_SUIT = new ConfigTranslation("gear.meka_suit", "MekaSuit",
          "Settings for configuring the MekaSuit", true);
    static final IConfigTranslation MEKA_SUIT_ARMOR_HELMET = new ConfigTranslation("gear.meka_suit.armor.helmet", "Helmet Armor",
          "Armor value of MekaSuit Helmets.");
    static final IConfigTranslation MEKA_SUIT_ARMOR_CHESTPLATE = new ConfigTranslation("gear.meka_suit.armor.chestplate", "BodyArmor Armor",
          "Armor value of MekaSuit BodyArmor.");
    static final IConfigTranslation MEKA_SUIT_ARMOR_LEGGINGS = new ConfigTranslation("gear.meka_suit.armor.leggings", "Pants Armor",
          "Armor value of MekaSuit Pants.");
    static final IConfigTranslation MEKA_SUIT_ARMOR_BOOTS = new ConfigTranslation("gear.meka_suit.armor.boots", "Boots Armor",
          "Armor value of MekaSuit Boots.");
    static final IConfigTranslation MEKA_SUIT_TOUGHNESS = new ConfigTranslation(".gear.meka_suit.toughness", "Toughness",
          "Toughness value of the MekaSuit.");
    static final IConfigTranslation MEKA_SUIT_KNOCKBACK_RESISTANCE = new ConfigTranslation(
          "gear.meka_suit.knockback_resistance", "Knockback Resistance", "Knockback resistance value of the MekaSuit.");

    private StartupConfigTranslations() {
    }
}
