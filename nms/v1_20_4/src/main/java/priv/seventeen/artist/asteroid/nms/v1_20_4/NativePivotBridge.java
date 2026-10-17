package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.joml.Vector3f;
import priv.seventeen.artist.asteroid.entity.NativeModelPivotNMS;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

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
        for (Pose pose : POSES) point(nativeHost, nativeHost.getDimensions(pose), 1);
        point(nativeHost, nativeHost.getDimensions(nativeHost.getPose()), ((LivingEntity) nativeHost).getScale());
        if (craft.getHandle() != nativeHost || nativeHost.isRemoved() || !host.isValid() || host.isDead()
                || host.getWorld() != world || Bukkit.getEntity(host.getUniqueId()) != host)
            throw new IllegalStateException("Pivot host changed during cold qualification");
        return new NativeModelPivotNMS.PivotHost(host, NativeModelPivotNMS.Family.ATTACHMENT_1204);
    }
    static NativeModelPivotNMS.PivotSample sample(org.bukkit.entity.Entity host, boolean camera) {
        var description = describe(host);
        var craft = (CraftEntity) host;
        Entity nativeHost = craft.getHandle(); var world = host.getWorld();
        double mount = (float) (point(nativeHost, nativeHost.getDimensions(Pose.STANDING), 1) + 0.5);
        double display = point(nativeHost, nativeHost.getDimensions(nativeHost.getPose()),
                ((LivingEntity) nativeHost).getScale()) + 0.5;
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
    // 1.20.4's native method returns Vector3f, not the old MEG helper's erroneous Vec3D cast.
    private static final Method ATTACHMENT = attachmentMethod();
    private static Method attachmentMethod() {
        Method found = null;
        for (var method : Entity.class.getDeclaredMethods()) {
            if (method.getReturnType() != Vector3f.class
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{Entity.class, EntityDimensions.class, float.class})) continue;
            if (found != null) throw new IllegalStateException("Ambiguous native passenger attachment method");
            found = method;
        }
        if (found == null || !found.trySetAccessible()) throw new IllegalStateException("Native passenger attachment unavailable");
        return found;
    }
    private static double point(Entity entity, EntityDimensions dimensions, float scale) {
        try {
            // Each exact admitted class was audited: the passenger argument is not read.
            var value = (Vector3f) ATTACHMENT.invoke(entity, null, dimensions, scale);
            if (value == null || value.x != 0 || value.z != 0 || !bounded(value.y + 0.5))
                throw new UnsupportedOperationException("Invalid native passenger attachment");
            return value.y;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) throw error;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Native passenger attachment failed", cause);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Native attachment access failed", failure); }
    }
}
