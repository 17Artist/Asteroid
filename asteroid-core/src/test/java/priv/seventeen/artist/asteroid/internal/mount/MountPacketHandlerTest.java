package priv.seventeen.artist.asteroid.internal.mount;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.internal.PacketHandlerImpl;
import priv.seventeen.artist.asteroid.packet.PacketEvent;
import priv.seventeen.artist.asteroid.packet.PacketListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MountPacketHandlerTest {
    private EmbeddedChannel channel;
    private PacketHandlerImpl handler;
    private Player player;
    private Plugin plugin;
    private Observer observer;
    private final List<Throwable> failures = new ArrayList<>();

    @BeforeEach void setup() {
        channel = new EmbeddedChannel(); channel.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
        handler = new PacketHandlerImpl(); player = mock(Player.class); plugin = mock(Plugin.class); observer = new Observer();
        handler.setChannelProvider(ignored -> channel); handler.setMountObserver(observer);
        handler.inject(player); channel.runPendingTasks(); assertTrue(handler.ready(channel));
    }
    @AfterEach void close() { channel.finishAndReleaseAll(); }
    @Test void inputObserverOnlySeesFinalUncancelledPacketAndNativePacketContinues() {
        handler.addListener(plugin, new PacketListener() { @Override public void onReceive(PacketEvent event) { event.setCancelled(true); } });
        Object denied = new Object(); channel.writeInbound(denied); assertTrue(observer.in.isEmpty()); assertNull(channel.readInbound());
        handler.removeListeners(plugin); Object accepted = new Object(); channel.writeInbound(accepted);
        assertEquals(List.of(accepted), observer.in); assertSame(accepted, channel.readInbound());
    }
    @Test void finalListenerCanUncancelBeforeMountObservation() {
        handler.addListener(plugin, new PacketListener() { @Override public void onReceive(PacketEvent event) { event.setCancelled(true); } });
        handler.addListener(plugin, new PacketListener() { @Override public void onReceive(PacketEvent event) { event.setCancelled(false); } });
        Object packet = new Object(); channel.writeInbound(packet); assertEquals(List.of(packet), observer.in);
    }
    @Test void ownedPacketsAreNativeObjectsAndRespectExternalCancellation() {
        handler.addListener(plugin, new PacketListener() { @Override public void onSend(PacketEvent event) { event.setCancelled(true); } });
        handler.sendOwnedPackets(channel, new Object[]{new Object(), new Object()}, () -> true, failures::add);
        channel.runPendingTasks(); assertNull(channel.readOutbound()); assertEquals(1, failures.size()); assertTrue(observer.out.isEmpty());
    }
    @Test void successfulOwnedBatchPreservesOrderingAndContainsNoWrapper() {
        Object first = new Object(), second = new Object();
        handler.sendOwnedPackets(channel, new Object[]{first, second}, () -> true, failures::add); channel.runPendingTasks();
        assertSame(first, channel.readOutbound()); assertSame(second, channel.readOutbound()); assertEquals(List.of(first, second), observer.out);
        assertTrue(failures.isEmpty());
    }
    @Test void fullNinePacketReplayFitsOneOrderedBatch() {
        Object[] replay = new Object[9];
        for (int i = 0; i < replay.length; i++) replay[i] = new Object();
        handler.sendOwnedPackets(channel, replay, () -> true, failures::add); channel.runPendingTasks();
        for (Object packet : replay) assertSame(packet, channel.readOutbound());
        assertNull(channel.readOutbound()); assertEquals(List.of(replay), observer.out); assertTrue(failures.isEmpty());
    }
    @Test void tenthPacketIsRejectedBeforeAnyPartIsQueued() {
        Object[] oversized = new Object[10];
        for (int i = 0; i < oversized.length; i++) oversized[i] = new Object();
        assertThrows(IllegalArgumentException.class,
                () -> handler.sendOwnedPackets(channel, oversized, () -> true, failures::add));
        channel.runPendingTasks(); assertNull(channel.readOutbound()); assertTrue(observer.out.isEmpty()); assertTrue(failures.isEmpty());
    }
    @Test void replayCancellationAfterRemovalNeverPublishesRemainingSpawnPackets() {
        AtomicInteger visited = new AtomicInteger();
        handler.addListener(plugin, new PacketListener() {
            @Override public void onSend(PacketEvent event) { if (visited.incrementAndGet() == 4) event.setCancelled(true); }
        });
        Object[] replay = new Object[9];
        for (int i = 0; i < replay.length; i++) replay[i] = new Object();
        handler.sendOwnedPackets(channel, replay, () -> true, failures::add); channel.runPendingTasks();
        for (int i = 0; i < 3; i++) assertSame(replay[i], channel.readOutbound());
        assertNull(channel.readOutbound()); assertEquals(4, visited.get());
        assertEquals(List.of(replay[0], replay[1], replay[2]), observer.out); assertEquals(1, failures.size());
    }
    @Test void revokedLeaseSkipsQueuedSpawn() {
        AtomicBoolean owned = new AtomicBoolean(false);
        handler.sendOwnedPackets(channel, new Object[]{new Object()}, owned::get, failures::add); channel.runPendingTasks();
        assertNull(channel.readOutbound()); assertTrue(failures.isEmpty());
    }
    @Test void uninjectionImmediatelyFencesNewWritesAndNotifiesDisconnect() {
        handler.uninject(player); assertFalse(handler.ready(channel)); assertEquals(1, observer.disconnects);
        assertThrows(IllegalStateException.class, () -> handler.sendOwnedPackets(channel, new Object[]{new Object()}, () -> true, failures::add));
        channel.runPendingTasks(); assertNull(channel.pipeline().get("asteroid_packet_handler"));
    }
    @Test void cleanupQueuedBeforeUninjectionStillFlushesThroughOriginalContext() {
        Object cleanup = new Object(); handler.sendOwnedCleanup(channel, new Object[]{cleanup}, failures::add);
        handler.uninject(player); channel.runPendingTasks(); assertSame(cleanup, channel.readOutbound()); assertTrue(failures.isEmpty());
    }
    @Test void observerFaultCannotDropVanillaAcceptedPacket() {
        handler.setMountObserver(new MountPacketObserver() {
            @Override public void acceptedInput(Object connection, Object packet) { throw new IllegalStateException("observer failed"); }
            @Override public void acceptedOutput(Object connection, Object packet) {}
            @Override public void disconnected(Object connection) {}
        });
        Object packet = new Object(); channel.writeInbound(packet); assertSame(packet, channel.readInbound());
    }
    private static final class Observer implements MountPacketObserver {
        final List<Object> in = new ArrayList<>(), out = new ArrayList<>(); int disconnects;
        @Override public void acceptedInput(Object connection, Object packet) { in.add(packet); }
        @Override public void acceptedOutput(Object connection, Object packet) { out.add(packet); }
        @Override public void disconnected(Object connection) { disconnects++; }
    }
}
