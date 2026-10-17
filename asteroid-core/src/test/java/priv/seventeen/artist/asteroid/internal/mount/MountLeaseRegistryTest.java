package priv.seventeen.artist.asteroid.internal.mount;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.*;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MountLeaseRegistryTest {
    private World world;
    private Plugin plugin;
    private FakePlatform nativeApi;
    private FakeEnvironment environment;
    private MountLeaseRegistry registry;
    private final List<Throwable> failures = new ArrayList<>();
    private int entityId;
    private static final Anchor ANCHOR = new Anchor(10, 20, 30);

    @BeforeEach void setup() {
        world = mock(World.class); when(world.getUID()).thenReturn(UUID.randomUUID());
        plugin = mock(Plugin.class); when(plugin.isEnabled()).thenReturn(true);
        nativeApi = new FakePlatform(); environment = new FakeEnvironment();
        registry = new MountLeaseRegistry(nativeApi, environment);
    }
    private <T extends Entity> T entity(Class<T> type) {
        T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID()); when(entity.getEntityId()).thenReturn(++entityId);
        when(entity.getWorld()).thenReturn(world); when(entity.isValid()).thenReturn(true);
        if (entity instanceof Player player) {
            when(player.isOnline()).thenReturn(true);
            Object channel = new Object(); nativeApi.channels.put(player, channel); environment.ready.add(channel);
        }
        nativeApi.handles.put(entity, new Object());
        return entity;
    }
    private OwnerLease owner() { return registry.open(plugin, entity(Entity.class), UUID.randomUUID(), failures::add); }
    private RiderLease bind(OwnerLease owner, Entity rider) { return owner.bind(rider, UUID.randomUUID(), ANCHOR); }

    @Test void retainsOriginalHostIdentityAndNeverCreatesNativeVehicleRelationship() {
        Entity host = entity(Entity.class), rider = entity(Entity.class); UUID epoch = UUID.randomUUID();
        OwnerLease owner = registry.open(plugin, host, epoch, failures::add);
        RiderLease lease = bind(owner, rider);
        assertSame(host, owner.host()); assertEquals(epoch, owner.ownerEpoch()); assertSame(rider, lease.rider());
        assertFalse(nativeApi.hasVehicle(nativeApi.handle(rider))); assertEquals(1, nativeApi.followCount);
        assertNotEquals(lease.transportIds().pivotId(), lease.transportIds().vehicleId());
    }
    @Test void duplicateHostOwnerRejected() {
        OwnerLease owner = owner();
        assertThrows(IllegalStateException.class, () -> registry.open(plugin, owner.host(), UUID.randomUUID(), failures::add));
    }
    @Test void foreignHostVehicleRejected() {
        Entity host = entity(Entity.class); nativeApi.foreign.add(nativeApi.handle(host));
        assertThrows(IllegalStateException.class, () -> registry.open(plugin, host, UUID.randomUUID(), failures::add));
    }
    @Test void foreignRiderVehicleRejectedBeforeTransportCreation() {
        OwnerLease owner = owner(); Entity rider = entity(Entity.class); nativeApi.foreign.add(nativeApi.handle(rider));
        assertThrows(IllegalStateException.class, () -> bind(owner, rider)); assertEquals(0, nativeApi.created.size());
    }
    @Test void selfAndOwnedHostAsRiderRejected() {
        OwnerLease first = owner(), second = owner();
        assertThrows(IllegalStateException.class, () -> bind(first, first.host()));
        assertThrows(IllegalStateException.class, () -> bind(first, second.host()));
    }
    @Test void ownedRiderCannotBecomeHost() {
        Entity rider = entity(Entity.class); bind(owner(), rider);
        assertThrows(IllegalStateException.class, () -> registry.open(plugin, rider, UUID.randomUUID(), failures::add));
    }
    @Test void duplicateRiderAndTokenRejectedAcrossOwners() {
        OwnerLease first = owner(), second = owner(); Entity rider = entity(Entity.class); UUID token = UUID.randomUUID();
        first.bind(rider, token, ANCHOR);
        assertThrows(IllegalStateException.class, () -> bind(second, rider));
        assertThrows(IllegalStateException.class, () -> second.bind(entity(Entity.class), token, ANCHOR));
    }
    @Test void worldMismatchRejectedBeforeMutation() {
        OwnerLease owner = owner(); Entity rider = entity(Entity.class); World elsewhere = mock(World.class);
        when(elsewhere.getUID()).thenReturn(UUID.randomUUID()); when(rider.getWorld()).thenReturn(elsewhere);
        assertThrows(IllegalArgumentException.class, () -> bind(owner, rider)); assertEquals(0, nativeApi.created.size());
    }
    @Test void disabledConsumerRejected() {
        when(plugin.isEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, this::owner);
    }
    @Test void noReadyPlayerChannelRejectsBeforeAllocatingPair() {
        Player rider = entity(Player.class); environment.ready.clear();
        assertThrows(IllegalStateException.class, () -> bind(owner(), rider)); assertEquals(0, nativeApi.created.size());
    }
    @Test void playerBindShowsOwnNativeVehicle() {
        RiderLease lease = bind(owner(), entity(Player.class));
        assertEquals(1, environment.sent.size()); assertEquals("spawn", environment.sent.get(0)[0]);
        assertTrue(lease.active());
    }
    @Test void showAndHideAreSparseAndIdempotent() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class);
        lease.show(viewer); lease.show(viewer); assertEquals(1, environment.sent.size());
        lease.hide(viewer); lease.hide(viewer); assertEquals(2, environment.sent.size());
    }
    @Test void ownActiveVehicleCannotBeHidden() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider);
        assertThrows(IllegalStateException.class, () -> lease.hide(rider));
        lease.close(); lease.hide(rider); assertFalse(lease.active());
    }
    @Test void followNeverPublishesCoordinates() {
        RiderLease lease = bind(owner(), entity(Player.class)); int sent = environment.sent.size();
        for (int i = 0; i < 50; i++) lease.follow(new Anchor(i, i + 1, i + 2));
        assertEquals(sent, environment.sent.size()); assertEquals(51, nativeApi.followCount);
        assertEquals(new Anchor(49, 50, 51), nativeApi.lastAnchor);
    }
    @Test void lateObserverSpawnUsesLastAuthoritativeAnchor() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Anchor latest = new Anchor(60, 70, 80);
        lease.follow(latest); lease.show(entity(Player.class)); assertEquals(latest, environment.sent.get(0)[1]);
    }
    @Test void heldAnalogInputAndNativeSprintRetainMagnitudes() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider);
        registry.acceptedInput(nativeApi.connection(rider), Input.legacy(1, 0.25f, -0.75f, true, false, false));
        nativeApi.sprinting = true;
        for (int i = 0; i < 100; i++) registry.maintain();
        Input input = lease.latestInput(); assertEquals(1, input.sequence()); assertEquals(0.25f, input.side());
        assertEquals(-0.75f, input.front()); assertTrue(input.jump()); assertTrue(input.sprint());
    }
    @Test void modernOpposingFlagsAndSprintAreNotCollapsed() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider);
        registry.acceptedInput(nativeApi.connection(rider), Input.modern(1, true, true, true, true, true, true, true));
        Input input = lease.latestInput(); assertEquals(0, input.front()); assertEquals(0, input.side());
        assertTrue(input.forward() && input.backward() && input.left() && input.right() && input.sprint());
    }
    @Test void unrelatedAndForeignChannelPacketsCannotAffectRider() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider);
        registry.acceptedInput(new Object(), Input.legacy(1, 1, 1, false, false, false));
        registry.acceptedInput(nativeApi.connection(rider), new Object()); assertEquals(Input.EMPTY, lease.latestInput());
    }
    @Test void disconnectClosesMailboxAndReportsOnce() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider); Object channel = nativeApi.connection(rider);
        registry.disconnected(channel); registry.acceptedInput(channel, Input.modern(1, true, false, false, false, false, false, false));
        registry.maintain(); registry.maintain(); assertFalse(lease.active()); assertEquals(Input.EMPTY, lease.latestInput());
        assertEquals(1, failures.size());
    }
    @Test void changedConnectionIsFencedEvenWithoutQuitEvent() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider); Object old = nativeApi.connection(rider);
        Object replacement = new Object(); nativeApi.channels.put(rider, replacement); environment.ready.add(replacement);
        registry.maintain(); registry.acceptedInput(old, Input.legacy(1, 1, 1, false, false, false));
        assertFalse(lease.active()); assertEquals(Input.EMPTY, lease.latestInput());
    }
    @Test void nativeHandleReplacementRevokesLease() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider);
        nativeApi.handles.put(rider, new Object()); registry.maintain(); assertFalse(lease.active());
    }
    @Test void nativeIdOrUuidMutationOnSameHandleCannotReuseOldLease() {
        OwnerLease owner = owner(); Entity rider = entity(Entity.class); RiderLease first = bind(owner, rider);
        when(rider.getEntityId()).thenReturn(8000); registry.maintain(); assertFalse(first.active());
        RiderLease replacement = bind(owner, rider); first.close(); assertTrue(replacement.active());
        when(rider.getUniqueId()).thenReturn(UUID.randomUUID()); registry.maintain(); assertFalse(replacement.active());
    }
    @Test void hostWorldChangeRevokesEveryOwnedRelationship() {
        OwnerLease owner = owner(); RiderLease first = bind(owner, entity(Entity.class)), second = bind(owner, entity(Entity.class));
        World elsewhere = mock(World.class); when(elsewhere.getUID()).thenReturn(UUID.randomUUID());
        when(owner.host().getWorld()).thenReturn(elsewhere); registry.maintain();
        assertFalse(owner.active()); assertFalse(first.active()); assertFalse(second.active());
    }
    @Test void foreignVehicleInstalledLaterIsNeverEjected() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider);
        nativeApi.foreign.add(nativeApi.handle(rider)); registry.maintain();
        assertFalse(lease.active()); assertTrue(nativeApi.foreign.contains(nativeApi.handle(rider)));
    }
    @Test void forceCloseIsIdempotentAndStaleOwnerCannotCloseReplacement() {
        OwnerLease first = owner(); Entity host = first.host(), rider = entity(Entity.class); RiderLease old = bind(first, rider);
        first.close(); first.close(); OwnerLease replacement = registry.open(plugin, host, UUID.randomUUID(), failures::add);
        RiderLease current = bind(replacement, rider); first.close(); old.close();
        assertTrue(current.active()); assertEquals(1, nativeApi.created.get(0).closed);
    }
    @Test void pluginCleanupDoesNotTouchOtherConsumer() {
        OwnerLease first = owner(); RiderLease firstRider = bind(first, entity(Entity.class)); Plugin another = mock(Plugin.class);
        when(another.isEnabled()).thenReturn(true);
        OwnerLease second = registry.open(another, entity(Entity.class), UUID.randomUUID(), failures::add);
        RiderLease secondRider = bind(second, entity(Entity.class)); registry.removePlugin(plugin);
        assertFalse(firstRider.active()); assertTrue(secondRider.active());
    }
    @Test void entityRemovalReleasesOnlyAffectedRelationship() {
        OwnerLease owner = owner(); Entity first = entity(Entity.class), second = entity(Entity.class);
        RiderLease a = bind(owner, first), b = bind(owner, second); registry.removeEntity(first.getUniqueId());
        assertFalse(a.active()); assertTrue(b.active());
    }
    @Test void deadHostRemovesEveryOwnedRider() {
        OwnerLease owner = owner(); RiderLease first = bind(owner, entity(Entity.class)), second = bind(owner, entity(Entity.class));
        when(owner.host().isDead()).thenReturn(true); registry.maintain(); assertFalse(first.active()); assertFalse(second.active());
    }
    @Test void perHostRiderBudgetIsBoundedAndRecoversAfterRelease() {
        OwnerLease owner = owner(); List<RiderLease> leases = new ArrayList<>();
        for (int i = 0; i < MountLeaseRegistry.MAX_HOST_RIDERS; i++) leases.add(bind(owner, entity(Entity.class)));
        assertThrows(IllegalStateException.class, () -> bind(owner, entity(Entity.class)));
        leases.get(0).close(); assertTrue(bind(owner, entity(Entity.class)).active());
    }
    @Test void allocationFailureRollsBackConnectionAndOwnership() {
        Player rider = entity(Player.class); OwnerLease owner = owner(); nativeApi.failCreate = true;
        assertThrows(IllegalStateException.class, () -> bind(owner, rider)); nativeApi.failCreate = false;
        assertTrue(bind(owner, rider).active());
    }
    @Test void followFailureReleasesPairAndReports() {
        RiderLease lease = bind(owner(), entity(Entity.class)); nativeApi.failFollow = true;
        assertThrows(IllegalStateException.class, () -> lease.follow(ANCHOR)); assertFalse(lease.active());
        assertEquals(1, failures.size());
    }
    @Test void nativeUntrackingRemovesPairAndRetrackingRestoresOnceAtLatestAnchor() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider); Player viewer = entity(Player.class); lease.show(viewer);
        Object channel = nativeApi.connection(viewer); registry.acceptedOutput(channel, new Remove(rider.getEntityId()));
        assertEquals("remove", environment.sent.get(1)[0]);
        lease.follow(new Anchor(100, 200, 300)); registry.maintain(); assertEquals(2, environment.sent.size());
        registry.acceptedOutput(channel, new Spawn(rider.getEntityId())); registry.maintain(); registry.maintain();
        assertEquals(3, environment.sent.size()); assertEquals(new Anchor(100, 200, 300), environment.sent.get(2)[1]);
    }
    @Test void closeBeforeQueuedSpawnPreventsResurrection() {
        environment.defer = true; RiderLease lease = bind(owner(), entity(Player.class)); lease.close(); environment.flush();
        assertEquals(1, environment.sent.size()); assertEquals("remove", environment.sent.get(0)[0]);
    }
    @Test void failedPublicationIsFailClosedOnNextMaintenance() {
        RiderLease lease = bind(owner(), entity(Player.class)); environment.lastFailure.accept(new IllegalStateException("cancelled"));
        registry.maintain(); assertFalse(lease.active()); assertEquals(1, failures.size());
    }
    @Test void globalShutdownRejectsNewOwnersAndClosesAllLeases() {
        RiderLease lease = bind(owner(), entity(Player.class)); registry.close(); registry.close();
        assertFalse(lease.active()); assertThrows(IllegalStateException.class, this::owner);
    }
    @Test void everyMutableOperationRequiresOwnerThread() {
        OwnerLease owner = owner(); RiderLease lease = bind(owner, entity(Entity.class)); environment.ownerThread = false;
        assertThrows(IllegalStateException.class, () -> bind(owner, entity(Entity.class)));
        assertThrows(IllegalStateException.class, () -> lease.follow(ANCHOR));
        assertThrows(IllegalStateException.class, lease::close); assertThrows(IllegalStateException.class, lease::latestInput);
        assertThrows(IllegalStateException.class, registry::maintain);
    }

    @Test void replayIsOneOrderedRemoveThenSpawnBatchAtLatestAnchor() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        UUID token = lease.token(); TransportIds ids = lease.transportIds(); Anchor latest = new Anchor(45, 65, 85);
        lease.follow(latest); int moved = nativeApi.followCount;
        lease.replay(viewer);
        assertEquals(2, environment.sent.size());
        assertArrayEquals(new Object[]{"remove", ids, "spawn", latest}, environment.sent.get(1));
        assertSame(nativeApi.connection(viewer), environment.sentChannels.get(1));
        assertEquals(token, lease.token()); assertEquals(ids, lease.transportIds()); assertEquals(moved, nativeApi.followCount);
    }
    @Test void ownReplayPreservesHeldInputAndActiveOwnership() {
        Player rider = entity(Player.class); RiderLease lease = bind(owner(), rider);
        registry.acceptedInput(nativeApi.connection(rider), Input.legacy(1, .25f, 1, true, false, false));
        Input before = lease.latestInput(); lease.replay(rider);
        assertEquals(before, lease.latestInput()); assertTrue(lease.active()); assertEquals(2, environment.sent.size());
    }
    @Test void replayNeverGrantsAnUnshownObserverAdmission() {
        RiderLease lease = bind(owner(), entity(Entity.class));
        assertThrows(IllegalStateException.class, () -> lease.replay(entity(Player.class)));
        assertTrue(lease.active()); assertTrue(environment.sent.isEmpty());
    }
    @Test void replayCannotResurrectAnExplicitlyHiddenView() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class);
        lease.show(viewer); lease.hide(viewer); int before = environment.sent.size();
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(before, environment.sent.size());
    }
    @Test void replayRejectsObserverWorldChangeBeforePublication() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        World elsewhere = mock(World.class); when(elsewhere.getUID()).thenReturn(UUID.randomUUID()); when(viewer.getWorld()).thenReturn(elsewhere);
        assertThrows(IllegalArgumentException.class, () -> lease.replay(viewer)); assertEquals(1, environment.sent.size());
    }
    @Test void replayRejectsReplacementChannelWithoutPublishingToEitherSession() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        Object replacement = new Object(); nativeApi.channels.put(viewer, replacement); environment.ready.add(replacement);
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(1, environment.sent.size());
    }
    @Test void replayRejectsUnreadyAndDisconnectedObserverChannels() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        Object channel = nativeApi.connection(viewer); environment.ready.remove(channel);
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); environment.ready.add(channel); registry.disconnected(channel);
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(1, environment.sent.size());
    }
    @Test void replayRequiresOwnerThreadBeforeReadingBukkitState() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer); environment.ownerThread = false;
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(1, environment.sent.size());
    }
    @Test void replayRejectsClosedLeaseAndForeignNativeVehicle() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider); Player viewer = entity(Player.class); lease.show(viewer);
        nativeApi.foreign.add(nativeApi.handle(rider)); assertThrows(IllegalStateException.class, () -> lease.replay(viewer));
        assertFalse(lease.active()); assertTrue(nativeApi.foreign.contains(nativeApi.handle(rider))); int before = environment.sent.size();
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(before, environment.sent.size());
    }
    @Test void observedUntrackBlocksReplayUntilSpawnAndConsumesThePendingNativeRepublish() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider); Player viewer = entity(Player.class); lease.show(viewer);
        Object channel = nativeApi.connection(viewer); registry.acceptedOutput(channel, new Remove(rider.getEntityId())); int before = environment.sent.size();
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertEquals(before, environment.sent.size());
        registry.acceptedOutput(channel, new Spawn(rider.getEntityId())); lease.replay(viewer); int replayed = environment.sent.size();
        for (int i = 0; i < 200; i++) registry.maintain();
        assertEquals(replayed, environment.sent.size()); assertEquals("remove", environment.sent.get(replayed - 1)[0]);
        assertEquals("spawn", environment.sent.get(replayed - 1)[2]);
    }
    @Test void deferredReplayCannotResurrectAfterClose() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        environment.defer = true; lease.replay(viewer); lease.close(); environment.flush();
        assertEquals(2, environment.sent.size()); assertEquals("remove", environment.sent.get(1)[0]); assertEquals(2, environment.sent.get(1).length);
    }
    @Test void deferredReplayCannotPublishAfterAnObservedNativeUntrack() {
        Entity rider = entity(Entity.class); RiderLease lease = bind(owner(), rider); Player viewer = entity(Player.class); lease.show(viewer);
        environment.defer = true; lease.replay(viewer); registry.acceptedOutput(nativeApi.connection(viewer), new Remove(rider.getEntityId())); environment.flush();
        assertEquals(2, environment.sent.size()); assertEquals(2, environment.sent.get(1).length); assertEquals("remove", environment.sent.get(1)[0]); assertTrue(lease.active());
    }
    @Test void replayAsyncFailureClosesOnMaintenanceAndReportsOnce() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer); lease.replay(viewer);
        environment.lastFailure.accept(new IllegalStateException("replay rejected")); registry.maintain(); registry.maintain();
        assertFalse(lease.active()); assertEquals(1, failures.size());
    }
    @Test void replayPacketConstructionFailureCleansUpAndReports() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer);
        nativeApi.created.get(0).failSpawn = true;
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertFalse(lease.active()); assertEquals(1, failures.size());
        assertEquals("remove", environment.sent.get(environment.sent.size() - 1)[0]);
    }
    @Test void replaySynchronousWriteFailureCleansOwnershipWithoutRetryLoop() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player viewer = entity(Player.class); lease.show(viewer); environment.failSend = true;
        assertThrows(IllegalStateException.class, () -> lease.replay(viewer)); assertFalse(lease.active()); assertEquals(1, failures.size());
        registry.maintain(); assertEquals(1, environment.sent.size());
    }
    @Test void replayTouchesOnlyTheRequestedOwnedObserver() {
        RiderLease lease = bind(owner(), entity(Entity.class)); Player first = entity(Player.class), second = entity(Player.class);
        lease.show(first); lease.show(second); lease.replay(first);
        assertEquals(3, environment.sent.size()); assertSame(nativeApi.connection(first), environment.sentChannels.get(2));
        lease.show(second); assertEquals(3, environment.sent.size());
    }
    @Test void defaultReplayOnOldProviderFailsClosed() {
        RiderLease previousProvider = mock(RiderLease.class, CALLS_REAL_METHODS);
        assertThrows(UnsupportedOperationException.class, () -> previousProvider.replay(entity(Player.class)));
    }

    private record Spawn(int id) {}
    private record Remove(int id) {}
    private static final class FakeEnvironment implements MountEnvironment {
        final Set<Object> ready = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Object[]> sent = new ArrayList<>(); final List<Object> sentChannels = new ArrayList<>(); final List<Runnable> pending = new ArrayList<>();
        boolean ownerThread = true, defer, failSend; Consumer<Throwable> lastFailure;
        @Override public void checkThread() { if (!ownerThread) throw new IllegalStateException("Wrong thread"); }
        @Override public boolean ready(Object connection) { return ready.contains(connection); }
        @Override public void send(Object connection, Object[] packets, BooleanSupplier owned, Consumer<Throwable> failure) {
            if (failSend) throw new IllegalStateException("write failed");
            lastFailure = failure; Runnable send = () -> { if (owned.getAsBoolean()) { sent.add(packets); sentChannels.add(connection); } };
            if (defer) pending.add(send); else send.run();
        }
        void flush() { List.copyOf(pending).forEach(Runnable::run); pending.clear(); }
    }
    private static final class FakePlatform implements NativeMountPlatform {
        final Map<Entity, Object> handles = new IdentityHashMap<>(); final Map<Player, Object> channels = new IdentityHashMap<>();
        final Set<Object> foreign = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<FakeTransport> created = new ArrayList<>(); boolean sprinting, failCreate, failFollow;
        int followCount; Anchor lastAnchor;
        @Override public void preflight() {}
        @Override public Object handle(Entity entity) { return handles.get(entity); }
        @Override public boolean sameHandle(Entity entity, Object handle) { return handles.get(entity) == handle; }
        @Override public boolean hasVehicle(Object handle) { return foreign.contains(handle); }
        @Override public Object connection(Player player) { return channels.get(player); }
        @Override public boolean sprint(Object handle) { return sprinting; }
        @Override public Input decodeInput(Object packet, long sequence) {
            if (!(packet instanceof Input input)) return null;
            return new Input(sequence, input.encoding(), input.side(), input.front(), input.forward(), input.backward(), input.left(),
                    input.right(), input.jump(), input.sneak(), input.sprint());
        }
        @Override public int spawnedEntityId(Object packet) { return packet instanceof Spawn spawn ? spawn.id() : -1; }
        @Override public int[] removedEntityIds(Object packet) { return packet instanceof Remove remove ? new int[]{remove.id()} : new int[0]; }
        @Override public Transport createTransport(Object host, Object rider, Anchor anchor) {
            if (failCreate) throw new IllegalStateException("allocation failed");
            FakeTransport transport = new FakeTransport(1000 + created.size() * 2); created.add(transport); return transport;
        }
        @Override public void follow(Object rider, Transport transport, Anchor anchor) {
            if (failFollow) throw new IllegalStateException("follow failed"); followCount++; lastAnchor = anchor;
        }
    }
    private static final class FakeTransport implements NativeMountPlatform.Transport {
        final TransportIds ids; int closed; boolean failSpawn;
        FakeTransport(int id) { ids = new TransportIds(id, UUID.randomUUID(), id + 1, UUID.randomUUID(), -0.5); }
        @Override public TransportIds ids() { return ids; }
        @Override public Object[] spawnPackets(Anchor anchor) { if (failSpawn) throw new IllegalStateException("replay construction failed"); return new Object[]{"spawn", anchor}; }
        @Override public Object[] removePackets() { return new Object[]{"remove", ids}; }
        @Override public void close() { closed++; }
    }
}
