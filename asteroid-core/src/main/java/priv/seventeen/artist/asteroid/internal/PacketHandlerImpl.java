/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.asteroid.internal;

import io.netty.channel.*;
import io.netty.util.ReferenceCountUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.packet.*;
import priv.seventeen.artist.asteroid.util.FoliaScheduler;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import priv.seventeen.artist.asteroid.internal.mount.MountPacketObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public class PacketHandlerImpl implements IPacketHandler, Listener {

    private static final Logger LOGGER = Logger.getLogger("Asteroid");
    private static final String HANDLER_NAME = "asteroid_packet_handler";

    public interface ChannelProvider {
        Channel getChannel(Player player);
    }

    private final Map<Plugin, List<PacketListener>> listeners = new ConcurrentHashMap<>();
    private ChannelProvider channelProvider;
    private Plugin hostPlugin;
    private volatile MountPacketObserver mountObserver;
    private final Map<Channel, ConnectionState> connections = new ConcurrentHashMap<>();

    private static final class ConnectionState {
        volatile boolean ready;
        ChannelHandlerContext context;
        Player player;
    }

    /** Internal mount observer, deliberately outside the unordered external listener collection. */
    public void setMountObserver(MountPacketObserver observer) { this.mountObserver = observer; }
    public void clearMountObserver(MountPacketObserver observer) {
        if (mountObserver == observer) mountObserver = null;
    }
    public boolean ready(Object identity) {
        if (!(identity instanceof Channel channel)) return false;
        ConnectionState state = connections.get(channel);
        return state != null && state.ready && channel.isActive();
    }

    /** Owned virtual transport packets still pass through real external cancellation. */
    public void sendOwnedPackets(Object identity, Object[] packets, BooleanSupplier stillOwned, Consumer<Throwable> failure) {
        sendOwnedPackets(identity, packets, stillOwned, failure, false);
    }

    /** Already-owned cleanup may flush before a queued injection removal, never through its replacement. */
    public void sendOwnedCleanup(Object identity, Object[] packets, Consumer<Throwable> failure) {
        sendOwnedPackets(identity, packets, () -> true, failure, true);
    }

    private void sendOwnedPackets(Object identity, Object[] packets, BooleanSupplier stillOwned,
                                  Consumer<Throwable> failure, boolean cleanup) {
        if (!(identity instanceof Channel channel) || !ready(channel)) {
            throw new IllegalStateException("Passenger connection is not injected or active");
        }
        // Full sparse replay is three owned removals followed by six spawn/metadata/passenger packets.
        if (packets.length > 9) throw new IllegalArgumentException("Virtual transport packet batch is too large");
        Object[] batch = packets.clone();
        for (Object packet : batch) Objects.requireNonNull(packet);
        ConnectionState expected = connections.get(channel);
        channel.eventLoop().execute(() -> {
            if (!stillOwned.getAsBoolean()) return;
            if (expected == null || !channel.isActive() || expected.context == null
                    || channel.pipeline().context(HANDLER_NAME) != expected.context
                    || !cleanup && (connections.get(channel) != expected || !expected.ready)) {
                failure.accept(new IllegalStateException("Passenger connection changed before publication"));
                return;
            }
            // Execute cancellation and forward the unwrapped native packet from this context.
            // No wrapper can leak to Minecraft's encoder during a pipeline removal race.
            try {
                for (Object packet : batch) {
                    if (!stillOwned.getAsBoolean()) break;
                    ChannelPromise promise = channel.newPromise();
                    promise.addListener(result -> { if (!result.isSuccess()) failure.accept(result.cause()); });
                    writePacket(expected.player, channel, expected.context, packet, promise, true);
                    if (promise.isDone() && !promise.isSuccess()) break;
                }
                expected.context.flush();
            } catch (Exception error) { failure.accept(error); }
        });
    }

    public void setChannelProvider(ChannelProvider provider) {
        this.channelProvider = provider;
    }

    public void registerEvents(Plugin plugin) {
        this.hostPlugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {

        if (PaperCompat.isFolia() && hostPlugin != null) {
            FoliaScheduler.runEntityTask(hostPlugin, event.getPlayer(), () -> inject(event.getPlayer()));
        } else {
            inject(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        uninject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHostDisable(PluginDisableEvent event) {
        if (event.getPlugin() == hostPlugin) shutdown();
    }

    /** Global host shutdown/replacement only; never called on an individual consumer's disable. */
    public void shutdown() {
        HandlerList.unregisterAll(this);
        for (Channel channel : new ArrayList<>(connections.keySet())) remove(channel);
        listeners.clear();
        mountObserver = null;
        hostPlugin = null;
    }

    @Override
    public void addListener(Plugin plugin, PacketListener listener) {
        listeners.computeIfAbsent(plugin, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void removeListener(Plugin plugin, PacketListener listener) {
        List<PacketListener> list = listeners.get(plugin);
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public void removeListeners(Plugin plugin) {
        listeners.remove(plugin);
    }

    @Override
    public void inject(Player player) {
        if (channelProvider == null) return;
        Channel channel = channelProvider.getChannel(player);
        if (channel == null || !channel.isActive()) return;

        ConnectionState state = new ConnectionState();
        state.player = player;
        ConnectionState previous = connections.put(channel, state);
        if (previous != null) {
            previous.ready = false;
            MountPacketObserver observer = mountObserver;
            if (observer != null) observer.disconnected(channel);
        }

        channel.eventLoop().execute(() -> {
            try {
                if (connections.get(channel) != state) return;
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }

                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        PacketEvent event = new PacketEvent(player, msg, PacketDirection.IN);
                        fireEvent(event, true);
                        if (!event.isCancelled()) {
                            MountPacketObserver observer = mountObserver;
                            if (observer != null) observe(() -> observer.acceptedInput(channel, msg));
                            super.channelRead(ctx, msg);
                        } else {
                            ReferenceCountUtil.release(msg);
                        }
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        writePacket(player, channel, ctx, msg, promise, false);
                    }

                    @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        if (connections.remove(channel, state)) {
                            state.ready = false;
                            MountPacketObserver observer = mountObserver;
                            if (observer != null) observer.disconnected(channel);
                        }
                        super.channelInactive(ctx);
                    }
                });
                state.context = channel.pipeline().context(HANDLER_NAME);
                state.ready = true;
            } catch (Exception e) {
                state.ready = false;
                connections.remove(channel, state);
                LOGGER.log(Level.WARNING, "[Asteroid] Failed to inject packet handler for " + player.getName(), e);
            }
        });
    }

    @Override
    public void uninject(Player player) {
        if (channelProvider == null) return;
        Channel channel = channelProvider.getChannel(player);
        if (channel == null) return;

        remove(channel);
    }

    private void remove(Channel channel) {
        ConnectionState state = connections.remove(channel);
        if (state != null) state.ready = false;
        MountPacketObserver observer = mountObserver;
        if (observer != null) observer.disconnected(channel);
        channel.eventLoop().execute(() -> {
            try {
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        if (channelProvider == null) return;
        Channel channel = channelProvider.getChannel(player);
        if (channel != null) {
            channel.writeAndFlush(packet);
        }
    }

    private void fireEvent(PacketEvent event, boolean isReceive) {
        for (List<PacketListener> list : listeners.values()) {
            for (PacketListener listener : list) {
                try {
                    if (isReceive) {
                        listener.onReceive(event);
                    } else {
                        listener.onSend(event);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "[Asteroid] Error in packet listener", e);
                }
            }
        }
    }

    private void writePacket(Player player, Channel channel, ChannelHandlerContext ctx, Object packet,
                             ChannelPromise promise, boolean owned) {
        PacketEvent event = new PacketEvent(player, packet, PacketDirection.OUT);
        fireEvent(event, false);
        if (event.isCancelled()) {
            ReferenceCountUtil.release(packet);
            if (owned) promise.tryFailure(new IllegalStateException("Virtual passenger packet was cancelled"));
            else promise.trySuccess();
            return;
        }
        ctx.write(packet, promise);
        MountPacketObserver observer = mountObserver;
        if (observer != null) observe(() -> observer.acceptedOutput(channel, packet));
    }

    private static void observe(Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { LOGGER.log(Level.WARNING, "[Asteroid] Passenger observer failed", error); }
    }
}
