package priv.seventeen.artist.asteroid.internal.navigation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS.Point;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS.RouteGuard;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import priv.seventeen.artist.asteroid.internal.movement.NativeMotionCleanup;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Owner-thread query/commit fence. Native query metadata must belong to a detached navigator;
 * installed navigation must never run an unbudgeted automatic query. The Port is internal NMS,
 * not a plugin-supplied adapter. Tests with a recording Port are not native/Paper evidence.
 */
public final class NativeNavigationLease implements NativeNavigationNMS.NavigationLease, Listener {
    public static final int MAX_LEASES=256, MAX_POINTS=256, MAX_QUERIES=64, MAX_READS=256, MAX_OPENS=64, MAX_DEPTH=4;
    public interface Port {
        void install(NativeNavigationLease lease);
        boolean owned();
        long tick();
        Object query(Point target);
        boolean reachable(Object path);
        /** Must reject size > MAX_POINTS before allocating or traversing points. */
        List<Point> points(Object path, boolean remaining);
        Object currentPath();
        boolean commit(Object path, double speed);
        void stop();
        /** Cold, read-only exact LOOK/Body preparation; no native write before retention. */
        default FacingPort prepareFacing(){throw new UnsupportedOperationException("Native facing is unavailable");}
        /** Each field is restored only if still this exact Port's controller. */
        void restore();
    }
    public interface FacingPort {
        void install(NativeNavigationNMS.FacingLease receipt,float yaw,float pitch);
        boolean owned(float yaw,float pitch);
        /** Restore only exact owned fields; failed attempts remain retryable. */
        void restore();
    }
    private Facing facing;
    private static final Map<UUID,NativeNavigationLease> LIVE=new HashMap<>();
    private static long budgetTick=-1;
    private static int queries, reads, opens, depth;
    private final Plugin plugin;
    private final Mob entity;
    private final UUID id;
    private final World world;
    private final int nativeId;
    private final Port port;
    private final Thread thread=Thread.currentThread();
    private long epoch;
    private boolean closed, registered, operation, released, restored, cleaning, admitting=true;
    private PluginDisableEvent lastDisableEvent;

    private NativeNavigationLease(Plugin plugin, Mob entity, Port port) {
        this.plugin=Objects.requireNonNull(plugin); this.entity=Objects.requireNonNull(entity);
        this.port=Objects.requireNonNull(port); id=entity.getUniqueId(); world=entity.getWorld();
        nativeId=entity.getEntityId();
    }
    public static NativeNavigationLease open(Plugin owner, Mob entity, Port port) {
        Objects.requireNonNull(port);
        return open(owner,entity,port.tick(),()->port);
    }
    /** Charge cold NMS admission before constructing/reflection-scanning a native Port. */
    public static NativeNavigationLease open(Plugin owner,Mob entity,long now,Supplier<? extends Port> factory) {
        return openRetained(owner,entity,now,factory,ignored->{});
    }
    public static NativeNavigationLease openRetained(Plugin owner,Mob entity,long now,Supplier<? extends Port> factory,
            Consumer<NativeNavigationNMS.NavigationLease> retain) {
        checkThread();Objects.requireNonNull(retain);NativeMotionCleanup.requireAdmission(owner);
        budget(now);
        if (opens>=MAX_OPENS) throw new IllegalStateException("Native navigation acquisition budget exceeded");
        opens++;
        if (depth>=MAX_DEPTH) throw new IllegalStateException("Native navigation callback depth exceeded");
        depth++;
        try {
            Objects.requireNonNull(owner);Objects.requireNonNull(entity);Objects.requireNonNull(factory);
            if (LIVE.size()>=MAX_LEASES || LIVE.containsKey(entity.getUniqueId()))
                throw new IllegalStateException("Native navigation capacity or owner conflict");
            Port port=Objects.requireNonNull(factory.get());
            if (port.tick()!=now) throw new IllegalStateException("Native navigation tick changed during admission");
            var lease=new NativeNavigationLease(owner,entity,port);
            if (!lease.live() || LIVE.size()>=MAX_LEASES || LIVE.containsKey(lease.id))
                throw new IllegalStateException("Native navigation host, capacity or owner changed during admission");
            LIVE.put(lease.id,lease);
            Throwable rejected=null;
            try {
                retain.accept(lease);NativeMotionCleanup.requireAdmission(owner);
                if(!lease.live()||port.tick()!=now||lease.closed||LIVE.get(lease.id)!=lease)
                    throw new IllegalStateException("Native navigation receipt was revoked before installation");
                port.install(lease);
                if(lease.closed)throw new IllegalStateException("Native navigation closed during installation");
                lease.registered=true; // include a partial registerEvents failure in exact cleanup
                Bukkit.getPluginManager().registerEvents(lease,owner);
                NativeMotionCleanup.requireAdmission(owner);
                if (!lease.installedCurrent() || port.tick()!=now || lease.closed || LIVE.get(lease.id)!=lease) throw new IllegalStateException("Native navigation changed during installation");
            } catch (RuntimeException | Error error) { rejected=error; }
            finally { lease.admitting=false; }
            if(rejected!=null){
                try { lease.close(); } catch (RuntimeException | Error cleanup) { rejected=NativeMotionCleanup.combine(rejected,cleanup); }
                NativeMotionCleanup.rethrow(rejected);
            }
            return lease;
        } finally { depth--; }
    }
    /** Cold preparation cannot write through a live/provisionally installed exact UUID lease. */
    public static void requireUnowned(Mob entity) {
        checkThread();
        if (LIVE.containsKey(Objects.requireNonNull(entity).getUniqueId()))
            throw new IllegalStateException("Native navigation already owns this UUID");
    }
    private static void checkThread() {
        if (PaperCompat.isFolia()) throw new UnsupportedOperationException("Native navigation does not support Folia");
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Native navigation requires the owner thread");
    }
    private void owner() {
        checkThread();
        if (Thread.currentThread()!=thread) throw new IllegalStateException("Native navigation owner thread changed");
    }
    private boolean live() {
        return plugin.isEnabled() && entity.isValid() && !entity.isDead() && entity.hasAI()
                && entity.getWorld()==world && entity.getEntityId()==nativeId && entity.getUniqueId().equals(id);
    }
    private boolean installedCurrent() { return !closed && LIVE.get(id)==this && live() && port.owned() && !closed && LIVE.get(id)==this; }
    private boolean valid() { return !admitting && installedCurrent(); }
    /** Called by both owned native controllers, with no extra Bukkit scheduler. */
    public boolean maintain() {
        owner();
        if (closed) return false;
        if (valid()) {
            Facing child=facing;if(child!=null&&!child.closed)child.current();
            if(valid())return true;
        }
        close(); return false;
    }
    @Override public boolean active() { return maintain(); }
    @Override public long epoch() { owner(); return epoch; }
    private boolean unchanged(long expected, long tick) {
        return epoch==expected && valid() && port.tick()==tick;
    }
    private boolean current(long expected,long tick,RouteGuard guard) {
        if (!unchanged(expected,tick)) return false;
        boolean accepted=guard.current();
        return accepted && unchanged(expected,tick);
    }
    private boolean begin(boolean query) {
        owner();
        if (operation) throw new IllegalStateException("Reentrant native navigation operation");
        if (!maintain()) return false;
        budget(port.tick());
        if (depth>=MAX_DEPTH) throw new IllegalStateException("Native navigation callback depth exceeded");
        if (query ? queries>=MAX_QUERIES : reads>=MAX_READS) return false;
        if (query) queries++; else reads++;
        depth++; operation=true; return true;
    }
    private static void budget(long now) {
        if (now<0 || now<budgetTick) throw new IllegalStateException("Native navigation tick moved backwards");
        // A callback cannot introduce a fresh tick to refill the global budget.
        if (depth!=0 && now!=budgetTick) throw new IllegalStateException("Native navigation tick changed inside callback");
        if (now!=budgetTick) { budgetTick=now; queries=reads=opens=0; }
    }
    private void end() { operation=false; depth--; }
    @Override public boolean move(Location destination,double speed,RouteGuard guard) {
        Objects.requireNonNull(destination); Objects.requireNonNull(guard);
        owner();
        if(facing!=null){
            if(operation)throw new IllegalStateException("Reentrant native navigation operation");
            if(!maintain()||facing!=null)return false; // no query or movement while facing is owned/pending
        }
        if (!begin(true)) { stop(); return false; }
        long expected=epoch, tick=budgetTick;
        try {
            if (destination.getWorld()!=world || !Double.isFinite(speed) || speed<=0 || speed>4)
                throw new IllegalArgumentException("Invalid native navigation destination/speed");
            Point target=new Point(destination.getX(),destination.getY(),destination.getZ());
            if (!world.isChunkLoaded(destination.getBlockX()>>4,destination.getBlockZ()>>4)) return reject(expected,tick);
            if (!current(expected,tick,guard)) return reject(expected,tick);
            Object path=port.query(target);
            if (!current(expected,tick,guard)) return reject(expected,tick);
            if (path==null || !port.reachable(path)) return reject(expected,tick);
            List<Point> points=bounded(port.points(path,false));
            if (points==null) return reject(expected,tick);
            boolean accepted=guard.accept(points);
            if (!current(expected,tick,guard)) return reject(expected,tick);
            if (!accepted) return reject(expected,tick);
            boolean committed=port.commit(path,speed);
            return committed && unchanged(expected,tick);
        } catch (RuntimeException | Error failure) {
            if (unchanged(expected,tick)) {
                try { stop(); } catch (RuntimeException | Error cleanup) { if (cleanup!=failure) failure.addSuppressed(cleanup); }
            }
            throw failure;
        } finally { end(); }
    }
    private boolean reject(long expected,long tick) {
        if (unchanged(expected,tick)) stop();
        return false;
    }
    private static List<Point> bounded(List<Point> values) {
        if (values==null) return null;
        int size=values.size();
        if (size<0 || size>MAX_POINTS) return null;
        // Port returns immutable native snapshots. Copy with a hard traversal bound, not toArray.
        var result=new ArrayList<Point>(size); int count=0;
        for (Point point:values) { if (++count>MAX_POINTS) return null; result.add(Objects.requireNonNull(point)); }
        if (count!=size) return null;
        return Collections.unmodifiableList(result);
    }
    @Override public boolean currentSafe(Predicate<List<Point>> accept) {
        Objects.requireNonNull(accept);
        if (!begin(false)) return false;
        long expected=epoch,tick=budgetTick;
        try {
            Object path=port.currentPath();
            if (path==null) return unchanged(expected,tick);
            var route=bounded(port.points(path,true));
            if (route==null) return false;
            boolean accepted=accept.test(route);
            return accepted && unchanged(expected,tick);
        } finally { end(); }
    }
    @Override public void stop() {
        owner(); if (!maintain()) return;
        if (epoch==Long.MAX_VALUE) { close(); throw new IllegalStateException("Native navigation epoch exhausted"); }
        epoch++; port.stop();
    }
    @Override public NativeNavigationNMS.FacingLease freezeFacingRetained(float yaw,float pitch,int ticks,
            Consumer<NativeNavigationNMS.FacingLease> retain) {
        owner();Objects.requireNonNull(retain);
        if(!Float.isFinite(yaw)||yaw<-180||yaw>180||!Float.isFinite(pitch)||pitch<-90||pitch>90||ticks<6||ticks>200)
            throw new IllegalArgumentException("Native facing requires finite canonical angles and 6..200 ticks");
        if(operation||facing!=null)throw new IllegalStateException("Native facing is already owned or navigation is on-stack");
        if(!maintain()||Bukkit.getEntity(id)!=entity)throw new IllegalStateException("Native navigation is unavailable");
        long now=port.tick(),expected=epoch,expires=Math.addExact(now,ticks);budget(now);
        if(opens>=MAX_OPENS||depth>=MAX_DEPTH)throw new IllegalStateException("Native facing admission budget exceeded");
        opens++;depth++;operation=true;
        try {
            // Internal native factory resolves every field and eligibility gate before any write.
            FacingPort facePort=Objects.requireNonNull(port.prepareFacing());
            if(!unchanged(expected,now)||Bukkit.getEntity(id)!=entity)throw new IllegalStateException("Native navigation changed during facing preflight");
            Facing child=new Facing(facePort,yaw,pitch,now,expires);facing=child;
            Throwable failure=null;
            try {
                retain.accept(child);NativeMotionCleanup.requireAdmission(plugin);
                if(child.closed||facing!=child||!unchanged(expected,now)||Bukkit.getEntity(id)!=entity)
                    throw new IllegalStateException("Native facing was revoked before installation");
                facePort.install(child,yaw,pitch);
                if(child.closed||facing!=child||!unchanged(expected,now)||Bukkit.getEntity(id)!=entity||!facePort.owned(yaw,pitch))
                    throw new IllegalStateException("Native facing changed during installation");
            }catch(RuntimeException|Error error){failure=error;}
            finally{child.admitting=false;}
            if(failure!=null){
                try{child.close();}catch(RuntimeException|Error cleanup){failure=NativeMotionCleanup.combine(failure,cleanup);}
                NativeMotionCleanup.rethrow(failure);
            }
            return child;
        }finally{operation=false;depth--;}
    }
    private final class Facing implements NativeNavigationNMS.FacingLease {
        final FacingPort control;final float yaw,pitch;final long started,expires;
        boolean admitting=true,closed,released,cleaning,checking;
        Facing(FacingPort control,float yaw,float pitch,long started,long expires){
            this.control=control;this.yaw=yaw;this.pitch=pitch;this.started=started;this.expires=expires;
        }
        @Override public boolean current(){
            owner();if(closed||admitting)return false;
            if(checking)throw new IllegalStateException("Reentrant native facing readback");
            checking=true;
            try {
                long now=port.tick();
                if(facing==this&&valid()&&Bukkit.getEntity(id)==entity&&now>=started&&now<expires&&control.owned(yaw,pitch)
                        &&!closed&&facing==this&&valid()&&Bukkit.getEntity(id)==entity&&port.tick()==now)return true;
            }catch(RuntimeException|Error error){
                Throwable failure=error;
                try{close();}catch(RuntimeException|Error cleanup){failure=NativeMotionCleanup.combine(failure,cleanup);}
                NativeMotionCleanup.rethrow(failure);
            }finally{checking=false;}
            close();return false;
        }
        @Override public void close(){
            owner();if(released)return;closed=true;
            if(admitting)throw new IllegalStateException("Native facing cleanup awaits the admission stack");
            if(cleaning)throw new IllegalStateException("Native facing cleanup is already in progress");
            cleaning=true;
            try{control.restore();released=true;if(facing==this)facing=null;}
            finally{cleaning=false;}
        }
    }
    /** Block updates invalidate a route; they cannot acquire another query ticket. */
    public void recomputeRequested() { stop(); }
    @EventHandler(priority=EventPriority.LOWEST)
    public void disabled(PluginDisableEvent event) {
        if (event.getPlugin()==plugin && lastDisableEvent!=event) NativeMotionCleanup.closeForDisable(event);
    }
    @Override public void close() {
        owner();if(released)return;revokeForCleanup();
        if(admitting)throw new IllegalStateException("Native navigation cleanup awaits the admission stack");
        if(cleaning)throw new IllegalStateException("Native navigation cleanup is already in progress");
        cleaning=true;Throwable error=null;
        try{
            if(facing!=null)try{facing.close();}catch(RuntimeException|Error failure){error=NativeMotionCleanup.combine(error,failure);}
            if(!restored)try{port.restore();restored=true;}catch(RuntimeException|Error failure){error=NativeMotionCleanup.combine(error,failure);}
            if(registered)try{HandlerList.unregisterAll(this);registered=false;}catch(RuntimeException|Error failure){error=NativeMotionCleanup.combine(error,failure);}
            if(facing==null&&restored&&!registered){released=true;LIVE.remove(id,this);}
        }finally{cleaning=false;}
        NativeMotionCleanup.rethrow(error);
    }
    public void revokeForCleanup(){owner();if(!closed){closed=true;if(epoch<Long.MAX_VALUE)epoch++;}if(facing!=null)facing.closed=true;}
    public static List<NativeNavigationLease> ownedForCleanup(Plugin owner){return ownedForCleanup(owner,null);}
    public static List<NativeNavigationLease> ownedForCleanup(Plugin owner,PluginDisableEvent event){
        checkThread();Objects.requireNonNull(owner);
        if(event!=null&&event.getPlugin()!=owner)throw new IllegalArgumentException("Native navigation disable owner changed");
        var result=new ArrayList<NativeNavigationLease>();
        for(var lease:LIVE.values())if(lease.plugin==owner&&(event==null||lease.lastDisableEvent!=event)){
            // Kept on the existing bounded receipt, including failed release; not an event history.
            if(event!=null)lease.lastDisableEvent=event;
            result.add(lease);
        }
        return List.copyOf(result);
    }
}
