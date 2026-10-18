package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.util.BoundingBox;
import priv.seventeen.artist.asteroid.entity.NativePrecisionQuery;
import priv.seventeen.artist.asteroid.entity.PrecisionHitbox;
import priv.seventeen.artist.asteroid.entity.NativeReceiptOwnership;
import priv.seventeen.artist.asteroid.internal.entity.NativePrecisionParameters;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Never tracked by clients. One broad-phase entry per owner, not one network entity per bone. */
final class PrecisionProxyImpl extends CustomEntityImpl implements PrecisionHitbox, NativeReceiptOwnership {
    private static final VarHandle BOUNDS = boundsField();
    private static final Map<Integer,org.bukkit.entity.Entity> OWNERS = new HashMap<>();
    private static final Map<Integer,Integer> OWNER_REFS = new HashMap<>();
    // Damage relays are replay destinations, not extra query proxies. Never suppress the owner's
    // vanilla box solely because a relay exists while precision projectiles are disabled.
    private static final Map<Integer,org.bukkit.entity.Entity> REPLAY_OWNERS = new HashMap<>();
    private static final Map<Integer,Integer> EXCLUDED_BODIES = new HashMap<>();
    static boolean excluded(int id){return EXCLUDED_BODIES.containsKey(id);}
    static AutoCloseable exclude(org.bukkit.entity.Entity entity){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Projectile exclusions require the entity thread");
        Objects.requireNonNull(entity);int id=entity.getEntityId();
        if(!EXCLUDED_BODIES.containsKey(id)&&EXCLUDED_BODIES.size()>=4096)throw new IllegalStateException("Projectile exclusion budget exceeded");
        EXCLUDED_BODIES.merge(id,1,Integer::sum);
        return new AutoCloseable(){boolean closed;public void close(){
            if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Projectile exclusions require the entity thread");
            if(!closed){closed=true;EXCLUDED_BODIES.computeIfPresent(id,(key,count)->count<=1?null:count-1);}
        }};
    }
    private BoundingBox bounds;
    private Trace trace;
    private final org.bukkit.entity.Entity target;
    private final Entity nativeOwner;

    PrecisionProxyImpl(ServerLevel level, org.bukkit.entity.Entity owner, BoundingBox bounds, Trace trace) {
        super(level, owner);
        this.target = Objects.requireNonNull(owner);
        this.nativeOwner = ((CraftEntity) owner).getHandle();
        getBukkitEntity().setPersistent(false);
        update(bounds, trace);
    }
    static org.bukkit.entity.Entity proxyOwner(int id) { org.bukkit.entity.Entity owner=OWNERS.get(id);return owner==null?REPLAY_OWNERS.get(id):owner; }
    static void registerReplayAlias(int id,org.bukkit.entity.Entity owner){if(REPLAY_OWNERS.putIfAbsent(id,owner)!=null)throw new IllegalStateException("Duplicate damage relay ID");}
    static void removeReplayAlias(int id){REPLAY_OWNERS.remove(id);}
    static boolean isOwner(int id) { return OWNER_REFS.containsKey(id); }
    static int canonicalId(int id) { org.bukkit.entity.Entity owner=proxyOwner(id);return owner==null?id:owner.getEntityId(); }
    void registered() { if(OWNERS.putIfAbsent(getId(),target)==null) OWNER_REFS.merge(target.getEntityId(),1,Integer::sum); }
    private void unregistered() { org.bukkit.entity.Entity owner=OWNERS.remove(getId());if(owner!=null)OWNER_REFS.computeIfPresent(owner.getEntityId(),(id,count)->count<=1?null:count-1); }
    private static VarHandle boundsField() {
        try {
            Field found = null;
            for (Field field : Entity.class.getDeclaredFields()) if (field.getType() == AABB.class && !Modifier.isStatic(field.getModifiers())) {
                if (found != null) throw new IllegalStateException("Ambiguous entity bounds field");
                found = field;
            }
            if (found == null) throw new IllegalStateException("Entity bounds field is unavailable");
            return MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup()).unreflectVarHandle(found);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }
    private void install() {
        if (bounds != null) BOUNDS.set(this, new PreciseBounds(bounds, trace, nativeOwner));
    }
    @Override public void update(BoundingBox bounds, Trace trace) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Precision hitbox requires the entity thread");
        double[] coordinates = {bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ()};
        for (double value : coordinates) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite hitbox");
        if (bounds.getWidthX() > 64 || bounds.getWidthZ() > 64 || bounds.getHeight() > 64)
            throw new IllegalArgumentException("Precision broad-phase span exceeds 64 blocks");
        this.bounds = bounds.clone();
        this.trace = Objects.requireNonNull(trace);
        setPos(bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterZ());
        install();
    }
    @Override public void setPos(double x, double y, double z) { super.setPos(x,y,z); install(); }
    @Override public void tick() {
        if (!target.isValid() || target.getWorld() != getBukkitEntity().getWorld()) { close(); return; }
        // Deliberately no LivingEntity AI, effects, movement or equipment ticking for a server-only query entry.
    }
    @Override public boolean broadcastToPlayer(ServerPlayer player) { return false; }
    @Override public float getPickRadius(){return queryPickRadius(trace,nativeOwner,super.getPickRadius());}
    static float queryPickRadius(Trace trace,Entity nativeOwner,float legacyRadius){
        return trace instanceof ContextTrace?nativeOwner.getPickRadius():legacyRadius;
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeHitByProjectile() { return !isRemoved(); }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public org.bukkit.entity.Entity entity() { return getBukkitEntity(); }
    @Override public boolean valid() { return !isRemoved() && target.isValid(); }
    @Override public boolean retainedOwner(org.bukkit.entity.Entity expectedOwner) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Precision ownership requires the entity thread");
        return expectedOwner == target && OWNERS.get(getId()) == target && !isRemoved()
            && getBukkitEntity().isValid() && target.getWorld() == getBukkitEntity().getWorld();
    }
    @Override public void close() { unregistered(); if (!isRemoved()) remove(); }
    @Override public void remove() { unregistered(); super.remove(); }
    @Override public void remove(Entity.RemovalReason reason) { unregistered(); super.remove(reason); }
    @Override public void remove(Entity.RemovalReason reason, EntityRemoveEvent.Cause cause) { unregistered(); super.remove(reason,cause); }

    static final class PreciseBounds extends AABB {
        private final Trace trace;
        private final Entity nativeOwner;
        private final double inflateX,inflateY,inflateZ;
        PreciseBounds(BoundingBox b,Trace trace,Entity nativeOwner) {
            this(b.getMinX(),b.getMinY(),b.getMinZ(),b.getMaxX(),b.getMaxY(),b.getMaxZ(),trace,nativeOwner,0,0,0);
        }
        private PreciseBounds(double ax,double ay,double az,double bx,double by,double bz,Trace trace,
                              Entity nativeOwner,double inflateX,double inflateY,double inflateZ) {
            super(ax,ay,az,bx,by,bz);this.trace=Objects.requireNonNull(trace);this.nativeOwner=Objects.requireNonNull(nativeOwner);
            this.inflateX=inflateX;this.inflateY=inflateY;this.inflateZ=inflateZ;
        }
        @Override public AABB inflate(double value){return inflate(value,value,value);}
        @Override public AABB inflate(double x,double y,double z) {
            // Keep the exact native operation sequence; authored Trace still receives no margin.
            return new PreciseBounds(minX-x,minY-y,minZ-z,maxX+x,maxY+y,maxZ+z,trace,nativeOwner,
                inflateX+x,inflateY+y,inflateZ+z);
        }
        @Override public Optional<Vec3> clip(Vec3 a,Vec3 b) {
            double t;
            if(trace instanceof ContextTrace context){
                AABB ownerBounds=nativeOwner.getBoundingBox();
                var ownerHit=ownerBounds.inflate(inflateX,inflateY,inflateZ).clip(a,b);
                double ownerParameter=Double.NaN;
                if(ownerHit.isPresent()){
                    var point=ownerHit.get();ownerParameter=NativePrecisionParameters.clipped(a.x,a.y,a.z,b.x,b.y,b.z,point.x,point.y,point.z);
                }
                t=context.intersect(new NativePrecisionQuery(a.x,a.y,a.z,b.x,b.y,b.z,inflateX,inflateY,inflateZ,
                    NativePrecisionQuery.Mode.CLIP,ownerParameter,ownerBounds.minX,ownerBounds.minY,ownerBounds.minZ,
                    ownerBounds.maxX,ownerBounds.maxY,ownerBounds.maxZ));
            }else{
                // The existing hot path does not allocate a context record/array or query the owner.
                t=trace.intersect(a.x,a.y,a.z,b.x,b.y,b.z);
            }
            return Double.isFinite(t)&&t>=0&&t<=1?Optional.of(a.add(b.subtract(a).scale(t))):Optional.empty();
        }
        @Override public boolean contains(double x,double y,double z) {
            double t;
            if(trace instanceof ContextTrace context){
                AABB ownerBounds=nativeOwner.getBoundingBox();
                boolean ownerContains=ownerBounds.inflate(inflateX,inflateY,inflateZ).contains(x,y,z);
                t=context.intersect(new NativePrecisionQuery(x,y,z,x,y,z,inflateX,inflateY,inflateZ,
                    NativePrecisionQuery.Mode.CONTAINS,ownerContains?0:Double.NaN,ownerBounds.minX,ownerBounds.minY,ownerBounds.minZ,
                    ownerBounds.maxX,ownerBounds.maxY,ownerBounds.maxZ));
            }else t=trace.intersect(x,y,z,x,y,z);
            return Double.isFinite(t)&&t>=0&&t<=1;
        }
    }
}
