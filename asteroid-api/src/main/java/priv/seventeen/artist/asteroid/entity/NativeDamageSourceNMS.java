package priv.seventeen.artist.asteroid.entity;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

/** Optional read-only capability. Does not create a DamageSource, invoke hurt, or modify an entity/tag. */
public interface NativeDamageSourceNMS {
    /** Fixed Paper implementation proof and exact host/consumer ownership are checked at binding.
     * A tag rebind permanently invalidates this snapshot, even before reload-completion callbacks.
     * All methods, including close, require the captured primary thread. */
    DirectSources freezeDirectDamageSources(Plugin consumer, Plugin nativeOwner);

    interface DirectSources extends AutoCloseable {
        SourceEpoch mobAttack();
        SourceEpoch playerAttack();
        /** O(1), no registry scan or reflective lookup. False never becomes true again. */
        boolean current();
        @Override void close();
    }

    interface SourceEpoch {
        String key();
        /** Value frozen from the real native holder; meaningful only while current(). */
        boolean bypassesCooldown();
        boolean current();
        /** Exact real event/source holder and direct/causing Craft entity handles. No source builder. */
        boolean matches(EntityDamageByEntityEvent event);
    }
}
