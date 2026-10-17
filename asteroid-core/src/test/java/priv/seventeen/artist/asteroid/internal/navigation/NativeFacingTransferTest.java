package priv.seventeen.artist.asteroid.internal.navigation;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real one-call transfer algorithm. Native exact-identity predicates are separately byte/source reviewed. */
class NativeFacingTransferTest {
    static final class Rig {
        Object transfer;Method forward;boolean installed=true,current=true,restored=true;long epoch=1;
        int closes,calls;Runnable onCurrent,onClose,onCall;Throwable closeFailure,callFailure;
        Rig(){try{
            Class<?> type=Class.forName("priv.seventeen.artist.asteroid.internal.navigation.NativeFacingTransfer"),api=Class.forName(type.getName()+"$Port");
            Object p=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(x,m,a)->switch(m.getName()){
                case "installed"->installed;case "current"->{if(onCurrent!=null)onCurrent.run();yield current;}
                case "epoch"->epoch;case "restored"->restored&&!installed;
                case "close"->{closes++;current=false;if(onClose!=null)onClose.run();NativeNavigationFacingTest.raise(closeFailure);installed=false;yield null;}
                default->throw new AssertionError(m);
            });transfer=type.getConstructor(api).newInstance(p);forward=type.getMethod("forward",Runnable.class);
        }catch(ReflectiveOperationException e){throw new AssertionError("Missing exact LOOK transfer",e);}}
        boolean run(){try{return (boolean)forward.invoke(transfer,(Runnable)()->{calls++;if(onCall!=null)onCall.run();NativeNavigationFacingTest.raise(callFailure);});}
            catch(InvocationTargetException e){NativeNavigationFacingTest.raise(e.getCause());throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    }
    @Test void ordinaryExternalRequestRevokesThenRunsExactlyOnce(){var r=new Rig();r.onCall=()->{assertFalse(r.current);assertFalse(r.installed);assertEquals(1,r.closes);};assertTrue(r.run());assertEquals(1,r.calls);assertFalse(r.run());assertEquals(1,r.calls);}
    @Test void lateOldFrozenControlCannotTouchCurrentOrCloseReplacement(){var r=new Rig();r.installed=false;r.onCurrent=()->fail("stale reads");assertFalse(r.run());assertEquals(0,r.calls);assertEquals(0,r.closes);}
    @Test void alreadyRevokedAuthorityCannotForward(){var r=new Rig();r.current=false;assertFalse(r.run());assertEquals(0,r.closes);assertEquals(0,r.calls);}
    @Test void currentCheckChangingEpochRejectsBeforeClose(){var r=new Rig();r.onCurrent=()->r.epoch++;assertFalse(r.run());assertEquals(0,r.closes);assertEquals(0,r.calls);}
    @Test void currentCheckReplacingControllerRejectsBeforeClose(){var r=new Rig();r.onCurrent=()->r.installed=false;assertFalse(r.run());assertEquals(0,r.closes);}
    @Test void closeCallbackChangingEpochNeverForwards(){var r=new Rig();r.onClose=()->r.epoch++;assertFalse(r.run());assertEquals(1,r.closes);assertEquals(0,r.calls);}
    @Test void closeCallbackChangingExactLookNeverForwards(){var r=new Rig();r.onClose=()->r.restored=false;assertFalse(r.run());assertEquals(0,r.calls);}
    @Test void closeCallbackChangingEntityOrWorldPredicateNeverForwards(){var r=new Rig();r.onClose=()->r.restored=false;assertFalse(r.run());assertEquals(1,r.closes);assertEquals(0,r.calls);}
    @Test void recursiveCloseCallbackCannotForwardOrCloseAgain(){var r=new Rig();r.onClose=()->assertFalse(r.run());assertTrue(r.run());assertEquals(1,r.closes);assertEquals(1,r.calls);}
    @Test void forwardCallbackCannotReplayOnOldFrozenReference(){var r=new Rig();r.onCall=()->assertFalse(r.run());assertTrue(r.run());assertEquals(1,r.closes);assertEquals(1,r.calls);}
    @Test void closeErrorIsUnchangedAndRequestNeverRuns(){var r=new Rig();Error e=new AssertionError("native restoration");r.closeFailure=e;assertSame(e,assertThrows(Error.class,r::run));assertEquals(0,r.calls);assertFalse(r.run());assertEquals(1,r.closes);}
    @Test void closeRuntimeFailureIsUnchangedAndRequestNeverRuns(){var r=new Rig();RuntimeException e=new IllegalStateException("restore");r.closeFailure=e;assertSame(e,assertThrows(RuntimeException.class,r::run));assertEquals(0,r.calls);}
    @Test void exceptionAfterForeignRequestIsNotReplayedOrReclosed(){var r=new Rig();Error e=new AssertionError("foreign target setter");r.callFailure=e;assertSame(e,assertThrows(Error.class,r::run));assertEquals(1,r.calls);assertFalse(r.run());assertEquals(1,r.calls);assertEquals(1,r.closes);}
}
