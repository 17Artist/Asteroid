package priv.seventeen.artist.asteroid.nms.v1_21_4;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exact mapped configuration fields and real native setters, not live navigation evidence. */
class NativeNavigationGroundFieldsTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Class<?> nested(String suffix)throws ClassNotFoundException{return Class.forName(NativeNavigationBridge.class.getName()+"$"+suffix);}
    static Field field(String name)throws Exception{var f=nested("Fields").getDeclaredField(name);f.setAccessible(true);return (Field)f.get(null);}
    @Test void exactAvoidSunFieldAndSetterCannotTargetTheOtherBoolean()throws Exception{
        var exact=field("AVOID_SUN");assertEquals("avoidSun",exact.getName());assertEquals(boolean.class,exact.getType());
        assertEquals(GroundPathNavigation.class,exact.getDeclaringClass());assertFalse(Modifier.isStatic(exact.getModifiers()));
        var nav=mock(GroundPathNavigation.class,CALLS_REAL_METHODS);
        java.util.Map<Field,Object> others=new java.util.LinkedHashMap<>();
        for(var candidate:GroundPathNavigation.class.getDeclaredFields())if(candidate.getType()==boolean.class&&!Modifier.isStatic(candidate.getModifiers())&&!candidate.equals(exact)){
            candidate.setAccessible(true);others.put(candidate,candidate.get(nav));
        }
        nav.setAvoidSun(true);assertTrue(exact.getBoolean(nav));nav.setAvoidSun(false);assertFalse(exact.getBoolean(nav));
        for(var entry:others.entrySet())assertEquals(entry.getValue(),entry.getKey().get(nav));
    }

}
