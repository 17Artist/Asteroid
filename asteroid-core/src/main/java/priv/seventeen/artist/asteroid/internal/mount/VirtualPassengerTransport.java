package priv.seventeen.artist.asteroid.internal.mount;

import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Anchor;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.TransportIds;

import java.util.UUID;

/** Shared sparse publication sequence. Neither native object is ever inserted into a world. */
public final class VirtualPassengerTransport implements NativeMountPlatform.Transport {
    public interface Codec {
        int id(Object entity);
        UUID uuid(Object entity);
        void position(Object entity, double x, double y, double z);
        Object spawn(Object entity);
        Object metadata(Object entity);
        Object passengers(Object vehicle, Object... passengers);
        Object remove(int... ids);
    }

    private final Codec codec;
    private final Object pivot, vehicle, rider;
    private final double pivotOffset;
    private final TransportIds ids;
    private final Object[] removePackets;
    private boolean closed;

    public VirtualPassengerTransport(Codec codec, Object pivot, Object vehicle, Object rider, double pivotOffset) {
        this.codec = codec; this.pivot = pivot; this.vehicle = vehicle; this.rider = rider; this.pivotOffset = pivotOffset;
        ids = new TransportIds(codec.id(pivot), codec.uuid(pivot), codec.id(vehicle), codec.uuid(vehicle), -pivotOffset);
        removePackets = new Object[]{codec.passengers(vehicle), codec.passengers(pivot), codec.remove(ids.pivotId(), ids.vehicleId())};
    }
    public Object vehicleHandle() { return vehicle; }
    @Override public TransportIds ids() { return ids; }
    @Override public Object[] spawnPackets(Anchor anchor) {
        if (closed) throw new IllegalStateException("Virtual passenger transport is closed");
        codec.position(pivot, anchor.x(), anchor.y() - pivotOffset, anchor.z());
        codec.position(vehicle, anchor.x(), anchor.y(), anchor.z());
        return new Object[]{codec.spawn(pivot), codec.metadata(pivot), codec.spawn(vehicle), codec.metadata(vehicle),
                codec.passengers(pivot, vehicle), codec.passengers(vehicle, rider)};
    }
    @Override public Object[] removePackets() { return removePackets.clone(); }
    @Override public void close() { closed = true; }
}
