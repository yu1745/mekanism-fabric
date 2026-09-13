package mekanism.common.lib.security;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
import mekanism.api.annotations.NothingNullByDefault;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side owner of the last-known security data per player UUID.
 *
 * <p>Fabric port ownership move (Task052): the original source keeps this map as the {@code MekanismClient.clientSecurityMap} static field, populated by the
 * physical-client packet receivers and cleared on client reset. The map itself is domain data read by common {@link SecurityUtils} through the client branch, so
 * the Fabric port hosts it here as the real owner; the excluded {@code MekanismClient} entrypoint aliases this exact map through its existing public field, the
 * same pattern used for {@code Mekanism.activeVibrators} and {@code MekanismServerState}. This class is only mutated from physical-client code paths.</p>
 */
@NothingNullByDefault
public final class ClientSecurityData {

    private static final Map<UUID, SecurityData> CLIENT_SECURITY_MAP = new Object2ObjectOpenHashMap<>();

    private ClientSecurityData() {
    }

    public static Map<UUID, SecurityData> securityMap() {
        return CLIENT_SECURITY_MAP;
    }

    public static SecurityData getOrDefault(@Nullable UUID uuid, SecurityData dummy) {
        return CLIENT_SECURITY_MAP.getOrDefault(uuid, dummy);
    }

    public static void clear() {
        CLIENT_SECURITY_MAP.clear();
    }
}
