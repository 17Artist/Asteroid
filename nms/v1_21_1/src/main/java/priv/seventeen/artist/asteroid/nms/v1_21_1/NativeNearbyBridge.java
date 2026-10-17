package priv.seventeen.artist.asteroid.nms.v1_21_1;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Paper 1.21.1's own untyped query, with its existing early-return overload. */
final class NativeNearbyBridge {
    private static final boolean AVAILABLE = available();
    private NativeNearbyBridge() {}

    private static boolean available() {
        try {
            // Cold once per loaded version bridge; absent Moonrise (including Spigot) is unsupported.
            var loader=NativeNearbyBridge.class.getClassLoader();
            var level=Class.forName("ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemLevel",false,loader);
            var lookup=Class.forName("ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup",false,loader);
            return level.getMethod("moonrise$getEntityLookup").getReturnType()==lookup
                    &&lookup.getMethod("getEntities",net.minecraft.world.entity.Entity.class,AABB.class,
                            List.class,Predicate.class,int.class).getReturnType()==void.class;
        } catch (ReflectiveOperationException | LinkageError unavailable) { return false; }
    }
    static boolean supported() { return AVAILABLE; }

    static List<Entity> nearby(Entity host,double x,double y,double z,int limit) {
        if(!AVAILABLE)throw new UnsupportedOperationException("Paper ordered nearby query unavailable");
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Nearby prefix requires the server owner thread");
        if(limit<1||limit>64||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||x<0||y<0||z<0)
            throw new IllegalArgumentException("Nearby prefix requires finite nonnegative extents and limit 1..64");
        if(!(host instanceof CraftEntity craft))throw new IllegalArgumentException("Nearby prefix requires a native entity");
        var entity=craft.getHandle();
        if(entity.generation)throw new IllegalStateException("Cannot get nearby entities during world generation");
        if(!((Object)entity.level() instanceof ChunkSystemLevel level))throw new UnsupportedOperationException("Native world has no ordered nearby query");
        AABB box=entity.getBoundingBox().inflate(x,y,z);
        var nativeResult=new ArrayList<net.minecraft.world.entity.Entity>(limit);
        // Exact old untyped allEntities path, including source exclusion and dragon parts.
        // null is the native equivalent of CraftEntity's side-effect-free alwaysTrue predicate.
        entity.level().getProfiler().incrementCounter("getEntities");
        EntityLookup lookup=level.moonrise$getEntityLookup();
        lookup.getEntities(entity,box,nativeResult,null,limit);
        if(nativeResult.size()>limit)throw new IllegalStateException("Native nearby prefix exceeded its result limit");
        var result=new ArrayList<Entity>(nativeResult.size());
        for(var value:nativeResult)result.add(value.getBukkitEntity());
        return result;
    }
}
