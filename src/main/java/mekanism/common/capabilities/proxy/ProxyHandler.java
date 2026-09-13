package mekanism.common.capabilities.proxy;

import mekanism.api.annotations.FieldsAreNotNullByDefault;
import mekanism.common.capabilities.holder.IHolder;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@FieldsAreNotNullByDefault
public class ProxyHandler {

    @Nullable
    protected final Direction side;
    @Nullable
    private final IHolder holder;
    protected final boolean readOnly;
    //Fabric port lifetime support: set once the owner drops this cached view; retained references must stop consulting the old owner
    private volatile boolean revoked;

    protected ProxyHandler(@Nullable Direction side, @Nullable IHolder holder) {
        this.side = side;
        this.holder = holder;
        this.readOnly = this.side == null;
    }

    protected boolean readOnlyInsert() {
        return readOnly || holder != null && !holder.canInsert(side);
    }

    protected boolean readOnlyExtract() {
        return readOnly || holder != null && !holder.canExtract(side);
    }

    /**
     * Fabric port lifetime support: permanently revokes this proxy. After revocation the proxy keeps being a safe, stable object, but every read, setter, and
     * transfer fails closed without consulting the old owner: reads report empty-handler defaults, setters and transfers are denied. Revocation complements the
     * official read-only gating ({@code side == null}) and never re-enables a revoked view. The wrapped base handler itself stays untouched so fresh proxies and
     * owner rollback over it remain valid.
     */
    public void revoke() {
        revoked = true;
    }

    /**
     * Fabric port lifetime support: whether {@link #revoke()} has permanently closed this proxy against its old owner.
     */
    protected final boolean isRevoked() {
        return revoked;
    }
}