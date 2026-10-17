package priv.seventeen.artist.asteroid.nms.v1_21_8;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.util.BoundingBox;
import priv.seventeen.artist.asteroid.entity.PrecisionHitbox;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Never tracked by clients. One broad-phase entry per owner, not one network entity per bone. */
final class PrecisionProxyImpl extends CustomEntityImpl implements PrecisionHitbox {
    private static final VarHandle BOUNDS = boundsField();
    private static final Map<Integer,LivingEntity> OWNERS = new HashMap<>();
    private static final Map<Integer,Integer> OWNER_REFS = new HashMap<>();
    // Damage relays are replay destinations, not extra query proxies. Never suppress the owner's
    // vanilla box solely because a relay exists while precision projectiles are disabled.
    private static final Map<Integer,LivingEntity> REPLAY_OWNERS = new HashMap<>();
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
    private final LivingEntity target;

    PrecisionProxyImpl(ServerLevel level, LivingEntity owner, BoundingBox bounds, Trace trace) {
        super(level, owner);
        this.target = Objects.requireNonNull(owner);
        getBukkitEntity().setPersistent(false);
        update(bounds, trace);
    }
    static LivingEntity proxyOwner(int id) { LivingEntity owner=OWNERS.get(id);return owner==null?REPLAY_OWNERS.get(id):owner; }
    static void registerReplayAlias(int id,LivingEntity owner){if(REPLAY_OWNERS.putIfAbsent(id,owner)!=null)throw new IllegalStateException("Duplicate damage relay ID");}
    static void removeReplayAlias(int id){REPLAY_OWNERS.remove(id);}
    static boolean isOwner(int id) { return OWNER_REFS.containsKey(id); }
    static int canonicalId(int id) { LivingEntity owner=proxyOwner(id);return owner==null?id:owner.getEntityId(); }
    void registered() { if(OWNERS.putIfAbsent(getId(),target)==null) OWNER_REFS.merge(target.getEntityId(),1,Integer::sum); }
    private void unregistered() { LivingEntity owner=OWNERS.remove(getId());if(owner!=null)OWNER_REFS.computeIfPresent(owner.getEntityId(),(id,count)->count<=1?null:count-1); }
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
        if (bounds != null) BOUNDS.set(this, new PreciseBounds(bounds, trace));
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
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeHitByProjectile() { return !isRemoved(); }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public org.bukkit.entity.Entity entity() { return getBukkitEntity(); }
    @Override public boolean valid() { return !isRemoved() && target.isValid(); }
    @Override public void close() { unregistered(); if (!isRemoved()) remove(); }
    @Override public void remove() { unregistered(); super.remove(); }
    @Override public void remove(Entity.RemovalReason reason) { unregistered(); super.remove(reason); }
    @Override public void remove(Entity.RemovalReason reason, EntityRemoveEvent.Cause cause) { unregistered(); super.remove(reason,cause); }

    static final class PreciseBounds extends AABB {
        private final Trace trace;
        PreciseBounds(BoundingBox b, Trace trace) { this(b.getMinX(),b.getMinY(),b.getMinZ(),b.getMaxX(),b.getMaxY(),b.getMaxZ(),trace); }
        private PreciseBounds(double ax,double ay,double az,double bx,double by,double bz,Trace trace) {
            super(ax,ay,az,bx,by,bz); this.trace = trace;
        }
        @Override public AABB inflate(double x,double y,double z) {
            // Vanilla broad-phase margin is not an inflation of authored combat geometry.
            return new PreciseBounds(minX-x,minY-y,minZ-z,maxX+x,maxY+y,maxZ+z,trace);
        }
        @Override public Optional<Vec3> clip(Vec3 a, Vec3 b) {
            double t = trace.intersect(a.x,a.y,a.z,b.x,b.y,b.z);
            return Double.isFinite(t) && t >= 0 && t <= 1 ? Optional.of(a.add(b.subtract(a).scale(t))) : Optional.empty();
        }
        @Override public boolean contains(double x,double y,double z) {
            double t=trace.intersect(x,y,z,x,y,z);
            return Double.isFinite(t)&&t>=0&&t<=1;
        }
    }
}
