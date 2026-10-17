package priv.seventeen.artist.asteroid.internal.movement;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** The fixed three-stage implementation used by every native Port; no NMS execution claim. */
class NativeMotionRestoreTest {
    static final class Restore {
        final Object receipt;final Method method;
        Restore(){try{Class<?> c=Class.forName("priv.seventeen.artist.asteroid.internal.movement.NativeMotionRestore");receipt=c.getConstructor().newInstance();method=c.getMethod("restore",Runnable.class,Runnable.class,Runnable.class);}
            catch(ReflectiveOperationException e){throw new AssertionError("Missing independently retryable native restore receipt",e);}}
        void run(Runnable neutral,Runnable first,Runnable second){try{method.invoke(receipt,neutral,first,second);}
            catch(InvocationTargetException e){NativeMotionCleanupTest.raise(e.getCause());}
            catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    }
    @Test void normalOrderAndIdempotence(){var receipt=new Restore();var steps=new ArrayList<Integer>();
        receipt.run(()->steps.add(0),()->steps.add(1),()->steps.add(2));receipt.run(()->fail("repeat neutral"),()->fail("repeat first"),()->fail("repeat second"));
        assertEquals(List.of(0,1,2),steps);
    }
    @Test void firstFailureDoesNotSkipEitherIndependentRestore(){var receipt=new Restore();var steps=new ArrayList<Integer>();var error=new IllegalStateException("neutral");
        assertSame(error,assertThrows(IllegalStateException.class,()->receipt.run(()->{steps.add(0);throw error;},()->steps.add(1),()->steps.add(2))));
        receipt.run(()->steps.add(3),()->fail("first already restored"),()->fail("second already restored"));assertEquals(List.of(0,1,2,3),steps);
    }
    @Test void secondFailureRetriesOnlySecondStage(){var receipt=new Restore();var steps=new ArrayList<Integer>();var error=new AssertionError("second");
        assertSame(error,assertThrows(AssertionError.class,()->receipt.run(()->steps.add(0),()->{steps.add(1);throw error;},()->steps.add(2))));
        receipt.run(()->fail("neutral repeated"),()->steps.add(3),()->fail("last repeated"));assertEquals(List.of(0,1,2,3),steps);
    }
    @Test void errorAfterRuntimeKeepsBothAndAttemptsFinalStage(){var receipt=new Restore();var first=new NativeMotionCleanupTest.NoSuppression("no suppression");var second=new AssertionError("fatal native");var last=new AtomicInteger();
        var thrown=assertThrows(Error.class,()->receipt.run(()->{throw first;},()->{throw second;},last::incrementAndGet));
        assertTrue(NativeMotionCleanupTest.contains(thrown,first));assertTrue(NativeMotionCleanupTest.contains(thrown,second));assertEquals(1,last.get());assertEquals(0,first.getSuppressed().length);
        receipt.run(()->{},()->{},()->fail("successful final stage repeated"));
    }
    @Test void repeatedSameThrowableNeverSelfSuppresses(){var receipt=new Restore();var failure=new NativeMotionCleanupTest.NoSuppression("same");
        assertSame(failure,assertThrows(NativeMotionCleanupTest.NoSuppression.class,()->receipt.run(()->{throw failure;},()->{throw failure;},()->{throw failure;})));
        assertEquals(0,failure.getSuppressed().length);receipt.run(()->{},()->{},()->{});
    }
    @Test void reentryCannotMarkOuterUnfinishedStageSuccessful(){var receipt=new Restore();var stages=new ArrayList<Integer>();
        assertThrows(IllegalStateException.class,()->receipt.run(()->receipt.run(()->fail("nested"),()->fail("nested"),()->fail("nested")),()->stages.add(1),()->stages.add(2)));
        receipt.run(()->stages.add(0),()->fail("first repeated"),()->fail("second repeated"));assertEquals(List.of(1,2,0),stages);
    }
    @Test void nativeIdentityMustBeRecheckedOnEachUnfinishedRetry(){var receipt=new Restore();Object ours=new Object(),previous=new Object(),foreign=new Object();var current=new AtomicReference<>(ours);var fail=new AtomicBoolean(true);var writes=new AtomicInteger();
        Runnable restore=()->{if(current.get()==ours){if(fail.get())throw new IllegalStateException("field write");current.set(previous);writes.incrementAndGet();}};
        assertThrows(IllegalStateException.class,()->receipt.run(()->{},restore,()->{}));current.set(foreign);fail.set(false);
        receipt.run(()->fail("neutral repeated"),restore,()->fail("last repeated"));assertSame(foreign,current.get());assertEquals(0,writes.get());
    }
    @Test void alreadyRestoredStageCannotRunAgainstAReusedPreviousObject(){var receipt=new Restore();Object ours=new Object(),previous=new Object();var current=new AtomicReference<>(ours);var writes=new AtomicInteger();var fault=new AtomicBoolean(true);
        Runnable first=()->{if(current.get()==ours){current.set(previous);writes.incrementAndGet();}};
        assertThrows(IllegalStateException.class,()->receipt.run(()->{},first,()->{if(fault.get())throw new IllegalStateException("other controller");}));
        current.set(ours);fault.set(false);receipt.run(()->fail("neutral repeated"),first,()->{});assertSame(ours,current.get());assertEquals(1,writes.get());
    }
}
