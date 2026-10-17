package priv.seventeen.artist.asteroid.internal.mount;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owner-thread and transport seams; tests need no live Bukkit scheduler or world. */
interface MountEnvironment {
    void checkThread();
    boolean ready(Object connection);
    void send(Object connection, Object[] packets, BooleanSupplier stillOwned, Consumer<Throwable> failure);
    default void cleanup(Object connection, Object[] packets, Consumer<Throwable> failure) {
        send(connection, packets, () -> true, failure);
    }
}
