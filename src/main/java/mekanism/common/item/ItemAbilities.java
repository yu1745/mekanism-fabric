package mekanism.common.item;

import java.util.Set;
import mekanism.api.ItemAbility;

/**
 * Fabric port delta: project-owned mirror of the NeoForge {@code ItemAbilities} catalog, restricted to the actions Mekanism actually queries or answers,
 * with token names kept byte-identical (note {@link #HOE_TILL} is named {@code "till"} upstream) so that interned tokens match the official identifiers.
 * The NeoForge-only actions that have no Mekanism caller ({@code sword_sweep}, {@code shield_block}, fishing rod/trident/brush/spyglass families) are not
 * declared; add them with their upstream names if a caller arrives.
 */
public final class ItemAbilities {

    public static final ItemAbility AXE_DIG = ItemAbility.get("axe_dig");
    public static final ItemAbility HOE_DIG = ItemAbility.get("hoe_dig");
    public static final ItemAbility SHOVEL_DIG = ItemAbility.get("shovel_dig");
    public static final ItemAbility PICKAXE_DIG = ItemAbility.get("pickaxe_dig");
    public static final ItemAbility SWORD_DIG = ItemAbility.get("sword_dig");
    public static final ItemAbility SHEARS_DIG = ItemAbility.get("shears_dig");

    public static final ItemAbility AXE_STRIP = ItemAbility.get("axe_strip");
    public static final ItemAbility AXE_SCRAPE = ItemAbility.get("axe_scrape");
    public static final ItemAbility AXE_WAX_OFF = ItemAbility.get("axe_wax_off");
    public static final ItemAbility SHOVEL_FLATTEN = ItemAbility.get("shovel_flatten");
    public static final ItemAbility SHOVEL_DOUSE = ItemAbility.get("shovel_douse");
    public static final ItemAbility HOE_TILL = ItemAbility.get("till");

    public static final ItemAbility SHEARS_HARVEST = ItemAbility.get("shears_harvest");
    public static final ItemAbility SHEARS_DISARM = ItemAbility.get("shears_disarm");
    public static final ItemAbility SHEARS_TRIM = ItemAbility.get("shears_trim");

    public static final ItemAbility FIRESTARTER_LIGHT = ItemAbility.get("firestarter_light");

    // Default actions supported by each tool type, mirroring the upstream sets
    public static final Set<ItemAbility> DEFAULT_AXE_ACTIONS = Set.of(AXE_DIG, AXE_STRIP, AXE_SCRAPE, AXE_WAX_OFF);
    public static final Set<ItemAbility> DEFAULT_HOE_ACTIONS = Set.of(HOE_DIG, HOE_TILL);
    public static final Set<ItemAbility> DEFAULT_SHOVEL_ACTIONS = Set.of(SHOVEL_DIG, SHOVEL_FLATTEN, SHOVEL_DOUSE);
    public static final Set<ItemAbility> DEFAULT_SHEARS_ACTIONS = Set.of(SHEARS_DIG, SHEARS_HARVEST, SHEARS_DISARM, SHEARS_TRIM);

    private ItemAbilities() {
    }
}
