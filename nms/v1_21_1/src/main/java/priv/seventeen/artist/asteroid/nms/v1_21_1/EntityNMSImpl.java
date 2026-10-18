/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.NativeArrowDamageEvidenceNMS;
import priv.seventeen.artist.asteroid.entity.NativeDamageRelay;
import priv.seventeen.artist.asteroid.entity.NativeDamageRelayNMS;
import priv.seventeen.artist.asteroid.entity.NativeEntityHitboxNMS;
import priv.seventeen.artist.asteroid.entity.NativeDamageSourceNMS;
import priv.seventeen.artist.asteroid.entity.NativeDimensionsNMS;
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.entity.NativeInteractionNMS;
import priv.seventeen.artist.asteroid.entity.NativeMainInteractionNMS;
import priv.seventeen.artist.asteroid.entity.NativeModelPivotNMS;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.entity.NativeNearbyEntitiesNMS;
import priv.seventeen.artist.asteroid.entity.NativeOcclusionNMS;
import priv.seventeen.artist.asteroid.entity.NativePoseNMS;
import priv.seventeen.artist.asteroid.entity.NativePrecisionNMS;
import priv.seventeen.artist.asteroid.entity.NativeStepNMS;
import priv.seventeen.artist.asteroid.entity.NativeTrackingRangeNMS;
import priv.seventeen.artist.asteroid.entity.PrecisionHitbox;
import priv.seventeen.artist.asteroid.internal.mount.AbstractNativeModelMountNMS;
import priv.seventeen.artist.asteroid.internal.movement.NativeMotionCleanup;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class EntityNMSImpl extends AbstractNativeModelMountNMS implements IEntityNMS, NativePrecisionNMS, NativeEntityHitboxNMS, NativePoseNMS, NativeOcclusionNMS, NativeDimensionsNMS, NativeStepNMS, NativeMovementNMS, NativeDamageRelayNMS, NativeInteractionNMS, NativeMainInteractionNMS, NativeNavigationNMS, NativeTrackingRangeNMS, NativeDamageSourceNMS, NativeArrowDamageEvidenceNMS, NativeNearbyEntitiesNMS, NativeModelPivotNMS {

    @Override public NativeModelPivotNMS.PivotHost describePivotHost(Entity host) { return NativePivotBridge.describe(host); }
    @Override public NativeModelPivotNMS.PivotSample samplePivotHost(Entity host, boolean camera) { return NativePivotBridge.sample(host, camera); }

    public EntityNMSImpl() { super(new NativeMountPlatformImpl()); }
    @Override public boolean supportsOrderedNearbyPrefix(){
        try{return NativeNearbyBridge.supported();}catch(LinkageError unavailable){return false;}
    }
    @Override public List<Entity> nearbyEntityPrefix(Entity host,double x,double y,double z,int limit){
        return NativeNearbyBridge.nearby(host,x,y,z,limit);
    }
    @Override public NativeDamageSourceNMS.DirectSources freezeDirectDamageSources(Plugin consumer,Plugin nativeOwner){
        return NativeDamageSourceBridge.open(this,consumer,nativeOwner);
    }
    @Override public NativeArrowDamageEvidenceNMS.ArrowSources freezeArrowDamageSources(Plugin consumer,Plugin nativeOwner){
        return NativeArrowDamageSourceBridge.open(this,consumer,nativeOwner);
    }
    private final NativeTrackingRangeBridge nativeTrackingRanges = new NativeTrackingRangeBridge();
    @Override public NativeTrackingRangeNMS.RangeLease controlTrackingRange(Entity host,int blocks){return nativeTrackingRanges.open(host,blocks);}
    @Override public boolean isTrackedBy(Entity host,Player viewer){return NativeTrackingRangeBridge.tracked(host,viewer);}
    @Override public void shutdownTrackingRanges(){nativeTrackingRanges.close();}

    private static void entityHitboxOwner(Entity owner){
        if(!Bukkit.isPrimaryThread()||owner==null||!owner.isValid()||owner.isDead())throw new IllegalStateException("Invalid entity hitbox owner/thread");
        if(!(owner instanceof CraftEntity craft))throw new IllegalArgumentException("Entity hitbox needs a real native owner");
        var handle=craft.getHandle();
        if(handle instanceof PrecisionProxyImpl||handle instanceof DamageRelayImpl)
            throw new IllegalArgumentException("A precision proxy or damage relay cannot own another native hitbox");
    }
    @Override public NativeDamageRelay createDamageRelay(LivingEntity owner,BoundingBox bounds,NativeDamageRelay.Handler handler,Consumer<Throwable> failure){
        return createEntityDamageRelay(owner,bounds,handler,failure);
    }
    @Override public NativeDamageRelay createEntityDamageRelay(Entity owner,BoundingBox bounds,NativeDamageRelay.Handler handler,Consumer<Throwable> failure){
        entityHitboxOwner(owner);
        ServerLevel world=((CraftWorld)owner.getWorld()).getHandle();
        DamageRelayImpl relay=new DamageRelayImpl(world,owner,bounds,handler,failure);
        try{if(!world.addFreshEntity(relay))throw new IllegalStateException("Damage relay spawn was rejected");relay.registered();return relay;}
        catch(RuntimeException|Error error){relay.close();throw error;}
    }

    @Override public boolean supportsRetainedHuskFlight(){return NativeNavigationBridge.supportsRetainedHuskFlight();}
    @Override public NativeNavigationNMS.NavigationLease controlNavigation(Plugin owner,Mob host){return NativeNavigationBridge.open(owner,host);}
    @Override public NativeNavigationNMS.NavigationLease controlNavigationRetained(Plugin owner,Mob host,Consumer<NativeNavigationNMS.NavigationLease> retain){return NativeNavigationBridge.openRetained(owner,host,retain);}
    @Override public void prepareSkeleton(Plugin owner,Mob host){NativeNavigationBridge.prepareSkeleton(owner,host);}

    @Override public void move(Entity entity,NativeMovementNMS.Input input){NativeMoveBridge.move(entity,input);}
    @Override public boolean supportsDriving(){return true;}
    @Override public boolean supportsDrivePrograms(){return true;}
    @Override public boolean supportsMotionCleanup(){return true;}
    @Override public void closeMotionOwned(Plugin owner){NativeMotionCleanup.closeAll(owner);}
    @Override public NativeMovementNMS.DriveLease controlDriveProgramRetained(Plugin owner,Entity entity,NativeDriveProgram program,Consumer<Throwable> failure,Consumer<NativeMovementNMS.DriveLease> retain){return NativeMoveBridge.programRetained(owner,entity,program,failure,retain);}
    @Override public NativeMovementNMS.DriveLease controlDriveProgram(Plugin owner,Entity entity,NativeDriveProgram program,Consumer<Throwable> failure){return NativeMoveBridge.program(owner,entity,program,failure);}
    @Override public NativeMovementNMS.DriveLease controlDriving(Plugin owner,Entity entity,Function<NativeMovementNMS.DriveContext,NativeMovementNMS.DriveFrame> source,Consumer<Throwable> failure){return NativeMoveBridge.drive(owner,entity,source,failure);}
    @Override public NativeMovementNMS.RootMotionLease controlRootMotion(Entity entity,Supplier<NativeMovementNMS.RootMotionFrame> source,Consumer<Throwable> failure){return NativeMoveBridge.root(entity,source,failure);}
    @Override public NativeMovementNMS.MovementLease controlMovement(Entity entity,Consumer<Throwable> failure){return NativeMoveBridge.control(entity,failure);}
    @Override public void requestJump(Entity entity){
        NativeMoveBridge.uncontrolled(entity);
        EntityPoseBridge.check();
        if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Movement host is not loaded");
        if(craft.getHandle() instanceof net.minecraft.world.entity.Mob mob)mob.getJumpControl().jump();
    }
    @Override public void resetFallDistance(Entity entity){
        NativeMoveBridge.uncontrolled(entity);
        EntityPoseBridge.check();
        if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Movement host is not loaded");
        if(craft.getHandle() instanceof net.minecraft.world.entity.Mob mob)mob.resetFallDistance();
    }

    @Override public double baseStepHeight(Entity entity){return NativeStepBridge.base(entity);}
    @Override public double stepHeight(Entity entity){return NativeStepBridge.effective(entity);}
    @Override public NativeStepNMS.StepChange changeStepHeight(Entity entity,double height){return NativeStepBridge.change(entity,height);}

    private static final Map<UUID,DimensionsControl> DIMENSION_CONTROLS=new HashMap<>();
    @Override public NativeDimensionsNMS.DimensionsLease controlDimensions(Entity entity){
        dimensionOwner();
        if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Model host is not loaded");
        if(DIMENSION_CONTROLS.containsKey(entity.getUniqueId()))throw new IllegalStateException("Entity already has a dimensions controller");
        DimensionsControl control=new DimensionsControl(craft.getHandle());
        DIMENSION_CONTROLS.put(entity.getUniqueId(),control);return control;
    }
    private static void dimensionOwner(){if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Model dimensions require the Paper owner thread");}
    private static final class DimensionsControl implements NativeDimensionsNMS.DimensionsLease {
        private final net.minecraft.world.entity.Entity entity;
        private final EntityDimensions original;
        private final float originalEye;
        private final net.minecraft.world.entity.Pose originalPose;
        private final AABB originalVanillaBox;
        private NativeDimensionsNMS.Dimensions requested;
        private EntityDimensions applied;
        private boolean open=true;
        private long writes;
        DimensionsControl(net.minecraft.world.entity.Entity entity){
            this.entity=entity;original=(EntityDimensions)SIZE_HANDLE.get(entity);originalEye=(float)HEIGHT_HANDLE.get(entity);originalPose=entity.getPose();
            originalVanillaBox=entity.getDimensions(originalPose).makeBoundingBox(Vec3.ZERO);
        }
        @Override public void update(NativeDimensionsNMS.Dimensions value){
            dimensionOwner();Objects.requireNonNull(value);
            if(!active())throw new IllegalStateException("Model dimensions controller is closed");
            if(!value.equals(requested)){requested=value;applied=new EntityDimensions(value.width(),value.height(),value.eyeHeight(),EntityAttachments.createDefault(value.width(),value.height()),true);}
            if(!refresh())throw new IllegalStateException("Model host was removed");
        }
        @Override public boolean refresh(){
            dimensionOwner();if(!active())return false;
            if(entity.isRemoved()){close();return false;}
            if(applied!=null&&(SIZE_HANDLE.get(entity)!=applied||Float.compare((float)HEIGHT_HANDLE.get(entity),requested.eyeHeight())!=0)){
                SIZE_HANDLE.set(entity,applied);HEIGHT_HANDLE.set(entity,requested.eyeHeight());
                entity.setBoundingBox(applied.makeBoundingBox(entity.position()));writes++;
            }
            return true;
        }
        @Override public boolean active(){dimensionOwner();return open&&DIMENSION_CONTROLS.get(entity.getUUID())==this;}
        @Override public long nativeWrites(){dimensionOwner();return writes;}
        @Override public void close(){
            dimensionOwner();if(!open)return;open=false;DIMENSION_CONTROLS.remove(entity.getUUID(),this);
            if(applied!=null&&SIZE_HANDLE.get(entity)==applied&&Float.compare((float)HEIGHT_HANDLE.get(entity),requested.eyeHeight())==0){
                var vanillaBox=entity.getDimensions(entity.getPose()).makeBoundingBox(Vec3.ZERO);
                // Age and scale can change canonical dimensions without changing the pose.
                // This check runs only when releasing the lease, never in the stable tick path.
                if(entity.getPose()!=originalPose||Double.compare(vanillaBox.getXsize(),originalVanillaBox.getXsize())!=0||Double.compare(vanillaBox.getYsize(),originalVanillaBox.getYsize())!=0)entity.refreshDimensions();
                else{SIZE_HANDLE.set(entity,original);HEIGHT_HANDLE.set(entity,originalEye);entity.setBoundingBox(original.makeBoundingBox(entity.position()));}
            }
        }
    }


    @Override public NativeOcclusionNMS.View occlusionView(World world){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Occlusion lookup requires the Paper owner thread");
        var level=((CraftWorld)world).getHandle();int min=world.getMinHeight(),max=world.getMaxHeight();
        return new NativeOcclusionNMS.View(){
            final BlockPos.MutableBlockPos position=new BlockPos.MutableBlockPos();
            LevelChunk chunk;int chunkX=Integer.MIN_VALUE,chunkZ=Integer.MIN_VALUE;
            public NativeOcclusionNMS.Cell at(int x,int y,int z){
                if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Occlusion view is owner-thread only");
                if(y<min||y>=max)return NativeOcclusionNMS.Cell.CLEAR;
                int cx=x>>4,cz=z>>4;if(cx!=chunkX||cz!=chunkZ){chunkX=cx;chunkZ=cz;chunk=level.getChunkSource().getChunkNow(cx,cz);}
                if(chunk==null)return NativeOcclusionNMS.Cell.UNLOADED;
                position.set(x,y,z);var state=chunk.getBlockState(position);
                return state.canOcclude()&&state.getOcclusionShape(chunk,position)==Shapes.block()
                    ?NativeOcclusionNMS.Cell.FULL:NativeOcclusionNMS.Cell.CLEAR;
            }
        };
    }

    @Override public void alignBody(Entity entity,float yaw){
        EntityPoseBridge.check();if(!Float.isFinite(yaw))throw new IllegalArgumentException("Non-finite body yaw");
        if(((CraftEntity)entity).getHandle() instanceof net.minecraft.world.entity.LivingEntity living)living.yBodyRot=yaw;
    }
    @Override public boolean syncYaw(Entity entity,YawAxis source,YawTarget target){
        EntityPoseBridge.check();Objects.requireNonNull(source);Objects.requireNonNull(target);
        if(!(entity instanceof CraftEntity craft)||!entity.isValid())throw new IllegalArgumentException("Yaw host is not loaded");
        var nativeEntity=craft.getHandle();
        var living=nativeEntity instanceof net.minecraft.world.entity.LivingEntity value?value:null;
        float yaw=switch(source){case YAW->nativeEntity.getYRot();case BODY->living==null?nativeEntity.getYRot():living.yBodyRot;case HEAD->living==null?nativeEntity.getYRot():living.getYHeadRot();};
        if(!Float.isFinite(yaw))throw new IllegalArgumentException("Non-finite source yaw");
        if(target==YawTarget.NONE)return false;
        if(living==null){if(Float.compare(nativeEntity.getYRot(),yaw)==0)return false;nativeEntity.setYRot(yaw);return true;}
        float beforeYaw=nativeEntity.getYRot(),beforeBody=living.yBodyRot,beforeHead=living.getYHeadRot();
        // Virtual setters may affect another axis (notably ArmorStand.setYBodyRot).
        // Comparing only the selected getter would incorrectly discard those native effects.
        if(target==YawTarget.YAW||target==YawTarget.ALL)nativeEntity.setYRot(yaw);
        if(target==YawTarget.BODY||target==YawTarget.ALL)living.setYBodyRot(yaw);
        if(target==YawTarget.HEAD||target==YawTarget.ALL)living.setYHeadRot(yaw);
        return Float.compare(beforeYaw,nativeEntity.getYRot())!=0||Float.compare(beforeBody,living.yBodyRot)!=0||Float.compare(beforeHead,living.getYHeadRot())!=0;
    }
    @Override public void readPose(Entity entity,NativePoseNMS.Pose output){
        EntityPoseBridge.check();EntityPoseBridge.read(((CraftEntity)entity).getHandle(),output);
    }
    @Override public NativePoseNMS.BodyLease controlBody(Mob mob,Consumer<NativePoseNMS.Pose> controller,Consumer<Throwable> failure){
        return new EntityPoseBridge.Control((net.minecraft.world.entity.Mob)((CraftEntity)mob).getHandle(),controller,failure);
    }

    @Override public PrecisionHitbox createPrecisionHitbox(LivingEntity owner,BoundingBox bounds,PrecisionHitbox.Trace trace){
        return openPrecisionHitbox(owner,bounds,trace);
    }
    @Override public PrecisionHitbox createEntityPrecisionHitbox(Entity owner,BoundingBox bounds,PrecisionHitbox.ContextTrace trace){
        return openPrecisionHitbox(Objects.requireNonNull(owner),bounds,Objects.requireNonNull(trace));
    }
    private PrecisionHitbox openPrecisionHitbox(Entity owner,BoundingBox bounds,PrecisionHitbox.Trace trace) {
        if(trace instanceof PrecisionHitbox.ContextTrace)entityHitboxOwner(owner);
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Precision hitboxes require the entity thread");
        ServerLevel world = ((CraftWorld) owner.getWorld()).getHandle();
        PrecisionProxyImpl proxy = new PrecisionProxyImpl(world, owner, bounds, trace);
        if (!world.addFreshEntity(proxy)) { proxy.close(); throw new IllegalStateException("Precision proxy spawn was rejected"); }
        proxy.registered();
        return proxy;
    }
    @Override public void attack(Player player, LivingEntity target) { attackEntity(player,target); }
    @Override public void attackEntity(Player player, Entity target) {
        if (!Bukkit.isPrimaryThread() || player.getWorld() != target.getWorld()) throw new IllegalStateException("Invalid attack context");
        ((CraftPlayer) player).getHandle().attack(((CraftEntity) target).getHandle());
    }
    @Override public AutoCloseable excludeProjectileCandidate(Entity entity){return PrecisionProxyImpl.exclude(entity);}
    @Override public NativeInteractionNMS.Result interact(Player player,Entity target,EquipmentSlot hand){
        if(!Bukkit.isPrimaryThread()||player.getWorld()!=target.getWorld()||!player.isValid()||!target.isValid())throw new IllegalStateException("Invalid native interaction context");
        if(hand!=EquipmentSlot.HAND&&hand!=EquipmentSlot.OFF_HAND)throw new IllegalArgumentException("Interaction requires a hand");
        var result=((CraftEntity)target).getHandle().interact(((CraftPlayer)player).getHandle(),hand==EquipmentSlot.HAND?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
        return result==InteractionResult.FAIL?NativeInteractionNMS.Result.FAIL:result.consumesAction()?NativeInteractionNMS.Result.CONSUMED:NativeInteractionNMS.Result.PASS;
    }
    private static final class MainInteractionPhase implements NativeMainInteractionNMS.Phase {
        final Player player;final Entity target;final EquipmentSlot hand;
        final ServerPlayer nativePlayer;final net.minecraft.world.entity.Entity nativeTarget;
        final InteractionHand nativeHand;final ItemStack used,before;
        boolean claimed;
        MainInteractionPhase(Player player,Entity target,EquipmentSlot hand){
            this.player=player;this.target=target;this.hand=hand;
            nativePlayer=((CraftPlayer)player).getHandle();nativeTarget=((CraftEntity)target).getHandle();
            nativeHand=hand==EquipmentSlot.HAND?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
            used=nativePlayer.getItemInHand(nativeHand);before=used.copy();
        }
    }
    @Override public NativeMainInteractionNMS.Phase prepareMainInteraction(Player player,Entity target,EquipmentSlot hand){
        return mainInteractionAllowed(player,target,hand)?new MainInteractionPhase(player,target,hand):null;
    }
    private static boolean mainInteractionAllowed(Player player,Entity target,EquipmentSlot hand){
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Native interaction requires owner thread");
        if(hand!=EquipmentSlot.HAND&&hand!=EquipmentSlot.OFF_HAND)throw new IllegalArgumentException("Interaction requires a hand");
        if(player.getWorld()!=target.getWorld()||!player.isValid()||player.isDead()||!target.isValid()||target.isDead())return false;
        var nativePlayer=((CraftPlayer)player).getHandle();var nativeTarget=((CraftEntity)target).getHandle();
        var nativeHand=hand==EquipmentSlot.HAND?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        return !nativePlayer.isImmobile()&&!nativePlayer.isSpectator()&&nativeTarget.level().getWorldBorder().isWithinBounds(nativeTarget.blockPosition())
            &&nativePlayer.getItemInHand(nativeHand).isItemEnabled(nativePlayer.level().enabledFeatures());
    }
    private static MainInteractionPhase claimMainInteraction(NativeMainInteractionNMS.Phase receipt){
        if(!(receipt instanceof MainInteractionPhase phase)||phase.claimed)throw new IllegalStateException("Stale native main interaction receipt");
        phase.claimed=true;
        return mainInteractionAllowed(phase.player,phase.target,phase.hand)?phase:null;
    }
    @Override public NativeInteractionNMS.Result interactMainAt(NativeMainInteractionNMS.Phase receipt,double x,double y,double z){
        var phase=claimMainInteraction(receipt);if(phase==null)return NativeInteractionNMS.Result.FAIL;
        if(!Float.isFinite((float)x)||!Float.isFinite((float)y)||!Float.isFinite((float)z))throw new IllegalArgumentException("Non-finite interaction point");
        var result=phase.nativeTarget.interactAt(phase.nativePlayer,new Vec3((float)x,(float)y,(float)z),phase.nativeHand);
        completeMainInteraction(phase,result);return mainInteractionResult(result);
    }
    @Override public NativeInteractionNMS.Result interactMain(NativeMainInteractionNMS.Phase receipt){
        var phase=claimMainInteraction(receipt);if(phase==null)return NativeInteractionNMS.Result.FAIL;
        // Player.interactOn reads the post-event current item. Only listener bookkeeping uses the pre-event receipt.
        var result=phase.nativePlayer.interactOn(phase.nativeTarget,phase.nativeHand);
        completeMainInteraction(phase,result);return mainInteractionResult(result);
    }
    private static void completeMainInteraction(MainInteractionPhase phase,InteractionResult result){
        if(!phase.used.isEmpty()&&phase.used.getCount()<0)phase.nativePlayer.containerMenu.sendAllDataToRemote();
        if(result.consumesAction()){
            CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(phase.nativePlayer,result.indicateItemUse()?phase.before:ItemStack.EMPTY,phase.nativeTarget);
            if(result.shouldSwing())phase.nativePlayer.swing(phase.nativeHand,true);
        }
    }
    private static NativeInteractionNMS.Result mainInteractionResult(InteractionResult result){
        return result==InteractionResult.FAIL?NativeInteractionNMS.Result.FAIL:result.consumesAction()?NativeInteractionNMS.Result.CONSUMED:NativeInteractionNMS.Result.PASS;
    }
    @Override public boolean prepareProjectile(org.bukkit.entity.Projectile projectile) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Projectile routing requires the entity thread");
        return PrecisionPiercing.prepare((Projectile)((CraftEntity)projectile).getHandle());
    }
    @Override public boolean canHit(org.bukkit.entity.Projectile projectile, Entity target) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Projectile query requires the entity thread");
        var handle = (Projectile) ((CraftEntity) projectile).getHandle();
        PrecisionPiercing.prepare(handle);
        return projectile.getWorld() == target.getWorld() && PrecisionPiercing.canHit(projectile,target);
    }
    @Override public void hit(org.bukkit.entity.Projectile projectile, LivingEntity target, Vector point) { hitEntity(projectile,target,point); }
    @Override public void hitEntity(org.bukkit.entity.Projectile projectile, Entity target, Vector point) {
        point.checkFinite();
        if (!Bukkit.isPrimaryThread() || projectile.getWorld() != target.getWorld()) throw new IllegalStateException("Invalid impact context");
        var handle = (Projectile) ((CraftEntity) projectile).getHandle();
        var nativeTarget = ((CraftEntity) target).getHandle();
        int targetId = target.getEntityId();
        PrecisionPiercing.prepare(handle);
        boolean previousCancelled=PrecisionPiercing.beginReplay(handle);
        try{
        handle.preHitTargetOrDeflectSelf(new EntityHitResult(nativeTarget, new Vec3(point.getX(),point.getY(),point.getZ())));
        }finally{PrecisionPiercing.endReplay(handle,previousCancelled,targetId);}
    }

    private static final VarHandle SIZE_HANDLE;
    private static final VarHandle HEIGHT_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    net.minecraft.world.entity.Entity.class, MethodHandles.lookup());
            SIZE_HANDLE = lookup.findVarHandle(net.minecraft.world.entity.Entity.class, "bd", EntityDimensions.class);
            HEIGHT_HANDLE = lookup.findVarHandle(net.minecraft.world.entity.Entity.class, "be", float.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void setSize(Entity entity, float width, float height) {
        if (entity instanceof CraftEntity ce) {
            net.minecraft.world.entity.Entity nms = ce.getHandle();
            if (width <= 0 || height <= 0) { nms.refreshDimensions(); return; }
            SIZE_HANDLE.set(nms, EntityDimensions.scalable(width, height));
            HEIGHT_HANDLE.set(nms, height * (1.74F / 2F));
            nms.setPos(nms.position());
        }
    }

    @Override
    public void doWithSeenBy(Entity entity, Consumer<Player> consumer) {
        if (entity.getWorld() instanceof CraftWorld cw) {
            ServerLevel level = cw.getHandle();
            ChunkMap.TrackedEntity tracker = level.getChunkSource().chunkMap.entityMap.get(entity.getEntityId());
            if (tracker == null) return;
            tracker.seenBy.forEach(p -> consumer.accept(p.getPlayer().getBukkitEntity()));
        }
    }

    @Override
    public void setPosition(Entity entity, double x, double y, double z) {
        if (entity instanceof CraftEntity ce) ce.getHandle().setPos(x, y, z);
    }

    @Override
    public void setRotation(Entity entity, float yaw, float pitch) {
        if (entity instanceof CraftEntity ce) {
            ce.getHandle().setXRot(pitch);
            ce.getHandle().setYRot(yaw);
        }
    }

    @Override
    public boolean isMoveKeyDown(Player player) {
        if (player instanceof CraftPlayer cp) {
            return cp.getHandle().xxa != 0 || cp.getHandle().zza != 0;
        }
        return false;
    }
}
