package mekanism.common.base;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric port alias for the excluded entrypoint's {@code Mekanism.modulesLoaded}: the addon module lifecycle list
 * (the {@code MekanismServerState} alias precedent - the original mutators reach the owner list here).
 */
public final class ModModuleState {

    private static final List<IModModule> MODULES_LOADED = new ArrayList<>();

    private ModModuleState() {
    }

    public static List<IModModule> modulesLoaded() {
        return MODULES_LOADED;
    }

    public static synchronized void addModule(IModModule modModule) {
        MODULES_LOADED.add(modModule);
    }
}
