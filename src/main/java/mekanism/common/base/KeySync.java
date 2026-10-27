package mekanism.common.base;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeySync {

    public static final int ASCEND = 0;
    public static final int BOOST = 1;

    /** Fabric port delta: owner of the excluded entrypoint's {@code keyMap} field. */
    public static final KeySync INSTANCE = new KeySync();

    //An integrated server and its client share this owner but access it from different game threads.
    public final Map<UUID, KeySet> keys = new ConcurrentHashMap<>();

    public KeySet getPlayerKeys(UUID playerUUID) {
        return keys.get(playerUUID);
    }

    public void add(UUID playerUUID, int key) {
        keys.compute(playerUUID, (id, existing) -> {
            if (existing == null) {
                return new KeySet(key);
            }
            existing.keysActive.add(key);
            return existing;
        });
    }

    public void remove(UUID playerUUID, int key) {
        KeySet playerKeys = keys.get(playerUUID);
        if (playerKeys != null) {
            playerKeys.keysActive.remove(key);
        }
    }

    public boolean has(UUID playerUUID, int key) {
        KeySet playerKeys = keys.get(playerUUID);
        return playerKeys != null && playerKeys.keysActive.contains(key);
    }

    public void update(UUID playerUUID, int key, boolean add) {
        if (add) {
            add(playerUUID, key);
        } else {
            remove(playerUUID, key);
        }
    }

    public static class KeySet {

        public final IntSet keysActive = IntSets.synchronize(new IntOpenHashSet());

        public KeySet(int key) {
            keysActive.add(key);
        }
    }
}