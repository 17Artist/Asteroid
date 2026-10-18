package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import priv.seventeen.artist.asteroid.entity.NativeEntityHitboxNMS;
import priv.seventeen.artist.asteroid.entity.NativePrecisionQuery;
import priv.seventeen.artist.asteroid.entity.PrecisionHitbox;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual mapped AABB.inflate/clip/contains, not a copied intersection algorithm or a test server. */
class NativePrecisionContextTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static final class Rig {
        final Entity owner=mock(Entity.class);
        final AtomicReference<NativePrecisionQuery> query=new AtomicReference<>();
        double reply=.25;
        AABB ownerBounds=new AABB(10,0,0,12,2,2);Runnable onContext=()->{};
        final PrecisionHitbox.ContextTrace context=q->{query.set(q);onContext.run();return reply;};
        Rig(){when(owner.getBoundingBox()).thenAnswer(i->ownerBounds);}
        AABB bounds(){return new PrecisionProxyImpl.PreciseBounds(new BoundingBox(-3,-3,-3,3,3,3),context,owner);}
    }
    @Test void legacyClipKeepsTheAuthoredParameterAndNeverQueriesTheNativeOwner(){
        var owner=mock(Entity.class);PrecisionHitbox.Trace old=(ax,ay,az,bx,by,bz)->.25;
        var box=new PrecisionProxyImpl.PreciseBounds(new BoundingBox(0,0,0,1,1,1),old,owner);
        var hit=box.inflate(.3).inflate(1,2,3).clip(new Vec3(0,0,0),new Vec3(8,4,2)).orElseThrow();
        assertEquals(2,hit.x);assertEquals(1,hit.y);assertEquals(.5,hit.z);verifyNoInteractions(owner);
    }
    @Test void legacyContainsStillSuppliesASixCoordinatePointQuery(){
        var owner=mock(Entity.class);double[] observed=new double[6];PrecisionHitbox.Trace old=(ax,ay,az,bx,by,bz)->{
            observed[0]=ax;observed[1]=ay;observed[2]=az;observed[3]=bx;observed[4]=by;observed[5]=bz;return 0;
        };
        var box=new PrecisionProxyImpl.PreciseBounds(new BoundingBox(0,0,0,1,1,1),old,owner);
        assertTrue(box.contains(2,3,4));assertArrayEquals(new double[]{2,3,4,2,3,4},observed);verifyNoInteractions(owner);
    }
    @Test void contextClipUsesTheActualOwnerAabbNotTheAuthoredProxyBounds(){
        var r=new Rig();var from=new Vec3(0,1,1);var to=new Vec3(20,1,1);
        var returned=r.bounds().clip(from,to).orElseThrow();var q=r.query.get();
        assertEquals(NativePrecisionQuery.Mode.CLIP,q.mode());assertEquals(.5,q.ownerParameter());assertEquals(5,returned.x);
        assertEquals(0,q.inflateX());assertEquals(0,q.inflateY());assertEquals(0,q.inflateZ());
    }
    @Test void aNativeCoarseMissIsNanEvenWhenTheAuthoredTraceFindsAHit(){
        var r=new Rig();var returned=r.bounds().clip(new Vec3(-2,1,1),new Vec3(2,1,1));
        assertTrue(returned.isPresent());assertTrue(Double.isNaN(r.query.get().ownerParameter()));
    }
    @Test void cumulativeAsymmetricAndUniformInflationMatchesActualMappedAabbClip(){
        var r=new Rig();var box=r.bounds().inflate(.25,.5,.75).inflate(.125).inflate(.5,.25,1);
        var from=new Vec3(0,1,1);var to=new Vec3(20,1,1);box.clip(from,to);
        var q=r.query.get();assertEquals(.875,q.inflateX());assertEquals(.875,q.inflateY());assertEquals(1.875,q.inflateZ());
        var expected=r.owner.getBoundingBox().inflate(.875,.875,1.875).clip(from,to).orElseThrow();
        assertEquals(expected.x/20,q.ownerParameter());
    }
    @Test void containsCarriesItsOwnModeAndTheRealInflatedNativeContainment(){
        var r=new Rig();var box=r.bounds().inflate(.25,.5,.75).inflate(.5,0,0);
        assertTrue(box.contains(9.5,1,1));var inside=r.query.get();assertEquals(NativePrecisionQuery.Mode.CONTAINS,inside.mode());
        assertEquals(0,inside.ownerParameter());assertEquals(inside.ax(),inside.bx());assertEquals(.75,inside.inflateX());
        assertTrue(box.contains(8,1,1));assertTrue(Double.isNaN(r.query.get().ownerParameter()));
    }
    @Test void zeroLengthClipAndContainsRemainDifferentNativeOperations(){
        var r=new Rig();var at=new Vec3(11,1,1);r.bounds().clip(at,at);var clip=r.query.get();
        assertEquals(NativePrecisionQuery.Mode.CLIP,clip.mode());
        assertEquals(r.owner.getBoundingBox().clip(at,at).isPresent(),!Double.isNaN(clip.ownerParameter()));
        r.bounds().contains(at);assertEquals(NativePrecisionQuery.Mode.CONTAINS,r.query.get().mode());assertEquals(0,r.query.get().ownerParameter());
    }
    @Test void invalidContextResultsCannotTurnIntoNativeHits(){
        var r=new Rig();for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,1.001}){
            r.reply=invalid;assertTrue(r.bounds().clip(new Vec3(0,1,1),new Vec3(20,1,1)).isEmpty());assertFalse(r.bounds().contains(11,1,1));
        }
    }
    @Test void contextPickRadiusComesFromTheOwnerButLegacyKeepsItsExistingRadius(){
        probe("pickRadius");
    }
    @Test void aLaterNativeBoundsChangeIsReadAsNativeStateNotAStaleProxySnapshot(){
        var r=new Rig();var box=r.bounds();var from=new Vec3(0,1,1);var to=new Vec3(20,1,1);box.clip(from,to);assertEquals(.5,r.query.get().ownerParameter());
        when(r.owner.getBoundingBox()).thenReturn(new AABB(4,0,0,6,2,2));box.clip(from,to);assertEquals(.2,r.query.get().ownerParameter());
    }
    @Test void negativeInflationIsAccumulatedWithoutSubstitutingAnArrowConstant(){
        var r=new Rig();var box=r.bounds().inflate(1,1,1).inflate(-.25,-.5,-.75);box.clip(new Vec3(0,1,1),new Vec3(20,1,1));
        var q=r.query.get();assertEquals(.75,q.inflateX());assertEquals(.5,q.inflateY());assertEquals(.25,q.inflateZ());assertEquals(9.25/20,q.ownerParameter());
    }
    @Test void nativeContainsAfterUniformInflateOneKeepsTheActualMargin(){
        var r=new Rig();r.reply=0;assertTrue(r.bounds().inflate(1).contains(new Vec3(9.5,1,1)));
        var q=r.query.get();assertEquals(1,q.inflateX());assertEquals(1,q.inflateY());assertEquals(1,q.inflateZ());
        assertEquals(NativePrecisionQuery.Mode.CONTAINS,q.mode());assertEquals(0,q.ownerParameter());
    }
    @Test void clipCarriesOneRawNativeBoundsCaptureBeforeOpaqueCallbackChangesOnlyItsSize(){
        var r=new Rig();var box=r.bounds().inflate(.25);clearInvocations(r.owner);
        r.onContext=()->r.ownerBounds=new AABB(10.5,.5,.5,11.5,1.5,1.5);box.clip(new Vec3(0,1,1),new Vec3(20,1,1));
        var q=r.query.get();assertEquals(10,q.ownerMinX());assertEquals(0,q.ownerMinY());assertEquals(0,q.ownerMinZ());
        assertEquals(12,q.ownerMaxX());assertEquals(2,q.ownerMaxY());assertEquals(2,q.ownerMaxZ());assertEquals(9.75/20,q.ownerParameter());
        assertEquals(11,(q.ownerMinX()+q.ownerMaxX())/2);assertEquals(11,(r.ownerBounds.minX+r.ownerBounds.maxX)/2);
        assertNotEquals(q.ownerMinX(),r.ownerBounds.minX);verify(r.owner,times(1)).getBoundingBox();
    }
    @Test void containsAlsoCarriesTheSameUninflatedRawBoxRatherThanASecondNativeRead(){
        var r=new Rig();var box=r.bounds().inflate(1);clearInvocations(r.owner);
        r.onContext=()->r.ownerBounds=new AABB(10.5,.5,.5,11.5,1.5,1.5);assertTrue(box.contains(9.5,1,1));
        var q=r.query.get();assertEquals(10,q.ownerMinX());assertEquals(12,q.ownerMaxX());assertEquals(1,q.inflateX());assertEquals(0,q.ownerParameter());
        assertNotEquals(q.ownerMaxX(),r.ownerBounds.maxX);verify(r.owner,times(1)).getBoundingBox();
    }
    @Test void aNonLivingReplayAliasCanonicalizesToTheSameRealOwnerWithoutALivingStandIn(){
        probe("replayAlias");
    }
    @Test void theOptionalEntityCapabilityDoesNotChangeTheLegacyLivingMethodDescriptors()throws Exception{
        assertTrue(NativeEntityHitboxNMS.class.isAssignableFrom(EntityNMSImpl.class));
        assertNotNull(EntityNMSImpl.class.getMethod("createEntityPrecisionHitbox",org.bukkit.entity.Entity.class,BoundingBox.class,PrecisionHitbox.ContextTrace.class));
        assertNotNull(EntityNMSImpl.class.getMethod("createPrecisionHitbox",org.bukkit.entity.LivingEntity.class,BoundingBox.class,PrecisionHitbox.Trace.class));
    }
    @Test void aPrecisionProxyCannotRecursivelyBecomeTheOwnerOfTheNewNativeCapability(){
        probe("rejectProxy");
    }
    @Test void aDamageRelayAliasCannotBecomeANativeHostForAnotherProxyOrRelay(){
        probe("rejectRelay");
    }
    @Test void anInterfaceOnlyFakeOwnerIsRejectedBeforeAnyWorldOrSpawnLookup(){
        probe("rejectInterface");
    }
    @Test void mappedFixtureRunsBothRealInitializersWithExactlyThreeFieldNameTranslations()throws Exception{
        var fixture=fixture();String prefix=NativePrecisionContextTest.class.getPackageName()+".";
        Class.forName(prefix+"CustomEntityImpl",true,fixture);Class.forName(prefix+"EntityNMSImpl",true,fixture);
        assertEquals(Map.of(prefix+"CustomEntityImpl",1,prefix+"EntityNMSImpl",2),fixture.translated);
        assertNotSame(CustomEntityImpl.class,Class.forName(prefix+"CustomEntityImpl",false,fixture));
    }

    /**
     * These five component probes need the enclosing adapters, unlike the nested AABB tests.
     * The plain paperweight mapped JAR does not run Paper's plugin reflection rewriter. Adapt
     * ONLY its three exact legacy field-name literals to the already-verified mapped fields;
     * do not remove an initializer, replace a guard, stub a VarHandle or change any probe body.
     * This is deliberately NOT a production remapper / plugin cold-start acceptance test.
     */
    private static MappedFixture mappedFixture;
    private static MappedFixture fixture()throws ReflectiveOperationException{
        if(mappedFixture==null){
            assertEquals(boolean.class,net.minecraft.world.entity.LivingEntity.class.getDeclaredField("jumping").getType());
            assertEquals(net.minecraft.world.entity.EntityDimensions.class,Entity.class.getDeclaredField("dimensions").getType());
            assertEquals(float.class,Entity.class.getDeclaredField("eyeHeight").getType());
            mappedFixture=new MappedFixture();
        }
        return mappedFixture;
    }
    private static void probe(String name){
        try{
            var type=Class.forName(NativePrecisionContextTest.class.getName()+"$OuterProbes",true,fixture());
            var method=type.getDeclaredMethod(name);method.setAccessible(true);method.invoke(null);
        }catch(InvocationTargetException wrapped){
            var cause=wrapped.getCause();if(cause instanceof Error error)throw error;if(cause instanceof RuntimeException error)throw error;
            throw new AssertionError(cause);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
    private static final class MappedFixture extends ClassLoader {
        final String prefix=NativePrecisionContextTest.class.getPackageName()+".";
        final Map<String,Integer> translated=new HashMap<>();
        MappedFixture(){super(NativePrecisionContextTest.class.getClassLoader());}
        @Override protected synchronized Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!name.startsWith(prefix)||name.startsWith(NativePrecisionContextTest.class.getName())
                    &&!name.equals(NativePrecisionContextTest.class.getName()+"$OuterProbes"))return super.loadClass(name,resolve);
            var type=findLoadedClass(name);
            if(type==null){
                try(var stream=getParent().getResourceAsStream(name.replace('.','/')+".class")){
                    if(stream==null)throw new ClassNotFoundException(name);
                    byte[] bytes=stream.readAllBytes();
                    Map<String,String> names=name.equals(prefix+"CustomEntityImpl")?Map.of("bn","jumping")
                        :name.equals(prefix+"EntityNMSImpl")?Map.of("bd","dimensions","be","eyeHeight"):Map.of();
                    if(!names.isEmpty()){
                        var writer=new ClassWriter(0);int[] changes={0};
                        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9,writer){
                            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                                var target=super.visitMethod(access,method,descriptor,signature,exceptions);
                                if(!method.equals("<clinit>"))return target;
                                return new MethodVisitor(Opcodes.ASM9,target){
                                    @Override public void visitLdcInsn(Object constant){
                                        if(constant instanceof String text&&names.containsKey(text)){
                                            changes[0]++;super.visitLdcInsn(names.get(text));
                                        }else super.visitLdcInsn(constant);
                                    }
                                };
                            }
                        },0);
                        assertEquals(names.size(),changes[0],"Only the exact three legacy initializer literals may be translated");
                        translated.put(name,changes[0]);bytes=writer.toByteArray();
                    }
                    type=defineClass(name,bytes,0,bytes.length);
                }catch(IOException failure){throw new ClassNotFoundException(name,failure);}
            }
            if(resolve)resolveClass(type);return type;
        }
    }
    /** Original assertions executed against the real adapter code in the mapped fixture. */
    public static final class OuterProbes {
        public static void pickRadius(){
            var owner=mock(Entity.class);when(owner.getPickRadius()).thenReturn(.875f);
            PrecisionHitbox.ContextTrace context=q->Double.NaN;assertEquals(.875f,PrecisionProxyImpl.queryPickRadius(context,owner,.1f));
            verify(owner,times(1)).getPickRadius();clearInvocations(owner);
            assertEquals(.1f,PrecisionProxyImpl.queryPickRadius((ax,ay,az,bx,by,bz)->0,owner,.1f));verifyNoInteractions(owner);
        }
        public static void replayAlias(){
            var owner=mock(org.bukkit.entity.Item.class);when(owner.getEntityId()).thenReturn(1234567);int alias=1234568;
            try{PrecisionProxyImpl.registerReplayAlias(alias,owner);assertSame(owner,PrecisionProxyImpl.proxyOwner(alias));assertEquals(1234567,PrecisionProxyImpl.canonicalId(alias));}
            finally{PrecisionProxyImpl.removeReplayAlias(alias);}assertNull(PrecisionProxyImpl.proxyOwner(alias));
        }
        public static void rejectProxy(){
            try(var thread=mockStatic(Bukkit.class)){
                thread.when(Bukkit::isPrimaryThread).thenReturn(true);var owner=mock(CraftEntity.class);var proxy=mock(PrecisionProxyImpl.class);
                when(owner.isValid()).thenReturn(true);when(owner.getHandle()).thenReturn(proxy);var adapter=mock(EntityNMSImpl.class,CALLS_REAL_METHODS);
                var box=new BoundingBox(0,0,0,1,1,1);assertThrows(IllegalArgumentException.class,()->adapter.createEntityPrecisionHitbox(owner,box,q->Double.NaN));
                assertThrows(IllegalArgumentException.class,()->adapter.createEntityDamageRelay(owner,box,d->false,e->{}));verify(owner,never()).getWorld();
            }
        }
        public static void rejectRelay(){
            try(var thread=mockStatic(Bukkit.class)){
                thread.when(Bukkit::isPrimaryThread).thenReturn(true);var owner=mock(CraftEntity.class);var relay=mock(DamageRelayImpl.class);
                when(owner.isValid()).thenReturn(true);when(owner.getHandle()).thenReturn(relay);var adapter=mock(EntityNMSImpl.class,CALLS_REAL_METHODS);
                var box=new BoundingBox(0,0,0,1,1,1);assertThrows(IllegalArgumentException.class,()->adapter.createEntityPrecisionHitbox(owner,box,q->Double.NaN));
                assertThrows(IllegalArgumentException.class,()->adapter.createEntityDamageRelay(owner,box,d->false,e->{}));verify(owner,never()).getWorld();
            }
        }
        public static void rejectInterface(){
            try(var thread=mockStatic(Bukkit.class)){
                thread.when(Bukkit::isPrimaryThread).thenReturn(true);var owner=mock(org.bukkit.entity.Entity.class);
                when(owner.isValid()).thenReturn(true);var adapter=mock(EntityNMSImpl.class,CALLS_REAL_METHODS);var box=new BoundingBox(0,0,0,1,1,1);
                assertThrows(IllegalArgumentException.class,()->adapter.createEntityPrecisionHitbox(owner,box,q->Double.NaN));
                assertThrows(IllegalArgumentException.class,()->adapter.createEntityDamageRelay(owner,box,d->false,e->{}));verify(owner,never()).getWorld();
            }
        }
    }
}
