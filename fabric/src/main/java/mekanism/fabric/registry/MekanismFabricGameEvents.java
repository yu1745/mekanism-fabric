package mekanism.fabric.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gameevent.GameEvent;

/** Temporary single declaration proving the Fabric registry path. */
public final class MekanismFabricGameEvents {
    public static final FabricDeferredRegister<GameEvent> REGISTER =
          new FabricDeferredRegister<>(Registries.GAME_EVENT, "mekanism");
    public static final FabricDeferredHolder<GameEvent> SEISMIC_VIBRATION =
          REGISTER.register("seismic_vibration", () -> new GameEvent(64));

    private MekanismFabricGameEvents() {
    }
}
