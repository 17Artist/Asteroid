package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.spigotmc.TrackingRange;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import priv.seventeen.artist.asteroid.internal.tracking.TrackingRangeControls;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Paper 1.21.1 only. Exact names verified in the fixed mappedServerJar + reobfMappings.tiny. */
final class NativeTrackingRangeBridge implements AutoCloseable {
    private final TrackingRangeControls<Entity, ChunkMap.TrackedEntity> controls = new TrackingRangeControls<>(new Port());
    NativeTrackingRangeNMS.RangeLease open(Entity host, int blocks) { return controls.open(host, blocks); }
    @Override public void close() { controls.close(); }
    static void check() {
        if (PaperCompat.isFolia()) throw new UnsupportedOperationException("Native range leases do not support Folia region transfers");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Native tracking range requires the Paper owner thread");
    }
    static boolean tracked(Entity host, Player viewer) {
        check();
        if (!(host instanceof CraftEntity craft) || !(viewer instanceof CraftPlayer player)
                || !host.isValid() || !viewer.isOnline() || host.getWorld() != viewer.getWorld()
                || Bukkit.getEntity(host.getUniqueId()) != host) return false;
        var tracker = ((CraftWorld) host.getWorld()).getHandle().getChunkSource().chunkMap.entityMap.get(host.getEntityId());
        return tracker != null && Access.entity(tracker) == craft.getHandle() && tracker.seenBy.contains(player.getHandle().connection);
    }
    private static final class Port implements TrackingRangeControls.Port<Entity, ChunkMap.TrackedEntity> {
        @Override public void checkOwner() { check(); }
        @Override public long tick() { return Integer.toUnsignedLong(MinecraftServer.getServer().getTickCount()); }
        @Override public TrackingRangeControls.Identity identity(Entity host) {
            if (!(host instanceof CraftEntity craft) || !host.isValid()
                    || Bukkit.getEntity(host.getUniqueId()) != host || craft.getHandle().isRemoved()) return null;
            return new TrackingRangeControls.Identity(host.getUniqueId(), host.getWorld().getUID(), host.getEntityId(), craft.getHandle());
        }
        @Override public ChunkMap.TrackedEntity tracker(Entity host, TrackingRangeControls.Identity identity) {
            if (identity == null) return null;
            var tracker = ((CraftWorld) host.getWorld()).getHandle().getChunkSource().chunkMap.entityMap.get(identity.entityId());
            return tracker != null && Access.entity(tracker) == identity.entity() ? tracker : null;
        }
        @Override public int read(ChunkMap.TrackedEntity tracker) { return Access.range(tracker); }
        @Override public boolean compareAndSet(ChunkMap.TrackedEntity tracker, int expected, int value) {
            if (Access.range(tracker) != expected) return false;
            Access.range(tracker, value);
            return Access.range(tracker) == value;
        }
        @Override public int canonicalRange(Entity host, TrackingRangeControls.Identity identity) {
            var nativeEntity = ((CraftEntity) host).getHandle();
            return TrackingRange.getEntityTrackingRange(nativeEntity, nativeEntity.getType().clientTrackingRange() * 16);
        }
    }
    private static final class Access {
        // Reflection, not VarHandle: final instance fields cannot be written by a VarHandle.
        static final Field RANGE = field(int.class, "range", "d");
        static final Field ENTITY = field(net.minecraft.world.entity.Entity.class, "entity", "c");
        static Field field(Class<?> expected, String mojang, String spigot) {
            for (String name : new String[]{mojang, spigot}) {
                try {
                    Field field = ChunkMap.TrackedEntity.class.getDeclaredField(name);
                    if (field.getType() != expected || Modifier.isStatic(field.getModifiers()))
                        throw new IllegalStateException("Unexpected native tracking field type: " + name);
                    if (!field.trySetAccessible()) throw new IllegalStateException("Native tracking field is inaccessible: " + name);
                    return field;
                } catch (NoSuchFieldException ignored) { /* The other proven namespace is expected. */ }
            }
            throw new IllegalStateException("Native tracking field is unavailable: " + mojang);
        }
        static Object entity(ChunkMap.TrackedEntity tracker) {
            try { return ENTITY.get(tracker); } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        static int range(ChunkMap.TrackedEntity tracker) {
            try { return RANGE.getInt(tracker); } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        static void range(ChunkMap.TrackedEntity tracker, int value) {
            try { RANGE.setInt(tracker, value); } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
    }
}
