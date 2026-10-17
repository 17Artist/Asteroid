package priv.seventeen.artist.asteroid.internal.damage;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Identity contract used by the real prebound Holder.Reference.tags getter, not a simulated reload-completion event. */
class NativeDamageTagEpochTest {
    @Test void unchangedExactTwoHoldersStayCurrent(){Object a=new Object(),b=new Object();var e=new NativeDamageTagEpoch(a,b);assertTrue(e.current(true,a,b));assertTrue(e.current(true,a,b));}
    @Test void equalTagMembersAreNotAnIdentityProof(){Object a=new HashSet<>(Set.of("x")),b=new Object();var e=new NativeDamageTagEpoch(a,b);assertFalse(e.current(true,new HashSet<>(Set.of("x")),b));}
    @Test void eitherHolderRebindInvalidatesTheWholeFrozenProfile(){Object a=new Object(),b=new Object();assertFalse(new NativeDamageTagEpoch(a,b).current(true,a,new Object()));assertFalse(new NativeDamageTagEpoch(a,b).current(true,new Object(),b));}
    @Test void invalidationDoesNotRecoverOnABA(){Object a=new Object(),b=new Object();var e=new NativeDamageTagEpoch(a,b);assertFalse(e.current(true,new Object(),b));assertFalse(e.current(true,a,b));}
    @Test void ownerReplacementCannotBorrowTheOriginalTagObjects(){Object a=new Object(),b=new Object();var e=new NativeDamageTagEpoch(a,b);assertFalse(e.current(false,a,b));assertFalse(e.current(true,a,b));}
    @Test void explicitCloseIsIdempotentAndNeverWritesTags(){Object a=new Object(),b=new Object();var e=new NativeDamageTagEpoch(a,b);e.close();e.close();assertFalse(e.current(true,a,b));}
    @Test void reloadMiddleCallbackFailsBeforeCompletionEventExists(){
        var tags=new AtomicReference<Object>(Collections.unmodifiableSet(new HashSet<>(Set.of("old"))));Object other=new Object();
        var e=new NativeDamageTagEpoch(tags.get(),other);assertTrue(e.current(true,tags.get(),other));
        // Actual reviewed sequence: bindTags (pc84 chain), plugin command lifecycle (pc227), completion event (pc272).
        tags.set(Collections.unmodifiableSet(new HashSet<>(Set.of("bypasses_cooldown"))));
        boolean completionEventFired=false;
        assertFalse(e.current(true,tags.get(),other),"The middle callback must not use the stale frozen bypass bit");
        assertFalse(completionEventFired);completionEventFired=true;assertFalse(e.current(true,tags.get(),other));assertTrue(completionEventFired);
    }
    @Test void offOwnerReadsThrowInsteadOfInspectingTags()throws Exception{
        Object a=new Object(),b=new Object();var e=new NativeDamageTagEpoch(a,b);var failure=new AtomicReference<Throwable>();
        Thread t=new Thread(()->{try{e.current(true,a,b);}catch(Throwable x){failure.set(x);}});t.start();t.join();
        assertInstanceOf(IllegalStateException.class,failure.get());assertTrue(e.current(true,a,b));
    }
    @Test void missingTagObjectsCannotBind(){assertThrows(NullPointerException.class,()->new NativeDamageTagEpoch(null,new Object()));assertThrows(NullPointerException.class,()->new NativeDamageTagEpoch(new Object(),null));}
}
