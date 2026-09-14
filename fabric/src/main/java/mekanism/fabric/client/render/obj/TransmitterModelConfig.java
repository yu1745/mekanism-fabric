package mekanism.fabric.client.render.obj;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Physical-client lifecycle publication of the real client.toml gameRestart setting.
 * The lifecycle pins the loaded value at initialization; resource reloads rebuild sprite-bearing models.
 * Access before initialization or after shutdown is a lifecycle error, not an implicit configuration default.
 */
public final class TransmitterModelConfig {

    private static volatile BooleanSupplier owner;

    private TransmitterModelConfig() {
    }

    public static synchronized void install(BooleanSupplier newOwner) {
        Objects.requireNonNull(newOwner, "owner");
        if (owner != null) {
            throw new IllegalStateException("An opaque-transmitters owner is already installed");
        }
        owner = newOwner;
    }

    public static synchronized void remove(BooleanSupplier currentOwner) {
        if (owner != currentOwner) {
            throw new IllegalStateException("The requested opaque-transmitters owner is not installed");
        }
        owner = null;
    }

    public static boolean opaqueTransmitters() {
        BooleanSupplier current = owner;
        if (current == null) {
            throw new IllegalStateException("Physical client config has not installed the opaque-transmitters setting");
        }
        return current.getAsBoolean();
    }
}
