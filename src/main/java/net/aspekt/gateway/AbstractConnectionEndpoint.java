package net.aspekt.gateway;

import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base class for connection endpoints.
 *
 * <p>Manages the label and the thread-safe list of forwarding targets.
 * Subclasses implement {@link #send(ByteBuf)} for their specific transport.
 */
public abstract class AbstractConnectionEndpoint implements ConnectionEndpoint {

    private final String label;
    private final List<ConnectionEndpoint> targets = new CopyOnWriteArrayList<>();

    protected AbstractConnectionEndpoint(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public void addTarget(ConnectionEndpoint target) {
        targets.add(target);
    }

    @Override
    public List<ConnectionEndpoint> getTargets() {
        return targets;
    }

    /**
     * Fans out {@code buf} to every registered target.
     *
     * <p>Retains {@code buf} once per target before passing it, then releases
     * the caller's own reference so the overall reference count stays balanced.
     *
     * @param buf inbound data; ownership is transferred to this method
     */
    @Override
    public void onDataReceived(ByteBuf buf) {
        List<ConnectionEndpoint> t = targets;
        if (t.isEmpty()) {
            buf.release();
            return;
        }
        for (ConnectionEndpoint target : t) {
            target.send(buf.retain()); // retain once per target; target owns the extra ref
        }
        buf.release(); // release our own reference
    }
}
