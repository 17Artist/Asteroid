package priv.seventeen.artist.asteroid.internal.navigation;

import org.bukkit.*;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The real core lease registry. Native species/field evidence belongs to the fixed-Paper probe. */
class NativeNavigationColdPreparationTest {
    static void requireUnowned(Mob entity){
        try{NativeNavigationLease.class.getMethod("requireUnowned",Mob.class).invoke(null,entity);}
        catch(InvocationTargetException wrapped){Throwable e=wrapped.getCause();if(e instanceof Error error)throw error;if(e instanceof RuntimeException runtime)throw runtime;throw new AssertionError(e);}
        catch(ReflectiveOperationException missing){throw new AssertionError("Missing native cold-preparation fence",missing);}
    }
    static final class Rig implements AutoCloseable {
        final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);final MockedStatic<PaperCompat> paper=mockStatic(PaperCompat.class);
        final Plugin owner=mock(Plugin.class);final World world=mock(World.class);final Mob entity=mob(UUID.randomUUID());
        final NativeNavigationLease.Port port=mock(NativeNavigationLease.Port.class);NativeNavigationLease lease;
        Rig()throws Exception{
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            when(owner.isEnabled()).thenReturn(true);when(port.owned()).thenReturn(true);
            var field=NativeNavigationLease.class.getDeclaredField("budgetTick");field.setAccessible(true);when(port.tick()).thenReturn(Math.max(0,field.getLong(null))+1000);
        }
        Mob mob(UUID id){Mob m=mock(Mob.class);when(m.getUniqueId()).thenReturn(id);when(m.getWorld()).thenReturn(world);
            when(m.getEntityId()).thenReturn(1);when(m.isValid()).thenReturn(true);when(m.hasAI()).thenReturn(true);return m;}
        void open(){lease=NativeNavigationLease.open(owner,entity,port);}
        public void close(){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);paper.when(PaperCompat::isFolia).thenReturn(false);
            try{if(lease!=null)lease.close();}finally{paper.close();bukkit.close();}}
    }
    @Test void coldHostPassesWithoutInstallingOrQueryingAnyController()throws Exception{try(var r=new Rig()){
        requireUnowned(r.entity);verifyNoInteractions(r.port);
    }}
    @Test void liveExactLeaseRejectsWithoutClosingIt()throws Exception{try(var r=new Rig()){
        r.open();assertThrows(IllegalStateException.class,()->requireUnowned(r.entity));assertTrue(r.lease.active());verify(r.port,never()).restore();
    }}
    @Test void sameUuidReplacementAlsoRejectsButUnrelatedUuidPasses()throws Exception{try(var r=new Rig()){
        r.open();assertThrows(IllegalStateException.class,()->requireUnowned(r.mob(r.entity.getUniqueId())));requireUnowned(r.mob(UUID.randomUUID()));assertTrue(r.lease.active());
    }}
    @Test void provisionallyInstalledLeaseIsVisibleBeforeRegistrationCompletes()throws Exception{try(var r=new Rig()){
        doAnswer(c->{assertThrows(IllegalStateException.class,()->requireUnowned(r.entity));return null;}).when(r.port).install(any());r.open();assertTrue(r.lease.active());
    }}
    @Test void explicitSuccessfulCloseReleasesTheColdFence()throws Exception{try(var r=new Rig()){
        r.open();r.lease.close();requireUnowned(r.entity);verify(r.port).restore();
    }}
    @Test void wrongThreadAndFoliaRejectBeforeRegistryAdmission()throws Exception{try(var r=new Rig()){
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);assertThrows(IllegalStateException.class,()->requireUnowned(r.entity));
        r.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);r.paper.when(PaperCompat::isFolia).thenReturn(true);
        assertThrows(UnsupportedOperationException.class,()->requireUnowned(r.entity));verifyNoInteractions(r.port);
    }}
}
