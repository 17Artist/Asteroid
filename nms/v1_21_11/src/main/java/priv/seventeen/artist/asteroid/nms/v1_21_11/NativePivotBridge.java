package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import priv.seventeen.artist.asteroid.entity.NativeModelPivotNMS;

/** Cold native qualification and one-shot static capture; live draws use the matching client leaf. */
final class NativePivotBridge {
    private NativePivotBridge() { }
    // Exact classes only: custom subclasses can override native passenger placement.
    private static final ClassValue<Boolean> SUPPORTED = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return type == ServerPlayer.class
                    || type == Zombie.class
                    || type == Husk.class
                    || type == Drowned.class
                    || type == Skeleton.class
                    || type == Stray.class
                    || type == WitherSkeleton.class
                    || type == Creeper.class
                    || type == Cow.class
                    || type == Pig.class
                    || type == Sheep.class;
        }
    };

    static NativeModelPivotNMS.PivotHost describe(org.bukkit.entity.Entity host) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Pivot host query requires the owner thread");
        if (!(host instanceof CraftEntity craft) || !host.isValid() || host.isDead())
            throw new IllegalArgumentException("Pivot host is not loaded");
        Entity nativeHost = craft.getHandle();
        var world = host.getWorld();
        if (!SUPPORTED.get(nativeHost.getClass())) throw new UnsupportedOperationException("Unsupported native pivot host class");
        for (Pose pose : POSES) {
            var dimensions = nativeHost.getDimensions(pose);
            point(dimensions);
            if (!bounded(dimensions.eyeHeight())) throw new UnsupportedOperationException("Invalid native camera height");
        }
        if (craft.getHandle() != nativeHost || nativeHost.isRemoved() || !host.isValid() || host.isDead()
                || host.getWorld() != world || Bukkit.getEntity(host.getUniqueId()) != host)
            throw new IllegalStateException("Pivot host changed during cold qualification");
        return new NativeModelPivotNMS.PivotHost(host, NativeModelPivotNMS.Family.ATTACHMENTS_121X);
    }
    static NativeModelPivotNMS.PivotSample sample(org.bukkit.entity.Entity host, boolean camera) {
        var description = describe(host);
        var craft = (CraftEntity) host;
        Entity nativeHost = craft.getHandle(); var world = host.getWorld();
        var standing = nativeHost.getDimensions(Pose.STANDING);
        var current = nativeHost.getDimensions(nativeHost.getPose());
        double standingPoint = point(standing), currentPoint = point(current);
        double mount = camera ? standing.eyeHeight() : (float) (standingPoint + 0.5);
        double display = camera ? currentPoint + (float) (current.eyeHeight() - currentPoint) : currentPoint + 0.5;
        if (craft.getHandle() != nativeHost || nativeHost.isRemoved() || !host.isValid() || host.isDead()
                || host.getWorld() != world || Bukkit.getEntity(host.getUniqueId()) != host)
            throw new IllegalStateException("Pivot host changed during static capture");
        return new NativeModelPivotNMS.PivotSample(description, mount, display);
    }
    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 128; }
    private static final Pose[] POSES = poses();
    private static Pose[] poses() {
        Pose[] values = Pose.values();
        if (values.length > 32) throw new IllegalStateException("Native pose qualification budget exceeded");
        return values;
    }
    private static double point(EntityDimensions dimensions) {
        var points = dimensions.attachments();
        var value = points.getNullable(EntityAttachment.PASSENGER, 0, 0);
        if (value == null || points.getNullable(EntityAttachment.PASSENGER, 1, 0) != null
                || value.x != 0 || value.z != 0 || !bounded(value.y + 0.5))
            throw new UnsupportedOperationException("Pivot host requires one finite vertical passenger attachment");
        return value.y;
    }
}
