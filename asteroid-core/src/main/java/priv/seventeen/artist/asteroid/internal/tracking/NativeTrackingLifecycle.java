package priv.seventeen.artist.asteroid.internal.tracking;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import priv.seventeen.artist.asteroid.util.PaperCompat;

/** NMS-instance lifecycle only. No per-entity scheduler or world/player scan. */
public final class NativeTrackingLifecycle implements Listener, AutoCloseable {
    private final Plugin plugin;
    private final NativeTrackingRangeNMS nativeOwner;
    private boolean closed;
    private NativeTrackingLifecycle(Plugin plugin, NativeTrackingRangeNMS nativeOwner) {
        this.plugin = plugin; this.nativeOwner = nativeOwner;
    }
    public static NativeTrackingLifecycle install(Plugin plugin, IEntityNMS nativeOwner) {
        if (PaperCompat.isFolia() || !(nativeOwner instanceof NativeTrackingRangeNMS ranges)) return null;
        var lifecycle = new NativeTrackingLifecycle(plugin, ranges);
        if (plugin != null) Bukkit.getPluginManager().registerEvents(lifecycle, plugin);
        return lifecycle;
    }
    @EventHandler public void disabled(PluginDisableEvent event) { if (event.getPlugin() == plugin) close(); }
    @Override public void close() {
        if (closed) return;
        nativeOwner.shutdownTrackingRanges();
        closed = true; HandlerList.unregisterAll((Listener) this);
    }
}
