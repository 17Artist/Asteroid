package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual mapped classes/configuration helpers only. Mocks below are not real path events/ticks. */
class NativeNavigationAbiTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Class<?> nested(String suffix)throws ClassNotFoundException{
        return Class.forName(NativeNavigationBridge.class.getName()+"$"+suffix);
    }
    static Field field(String name)throws Exception{
        var f=nested("Fields").getDeclaredField(name);f.setAccessible(true);return (Field)f.get(null);
    }
    static Object settings(GroundPathNavigation nav)throws Exception{
        Method m=nested("Settings").getDeclaredMethod("read",GroundPathNavigation.class);m.setAccessible(true);
        try{return m.invoke(null,nav);}catch(InvocationTargetException error){
            if(error.getCause() instanceof RuntimeException runtime)throw runtime;
            if(error.getCause() instanceof Error failure)throw failure;
            throw error;
        }
    }
    static GroundPathNavigation ordinary(float required)throws Exception{
        var nav=mock(GroundPathNavigation.class);var evaluator=mock(WalkNodeEvaluator.class);
        when(nav.getNodeEvaluator()).thenReturn(evaluator);
        field("MULTIPLIER").setFloat(nav,1);field("REQUIRED").setFloat(nav,required);
        return nav;
    }
    @Test void exactNavigationAndMovementSlotsResolveBeforeAnyWrite()throws Exception{
        assertEquals(PathNavigation.class,field("NAV").getType());assertEquals(Mob.class,field("NAV").getDeclaringClass());
        assertEquals(MoveControl.class,field("MOVE").getType());assertEquals(Mob.class,field("MOVE").getDeclaringClass());
        assertEquals(BlockPos.class,field("TARGET").getType());assertEquals(boolean.class,field("DELAY").getType());
        assertEquals(float.class,field("MULTIPLIER").getType());assertEquals(float.class,field("REQUIRED").getType());
        assertEquals(boolean.class,field("AVOID_SUN").getType());
    }
    @Test void actualPaperCreatePathFunnelIsOverridden()throws Exception{
        var args=new Class<?>[]{Set.class,Entity.class,int.class,boolean.class,int.class,float.class};
        var nativeMethod=PathNavigation.class.getDeclaredMethod("createPath",args);
        var ownedMethod=nested("Follower").getDeclaredMethod("createPath",args);
        assertEquals(nativeMethod.getReturnType(),ownedMethod.getReturnType());
        assertTrue(Modifier.isProtected(ownedMethod.getModifiers()));assertFalse(Modifier.isFinal(nativeMethod.getModifiers()));
        assertNotNull(nested("Follower").getDeclaredMethod("setSpeedModifier",double.class));
    }
    @Test void correctNativeInterpolationABI()throws Exception{
        assertEquals(boolean.class,Entity.class.getDeclaredMethod("isInterpolating").getReturnType());
        assertThrows(NoSuchFieldException.class,()->LivingEntity.class.getDeclaredField("lerpSteps"));
        assertEquals(int.class,LivingEntity.class.getDeclaredField("lerpHeadSteps").getType());
        var head=nested("FacingFields").getDeclaredField("HEAD_LERP");head.setAccessible(true);
        assertEquals(int.class,((Field)head.get(null)).getType());
    }
    @Test void actualEntityProviderPublishesRetainedNavigationCapability(){
        assertTrue(NativeNavigationNMS.class.isAssignableFrom(EntityNMSImpl.class));
    }
    @Test void exactBoundedRequiredLengthIsPreserved()throws Exception{
        var nav=ordinary(32);Object result=settings(nav);
        var required=result.getClass().getDeclaredMethod("required");required.setAccessible(true);
        assertEquals(32f,(float)required.invoke(result));
    }
    @Test void nonFiniteOrUnboundedRequiredLengthRejectedBeforeInstall()throws Exception{
        for(float required:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,65}){
            var nav=ordinary(required);assertThrows(UnsupportedOperationException.class,()->settings(nav));
        }
    }
    @Test void nonDefaultSearchMultiplierRejectedBeforeInstall()throws Exception{
        var nav=ordinary(16);field("MULTIPLIER").setFloat(nav,2);
        assertThrows(UnsupportedOperationException.class,()->settings(nav));
    }
    @Test void changedConfigurationRevokesRatherThanOverwrites()throws Exception{
        var nav=ordinary(16);Object snapshot=settings(nav);
        var matches=snapshot.getClass().getDeclaredMethod("matches",GroundPathNavigation.class);matches.setAccessible(true);
        assertEquals(true,matches.invoke(snapshot,nav));field("REQUIRED").setFloat(nav,32);
        assertEquals(false,matches.invoke(snapshot,nav));assertEquals(32f,field("REQUIRED").getFloat(nav));
    }
    @Test void followerAndMovementWrapperEachBlockAnIndependentClaim()throws Exception{
        var mob=mock(Mob.class);
        when(mob.getNavigation()).thenReturn((PathNavigation)mock(nested("Follower")));
        assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireUnowned(mob));
        when(mob.getNavigation()).thenReturn(mock(GroundPathNavigation.class));
        when(mob.getMoveControl()).thenReturn((MoveControl)mock(nested("NavigationControl")));
        assertThrows(IllegalStateException.class,()->NativeNavigationBridge.requireUnowned(mob));
    }
}
