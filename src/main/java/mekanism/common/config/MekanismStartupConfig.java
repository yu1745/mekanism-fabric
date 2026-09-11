package mekanism.common.config;

import mekanism.common.config.value.CachedFloatValue;
import mekanism.common.config.value.CachedIntValue;
import mekanism.fabric.config.MekanismConfigSpec;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;

public class MekanismStartupConfig extends BaseMekanismConfig {

    private static final String ARMORED_SUBCATEGORY = "armored";

    private final MekanismConfigSpec configSpec;

    //Armored Free Runner
    public final CachedIntValue armoredFreeRunnerArmor;
    public final CachedFloatValue armoredFreeRunnerToughness;
    public final CachedFloatValue armoredFreeRunnerKnockbackResistance;
    //Armored Jetpack
    public final CachedIntValue armoredJetpackArmor;
    public final CachedFloatValue armoredJetpackToughness;
    public final CachedFloatValue armoredJetpackKnockbackResistance;
    //MekaSuit
    public final CachedIntValue mekaSuitHelmetArmor;
    public final CachedIntValue mekaSuitBodyArmorArmor;
    public final CachedIntValue mekaSuitPantsArmor;
    public final CachedIntValue mekaSuitBootsArmor;
    public final CachedFloatValue mekaSuitToughness;
    public final CachedFloatValue mekaSuitKnockbackResistance;

    public MekanismStartupConfig() {
        MekanismConfigSpec.Builder builder = new MekanismConfigSpec.Builder();

        StartupConfigTranslations.GEAR.applyToBuilder(builder).push("gear");

        StartupConfigTranslations.FREE_RUNNERS_ARMORED.applyToBuilder(builder).push(ARMORED_SUBCATEGORY + "_free_runner");
        armoredFreeRunnerArmor = CachedIntValue.wrap(this, StartupConfigTranslations.FREE_RUNNERS_ARMOR.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("armor", 3, 0, Integer.MAX_VALUE));
        armoredFreeRunnerToughness = CachedFloatValue.wrap(this, StartupConfigTranslations.FREE_RUNNERS_TOUGHNESS.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("toughness", 2.0, 0, Float.MAX_VALUE));
        armoredFreeRunnerKnockbackResistance = CachedFloatValue.wrap(this, StartupConfigTranslations.FREE_RUNNERS_KNOCKBACK_RESISTANCE.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("knockbackResistance", 0.0, 0, Float.MAX_VALUE));
        builder.pop();

        StartupConfigTranslations.JETPACK_ARMORED.applyToBuilder(builder).push(ARMORED_SUBCATEGORY + "_jetpack");
        armoredJetpackArmor = CachedIntValue.wrap(this, StartupConfigTranslations.JETPACK_ARMOR.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("armor", 8, 0, Integer.MAX_VALUE));
        armoredJetpackToughness = CachedFloatValue.wrap(this, StartupConfigTranslations.JETPACK_TOUGHNESS.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("toughness", 2.0, 0, Float.MAX_VALUE));
        armoredJetpackKnockbackResistance = CachedFloatValue.wrap(this, StartupConfigTranslations.JETPACK_KNOCKBACK_RESISTANCE.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("knockbackResistance", 0.0, 0, Float.MAX_VALUE));
        builder.pop();

        StartupConfigTranslations.MEKA_SUIT.applyToBuilder(builder).push("mekasuit");
        mekaSuitHelmetArmor = CachedIntValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_ARMOR_HELMET.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("helmetArmor", ArmorMaterials.NETHERITE.value().defense().get(ArmorItem.Type.HELMET), 0, Integer.MAX_VALUE));
        mekaSuitBodyArmorArmor = CachedIntValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_ARMOR_CHESTPLATE.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("bodyArmorArmor", ArmorMaterials.NETHERITE.value().defense().get(ArmorItem.Type.CHESTPLATE), 0, Integer.MAX_VALUE));
        mekaSuitPantsArmor = CachedIntValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_ARMOR_LEGGINGS.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("pantsArmor", ArmorMaterials.NETHERITE.value().defense().get(ArmorItem.Type.LEGGINGS), 0, Integer.MAX_VALUE));
        mekaSuitBootsArmor = CachedIntValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_ARMOR_BOOTS.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("bootsArmor", ArmorMaterials.NETHERITE.value().defense().get(ArmorItem.Type.BOOTS), 0, Integer.MAX_VALUE));
        mekaSuitToughness = CachedFloatValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_TOUGHNESS.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("toughness", ArmorMaterials.NETHERITE.value().toughness(), 0, Float.MAX_VALUE));
        mekaSuitKnockbackResistance = CachedFloatValue.wrap(this, StartupConfigTranslations.MEKA_SUIT_KNOCKBACK_RESISTANCE.applyToBuilder(builder)
              .gameRestart()
              .defineInRange("knockbackResistance", ArmorMaterials.NETHERITE.value().knockbackResistance(), 0, Float.MAX_VALUE));
        builder.pop();//End mekasuit

        builder.pop();//End gear
        configSpec = builder.build();
    }

    @Override
    public String getFileName() {
        return "startup";
    }

    @Override
    public String getTranslation() {
        return "Startup Config";
    }

    @Override
    public MekanismConfigSpec getConfigSpec() {
        return configSpec;
    }

    @Override
    public MekanismConfigType getConfigType() {
        return MekanismConfigType.STARTUP;
    }
}