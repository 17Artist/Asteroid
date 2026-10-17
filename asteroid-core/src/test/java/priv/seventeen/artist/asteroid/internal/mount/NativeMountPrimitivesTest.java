package priv.seventeen.artist.asteroid.internal.mount;

import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class NativeMountPrimitivesTest {
    @Test void anchorRejectsNaNInfinityAndOutOfWorldRange() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Anchor(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new Anchor(30_000_001, 0, 0));
    }
    @Test void transportIdentityRequiresDistinctOwnedIdsAndExplicitVersionOffset() {
        UUID pivot = UUID.randomUUID(), vehicle = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TransportIds(1, pivot, 1, vehicle, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new TransportIds(1, pivot, 2, pivot, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new TransportIds(1, pivot, 2, vehicle, 0));
        assertEquals(-0.375, new TransportIds(1, pivot, 2, vehicle, -0.375).pivotOffsetY());
    }
    @Test void analogValidationDoesNotConvertFractionsToDigitalInput() {
        Input value = Input.legacy(1, 0.125f, -0.8f, true, false, false);
        assertEquals(0.125f, value.side()); assertEquals(-0.8f, value.front());
        assertTrue(value.left()); assertTrue(value.backward());
        assertThrows(IllegalArgumentException.class, () -> Input.legacy(1, 2, 0, false, false, false));
    }
    @Test void modernOppositeKeysRemainObservable() {
        Input value = Input.modern(1, true, true, true, true, true, true, true);
        assertEquals(0, value.side()); assertEquals(0, value.front());
        assertTrue(value.left() && value.right() && value.forward() && value.backward());
        assertSame(value, value.withNativeSprint(false));
    }
    @Test void malformedEncodingAndZeroSequenceRejected() {
        assertThrows(IllegalArgumentException.class, () -> Input.modern(0, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new Input(1, Encoding.NONE, 0, 0, false, false, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new Input(1, Encoding.MODERN_FLAGS, 0, 0, true, false, false, false, false, false, false));
    }
    @Test void mailboxIsHeldMonotonicAndDoesNotResurrectAfterRelease() {
        MountInputMailbox mailbox = new MountInputMailbox(); Input first = Input.legacy(1, 0, 1, false, false, false);
        assertTrue(mailbox.publish(first)); assertFalse(mailbox.publish(first)); assertSame(first, mailbox.read());
        mailbox.close(); assertFalse(mailbox.publish(Input.legacy(2, 1, 0, false, false, false)));
        assertEquals(Input.EMPTY, mailbox.read());
    }
    @Test void concurrentCloseFencesInFlightInput() throws InterruptedException {
        MountInputMailbox mailbox = new MountInputMailbox(); CountDownLatch start = new CountDownLatch(1);
        Thread publisher = new Thread(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            for (int i = 1; i <= 1000; i++) mailbox.publish(Input.legacy(i, 0, 1, false, false, false));
        });
        publisher.start(); start.countDown(); mailbox.close(); publisher.join();
        assertTrue(mailbox.closed()); assertEquals(Input.EMPTY, mailbox.read());
    }
    @Test void exactMappedBooleanFieldResolvesBothNamespacesWithoutOrdinalFallback() {
        Mapped sample = new Mapped(); var mapped = NativeMountFields.booleanField(Mapped.class, "clientIsFloating", "G");
        mapped.set(sample, false); assertFalse(sample.clientIsFloating);
        Spigot alternative = new Spigot(); var spigot = NativeMountFields.booleanField(Spigot.class, "clientIsFloating", "G");
        spigot.set(alternative, false); assertFalse(alternative.G);
        assertThrows(IllegalStateException.class, () -> NativeMountFields.booleanField(Mapped.class, "missing", "alsoMissing"));
    }
    @Test void sparseSpawnBatchContainsBothEntitiesMetadataAndPassengerChain() {
        Codec codec = new Codec(); Node pivot = new Node(1), vehicle = new Node(2), rider = new Node(3);
        VirtualPassengerTransport transport = new VirtualPassengerTransport(codec, pivot, vehicle, rider, 0.5);
        Object[] packets = transport.spawnPackets(new Anchor(10, 20, 30));
        assertEquals(List.of("spawn:1", "metadata:1", "spawn:2", "metadata:2", "passengers:1:[2]", "passengers:2:[3]"), Arrays.asList(packets));
        assertEquals(19.5, pivot.y); assertEquals(20, vehicle.y); assertEquals(0, rider.y);
        assertEquals(-0.5, transport.ids().pivotOffsetY());
    }
    @Test void legacyPivotOffsetIsExplicitAndCleanupTouchesOnlyOwnedPair() {
        Codec codec = new Codec(); Node pivot = new Node(1), vehicle = new Node(2), rider = new Node(3);
        VirtualPassengerTransport transport = new VirtualPassengerTransport(codec, pivot, vehicle, rider, 0.375);
        transport.spawnPackets(new Anchor(1, 2, 3)); assertEquals(1.625, pivot.y);
        assertEquals(List.of("passengers:2:[]", "passengers:1:[]", "remove:[1, 2]"), Arrays.asList(transport.removePackets()));
        transport.close(); transport.close(); assertThrows(IllegalStateException.class, () -> transport.spawnPackets(new Anchor(1, 2, 3)));
        assertEquals(3, transport.removePackets().length);
    }
    private static final class Mapped { private boolean clientIsFloating = true; }
    private static final class Spigot { private boolean unrelated = true; private boolean G = true; }
    private static final class Node { final int id; final UUID uuid = UUID.randomUUID(); double y; Node(int id) { this.id = id; } }
    private static final class Codec implements VirtualPassengerTransport.Codec {
        @Override public int id(Object entity) { return ((Node) entity).id; }
        @Override public UUID uuid(Object entity) { return ((Node) entity).uuid; }
        @Override public void position(Object entity, double x, double y, double z) { ((Node) entity).y = y; }
        @Override public Object spawn(Object entity) { return "spawn:" + id(entity); }
        @Override public Object metadata(Object entity) { return "metadata:" + id(entity); }
        @Override public Object passengers(Object entity, Object... passengers) {
            return "passengers:" + id(entity) + ":" + Arrays.stream(passengers).map(this::id).toList();
        }
        @Override public Object remove(int... ids) { return "remove:" + Arrays.toString(ids); }
    }
}
