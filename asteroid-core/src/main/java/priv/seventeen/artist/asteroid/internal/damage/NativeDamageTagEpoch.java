package priv.seventeen.artist.asteroid.internal.damage;

import java.util.Objects;

/** Constant-size, owner-thread, monotonic read-only tag identity witness. */
public final class NativeDamageTagEpoch {
    private final Thread thread = Thread.currentThread();
    private final Object first, second;
    private boolean lost;

    public NativeDamageTagEpoch(Object first, Object second) {
        this.first = Objects.requireNonNull(first); this.second = Objects.requireNonNull(second);
    }

    public boolean current(boolean ownerCurrent, Object firstNow, Object secondNow) {
        check();
        if (!ownerCurrent || first != firstNow || second != secondNow) lost = true;
        return !lost;
    }

    public void close() { check(); lost = true; }
    private void check() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Damage tag epoch requires its owner thread");
    }
}
