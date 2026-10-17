package priv.seventeen.artist.asteroid.nms.v1_20_1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
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
        if (!bounded(nativeHost.getPassengersRidingOffset() + 0.375)) throw new UnsupportedOperationException("Invalid native pivot height");
        if (craft.getHandle() != nativeHost || nativeHost.isRemoved() || !host.isValid() || host.isDead()
                || host.getWorld() != world || Bukkit.getEntity(host.getUniqueId()) != host)
            throw new IllegalStateException("Pivot host changed during cold qualification");
        return new NativeModelPivotNMS.PivotHost(host, NativeModelPivotNMS.Family.LEGACY_1201);
    }
    static NativeModelPivotNMS.PivotSample sample(org.bukkit.entity.Entity host, boolean camera) {
        var description = describe(host);
        var craft = (CraftEntity) host;
        Entity nativeHost = craft.getHandle(); var world = host.getWorld();
        double offset = nativeHost.getPassengersRidingOffset();
        double mount = (float) offset, display = offset + 0.375;
        if (craft.getHandle() != nativeHost || nativeHost.isRemoved() || !host.isValid() || host.isDead()
                || host.getWorld() != world || Bukkit.getEntity(host.getUniqueId()) != host)
            throw new IllegalStateException("Pivot host changed during static capture");
        return new NativeModelPivotNMS.PivotSample(description, mount, display);
    }
    private static boolean bounded(double value) { return Double.isFinite(value) && Math.abs(value) <= 128; }
}
