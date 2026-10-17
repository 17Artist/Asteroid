package priv.seventeen.artist.asteroid.internal.mount;

import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.Input;
import java.util.concurrent.atomic.AtomicReference;

/** One bounded slot; release and publish are atomic so in-flight input cannot resurrect a rider. */
final class MountInputMailbox {
    private record State(boolean closed, Input input) {}
    private final AtomicReference<State> state = new AtomicReference<>(new State(false, Input.EMPTY));
    Input read() { return state.get().input(); }
    boolean closed() { return state.get().closed(); }
    boolean publish(Input input) {
        State previous = state.get();
        while (!previous.closed()) {
            if (input.sequence() <= previous.input().sequence()) return false;
            if (state.compareAndSet(previous, new State(false, input))) return true;
            previous = state.get();
        }
        return false;
    }
    void close() { state.set(new State(true, Input.EMPTY)); }
}
