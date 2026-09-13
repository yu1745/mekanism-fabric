package mekanism.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Last-known-username cache, persisted as {@code usernamecache.json} in the game directory.
 *
 * <p>Fabric port ownership (Task052): NeoForge ships a server-wide {@code UsernameCache} with exactly this behavior — a process-wide
 * UUID&rarr;name map, persisted to {@code usernamecache.json} next to the game's other root files, populated when players log in, and consulted for
 * owner-name display/sync (for example {@code Frequency}'s owner-name lookup). Fabric has no equivalent, so Mekanism owns the responsibility here.
 * Population is wired by the Fabric play-join lifecycle ({@code mekanism.fabric.MekanismUsernameLifecycle}, Task052): every accepted server-side play
 * connection records the player's current profile name; the JSON is rewritten on every change (the file is tiny, matching the original's
 * save-on-modification semantics).</p>
 *
 * <p>Concurrency model: the map is process-wide and reached from the server thread, the client thread (integrated server shares the process) and netty
 * threads. Initialization, mutation, read and file I/O all synchronize on {@link #FILE_LOCK}, so there is exactly one coherent map instance — never two
 * independently loaded copies. The map field is {@code volatile} so any unsynchronized reader still observes a fully initialized map.</p>
 */
public final class UsernameCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("Mekanism");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<UUID, String>>() {
    }.getType();
    private static final Path FILE = FabricLoader.getInstance().getGameDir().resolve("usernamecache.json").toAbsolutePath().normalize();

    private static final Object FILE_LOCK = new Object();
    private static volatile Map<UUID, String> map;

    private UsernameCache() {
    }

    @Nullable
    public static String getLastKnownUsername(UUID uuid) {
        synchronized (FILE_LOCK) {
            return map(false).get(uuid);
        }
    }

    public static void setLastKnownUsername(UUID uuid, String username) {
        String old;
        synchronized (FILE_LOCK) {
            old = map(true).put(uuid, username);
        }
        if (old == null || !old.equals(username)) {
            save();
        }
    }

    public static boolean containsUUID(UUID uuid) {
        synchronized (FILE_LOCK) {
            return map(false).containsKey(uuid);
        }
    }

    private static Map<UUID, String> map(boolean markLoaded) {
        //Lazy load under FILE_LOCK on first access; server and client share the same process-lifetime cache file.
        //The volatile field guarantees the fully initialized map is published to any unsynchronized reader.
        if (map == null) {
            Map<UUID, String> loaded = new Object2ObjectOpenHashMap<>();
            synchronized (FILE_LOCK) {
                if (Files.exists(FILE)) {
                    try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                        Map<UUID, String> read = GSON.fromJson(reader, TYPE);
                        if (read != null) {
                            loaded.putAll(read);
                        }
                    } catch (IOException | RuntimeException e) {
                        LOGGER.warn("Failed to read username cache file {}, starting with an empty cache", FILE, e);
                    }
                }
            }
            map = loaded;
        }
        return map;
    }

    private static void save() {
        synchronized (FILE_LOCK) {
            try {
                Files.createDirectories(FILE.getParent());
                try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                    GSON.toJson(map(false), TYPE, writer);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to save username cache file {}", FILE, e);
            }
        }
    }
}
