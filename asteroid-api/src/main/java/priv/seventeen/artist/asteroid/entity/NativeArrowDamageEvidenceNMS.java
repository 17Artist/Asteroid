package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

/**
 * Optional fixed-runtime, read-only evidence for an already-running native Arrow impact.
 * Never invokes hurt, constructs a source/event, predicts arrow damage, or resets combat state.
 * Consumers must retain their own exact single-event/native-return receipt.
 */
public interface NativeArrowDamageEvidenceNMS {
    ArrowSources freezeArrowDamageSources(Plugin consumer, Plugin nativeOwner);

    interface ArrowSources extends AutoCloseable {
        /** Capture exact native arrow/target/shooter before the consumer invokes native impact once. */
        ArrowInvocation bind(Arrow arrow, LivingEntity target);
        boolean current();
        @Override void close();
    }

    interface ArrowInvocation extends AutoCloseable {
        /** Owner/tag/shooter loss is permanent. Native removal of a spent arrow alone is permitted. */
        boolean current();
        /** Frozen real arrow holder tag bit; meaningful only while current(). */
        boolean bypassesCooldown();
        /** Exact event target, direct arrow, causing shooter and native holder, never DamageCause alone. */
        boolean matches(EntityDamageByEntityEvent event);
        /**
         * Read the first matching real event's immutable original BASE as the native float raw.
         * A different event or unsupported numeric value permanently invalidates this invocation.
         * There is no raw/no-hit result when native hurt emitted no event.
         */
        float originalRaw(EntityDamageByEntityEvent event);
        @Override void close();
    }
}
