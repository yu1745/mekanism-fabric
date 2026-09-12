package mekanism.common.config;

import java.util.Objects;

/**
 * Common-side access seam for the physical client's sole window configuration owner.
 *
 * <p>This class deliberately has no platform or client imports and no fallback owner. Persisted window operations
 * are only meaningful while the physical client lifetime has installed an owner, and only on that owner's thread.</p>
 */
public final class ClientWindowConfigAccess {

    public record Position(int x, int y, boolean pinned) {
    }

    public interface Owner {
        Position get(String saveName);

        void update(String saveName, int x, int y, boolean pinned, boolean canPin);
    }

    private record Registration(Owner owner, Thread thread) {
    }

    private static Registration registration;

    private ClientWindowConfigAccess() {
    }

    public static synchronized void install(Owner owner, Thread ownerThread) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ownerThread, "ownerThread");
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Client window config must be installed by its owner thread");
        }
        if (registration != null) {
            throw new IllegalStateException("A client window config owner is already installed");
        }
        registration = new Registration(owner, ownerThread);
    }

    public static Position get(String saveName) {
        return requireOwner().get(Objects.requireNonNull(saveName, "saveName"));
    }

    public static void update(String saveName, int x, int y, boolean pinned, boolean canPin) {
        requireOwner().update(Objects.requireNonNull(saveName, "saveName"), x, y, pinned, canPin);
    }

    public static synchronized void remove(Owner owner) {
        Registration current = registration;
        if (current == null || current.owner() != owner) {
            throw new IllegalStateException("The requested client window config owner is not installed");
        }
        if (Thread.currentThread() != current.thread()) {
            throw new IllegalStateException("Client window config must be removed by its owner thread");
        }
        registration = null;
    }

    public static synchronized boolean isInstalled(Owner owner) {
        return registration != null && registration.owner() == owner;
    }

    public static synchronized void verifyOwnerThread(Owner owner) {
        Registration current = registration;
        if (current == null || current.owner() != owner) {
            throw new IllegalStateException("The requested client window config owner is not installed");
        }
        if (Thread.currentThread() != current.thread()) {
            throw new IllegalStateException("Client window config accessed from a non-owner thread");
        }
    }

    private static synchronized Owner requireOwner() {
        Registration current = registration;
        if (current == null) {
            throw new IllegalStateException("Client window config is not initialized");
        }
        if (Thread.currentThread() != current.thread()) {
            throw new IllegalStateException("Client window config accessed from a non-owner thread");
        }
        return current.owner();
    }
}
