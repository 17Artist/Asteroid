package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeDriveProgram;
import priv.seventeen.artist.asteroid.entity.NativeMovementNMS;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeMotionCleanupAbiTest {
    static Object call(Object receiver,Class<?> api,String name,Class<?>[] signature,Object...args){
        try{return api.getMethod(name,signature).invoke(receiver,args);}
        catch(InvocationTargetException e){NativeMotionCleanupTest.raise(e.getCause());throw new AssertionError(e);}
        catch(ReflectiveOperationException e){throw new AssertionError("Missing explicit fail-closed ABI: "+name,e);}
    }
    @Test void oldMovementProviderDoesNotClaimCleanupAndRejectsShutdown(){
        var old=mock(NativeMovementNMS.class,CALLS_REAL_METHODS);
        assertEquals(false,call(old,NativeMovementNMS.class,"supportsMotionCleanup",new Class<?>[0]));
        assertThrows(UnsupportedOperationException.class,()->call(old,NativeMovementNMS.class,"closeMotionOwned",new Class<?>[]{Plugin.class},mock(Plugin.class)));
    }
    @Test void oldProgramProviderCannotAcceptReceiptContractOrInvokeReceiver(){
        var old=mock(NativeMovementNMS.class,CALLS_REAL_METHODS);var calls=new AtomicInteger();
        assertThrows(UnsupportedOperationException.class,()->call(old,NativeMovementNMS.class,"controlDriveProgramRetained",
            new Class<?>[]{Plugin.class,Entity.class,NativeDriveProgram.class,Consumer.class,Consumer.class},mock(Plugin.class),mock(Entity.class),(NativeDriveProgram)p->{},
            (Consumer<Throwable>)e->fail("failure callback"),(Consumer<NativeMovementNMS.DriveLease>)l->calls.incrementAndGet()));assertEquals(0,calls.get());
    }
    @Test void oldNavigationProviderCannotSilentlyUseTheLegacyAcquisition(){
        var old=mock(NativeNavigationNMS.class,CALLS_REAL_METHODS);var calls=new AtomicInteger();
        assertThrows(UnsupportedOperationException.class,()->call(old,NativeNavigationNMS.class,"controlNavigationRetained",
            new Class<?>[]{Plugin.class,Mob.class,Consumer.class},mock(Plugin.class),mock(Mob.class),(Consumer<NativeNavigationNMS.NavigationLease>)l->calls.incrementAndGet()));assertEquals(0,calls.get());
    }
}
