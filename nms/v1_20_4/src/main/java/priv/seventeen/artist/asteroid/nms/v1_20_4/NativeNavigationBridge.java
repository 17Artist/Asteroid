package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftSkeleton;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS.Point;
import priv.seventeen.artist.asteroid.internal.movement.NativeMotionRestore;
import priv.seventeen.artist.asteroid.internal.navigation.NativeFacingTransfer;
import priv.seventeen.artist.asteroid.internal.navigation.NativeNavigationLease;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Exact Paper 1.20.4 Zombie/Husk/prepared Skeleton Ground navigation; no timer, fake event or velocity. */
final class NativeNavigationBridge {
    private NativeNavigationBridge() {}
    static boolean supportsRetainedHuskFlight(){return PaperCompat.isPaper()&&!PaperCompat.isFolia();}
    static boolean supportsTargetPreservingNavigation(){return PaperCompat.isPaper()&&!PaperCompat.isFolia();}
    static boolean supportsTargetPreservingRetainedHuskFlight(){return supportsTargetPreservingNavigation()&&supportsRetainedHuskFlight();}
    /** Pure live-flight inspection: no navigation/controller admission and no entity/goal writes. */
    static void requireTargetPreservingFlightGoals(Plugin owner,org.bukkit.entity.Mob entity) {
        EntityPoseBridge.check();Objects.requireNonNull(owner);Objects.requireNonNull(entity);
        if(!supportsTargetPreservingRetainedHuskFlight())
            throw new UnsupportedOperationException("Native flight goal inspection requires Paper without Folia");
        if(!(entity instanceof CraftEntity craft) || !(craft.getHandle() instanceof Husk host) || host.getClass()!=Husk.class)
            throw new IllegalArgumentException("Native flight goal inspection requires an exact Husk");
        World world=entity.getWorld();UUID id=entity.getUniqueId();int tick=MinecraftServer.currentTick;
        if(!owner.isEnabled()||!entity.isValid()||entity.isDead()||host.isRemoved()||host.isNoAi()||!host.aware
                ||Bukkit.getEntity(id)!=entity||host.getBukkitEntity()!=entity)
            throw new IllegalStateException("Native flight goal inspection host is unavailable");
        requireFlightGoals(host.goalSelector,host.targetSelector);
        if(!owner.isEnabled()||!entity.isValid()||entity.isDead()||host.isRemoved()||host.isNoAi()||!host.aware
                ||Bukkit.getEntity(id)!=entity||host.getBukkitEntity()!=entity||entity.getWorld()!=world
                ||MinecraftServer.currentTick!=tick)
            throw new IllegalStateException("Native flight goal inspection host changed");
    }
    static void requireFlightGoals(GoalSelector primary,GoalSelector targeting) {
        if(!clearGoals(primary,true,true)||!clearGoals(targeting,true,true))
            throw new IllegalStateException("Native flight requires both selectors free of MOVE/LOOK/JUMP and foreign non-target goals");
    }

    private static final StackWalker STACK=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static int queriesOnStack;
    private static final class QueryAccess {
        static final String COMMON_DESCRIPTOR=MethodType.methodType(Path.class,Set.class,Entity.class,
                int.class,boolean.class,int.class,float.class).descriptorString();
        static final String RECOMPUTE=methodName(PathNavigation.class,"recomputePath","i");
        private static String methodName(Class<?> owner,String... names) {
            String found=null;
            for(String name:names)try {
                var method=owner.getDeclaredMethod(name);
                if(method.getReturnType()!=void.class || Modifier.isStatic(method.getModifiers()))
                    throw new IllegalStateException("Native navigation method type changed");
                if(found!=null)throw new IllegalStateException("Ambiguous native navigation method");
                found=name;
            }catch(NoSuchMethodException absent) { /* only these two audited runtime namespaces */ }
            if(found==null)throw new IllegalStateException("Native recomputation mapping unavailable");
            return found;
        }
    }
    static NativeNavigationNMS.NavigationLease open(Plugin owner,org.bukkit.entity.Mob entity) {
        return open(owner,entity,false);
    }
    static NativeNavigationNMS.NavigationLease openPreservingTargets(Plugin owner,org.bukkit.entity.Mob entity) {
        return open(owner,entity,true);
    }
    private static NativeNavigationNMS.NavigationLease open(Plugin owner,org.bukkit.entity.Mob entity,boolean preserveTargets) {
        if(preserveTargets&&!supportsTargetPreservingNavigation())throw new UnsupportedOperationException("Target-preserving navigation requires Paper without Folia");
        EntityPoseBridge.check();
        if(!PaperCompat.isPaper())throw new UnsupportedOperationException("Native navigation requires Paper 1.20.4");
        if (!(entity instanceof CraftEntity craft) || !(craft.getHandle() instanceof Mob host))
            throw new IllegalArgumentException("Native navigation requires a native Mob");
        if (PaperCompat.isFolia())
            throw new UnsupportedOperationException("Native navigation does not support Folia");
        return NativeNavigationLease.open(owner,entity,Integer.toUnsignedLong(MinecraftServer.currentTick),()->new Port(host,false,preserveTargets));
    }
    static NativeNavigationNMS.NavigationLease openRetained(Plugin owner,org.bukkit.entity.Mob entity,Consumer<NativeNavigationNMS.NavigationLease> retain) {
        return openRetained(owner,entity,retain,false);
    }
    static NativeNavigationNMS.NavigationLease openPreservingTargetsRetained(Plugin owner,org.bukkit.entity.Mob entity,Consumer<NativeNavigationNMS.NavigationLease> retain) {
        return openRetained(owner,entity,retain,true);
    }
    private static NativeNavigationNMS.NavigationLease openRetained(Plugin owner,org.bukkit.entity.Mob entity,Consumer<NativeNavigationNMS.NavigationLease> retain,boolean preserveTargets) {
        if(preserveTargets&&!supportsTargetPreservingNavigation())throw new UnsupportedOperationException("Target-preserving navigation requires Paper without Folia");
        EntityPoseBridge.check();Objects.requireNonNull(retain);
        if(!PaperCompat.isPaper())throw new UnsupportedOperationException("Native navigation requires Paper 1.20.4");
        if (!(entity instanceof CraftEntity craft) || !(craft.getHandle() instanceof Mob host))
            throw new IllegalArgumentException("Native navigation requires a native Mob");
        if (PaperCompat.isFolia())throw new UnsupportedOperationException("Native navigation does not support Folia");
        return NativeNavigationLease.openRetained(owner,entity,Integer.toUnsignedLong(MinecraftServer.currentTick),()->new Port(host,false,preserveTargets),retain);
    }
    /** Explicit caller-owned configuration, never silently performed by open(). */
    static void prepareSkeleton(Plugin owner,org.bukkit.entity.Mob entity) {
        prepareSkeleton(owner,entity,false);
    }
    static void prepareSkeletonPreservingTargets(Plugin owner,org.bukkit.entity.Mob entity) {
        prepareSkeleton(owner,entity,true);
    }
    private static void prepareSkeleton(Plugin owner,org.bukkit.entity.Mob entity,boolean preserveTargets) {
        EntityPoseBridge.check();
        if (!PaperCompat.isPaper()
                || PaperCompat.isFolia())
            throw new UnsupportedOperationException("Skeleton preparation requires Paper 1.20.4");
        Objects.requireNonNull(owner); Objects.requireNonNull(entity);
        if (entity.getClass()!=CraftSkeleton.class
                || !(((CraftEntity)entity).getHandle() instanceof Skeleton host)
                || host.getClass()!=Skeleton.class)
            throw new IllegalArgumentException("Only an exact native Skeleton can be prepared");
        long tick=Integer.toUnsignedLong(MinecraftServer.currentTick);
        var world=entity.getWorld();
        if (!owner.isEnabled() || !entity.isValid() || entity.isDead() || host.isFreezeConverting()
                || host.isInPowderSnow || Bukkit.getEntity(entity.getUniqueId())!=entity)
            throw new IllegalStateException("Skeleton preparation host is unavailable");
        NativeNavigationLease.requireUnowned(entity);
        new Port(host,true,preserveTargets); // Resolve all native controller/settings fields before either flag write.
        SkeletonSetupAccess.ready(); // Resolve BOTH setters before either invocation.
        // Resolve both getters in the same cold phase, including currently unsafe flag values.
        host.shouldBurnInDay(); host.canPickUpLoot();
        NativeNavigationLease.requireUnowned(entity);
        if (!owner.isEnabled() || entity.getWorld()!=world || !entity.isValid() || entity.isDead()
                || Bukkit.getEntity(entity.getUniqueId())!=entity
                || tick!=Integer.toUnsignedLong(MinecraftServer.currentTick))
            throw new IllegalStateException("Skeleton preparation changed before writing");
        SkeletonSetupAccess.apply(host);
        if (!owner.isEnabled() || !eligible(host,preserveTargets) || entity.getWorld()!=world
                || Bukkit.getEntity(entity.getUniqueId())!=entity
                || tick!=Integer.toUnsignedLong(MinecraftServer.currentTick))
            throw new IllegalStateException("Skeleton preparation readback failed");
    }
    /** Cold-only cached handles; the lease hot path uses direct, read-only native getters. */
    private static final class SkeletonSetupAccess {
        static final MethodHandle BURN=setter(
                AbstractSkeleton.class,"setShouldBurnInDay");
        static final MethodHandle PICKUP=setter(Mob.class,"setCanPickUpLoot","s");
        static MethodHandle setter(Class<?> owner,String... names) {
            MethodHandle found=null;
            for(String name:names)try {
                var handle=MethodHandles.publicLookup().findVirtual(owner,name,
                        MethodType.methodType(void.class,boolean.class));
                if(found!=null)throw new IllegalStateException("Ambiguous Skeleton setup method");
                found=handle.asType(MethodType.methodType(void.class,Skeleton.class,boolean.class));
            }catch(NoSuchMethodException absent) { /* exact mapped/production names only */ }
            catch(IllegalAccessException error) { throw new IllegalStateException("Skeleton setup inaccessible",error); }
            if(found==null)throw new IllegalStateException("Skeleton setup capability unavailable");
            return found;
        }
        static void ready() { if(BURN==null || PICKUP==null)throw new IllegalStateException("Skeleton setup unavailable"); }
        static void apply(Skeleton host) {
            try { BURN.invokeExact(host,false); PICKUP.invokeExact(host,false); }
            catch (RuntimeException|Error failure) { throw failure; }
            catch (Throwable failure) { throw new IllegalStateException("Skeleton setup invocation failed",failure); }
        }
    }
    /** Shared movement broker must call this before any immediate/queue/root/drive claim. */
    static void requireUnowned(Mob host) {
        if (host.getNavigation() instanceof Follower || host.getMoveControl() instanceof NavigationControl)
            throw new IllegalStateException("Native navigation owns movement");
    }
    private static void noForeignQueryOnStack() {
        // Acquisition is cold. Exact common createPath frames distinguish our detached event stack
        // from a pre-existing navigator whose targetPos is not written until the event returns.
        boolean permitted=STACK.walk(frames->{
            var iterator=frames.iterator(); int seen=0,nativeQueries=0;
            while (iterator.hasNext()) {
                if (++seen>128) return false;
                var frame=iterator.next();
                if (frame.getDeclaringClass()!=PathNavigation.class) continue;
                if (frame.getMethodName().equals(QueryAccess.RECOMPUTE)) return false;
                if (frame.getMethodName().equals("createPath") && frame.getDescriptor().equals(
                        QueryAccess.COMMON_DESCRIPTOR)) nativeQueries++;
            }
            return nativeQueries==queriesOnStack;
        });
        if (!permitted) throw new IllegalStateException("Cannot acquire an unowned in-flight native path query");
    }
    private static boolean clearGoals(GoalSelector selector) { return clearGoals(selector,false,false); }
    /** Read-only flag admission; never remove, stop or rewrite upstream goals. */
    private static boolean clearGoals(GoalSelector selector,boolean preserveTargets,boolean facing) {
        var goals=selector.getAvailableGoals();
        if (!preserveTargets && facing) return goals.isEmpty(); // original strict-facing contract
        if (goals.size()>64) return false;
        int seen=0;
        long target=1L<<Goal.Flag.TARGET.ordinal();
        long forbidden=(1L<<Goal.Flag.MOVE.ordinal()) | (preserveTargets?0:target)
                | (facing?(1L<<Goal.Flag.LOOK.ordinal()) | (1L<<Goal.Flag.JUMP.ordinal()):0);
        for (var wrapped:goals) {
            if (++seen>64 || wrapped.getClass()!=WrappedGoal.class) return false;
            long flags=wrapped.getFlags().getBackingSet();
            if ((flags&forbidden)!=0) return false;
            if (!vanilla(wrapped.getGoal().getClass()) && !(preserveTargets && flags==target)) return false;
        }
        return true;
    }
    private static boolean eligible(Mob host) { return eligible(host,false); }
    private static boolean eligible(Mob host,boolean preserveTargets) { return eligibleBase(host,preserveTargets) && stableSpeciesState(host); }
    private static boolean eligibleBase(Mob host) { return eligibleBase(host,false); }
    private static boolean eligibleBase(Mob host,boolean preserveTargets) {
        double range=host.getAttributeValue(Attributes.FOLLOW_RANGE);
        return supportedSpecies(host) && !host.isRemoved() && !host.isNoAi() && host.aware && !host.isPassenger()
                && Fields.get(Fields.LEASH_HOLDER,host)==null
                && (int)Fields.get(Fields.LEASH_DELAY,host)==0 && host.leashInfoTag==null
                && host.getPassengers().isEmpty() && Double.isFinite(range) && range>=1 && range<=64
                && clearGoals(host.goalSelector,preserveTargets,false) && clearGoals(host.targetSelector,preserveTargets,false);
    }
    /** Extend only after auditing the species' native AI phase; Ground alone excludes no Brain writers. */
    private static boolean supportedSpecies(Mob host) {
        return host.getClass()==Zombie.class
                || host.getClass()==Husk.class
                || host.getClass()==Skeleton.class;
    }
    private static boolean stableSpeciesState(Mob host) {
        if (host.getClass()!=Skeleton.class) return true;
        var skeleton=(Skeleton)host;
        // Sun helmet break / pickup can reinstall a MOVE goal before native navigation.tick.
        // Conversion and powder snow cease eligibility; never clear or rewrite those states.
        return !skeleton.shouldBurnInDay() && !skeleton.canPickUpLoot()
                && !skeleton.isFreezeConverting() && !skeleton.isInPowderSnow;
    }
    private static boolean vanilla(Class<?> type) {
        return type.getClassLoader()==Mob.class.getClassLoader() && type.getName().startsWith("net.minecraft.");
    }
    private static final class Fields {
        static final Field NAV=unique(Mob.class,PathNavigation.class), MOVE=unique(Mob.class,MoveControl.class);
        // Exact local mapped and production Paper 1.20.4 aliases; both are type-checked before install.
        static final Field TARGET=named(PathNavigation.class,"targetPos",BlockPos.class,"s");
        static final Field DELAY=named(PathNavigation.class,"hasDelayedRecomputation",boolean.class,"m");
        static final Field MULTIPLIER=named(PathNavigation.class,"maxVisitedNodesMultiplier",float.class,"u");
        static final Field AVOID_SUN=unique(GroundPathNavigation.class,boolean.class);
        static final Field LEASH_HOLDER=named(Mob.class,"leashHolder",Entity.class,"ce");
        static final Field LEASH_DELAY=named(Mob.class,"delayedLeashHolderId",int.class,"cf");
        static Field unique(Class<?> owner,Class<?> type) {
            Field result=null;
            for (Field field:owner.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers()) && field.getType()==type) {
                if (result!=null) throw new IllegalStateException("Ambiguous native navigation field");
                result=field;
            }
            if (result==null) throw new IllegalStateException("Native navigation field unavailable");
            result.setAccessible(true); return result;
        }
        static Field named(Class<?> owner,String name,Class<?> type,String... aliases) {
            var names=new ArrayList<String>();names.add(name);Collections.addAll(names,aliases);
            Field found=null;
            for(String candidate:names)try {
                Field field=owner.getDeclaredField(candidate);
                if(field.getType()!=type || Modifier.isStatic(field.getModifiers()))
                    throw new IllegalStateException("Native navigation field type changed: "+candidate);
                if(found!=null)throw new IllegalStateException("Ambiguous native navigation mapping");
                field.setAccessible(true);found=field;
            }catch(NoSuchFieldException absent) { /* exact mapped/production aliases only */ }
            if(found==null)throw new IllegalStateException("Native navigation mapping unavailable");
            return found;
        }
        static Object get(Field field,Object host) {
            try { return field.get(host); } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        static void set(Field field,Object host,Object value) {
            try { field.set(host,value); } catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        static void clearOld(GroundPathNavigation old) {
            old.stop(); set(TARGET,old,null); set(DELAY,old,false);
        }
    }
    private record Settings(boolean pass,boolean open,boolean floating,boolean fences,boolean sun) {
        static Settings read(GroundPathNavigation old) {
            var evaluator=old.getNodeEvaluator();
            if (evaluator.getClass()!=WalkNodeEvaluator.class)
                throw new UnsupportedOperationException("Custom ground node evaluator is unsupported");
            if (!Float.valueOf(1).equals(Fields.get(Fields.MULTIPLIER,old)))
                throw new UnsupportedOperationException("Non-default native path search multiplier is unsupported");
            return new Settings(evaluator.canPassDoors(),evaluator.canOpenDoors(),evaluator.canFloat(),
                    evaluator.canWalkOverFences(),(boolean)Fields.get(Fields.AVOID_SUN,old));
        }
        void apply(GroundPathNavigation target) {
            target.setCanPassDoors(pass); target.setCanOpenDoors(open); target.setCanFloat(floating);
            target.setCanWalkOverFences(fences); target.setAvoidSun(sun);
        }
        boolean matches(GroundPathNavigation target) {
            var evaluator=target.getNodeEvaluator();
            return evaluator.getClass()==WalkNodeEvaluator.class
                &&evaluator.canPassDoors()==pass&&evaluator.canOpenDoors()==open
                &&evaluator.canFloat()==floating&&evaluator.canWalkOverFences()==fences
                &&(boolean)Fields.get(Fields.AVOID_SUN,target)==sun
                &&Float.valueOf(1).equals(Fields.get(Fields.MULTIPLIER,target));
        }
    }
    private static final class Port implements NativeNavigationLease.Port {
        final Mob host; final GroundPathNavigation previous; final MoveControl previousMove;
        final Settings settings;final boolean preserveTargets;
        Follower follower; NavigationControl movement;
        NativeNavigationLease lease; boolean following;
        final NativeMotionRestore restoration=new NativeMotionRestore();
        Port(Mob host) { this(host,false,false); }
        Port(Mob host,boolean preparation) { this(host,preparation,false); }
        Port(Mob host,boolean preparation,boolean preserveTargets) {
            this.host=host;this.preserveTargets=preserveTargets; noForeignQueryOnStack();
            NativeMoveBridge.uncontrolled(host.getBukkitEntity());
            if (!supportedSpecies(host))
                throw new UnsupportedOperationException("Native navigation supports exact Zombie/Husk/prepared Skeleton only");
            if (!eligibleBase(host,preserveTargets) || (!preparation && !stableSpeciesState(host)))
                throw new IllegalStateException("Native navigation requires exclusive bounded AI goals/range and stable species state");
            if (host.getNavigation().getClass()!=GroundPathNavigation.class)
                throw new UnsupportedOperationException("Only exact GroundPathNavigation is supported");
            previous=(GroundPathNavigation)host.getNavigation(); previousMove=host.getMoveControl();
            if (previous.getPath()!=null || previous.getTargetPos()!=null || (boolean)Fields.get(Fields.DELAY,previous))
                throw new IllegalStateException("Original navigation has a path, target or pending recomputation");
            if (previousMove.getClass()!=MoveControl.class || !NavigationControl.idle(previousMove))
                throw new IllegalStateException("Original MoveControl is foreign or has pending input");
            settings=Settings.read(previous);
        }
        @Override public long tick() { return Integer.toUnsignedLong(MinecraftServer.currentTick); }
        @Override public void install(NativeNavigationLease value) {
            if (host.getNavigation()!=previous || host.getMoveControl()!=previousMove || !eligible(host,preserveTargets))
                throw new IllegalStateException("Native navigation changed before installation");
            lease=value;
            follower=new Follower(host,this);
            movement=new NavigationControl(host,this);
            settings.apply(follower);
            Fields.set(Fields.NAV,host,follower); Fields.set(Fields.MOVE,host,movement);
        }
        @Override public boolean owned() {
            return follower!=null && host.getNavigation()==follower && host.getMoveControl()==movement && eligible(host,preserveTargets) && settings.matches(follower);
        }
        @Override public Object query(Point target) {
            if (!owned()) return null;
            // Detached navigation is never installed or ticked. Any event-return metadata writes
            // are quarantined here, including when the event closes/replaces this lease.
            // Per-admitted-query scratch is not retained on an idle lease: vanilla NodeEvaluator
            // retains its explored node map until the NEXT query, so a persistent query object
            // would pin that search high-water mark across all 256 owners.
            var query=new GroundPathNavigation(host,host.level()); settings.apply(query);
            queriesOnStack++;
            try { return query.createPath(target.x(),target.y(),target.z(),0); }
            finally { queriesOnStack--; }
        }
        @Override public boolean reachable(Object candidate) { return candidate instanceof Path path && path.canReach(); }
        @Override public List<Point> points(Object candidate,boolean remaining) {
            if (!(candidate instanceof Path path) || path.getNodeCount()>NativeNavigationLease.MAX_POINTS) return null;
            int from=remaining?path.getNextNodeIndex():0;
            if (from<0 || from>path.getNodeCount()) return null;
            var result=new ArrayList<Point>(path.getNodeCount()-from);
            for (int i=from;i<path.getNodeCount();i++) {
                var point=path.getNodePos(i); result.add(new Point(point.getX(),point.getY(),point.getZ()));
            }
            return Collections.unmodifiableList(result);
        }
        @Override public Object currentPath() { return owned()?follower.getPath():null; }
        @Override public boolean commit(Object candidate,double speed) {
            return owned() && candidate instanceof Path path && follower.accept(path,speed);
        }
        @Override public void stop() {
            if (!owned()) return;
            follower.clear(); movement.neutral();
        }
        @Override public NativeNavigationLease.FacingPort prepareFacing(){return new FacingPort(this);}
        @Override public void restore() {
            restoration.restore(
                ()->{if(follower!=null&&host.getNavigation()==follower&&movement!=null&&host.getMoveControl()==movement){follower.clear();movement.neutral();}},
                ()->{if(follower!=null&&host.getNavigation()==follower){Fields.clearOld(previous);Fields.set(Fields.NAV,host,previous);}},
                ()->{if(movement!=null&&host.getMoveControl()==movement){NavigationControl.clearPrevious(previousMove);Fields.set(Fields.MOVE,host,previousMove);}});
        }
    }
    /** Facing touches only the LOOK field. The final vanilla Body control is pinned, never replaced. */
    private static final class FacingFields {
        static final Field LOOK=Fields.unique(Mob.class,LookControl.class);
        static final Field BODY=Fields.unique(Mob.class,BodyRotationControl.class);
        static final Field COOLDOWN=Fields.named(LookControl.class,"lookAtCooldown",int.class,"d");
        static final Field LERP=Fields.named(LivingEntity.class,"lerpSteps",int.class,"bn");
        static final Field HEAD_LERP=Fields.named(LivingEntity.class,"lerpHeadSteps",int.class,"bu");
        static boolean stable(Mob host,Object body,boolean preserveTargets){
            return Fields.get(BODY,host)==body&&!host.isSleeping()
                &&(int)Fields.get(LERP,host)==0&&(int)Fields.get(HEAD_LERP,host)==0
                &&clearGoals(host.goalSelector,preserveTargets,true)&&clearGoals(host.targetSelector,preserveTargets,true);
        }
    }
    private static final class FacingPort implements NativeNavigationLease.FacingPort {
        final Port owner;final Mob host;final LookControl previous;final Object body;
        final org.bukkit.entity.Mob entity;final World world;final UUID id;
        FrozenLook frozen;boolean restored;
        FacingPort(Port owner){
            this.owner=owner;host=owner.host;previous=host.getLookControl();body=Fields.get(FacingFields.BODY,host);
            entity=(org.bukkit.entity.Mob)host.getBukkitEntity();world=entity.getWorld();id=entity.getUniqueId();
            if(previous.getClass()!=LookControl.class
                    ||body.getClass()!=BodyRotationControl.class)
                throw new UnsupportedOperationException("Native facing requires exact vanilla LOOK and Body controls");
            if(!ready())throw new IllegalStateException("Native facing requires idle navigation, exclusive goal flags and no pending native angle writer");
        }
        private boolean ready(){
            return owner.owned()&&host.getLookControl()==previous&&(int)Fields.get(FacingFields.COOLDOWN,previous)==0
                &&FacingFields.stable(host,body,owner.preserveTargets)&&owner.follower.getPath()==null&&NavigationControl.idle(owner.movement)
                &&host.xxa==0&&host.yya==0&&host.zza==0&&host.getSpeed()==0
                &&Float.isFinite(host.getYRot())&&Float.isFinite(host.getXRot())&&Float.isFinite(host.yHeadRot)&&Float.isFinite(host.yBodyRot);
        }
        @Override public void install(NativeNavigationNMS.FacingLease receipt,float yaw,float pitch){
            if(!ready())throw new IllegalStateException("Native facing changed before installation");
            frozen=new FrozenLook(this,receipt);
            Fields.set(FacingFields.LOOK,host,frozen);
            // One cold alignment. FrozenLook.tick never writes an angle or counteracts a foreign face.
            host.setYRot(yaw);host.setXRot(pitch);host.setYHeadRot(yaw);host.setYBodyRot(yaw);
        }
        @Override public boolean owned(float yaw,float pitch){
            return frozen!=null&&host.getLookControl()==frozen&&FacingFields.stable(host,body,owner.preserveTargets)
                &&owner.follower.getPath()==null&&NavigationControl.idle(owner.movement)
                &&host.xxa==0&&host.yya==0&&host.zza==0&&host.getSpeed()==0
                &&host.getYRot()==yaw&&host.getXRot()==pitch&&host.yHeadRot==yaw&&host.yBodyRot==yaw;
        }
        @Override public void restore(){
            if(restored)return;
            if(frozen!=null&&host.getLookControl()==frozen)Fields.set(FacingFields.LOOK,host,previous);
            restored=true; // No angle rollback and no overwrite of a foreign LOOK or Body controller.
        }
    }
    private static final class FrozenLook extends LookControl {
        final NativeNavigationNMS.FacingLease receipt;final FacingPort source;
        final NativeFacingTransfer transfer;
        FrozenLook(FacingPort source,NativeNavigationNMS.FacingLease receipt){
            super(source.host);this.source=source;this.receipt=receipt;
            transfer=new NativeFacingTransfer(new NativeFacingTransfer.Port(){
                public boolean installed(){return source.host.getLookControl()==FrozenLook.this;}
                public boolean current(){return receipt.current();}
                public long epoch(){return source.owner.lease.epoch();}
                public void close(){receipt.close();}
                public boolean restored(){return source.host.getLookControl()==source.previous
                    &&Fields.get(FacingFields.BODY,source.host)==source.body
                    &&source.entity.getWorld()==source.world&&Bukkit.getEntity(source.id)==source.entity
                    &&source.host.getBukkitEntity()==source.entity&&source.owner.lease.active();}
            });
        }
        @Override public void tick(){receipt.current();}
        @Override public void setLookAt(double x,double y,double z,float yawSpeed,float pitchSpeed){
            // Every vanilla public setLookAt overload funnels through this method.
            // Revoke first. Only a still-current exact old control may receive this external request.
            transfer.forward(()->source.previous.setLookAt(x,y,z,yawSpeed,pitchSpeed));
        }
    }
    private static final class Follower extends GroundPathNavigation {
        final Port owner;
        Follower(Mob host,Port owner) { super(host,host.level()); this.owner=owner; }
        boolean accept(Path path,double speed) {
            // Never let sameAs reuse the earlier route/index. No callback in native moveTo/trimPath.
            super.stop(); return super.moveTo(path,speed);
        }
        void clear() { super.stop(); hasDelayedRecomputation=false; }
        @Override public void tick() {
            if (!owner.lease.maintain()) return;
            owner.following=true;
            try { super.tick(); } finally { owner.following=false; }
        }
        @Override public void recomputePath() { if (owner!=null) owner.lease.recomputeRequested(); }
        @Override public void stop() { if (owner!=null) owner.lease.stop(); }
        @Override protected Path createPath(Set<BlockPos> goals,Entity target,
                                            int padding,boolean above,int accuracy,float range) { return null; }
        @Override public Path createPath(Stream<BlockPos> goals,int accuracy) { return null; }
        @Override public void setSpeedModifier(double speed) { throw new IllegalStateException("Navigation owns native route speed"); }
        @Override public boolean moveTo(Path path,double speed) { return false; }
        @Override public boolean moveTo(double x,double y,double z,double speed) { return false; }
        @Override public boolean moveTo(Entity target,double speed) { return false; }
    }
    private static final class NavigationControl extends MoveControl {
        private static final Field OPERATION=Fields.unique(MoveControl.class,Operation.class);
        final Port owner;
        NavigationControl(Mob host,Port owner) { super(host); this.owner=owner; }
        static boolean idle(MoveControl previous) { return Fields.get(OPERATION,previous)==Operation.WAIT; }
        static void clearPrevious(MoveControl previous) { Fields.set(OPERATION,previous,Operation.WAIT); }
        void neutral() {
            operation=Operation.WAIT; speedModifier=0; strafeForwards=strafeRight=0;
            mob.setXxa(0); mob.setYya(0); mob.setZza(0); mob.setSpeed(0);
        }
        @Override public void tick() { if (owner.lease.maintain()) super.tick(); }
        @Override public void setWantedPosition(double x,double y,double z,double speed) {
            if (!owner.following || !owner.owned()) throw new IllegalStateException("External navigation cannot write an owned MoveControl");
            super.setWantedPosition(x,y,z,speed);
        }
        @Override public void strafe(float front,float side) { throw new IllegalStateException("Navigation owns native travel input"); }
    }
}
