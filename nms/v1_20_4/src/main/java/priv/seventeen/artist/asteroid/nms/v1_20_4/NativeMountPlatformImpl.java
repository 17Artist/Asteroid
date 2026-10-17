package priv.seventeen.artist.asteroid.nms.v1_20_4;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Anchor;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Input;
import priv.seventeen.artist.asteroid.internal.mount.NativeMountFields;
import priv.seventeen.artist.asteroid.internal.mount.NativeMountPlatform;
import priv.seventeen.artist.asteroid.internal.mount.VirtualPassengerTransport;

import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.UUID;

/** Typed v1_20_4 ABI only; ownership, mailboxes and publication live in asteroid-core. */
final class NativeMountPlatformImpl implements NativeMountPlatform, VirtualPassengerTransport.Codec {
    private VarHandle floating;
    @Override public void preflight() {
        if (floating == null) floating = NativeMountFields.booleanField(ServerGamePacketListenerImpl.class, "clientIsFloating", "E");
    }
    @Override public Object handle(Entity entity) {
        if (!(entity instanceof CraftEntity craft)) throw new IllegalArgumentException("Not a native passenger entity");
        return craft.getHandle();
    }
    @Override public boolean sameHandle(Entity entity, Object nativeHandle) {
        return entity instanceof CraftEntity craft && craft.getHandle() == nativeHandle;
    }
    @Override public boolean hasVehicle(Object nativeHandle) { return nativeEntity(nativeHandle).getVehicle() != null; }
    @Override public Object connection(Player player) { return ((CraftPlayer) player).getHandle().connection.connection.channel; }
    @Override public boolean sprint(Object nativeHandle) { return nativeEntity(nativeHandle).isSprinting(); }
    @Override public Input decodeInput(Object packet, long sequence) {
        if (!(packet instanceof ServerboundPlayerInputPacket input)) return null;
        return Input.legacy(sequence, input.getXxa(), input.getZza(), input.isJumping(), input.isShiftKeyDown(), false);
    }
    @Override public int spawnedEntityId(Object packet) { return packet instanceof ClientboundAddEntityPacket spawn ? spawn.getId() : -1; }
    @Override public int[] removedEntityIds(Object packet) {
        return packet instanceof ClientboundRemoveEntitiesPacket remove ? remove.getEntityIds().toIntArray() : NO_IDS;
    }
    private static final int[] NO_IDS = new int[0];
    @Override public Transport createTransport(Object hostHandle, Object riderHandle, Anchor anchor) {
        var level = nativeEntity(hostHandle).level();
        AreaEffectCloud pivot = new AreaEffectCloud(EntityType.AREA_EFFECT_CLOUD, level);
        pivot.setRadius(0); pivot.setDuration(Integer.MAX_VALUE); pivot.setInvisible(true); pivot.setNoGravity(true);
        ArmorStand vehicle = new ArmorStand(EntityType.ARMOR_STAND, level);
        vehicle.setMarker(true); vehicle.setInvisible(true); vehicle.setNoGravity(true);
        return new VirtualPassengerTransport(this, pivot, vehicle, riderHandle, 0.5);
    }
    @Override public void follow(Object riderHandle, Transport transport, Anchor anchor) {
        var passenger = nativeEntity(riderHandle);
        var vehicle = nativeEntity(((VirtualPassengerTransport) transport).vehicleHandle());
        passenger.setPos(anchor.x(), anchor.y() + (passenger.getMyRidingOffset(vehicle)), anchor.z());
        passenger.setDeltaMovement(Vec3.ZERO);
        passenger.resetFallDistance();
        if (passenger instanceof ServerPlayer player) floating.set(player.connection, false);
    }
    @Override public int id(Object entity) { return nativeEntity(entity).getId(); }
    @Override public UUID uuid(Object entity) { return nativeEntity(entity).getUUID(); }
    @Override public void position(Object entity, double x, double y, double z) { nativeEntity(entity).setPos(x, y, z); }
    @Override public Object spawn(Object value) {
        var entity = nativeEntity(value);
        return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(), entity.getType(), 0, Vec3.ZERO, entity.getYHeadRot());
    }
    @Override public Object metadata(Object value) {
        var entity = nativeEntity(value);
        var values = entity.getEntityData().getNonDefaultValues();
        return new ClientboundSetEntityDataPacket(entity.getId(), values == null ? List.of() : values);
    }
    @Override public Object passengers(Object value, Object... passengers) {
        var vehicle = nativeEntity(value);
        var previous = vehicle.passengers;
        var builder = ImmutableList.<net.minecraft.world.entity.Entity>builder();
        for (Object passenger : passengers) builder.add(nativeEntity(passenger));
        // Only our unspawned packet dummy is touched. Never assign a live rider's vehicle field.
        try { vehicle.passengers = builder.build(); return new ClientboundSetPassengersPacket(vehicle); }
        finally { vehicle.passengers = previous; }
    }
    @Override public Object remove(int... ids) { return new ClientboundRemoveEntitiesPacket(ids); }
    private static net.minecraft.world.entity.Entity nativeEntity(Object value) { return (net.minecraft.world.entity.Entity) value; }
}

