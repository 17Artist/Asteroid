package priv.seventeen.artist.asteroid.nms.v1_21_1;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import priv.seventeen.artist.asteroid.entity.NativeNearbyEntitiesNMS;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the real mapped Paper collection, not a rewritten loop or a live-world benchmark. */
class NativeNearbyPrefixTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static final class Rig {
        final ChunkEntitySlices slices=new ChunkEntitySlices(null,0,0,FullChunkStatus.FULL,-4,19);
        final AABB query=new AABB(0,0,0,4,64,4),inside=new AABB(1,1,1,2,2,2),outside=new AABB(12,1,12,13,2,13);
        final AtomicInteger bounds=new AtomicInteger();int serial;
        <T extends Entity>T entity(Class<T> type,AABB box){
            T e=mock(type);int id=++serial;when(e.getId()).thenReturn(id);
            doReturn(EntityType.ARMOR_STAND).when(e).getType();
            when(e.getBoundingBox()).thenAnswer(call->{bounds.incrementAndGet();return box;});return e;
        }
        Entity add(AABB box,int section){var e=entity(Entity.class,box);assertTrue(slices.addEntity(e,section));return e;}
        List<Entity> full(Entity except,Predicate<? super Entity> predicate){var result=new ArrayList<Entity>();slices.getEntities(except,query,result,predicate);return result;}
        List<Entity> prefix(Entity except,Predicate<? super Entity> predicate,int limit){var result=new ArrayList<Entity>();slices.getEntities(except,query,result,predicate,limit);return result;}
    }
    @Test void sameSection4096VisitsStopAt64InTheRealNativeLoop(){
        var r=new Rig();for(int i=0;i<4096;i++)r.add(r.inside,0);
        r.bounds.set(0);var full=r.full(null,null);int fullVisits=r.bounds.get();
        r.bounds.set(0);var prefix=r.prefix(null,null,64);int prefixVisits=r.bounds.get();
        assertEquals(4096,fullVisits);assertEquals(64,prefixVisits);assertEquals(full.subList(0,64),prefix);
        System.out.println("R7_NATIVE_PREFIX sameSectionN=4096 fullBoundingBoxVisits="+fullVisits+" prefixBoundingBoxVisits="+prefixVisits+" returned="+prefix.size());
    }
    @Test void sourceExclusionDoesNotConsumeOneOfThe64Results(){
        var r=new Rig();Entity source=null;for(int i=0;i<80;i++){var e=r.add(r.inside,0);if(i==7)source=e;}
        var full=r.full(source,null);r.bounds.set(0);var prefix=r.prefix(source,null,64);
        assertEquals(full.subList(0,64),prefix);assertFalse(prefix.contains(source));assertEquals(64,r.bounds.get());
    }
    @Test void RejectedPredicateEntriesDoNotConsumeTheLimit(){
        var r=new Rig();var accepted=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());
        for(int i=0;i<200;i++){var e=r.add(r.inside,0);if((i&1)==1)accepted.add(e);}
        var full=r.full(null,accepted::contains);r.bounds.set(0);var prefix=r.prefix(null,accepted::contains,64);
        assertEquals(full.subList(0,64),prefix);assertEquals(128,r.bounds.get());
    }
    @Test void sparseOutsideBoxesStillCostVisitsAndDoNotBecomeAFalseConstantBound(){
        var r=new Rig();for(int i=0;i<1000;i++)r.add(r.outside,0);for(int i=0;i<100;i++)r.add(r.inside,0);
        var full=r.full(null,null);r.bounds.set(0);var prefix=r.prefix(null,null,64);
        assertEquals(full.subList(0,64),prefix);assertEquals(1064,r.bounds.get());
        System.out.println("R7_NATIVE_PREFIX sparseOutside=1000 prefixBoundingBoxVisits="+r.bounds.get()+" returned="+prefix.size());
    }
    @Test void sectionOrderIsPreservedRatherThanReplacedWithInsertionOrDistanceSort(){
        var r=new Rig();var later=r.add(r.inside.move(0,32,0),2);var first=r.add(r.inside,0);var middle=r.add(r.inside.move(0,16,0),1);
        assertEquals(List.of(first,middle,later),r.full(null,null));assertEquals(List.of(first,middle),r.prefix(null,null,2));
    }
    @Test void dragonParentAndPartsRetainTheirOrderAcrossThe64Boundary(){
        var r=new Rig();for(int i=0;i<62;i++)r.add(r.inside,0);
        var dragon=r.entity(EnderDragon.class,r.inside);var first=r.entity(EnderDragonPart.class,r.inside);var second=r.entity(EnderDragonPart.class,r.inside);
        when(dragon.getSubEntities()).thenReturn(new EnderDragonPart[]{first,second});assertTrue(r.slices.addEntity(dragon,0));r.add(r.inside,0);
        var full=r.full(null,null);var prefix=r.prefix(null,null,64);
        assertEquals(full.subList(0,64),prefix);assertSame(dragon,prefix.get(62));assertSame(first,prefix.get(63));assertFalse(prefix.contains(second));
    }
    @Test void excludingDragonExcludesItsPartsAsTheOriginalUntypedPathDoes(){
        var r=new Rig();var dragon=r.entity(EnderDragon.class,r.inside);var part=r.entity(EnderDragonPart.class,r.inside);
        when(dragon.getSubEntities()).thenReturn(new EnderDragonPart[]{part});assertTrue(r.slices.addEntity(dragon,0));
        for(int i=0;i<100;i++)r.add(r.inside,0);
        var full=r.full(dragon,null);var prefix=r.prefix(dragon,null,64);
        assertEquals(full.subList(0,64),prefix);assertFalse(prefix.contains(part));
        // A part used as except is skipped independently; its parent still consumes one slot.
        var withoutPart=r.full(part,null);var partPrefix=r.prefix(part,null,64);
        assertEquals(withoutPart.subList(0,64),partPrefix);assertSame(dragon,partPrefix.get(0));assertFalse(partPrefix.contains(part));
    }
    @Test void rejectedDragonParentStillOffersItsPartsExactlyAsTheOldQueryDoes(){
        var r=new Rig();var dragon=r.entity(EnderDragon.class,r.inside);var part=r.entity(EnderDragonPart.class,r.inside);
        when(dragon.getSubEntities()).thenReturn(new EnderDragonPart[]{part});assertTrue(r.slices.addEntity(dragon,0));
        Predicate<Entity> onlyPart=e->e==part;assertEquals(List.of(part),r.full(null,onlyPart));assertEquals(List.of(part),r.prefix(null,onlyPart,1));
    }
    @Test void nativeBridgeCapabilityMatchesTheActualMappedPaperMethods(){assertTrue(NativeNearbyBridge.supported());}
    @Test void publicCapabilityContainsTheBridgeLinkageBoundaryRatherThanOnlyItsProbeBody()throws Exception{
        var provider=entryProjection();
        try(var bridge=mockStatic(NativeNearbyBridge.class)){
            bridge.when(NativeNearbyBridge::supported).thenThrow(new NoClassDefFoundError("absent Moonrise"));
            assertFalse(provider.supportsOrderedNearbyPrefix());
            bridge.when(NativeNearbyBridge::supported).thenThrow(new VerifyError("helper linkage"));
            assertFalse(provider.supportsOrderedNearbyPrefix());
        }
    }
    @Test void publicQueryDoesNotTurnRuntimeNativeFailureIntoAnEmptyOrFullFallbackResult()throws Exception{
        var provider=entryProjection();var host=mock(org.bukkit.entity.Entity.class);
        var expected=new IllegalStateException("native query failed");
        try(var bridge=mockStatic(NativeNearbyBridge.class)){
            bridge.when(()->NativeNearbyBridge.nearby(host,1,1,1,64)).thenThrow(expected);
            assertSame(expected,assertThrows(IllegalStateException.class,()->provider.nearbyEntityPrefix(host,1,1,1,64)));
            verifyNoInteractions(host);
        }
    }
    @Test void bridgeUsesExactHostAndNativeExpansionAndMapsOnlyThe64ReturnedEntities(){
        var r=new Rig();var source=r.entity(Entity.class,r.inside);var craft=mock(CraftEntity.class);
        var level=mock(ServerLevel.class);
        var lookup=mock(EntityLookup.class);
        when(craft.getHandle()).thenReturn(source);when(source.level()).thenReturn(level);when(level.moonrise$getEntityLookup()).thenReturn(lookup);
        var profiler=mock(ProfilerFiller.class);when(level.getProfiler()).thenReturn(profiler);
        var wrappers=new ArrayList<org.bukkit.entity.Entity>();var mapped=new AtomicInteger();
        for(int i=0;i<128;i++){
            var e=r.add(r.inside,0);var wrapper=mock(CraftEntity.class);if(i<64)wrappers.add(wrapper);
            when(e.getBukkitEntity()).thenAnswer(call->{mapped.incrementAndGet();return wrapper;});
        }
        doAnswer(call->{
            assertSame(source,call.getArgument(0));AABB box=call.getArgument(1);assertEquals(r.inside.inflate(8,12,8),box);
            assertNull(call.getArgument(3));assertEquals(64,(int)call.getArgument(4));
            r.slices.getEntities(source,box,call.getArgument(2),null,64);return null;
        }).when(lookup).getEntities(eq(source),any(AABB.class),anyList(),isNull(),eq(64));
        try(var bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals(wrappers,NativeNearbyBridge.nearby(craft,8,12,8,64));assertEquals(64,mapped.get());verify(profiler).incrementCounter("getEntities");
        }
    }
    /**
     * Execute the two actual compiled entry methods with their original exception tables.
     * EntityNMSImpl's unrelated legacy clinit uses Spigot field names that the real Paper plugin
     * remapper translates. This mapped-JAR test does not exercise that remapper or class startup.
     */
    private static Class<?> entryType;
    private static NativeNearbyEntitiesNMS entryProjection()throws Exception{
        if(entryType==null){
            String name=NativeNearbyPrefixTest.class.getPackageName().replace('.','/')+"/NativeNearbyEntryProjection";
            var writer=new ClassWriter(0);
            writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,name,null,"java/lang/Object",new String[]{"priv/seventeen/artist/asteroid/entity/NativeNearbyEntitiesNMS"});
            var constructor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
            constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(1,1);constructor.visitEnd();
            var selected=new HashSet<String>();
            try(var input=NativeNearbyPrefixTest.class.getResourceAsStream("EntityNMSImpl.class")){
                assertNotNull(input);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                    @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                        if(!Set.of("supportsOrderedNearbyPrefix","nearbyEntityPrefix").contains(method))return null;
                        assertTrue(selected.add(method));
                        return writer.visitMethod(access,method,descriptor,signature,exceptions);
                    }
                },ClassReader.SKIP_DEBUG);
            }
            assertEquals(Set.of("supportsOrderedNearbyPrefix","nearbyEntityPrefix"),selected);
            writer.visitEnd();entryType=MethodHandles.lookup().defineClass(writer.toByteArray());
        }
        return (NativeNearbyEntitiesNMS)entryType.getConstructor().newInstance();
    }
}
