package priv.seventeen.artist.asteroid.nms.v1_21_4;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Provider platform/ABI controls only; not a flight physics or real Paper acceptance claim. */
class NativeHuskFlightCapabilityTest {
    @BeforeAll static void registries(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void oldProviderDefaultIsFalse(){
        var old=mock(NativeNavigationNMS.class,CALLS_REAL_METHODS);
        assertFalse(old.supportsRetainedHuskFlight());
    }
    @Test void exactCurrentProviderExplicitlyOptsInOnlyOnPaperNotFolia()throws Exception{
        // Inspect the declared opt-in without initializing unrelated production mapping handles.
        var provider=Class.forName(getClass().getPackageName()+".EntityNMSImpl",false,getClass().getClassLoader());
        var capability=provider.getDeclaredMethod("supportsRetainedHuskFlight");
        assertTrue(NativeNavigationNMS.class.isAssignableFrom(provider));
        assertSame(provider,capability.getDeclaringClass());
        assertEquals(boolean.class,capability.getReturnType());
        assertEquals(0,capability.getParameterCount());
        assertTrue(Modifier.isPublic(capability.getModifiers()));
        assertFalse(Modifier.isStatic(capability.getModifiers()));
        assertFalse(Modifier.isAbstract(capability.getModifiers()));
        try(MockedStatic<PaperCompat> platform=mockStatic(PaperCompat.class)){
            assertFalse(NativeNavigationBridge.supportsRetainedHuskFlight());
            platform.when(PaperCompat::isPaper).thenReturn(true);
            assertTrue(NativeNavigationBridge.supportsRetainedHuskFlight());
            platform.when(PaperCompat::isFolia).thenReturn(true);
            assertFalse(NativeNavigationBridge.supportsRetainedHuskFlight());
        }
    }
    @Test void actualVersionLocalDriveJumpFieldIsAnIntegerHandle()throws Exception{
        assertEquals(int.class,LivingEntity.class.getDeclaredField("noJumpDelay").getType());
        var type=Class.forName(NativeMoveBridge.class.getName()+"$DriveAccess");
        var field=type.getDeclaredField("JUMP_DELAY");field.setAccessible(true);
        assertEquals(int.class,((VarHandle)field.get(null)).varType());
    }
}
