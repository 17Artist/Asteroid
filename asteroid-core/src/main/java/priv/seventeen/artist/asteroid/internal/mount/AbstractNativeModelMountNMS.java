package priv.seventeen.artist.asteroid.internal.mount;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS;
import priv.seventeen.artist.asteroid.internal.PacketHandlerImpl;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Inherited optional capability. Only NMSLoader may install/remove the shared host lifecycle. */
public abstract class AbstractNativeModelMountNMS implements NativeModelMountNMS {
    private final NativeMountPlatform platform;
    private Lifecycle lifecycle;

    protected AbstractNativeModelMountNMS(NativeMountPlatform platform) { this.platform = platform; }

    public final void bootstrapModelMounts(Plugin host, PacketHandlerImpl packets) {
        shutdownModelMounts();
        // Eventless load() and Folia continue to support other Asteroid capabilities, not mounts.
        if (host == null || PaperCompat.isFolia()) return;
        checkThread();
        Lifecycle installed = new Lifecycle(host, packets);
        try {
            Bukkit.getPluginManager().registerEvents(installed, host);
            installed.task = Bukkit.getScheduler().runTaskTimer(host, installed.registry::maintain, 1, 1);
            packets.setMountObserver(installed.registry);
            lifecycle = installed;
        } catch (RuntimeException | Error error) { installed.close(); throw error; }
    }

    public final void shutdownModelMounts() {
        if (lifecycle != null) {
            Lifecycle previous = lifecycle;
            lifecycle = null;
            previous.close();
        }
    }

    @Override public final OwnerLease openModelMount(Plugin owner, Entity originalHost, UUID epoch,
                                                    Consumer<Throwable> failure) {
        checkThread();
        Lifecycle current = lifecycle;
        if (current == null || current.closed) throw new IllegalStateException("Model mounts require NMSLoader.load(Plugin) and an active shared host");
        return current.registry.open(owner, originalHost, epoch, failure);
    }

    private static void checkThread() {
        if (PaperCompat.isFolia()) throw new UnsupportedOperationException("Model passenger leases do not yet support Folia regions");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Model passenger leases require the Paper owner thread");
    }

    private final class Lifecycle implements Listener, AutoCloseable {
        final Plugin host;
        final PacketHandlerImpl packets;
        final MountLeaseRegistry registry;
        BukkitTask task;
        boolean closed;
        Lifecycle(Plugin host, PacketHandlerImpl packets) {
            this.host = host; this.packets = packets;
            registry = new MountLeaseRegistry(platform, new MountEnvironment() {
                @Override public void checkThread() { AbstractNativeModelMountNMS.checkThread(); }
                @Override public boolean ready(Object connection) { return packets.ready(connection); }
                @Override public void send(Object connection, Object[] batch, BooleanSupplier owned, Consumer<Throwable> failure) {
                    packets.sendOwnedPackets(connection, batch, owned, failure);
                }
                @Override public void cleanup(Object connection, Object[] batch, Consumer<Throwable> failure) {
                    packets.sendOwnedCleanup(connection, batch, failure);
                }
            });
        }
        @EventHandler(priority = EventPriority.LOWEST)
        public void disable(PluginDisableEvent event) {
            if (event.getPlugin() == host) shutdownModelMounts();
            else registry.removePlugin(event.getPlugin());
        }
        @EventHandler(priority = EventPriority.LOWEST)
        public void quit(PlayerQuitEvent event) { registry.removeEntity(event.getPlayer().getUniqueId()); }
        @EventHandler(priority = EventPriority.MONITOR)
        public void world(PlayerChangedWorldEvent event) { registry.removeEntity(event.getPlayer().getUniqueId()); }
        @EventHandler(priority = EventPriority.MONITOR)
        public void death(EntityDeathEvent event) { registry.removeEntity(event.getEntity().getUniqueId()); }
        @EventHandler(priority = EventPriority.MONITOR)
        public void respawn(PlayerRespawnEvent event) { registry.removeEntity(event.getPlayer().getUniqueId()); }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (task != null) task.cancel();
            HandlerList.unregisterAll(this);
            registry.close();
            packets.clearMountObserver(registry);
        }
    }
}
