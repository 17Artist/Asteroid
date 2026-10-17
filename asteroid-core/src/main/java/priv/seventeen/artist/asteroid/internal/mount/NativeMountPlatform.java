package priv.seventeen.artist.asteroid.internal.mount;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Anchor;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Input;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.TransportIds;

/** Internal version SPI. No model names, business callbacks, registry, scheduler or ownership here. */
public interface NativeMountPlatform {
    void preflight();
    Object handle(Entity entity);
    boolean sameHandle(Entity entity, Object nativeHandle);
    boolean hasVehicle(Object nativeHandle);
    Object connection(Player player);
    boolean sprint(Object nativeHandle);

    /** Netty-safe: only immutable packet fields may be read. Null means unrelated packet. */
    Input decodeInput(Object packet, long sequence);

    /** These methods only read outgoing packet fields, never Bukkit or an NMS entity. */
    int spawnedEntityId(Object packet);
    int[] removedEntityIds(Object packet);

    /** Creates calculation/packet objects only. Never add an entity to a ServerLevel. */
    Transport createTransport(Object hostHandle, Object riderHandle, Anchor anchor);
    void follow(Object riderHandle, Transport transport, Anchor anchor);

    interface Transport extends AutoCloseable {
        TransportIds ids();
        /** Complete spawn/metadata/pivot-to-vehicle/vehicle-to-rider sequence. */
        Object[] spawnPackets(Anchor anchor);
        Object[] removePackets();
        @Override void close();
    }
}
