package priv.seventeen.artist.asteroid.internal.navigation;

import java.util.Objects;

/** Internal one-call hand-back to the exact restored LOOK control; no replay queue or scheduler. */
public final class NativeFacingTransfer {
    public interface Port {
        boolean installed();
        boolean current();
        long epoch();
        void close();
        /** Exact previous LOOK + same parent/entity/world must be read afresh after close. */
        boolean restored();
    }
    private final Port port;
    private boolean transferring;
    public NativeFacingTransfer(Port port){this.port=Objects.requireNonNull(port);}
    public boolean forward(Runnable foreignRequest){
        Objects.requireNonNull(foreignRequest);
        if(transferring||!port.installed())return false;
        transferring=true;
        try {
            long epoch=port.epoch();
            if(!port.current()||!port.installed()||port.epoch()!=epoch)return false;
            port.close(); // failure retains cleanup in the original receipt and propagates unchanged
            if(!port.restored()||port.epoch()!=epoch)return false;
            foreignRequest.run();return true; // attempted once; even an exception is never replayed
        }finally{transferring=false;}
    }
}
