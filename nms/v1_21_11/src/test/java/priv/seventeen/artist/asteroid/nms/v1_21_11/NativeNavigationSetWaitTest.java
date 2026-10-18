package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real mapped ABI plus an isolated ownership writer check, not native tick or path acceptance. */
class NativeNavigationSetWaitTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    static Class<?> control()throws Exception{return Class.forName(NativeNavigationBridge.class.getName()+"$NavigationControl");}
    @Test void actualNewPublicWaitWriterIsOverridableAndClosed()throws Exception{
        var nativeMethod=MoveControl.class.getDeclaredMethod("setWait");
        assertEquals(void.class,nativeMethod.getReturnType());assertTrue(Modifier.isPublic(nativeMethod.getModifiers()));
        assertFalse(Modifier.isFinal(nativeMethod.getModifiers()));
        assertEquals(void.class,control().getDeclaredMethod("setWait").getReturnType());
    }
    @Test void externalWaitCannotMutateOrReplaceOwnedNativeOperation()throws Exception{
        var type=control();var ownerType=Class.forName(NativeNavigationBridge.class.getName()+"$Port");
        var ctor=type.getDeclaredConstructor(Mob.class,ownerType);ctor.setAccessible(true);
        var owned=(MoveControl)ctor.newInstance(mock(Mob.class),null);
        var operation=MoveControl.class.getDeclaredField("operation");operation.setAccessible(true);
        @SuppressWarnings({"unchecked","rawtypes"}) Object move=Enum.valueOf((Class)operation.getType(),"MOVE_TO");
        operation.set(owned,move);
        assertThrows(IllegalStateException.class,owned::setWait);assertSame(move,operation.get(owned));
    }
}
