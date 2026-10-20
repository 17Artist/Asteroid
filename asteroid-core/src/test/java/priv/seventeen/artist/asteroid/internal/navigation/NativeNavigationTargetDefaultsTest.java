package priv.seventeen.artist.asteroid.internal.navigation;

import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import priv.seventeen.artist.asteroid.entity.NativeNavigationNMS;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** An old provider must never turn the explicit preserving-target mode into strict fallback. */
class NativeNavigationTargetDefaultsTest {
    private static final class OldProvider implements NativeNavigationNMS {
        int strictCalls;
        @Override public NavigationLease controlNavigation(Plugin owner,Mob host){
            strictCalls++;throw new AssertionError("No fallback to strict ownership");
        }
    }
    @Test void oldProviderRejectsAllNewCapabilitiesBeforeCallingStrictOrRetain(){
        var provider=new OldProvider();var retained=new AtomicBoolean();
        assertFalse(provider.supportsTargetPreservingNavigation());
        assertFalse(provider.supportsTargetPreservingRetainedHuskFlight());
        assertThrows(UnsupportedOperationException.class,()->provider.requireTargetPreservingFlightGoals(null,null));
        assertThrows(UnsupportedOperationException.class,()->provider.controlNavigationPreservingTargets(null,null));
        assertThrows(UnsupportedOperationException.class,()->provider.controlNavigationPreservingTargetsRetained(null,null,receipt->retained.set(true)));
        assertThrows(UnsupportedOperationException.class,()->provider.prepareSkeletonPreservingTargets(null,null));
        assertFalse(retained.get());assertEquals(0,provider.strictCalls);
    }
}
