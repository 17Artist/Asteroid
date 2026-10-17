package priv.seventeen.artist.asteroid.internal.movement;

import org.bukkit.Bukkit;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.internal.navigation.NativeNavigationLease;
import priv.seventeen.artist.asteroid.util.PaperCompat;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Explicit plugin shutdown only. Ordinary recovery closes the captured exact lease, never a UUID lookup. */
public final class NativeMotionCleanup {
    private NativeMotionCleanup() {}
    private static final Set<Plugin> CLOSING=Collections.newSetFromMap(new IdentityHashMap<>());
    public static void requireAdmission(Plugin owner) {
        Objects.requireNonNull(owner);
        if(CLOSING.contains(owner))throw new IllegalStateException("Native motion owner shutdown is in progress");
    }
    public static void closeAll(Plugin owner) { closeAll(owner,null); }
    /** One actual disable dispatch; every baked listener is marked before the first port callback. */
    public static void closeForDisable(PluginDisableEvent event) {
        Objects.requireNonNull(event);closeAll(event.getPlugin(),event);
    }
    private static void closeAll(Plugin owner,PluginDisableEvent event) {
        Objects.requireNonNull(owner);
        if(PaperCompat.isFolia()||!Bukkit.isPrimaryThread())throw new IllegalStateException("Native motion cleanup requires the owner thread");
        if(CLOSING.size()>=4||!CLOSING.add(owner))throw new IllegalStateException("Native motion owner shutdown is already in progress or too deeply nested");
        try {
            // Existing registries each cap at 256 including failed release. No new orphan queue.
            var navigation=NativeNavigationLease.ownedForCleanup(owner,event);
            var driving=NativeDriveLease.ownedForCleanup(owner,event);
            for(var lease:navigation)lease.revokeForCleanup();
            for(var lease:driving)lease.revokeForCleanup();
            Throwable failure=null;
            for(var lease:navigation)try{lease.close();}catch(RuntimeException|Error error){failure=combine(failure,error);}
            for(var lease:driving)try{lease.close();}catch(RuntimeException|Error error){failure=combine(failure,error);}
            rethrow(failure);
        } finally { CLOSING.remove(owner); }
    }
    /** Bounded call sites; never mutate provider-owned or suppression-disabled Throwables. */
    public static Throwable combine(Throwable first,Throwable next) {
        Objects.requireNonNull(next);if(first==null||first==next)return next;
        Throwable both=first instanceof Error||next instanceof Error
            ?new Error("Native motion operation and cleanup failed",first)
            :new IllegalStateException("Native motion operation and cleanup failed",first);
        both.addSuppressed(next);return both;
    }
    public static void rethrow(Throwable failure) {
        if(failure instanceof Error error)throw error;
        if(failure instanceof RuntimeException error)throw error;
        if(failure!=null)throw new IllegalStateException("Native motion cleanup failed",failure);
    }
}
