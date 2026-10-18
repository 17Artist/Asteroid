package priv.seventeen.artist.asteroid.nms.v1_20_4;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.util.PaperCompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual mapped ABI/configuration checks. Mocks are not real navigation or flight acceptance. */
class NativeNavigationAbiTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Class<?> nested(String suffix)throws ClassNotFoundException{
        return Class.forName(NativeNavigationBridge.class.getName()+"$"+suffix);
    }
    static Object value(String owner,String name)throws Exception{
        var f=nested(owner).getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    static Field field(String name)throws Exception{return (Field)value("Fields",name);}
    static Object settings(GroundPathNavigation nav)throws Exception{
        var m=nested("Settings").getDeclaredMethod("read",GroundPathNavigation.class);m.setAccessible(true);
        try{return m.invoke(null,nav);}catch(InvocationTargetException error){
            if(error.getCause() instanceof RuntimeException runtime)throw runtime;
            if(error.getCause() instanceof Error failure)throw failure;
            throw error;
        }
    }
    static GroundPathNavigation ordinary()throws Exception{
        var nav=mock(GroundPathNavigation.class);var evaluator=mock(WalkNodeEvaluator.class);
        when(nav.getNodeEvaluator()).thenReturn(evaluator);field("MULTIPLIER").setFloat(nav,1);return nav;
    }
    @Test void exactNativeSlotsResolveBeforeInstall()throws Exception{
        assertEquals(PathNavigation.class,field("NAV").getType());assertEquals(Mob.class,field("NAV").getDeclaringClass());
        assertEquals(MoveControl.class,field("MOVE").getType());assertEquals(BlockPos.class,field("TARGET").getType());
        assertEquals(boolean.class,field("DELAY").getType());assertEquals(float.class,field("MULTIPLIER").getType());
        assertEquals(Entity.class,field("LEASH_HOLDER").getType());assertEquals(int.class,field("LEASH_DELAY").getType());
    }
    @Test void actualPaperCommonFunnelAndSpeedAreOverridden()throws Exception{
        var args=new Class<?>[]{Set.class,Entity.class,int.class,boolean.class,int.class,float.class};
        var original=PathNavigation.class.getDeclaredMethod("createPath",args);
        var owned=nested("Follower").getDeclaredMethod("createPath",args);
        assertEquals(Path.class,original.getReturnType());assertEquals(original.getReturnType(),owned.getReturnType());
        assertTrue(Modifier.isProtected(owned.getModifiers()));assertFalse(Modifier.isFinal(original.getModifiers()));
        assertNotNull(nested("Follower").getDeclaredMethod("setSpeedModifier",double.class));
    }
    @Test void runtimeDescriptorUsesActualClassNamespace()throws Exception{
        assertEquals(MethodType.methodType(Path.class,Set.class,Entity.class,int.class,boolean.class,int.class,float.class)
                .descriptorString(),value("QueryAccess","COMMON_DESCRIPTOR"));
        var name=(String)value("QueryAccess","RECOMPUTE");
        assertEquals(void.class,PathNavigation.class.getDeclaredMethod(name).getReturnType());
    }
    @Test void correctInterpolationSlotsResolve()throws Exception{
        assertEquals(int.class,((Field)value("FacingFields","LERP")).getType());
        assertEquals(int.class,((Field)value("FacingFields","HEAD_LERP")).getType());
        assertEquals(int.class,LivingEntity.class.getDeclaredField("lerpHeadSteps").getType());
    }
    @Test void bothSkeletonSettersResolveBeforeAnyInvocation()throws Exception{
        for(String name:new String[]{"BURN","PICKUP"})assertEquals(
                MethodType.methodType(void.class,Skeleton.class,boolean.class),
                ((MethodHandle)value("SkeletonSetupAccess",name)).type());
    }
    @Test void providerPublishesRetainedNavigation(){
        assertTrue(NativeNavigationNMS.class.isAssignableFrom(EntityNMSImpl.class));
    }
    @Test void nonDefaultMultiplierRejectedBeforeInstall()throws Exception{
        var nav=ordinary();field("MULTIPLIER").setFloat(nav,2);
        assertThrows(UnsupportedOperationException.class,()->settings(nav));
    }
    @Test void changedConfigurationRevokesWithoutRepairingIt()throws Exception{
        var nav=ordinary();Object snapshot=settings(nav);
        var matches=snapshot.getClass().getDeclaredMethod("matches",GroundPathNavigation.class);matches.setAccessible(true);
        assertEquals(true,matches.invoke(snapshot,nav));field("MULTIPLIER").setFloat(nav,2);
        assertEquals(false,matches.invoke(snapshot,nav));assertEquals(2f,field("MULTIPLIER").getFloat(nav));
    }
    @Test void followerAndMovementEachBlockIndependentClaim()throws Exception{
        var mob=mock(Mob.class);
        when(mob.getNavigation()).thenReturn((PathNavigation)mock(nested("Follower")));
        assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireUnowned(mob));
        when(mob.getNavigation()).thenReturn(mock(GroundPathNavigation.class));
        when(mob.getMoveControl()).thenReturn((MoveControl)mock(nested("NavigationControl")));
        assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireUnowned(mob));
    }
    @Test void realNativeJumpHandleResolves()throws Exception{
        var access=Class.forName(NativeMoveBridge.class.getName()+"$DriveAccess");
        var f=access.getDeclaredField("JUMP_DELAY");f.setAccessible(true);
        var handle=(VarHandle)f.get(null);assertEquals(int.class,handle.varType());
        assertEquals(java.util.List.of(LivingEntity.class),handle.coordinateTypes());
    }
    @Test void explicitRetainedFlightCapabilityRequiresPaperAndNotFolia()throws Exception{
        assertNotNull(EntityNMSImpl.class.getDeclaredMethod("supportsRetainedHuskFlight"));
        try(var platform=mockStatic(PaperCompat.class)){
            platform.when(PaperCompat::isPaper).thenReturn(false);platform.when(PaperCompat::isFolia).thenReturn(false);
            assertFalse(NativeNavigationBridge.supportsRetainedHuskFlight());
            platform.when(PaperCompat::isPaper).thenReturn(true);assertTrue(NativeNavigationBridge.supportsRetainedHuskFlight());
            platform.when(PaperCompat::isFolia).thenReturn(true);assertFalse(NativeNavigationBridge.supportsRetainedHuskFlight());
        }
    }
}
