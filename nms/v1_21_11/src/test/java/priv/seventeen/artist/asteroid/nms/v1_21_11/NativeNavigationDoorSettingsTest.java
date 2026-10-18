package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual mapped setters/fields. No mocked evaluator, setter, Settings.apply or pathfinder update. */
class NativeNavigationDoorSettingsTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Class<?> nested(String suffix)throws ClassNotFoundException{return Class.forName(NativeNavigationBridge.class.getName()+"$"+suffix);}
    static Field bridgeField(String name)throws Exception{
        var field=nested("Fields").getDeclaredField(name);field.setAccessible(true);return (Field)field.get(null);
    }
    static void setUnique(Class<?> owner,Class<?> type,Object target,Object value)throws Exception{
        Field found=null;
        for(var field:owner.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&field.getType()==type){assertNull(found);found=field;}
        assertNotNull(found);found.setAccessible(true);found.set(target,value);
    }
    static Object invoke(Object target,String name,Class<?> argumentType,Object argument)throws Exception{
        var method=target.getClass().getDeclaredMethod(name,argumentType);method.setAccessible(true);
        try{return method.invoke(target,argument);}catch(InvocationTargetException error){
            if(error.getCause() instanceof RuntimeException runtime)throw runtime;
            if(error.getCause() instanceof Error fatal)throw fatal;
            throw error;
        }
    }
    static Object setting(boolean pass,boolean open,boolean floating,boolean fences,boolean sun,float required)throws Exception{
        var ctor=nested("Settings").getDeclaredConstructor(boolean.class,boolean.class,boolean.class,boolean.class,boolean.class,float.class);
        ctor.setAccessible(true);return ctor.newInstance(pass,open,floating,fences,sun,required);
    }
    @Test void actualEvaluatorOwnsPassDoorWriterAndNativeNavigationHasNoConvenienceMethod()throws Exception{
        var setter=NodeEvaluator.class.getDeclaredMethod("setCanPassDoors",boolean.class);
        assertTrue(Modifier.isPublic(setter.getModifiers()));assertEquals(void.class,setter.getReturnType());
        assertThrows(NoSuchMethodException.class,()->GroundPathNavigation.class.getMethod("setCanPassDoors",boolean.class));
        var evaluator=new WalkNodeEvaluator();evaluator.setCanPassDoors(true);assertTrue(evaluator.canPassDoors());
        evaluator.setCanPassDoors(false);assertFalse(evaluator.canPassDoors());
    }
    @Test void realSettingsApplyAndReadbackPreserveEveryNativeFlagAndRequiredLength()throws Exception{
        var mob=mock(Mob.class);when(mob.getAttributeValue(Attributes.FOLLOW_RANGE)).thenReturn(16.0);
        when(mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE)).thenReturn(16.0);
        // Only host identity is mocked. Every getter/setter and Settings method executes real code.
        var target=mock(GroundPathNavigation.class,CALLS_REAL_METHODS);
        var evaluator=new WalkNodeEvaluator();var finder=new PathFinder(evaluator,256);
        setUnique(PathNavigation.class,Mob.class,target,mob);
        setUnique(PathNavigation.class,NodeEvaluator.class,target,evaluator);
        setUnique(PathNavigation.class,PathFinder.class,target,finder);
        bridgeField("MULTIPLIER").setFloat(target,1);
        Object[] cases={setting(true,false,true,false,true,32),setting(false,true,false,true,false,12)};
        for(Object expected:cases){
            invoke(expected,"apply",GroundPathNavigation.class,target);
            assertEquals(expected,invoke(expected,"read",GroundPathNavigation.class,target));
            assertEquals(true,invoke(expected,"matches",GroundPathNavigation.class,target));
            assertSame(evaluator,target.getNodeEvaluator());
            var required=nested("Settings").getDeclaredMethod("required");required.setAccessible(true);
            float length=(float)required.invoke(expected);
            assertEquals(length,bridgeField("REQUIRED").getFloat(target));
            var limit=PathFinder.class.getDeclaredField("maxVisitedNodes");limit.setAccessible(true);
            assertEquals((int)(Math.max(16,length)*16),limit.getInt(finder));
        }
        assertFalse(evaluator.canPassDoors());assertTrue(evaluator.canOpenDoors());
        assertFalse(evaluator.canFloat());assertTrue(evaluator.canWalkOverFences());
        assertEquals(false,bridgeField("AVOID_SUN").getBoolean(target));
    }
}
