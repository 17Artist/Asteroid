package priv.seventeen.artist.asteroid.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import java.util.function.Consumer;

/** Optional capability for authored child hitboxes. Never creates a client-tracked entity. */
public interface NativeDamageRelayNMS {
    /**
     * Creates one real, server-only LivingEntity. The caller owns its lifetime and admission budget.
     * It is excluded from ordinary projectile picking: precision routing must resolve the authored
     * part first, then invoke the normal native attack/impact against {@link NativeDamageRelay#entity()}.
     * Broad vanilla queries still see the entity, as required by explicit entity-backed hitboxes.
     */
    NativeDamageRelay createDamageRelay(LivingEntity owner,BoundingBox bounds,
                                       NativeDamageRelay.Handler handler,Consumer<Throwable> failure);
}
