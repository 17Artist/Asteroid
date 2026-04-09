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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.packet.*;
import priv.seventeen.artist.asteroid.util.FoliaScheduler;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PacketHandlerImpl implements IPacketHandler, Listener {

    private static final Logger LOGGER = Logger.getLogger("Asteroid");
    private static final String HANDLER_NAME = "asteroid_packet_handler";

    public interface ChannelProvider {
        Channel getChannel(Player player);
    }

    private final Map<Plugin, List<PacketListener>> listeners = new ConcurrentHashMap<>();
    private ChannelProvider channelProvider;
    private Plugin hostPlugin;

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

        channel.eventLoop().execute(() -> {
            try {
                if (channel.pipeline().get(HANDLER_NAME) != null) {
                    channel.pipeline().remove(HANDLER_NAME);
                }

                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        PacketEvent event = new PacketEvent(player, msg, PacketDirection.IN);
                        fireEvent(event, true);
                        if (!event.isCancelled()) {
                            super.channelRead(ctx, msg);
                        }
                    }

                    @Override
                    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                        PacketEvent event = new PacketEvent(player, msg, PacketDirection.OUT);
                        fireEvent(event, false);
                        if (!event.isCancelled()) {
                            super.write(ctx, msg, promise);
                        }
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "[Asteroid] Failed to inject packet handler for " + player.getName(), e);
            }
        });
    }

    @Override
    public void uninject(Player player) {
        if (channelProvider == null) return;
        Channel channel = channelProvider.getChannel(player);
        if (channel == null) return;

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
}
