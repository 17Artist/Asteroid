package priv.seventeen.artist.asteroid.nms.v1_20_1;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import org.bukkit.entity.Entity;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** Arrow candidate routing, with native piercing IDs counting real owners exactly once. */
final class PrecisionPiercing {
    private static final VarHandle IDS = findIds();
    private static final VarHandle HIT_CANCELLED = findHitCancelled();
    private static final ThreadLocal<Integer> QUERY_OWNER = new ThreadLocal<>();
    private static VarHandle findHitCancelled(){
        try{
            Class<?> type=Projectile.class;
            return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).findVarHandle(type,"hitCancelled",boolean.class);
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    static boolean beginReplay(Projectile projectile){
        boolean previous=(boolean)HIT_CANCELLED.get(projectile);HIT_CANCELLED.set(projectile,false);return previous;
    }
    static void endReplay(Projectile projectile,boolean previous,int target){
        // A real impact cancelled by another plugin is still one native piercing attempt, not an
        // invitation to dispatch its event again while the arrow is on the collider boundary.
        // Non-piercing arrows also need owner deduplication: a rejected native hurt may
        // leave the arrow inside several overlapping part clusters in this same flight.
        if(projectile instanceof AbstractArrow arrow){
            IntOpenHashSet ids=(IntOpenHashSet)IDS.get(arrow);if(ids!=null)ids.add(PrecisionProxyImpl.canonicalId(target));
        }
        HIT_CANCELLED.set(projectile,previous);
    }
    private static VarHandle findIds() {
        try {
            Field found = null;
            for (Field f : AbstractArrow.class.getDeclaredFields()) if (f.getType() == IntOpenHashSet.class) {
                if (found != null) throw new IllegalStateException("Ambiguous arrow piercing set");
                found = f;
            }
            if (found == null) throw new IllegalStateException("Arrow piercing set unavailable");
            return MethodHandles.privateLookupIn(AbstractArrow.class,MethodHandles.lookup()).unreflectVarHandle(found);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    static boolean prepare(Projectile projectile) {
        if (!(projectile instanceof AbstractArrow arrow)) return false;
        IntOpenHashSet previous = (IntOpenHashSet) IDS.get(arrow);
        if (previous instanceof OwnerIds) return true;
        OwnerIds ids = new OwnerIds((org.bukkit.entity.Projectile) arrow.getBukkitEntity());
        if (previous != null) ids.addAll(previous);
        IDS.set(arrow, ids);
        return true;
    }
    static boolean canHit(org.bukkit.entity.Projectile projectile,Entity target) {
        Integer previous=QUERY_OWNER.get();QUERY_OWNER.set(target.getEntityId());
        try{return projectile.canHitEntity(target);}finally{if(previous==null)QUERY_OWNER.remove();else QUERY_OWNER.set(previous);}
    }
    private static final class OwnerIds extends IntOpenHashSet {
        private final org.bukkit.entity.Projectile projectile;
        // Paper may record a cancelled proxy before dispatching the next hit event (1.21.8+).
        // Those IDs suppress query entries, but must never consume a real owner's piercing slot.
        private IntOpenHashSet skippedProxies;
        OwnerIds(org.bukkit.entity.Projectile projectile){this.projectile=projectile;}
        @Override public boolean contains(int id) {
            if(PrecisionProxyImpl.excluded(id))return true;
            var owner=PrecisionProxyImpl.proxyOwner(id);
            if(owner!=null)return (skippedProxies!=null&&skippedProxies.contains(id))||super.contains(owner.getEntityId())||!canHit(projectile,owner);
            Integer query=QUERY_OWNER.get();
            return (PrecisionProxyImpl.isOwner(id)&&(query==null||query!=id))||super.contains(id);
        }
        @Override public boolean add(int id) {
            if(PrecisionProxyImpl.proxyOwner(id)!=null){
                if(skippedProxies==null)skippedProxies=new IntOpenHashSet();
                return skippedProxies.add(id);
            }
            return super.add(id);
        }
        @Override public void clear(){super.clear();if(skippedProxies!=null)skippedProxies.clear();}
    }
}
