package priv.seventeen.artist.asteroid.internal.mount;

/** Internal observer invoked after external packet cancellation has been resolved. Never Bukkit. */
public interface MountPacketObserver {
    void acceptedInput(Object connection, Object packet);
    void acceptedOutput(Object connection, Object packet);
    void disconnected(Object connection);
}
