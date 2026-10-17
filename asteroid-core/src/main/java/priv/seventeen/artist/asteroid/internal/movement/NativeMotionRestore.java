package priv.seventeen.artist.asteroid.internal.movement;

import java.util.Objects;

/** Fixed native neutral/first-controller/second-controller release receipt, not a task queue. */
public final class NativeMotionRestore {
    private boolean neutralDone,firstDone,secondDone,running;
    public void restore(Runnable neutral,Runnable first,Runnable second) {
        Objects.requireNonNull(neutral);Objects.requireNonNull(first);Objects.requireNonNull(second);
        if(running)throw new IllegalStateException("Native controller restoration is already in progress");
        running=true;Throwable failure=null;
        try {
            if(!neutralDone)try{neutral.run();neutralDone=true;}catch(RuntimeException|Error error){failure=NativeMotionCleanup.combine(failure,error);}
            if(!firstDone)try{first.run();firstDone=true;}catch(RuntimeException|Error error){failure=NativeMotionCleanup.combine(failure,error);}
            if(!secondDone)try{second.run();secondDone=true;}catch(RuntimeException|Error error){failure=NativeMotionCleanup.combine(failure,error);}
        } finally { running=false; }
        NativeMotionCleanup.rethrow(failure);
    }
}
