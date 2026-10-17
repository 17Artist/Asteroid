package priv.seventeen.artist.asteroid.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/** Optional owner-thread movement capability; no tick loop, packets or Blink dependency. */
public interface NativeStepNMS {
    double MAX_ABSOLUTE_STEP = 16;

    /** Raw attribute base (or the legacy float field), before vanilla modifiers/clamping. */
    double baseStepHeight(Entity entity);
    /** The value actually read by native movement, including vanilla modifiers/clamping. */
    double stepHeight(Entity entity);
    /**
     * Apply one explicit setting. It deliberately persists after model removal, matching MEG.
     * The returned transaction may be rolled back once if publication fails. A rollback only
     * restores an unchanged native value, and never re-registers a removed entity.
     * On modern versions non-living hosts have no step attribute and this is a no-op.
     */
    StepChange changeStepHeight(Entity entity, double height);

    interface StepChange {
        boolean changed();
        boolean rollback();
    }

    StepChange NO_CHANGE = new StepChange() {
        public boolean changed() { return false; }
        public boolean rollback() {
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Step height requires the entity thread");
            return false;
        }
    };

    static void validate(double height) {
        if (!Double.isFinite(height) || Math.abs(height) > MAX_ABSOLUTE_STEP)
            throw new IllegalArgumentException("Step height must be finite and between -16 and 16 blocks");
    }
}

