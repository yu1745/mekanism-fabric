package mekanism.client.model.data;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import mekanism.client.model.data.TransmitterModelData.Diversion;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.Direction;

public sealed class TransmitterModelData permits Diversion {

    private static final Direction[] DIRECTIONS = Direction.values();

    private final Map<Direction, ConnectionType> connections = new EnumMap<>(Direction.class);
    //Always an unmodifiable view, even before freeze: a caller must never be able to mutate the map through
    //getConnectionsMap() while the builder is still assembling the snapshot
    private final Map<Direction, ConnectionType> connectionView = Collections.unmodifiableMap(connections);
    private boolean hasColor;
    private boolean frozen;

    public void setConnectionData(Direction direction, ConnectionType connectionType) {
        ensureMutable();
        connections.put(direction, connectionType);
    }

    /** Unmodifiable view; safe to publish as a render-attachment cache key. */
    public Map<Direction, ConnectionType> getConnectionsMap() {
        return connectionView;
    }

    /**
     * Locks this snapshot against further mutation and returns it. Producers call this before publishing (render
     * attachment / model data) so async chunk meshing can never observe a mutating connections map through a cache
     * key. Subsequent {@link #setConnectionData} calls fail loudly instead of silently corrupting a published key.
     */
    public TransmitterModelData freeze() {
        frozen = true;
        return this;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("TransmitterModelData snapshot is frozen; build a new snapshot instead of mutating a published one");
        }
    }

    public ConnectionType getConnectionType(Direction side) {
        return connections.get(side);
    }

    public void setHasColor(boolean hasColor) {
        ensureMutable();
        this.hasColor = hasColor;
    }

    public boolean getHasColor() {
        return hasColor;
    }

    public boolean check(ConnectionType... types) {
        //Cached Direction.values() (same declaration order EnumUtils.DIRECTIONS used) avoids the EnumUtils slice drag
        if (types.length != DIRECTIONS.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (connections.get(DIRECTIONS[i]) != types[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        return o instanceof TransmitterModelData other && hasColor == other.hasColor && connections.equals(other.connections);
    }

    @Override
    public int hashCode() {
        int result = connections.hashCode();
        result = 31 * result + Boolean.hashCode(hasColor);
        return result;
    }

    public static final class Diversion extends TransmitterModelData {

        @Override
        public void setHasColor(boolean hasColor) {
            //Don't allow setting has color for diversion model data
        }
    }
}