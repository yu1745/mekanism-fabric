package mekanism.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu hook for Mekanism's configuration.
 * <p>
 * The official NeoForge build gets a configuration screen for free: NeoForge renders one for any registered config and
 * exposes it through {@code IConfigScreenFactory}. Fabric has no loader-provided config screen, and Mod Menu is the
 * conventional place to hang one. Mekanism's config is a set of TOML files that are read at startup, so this screen
 * lists them (and whether they exist) rather than pretending to edit them in game: editing a value still means editing
 * the file and restarting, which is what the screen says.
 */
public class FabricModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new GuiMekanismConfig(parent);
    }
}
