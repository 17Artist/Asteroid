package priv.seventeen.artist.asteroid.internal;

import io.netty.channel.Channel;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Tests-only RED can compile before the new core reader exists. Reflection here is not a runtime API. */
class PacketChannelAccessTest {
    static final class Network {
        private Channel renamed;
        static Channel staticDecoy;
        private Object objectDecoy;
        Network(Channel channel){renamed=channel;objectDecoy=channel;}
    }
    static class CommonListener {
        protected Network obfuscated;
        static Network staticDecoy;
        CommonListener(Network connection){obfuscated=connection;}
    }
    static final class GameListener extends CommonListener {
        private Object unrelated;
        GameListener(Network connection){super(connection);unrelated=connection;}
    }
    static final class LegacyListener {public Network h;LegacyListener(Network value){h=value;}}
    static final class WrongDeclaredType {Object connection;WrongDeclaredType(Object value){connection=value;}}
    static final class Duplicate {Network first,second;}
    static final class Shadow extends CommonListener {Network second;Shadow(){super(null);}}
    static class ChannelBase {private Channel q;ChannelBase(Channel channel){q=channel;}}
    static final class InheritedNetwork extends ChannelBase {InheritedNetwork(Channel channel){super(channel);}}
    static final class InheritedListener {InheritedNetwork c;InheritedListener(InheritedNetwork network){c=network;}}
    static final class MissingChannel {Object channel;}
    static final class MissingChannelListener {MissingChannel c;}
    static final class DuplicateChannel {Channel first,second;}
    static final class DuplicateChannelListener {DuplicateChannel c;}
    static final class NoConnection {static Network only;}

    static RuntimeException failure(Throwable error){
        if(error instanceof RuntimeException runtime)return runtime;
        if(error instanceof Error fatal)throw fatal;
        throw new AssertionError(error);
    }
    static Class<?> accessType(){
        try{return Class.forName("priv.seventeen.artist.asteroid.internal.PacketChannelAccess");}
        catch(ClassNotFoundException error){throw new AssertionError("Missing cached native packet channel reader",error);}
    }
    static Object access(Class<?> listener,Class<?> network){
        try{return accessType().getConstructor(Class.class,Class.class).newInstance(listener,network);}
        catch(InvocationTargetException error){throw failure(error.getCause());}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    static Channel channel(Object access,Object listener){
        try{return (Channel)accessType().getMethod("channel",Object.class).invoke(access,listener);}
        catch(InvocationTargetException error){throw failure(error.getCause());}
        catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    static Object access(){return access(GameListener.class,Network.class);}

    @Test void protectedInheritedConnectionAndPrivateRenamedChannelAreReadable(){
        Channel expected=mock(Channel.class);assertSame(expected,channel(access(),new GameListener(new Network(expected))));
    }
    @Test void oldPublicObfuscatedFieldDoesNotNeedItsName(){
        Channel expected=mock(Channel.class);assertSame(expected,channel(access(LegacyListener.class,Network.class),new LegacyListener(new Network(expected))));
    }
    @Test void inheritedPrivateChannelIsResolvedByExactType(){
        Channel expected=mock(Channel.class);assertSame(expected,channel(access(InheritedListener.class,InheritedNetwork.class),new InheritedListener(new InheritedNetwork(expected))));
    }
    @Test void separateListenersDoNotShareCachedInstances(){
        Object reader=access();Channel first=mock(Channel.class),second=mock(Channel.class);
        assertSame(first,channel(reader,new GameListener(new Network(first))));
        assertSame(second,channel(reader,new GameListener(new Network(second))));
    }
    @Test void connectionReplacementIsVisibleWithoutResolvingFieldsAgain(){
        Object reader=access();Channel first=mock(Channel.class),second=mock(Channel.class);var listener=new GameListener(new Network(first));
        assertSame(first,channel(reader,listener));listener.obfuscated=new Network(second);assertSame(second,channel(reader,listener));
    }
    @Test void channelReplacementIsVisibleWithoutCachingAChannel(){
        Object reader=access();Channel first=mock(Channel.class),second=mock(Channel.class);var network=new Network(first);var listener=new GameListener(network);
        assertSame(first,channel(reader,listener));network.renamed=second;assertSame(second,channel(reader,listener));
    }
    @Test void staticFieldsAndDifferentDeclaredTypesDoNotCauseAmbiguity(){
        Channel expected=mock(Channel.class);CommonListener.staticDecoy=new Network(mock(Channel.class));Network.staticDecoy=mock(Channel.class);
        try{assertSame(expected,channel(access(),new GameListener(new Network(expected))));}
        finally{CommonListener.staticDecoy=null;Network.staticDecoy=null;}
    }
    @Test void absentInstanceConnectionFailsInsteadOfReturningNull(){
        var error=assertThrows(IllegalStateException.class,()->access(NoConnection.class,Network.class));assertTrue(error.getMessage().contains("Missing native packet field"));
    }
    @Test void objectTypedDecoyIsNotAcceptedFromItsCurrentValue(){
        assertThrows(IllegalStateException.class,()->access(WrongDeclaredType.class,Network.class));
    }
    @Test void ambiguousConnectionFieldsFailClosed(){
        var error=assertThrows(IllegalStateException.class,()->access(Duplicate.class,Network.class));assertTrue(error.getMessage().contains("Ambiguous native packet field"));
    }
    @Test void hiddenSuperclassConnectionAlsoCountsAsAmbiguity(){
        assertThrows(IllegalStateException.class,()->access(Shadow.class,Network.class));
    }
    @Test void absentChannelFailsAtColdResolution(){
        assertThrows(IllegalStateException.class,()->access(MissingChannelListener.class,MissingChannel.class));
    }
    @Test void ambiguousChannelsFailAtColdResolution(){
        assertThrows(IllegalStateException.class,()->access(DuplicateChannelListener.class,DuplicateChannel.class));
    }
    @Test void nullListenerIsAnExplicitFailure(){assertThrows(IllegalStateException.class,()->channel(access(),null));}
    @Test void nullConnectionIsAnExplicitFailure(){assertThrows(IllegalStateException.class,()->channel(access(),new GameListener(null)));}
    @Test void nullChannelIsAnExplicitFailure(){assertThrows(IllegalStateException.class,()->channel(access(),new GameListener(new Network(null))));}
    @Test void wrongListenerTypeDoesNotBypassTypedCache(){assertThrows(ClassCastException.class,()->channel(access(),new LegacyListener(new Network(mock(Channel.class)))));}
    @Test void readerRetainsOnlyTypesAndTwoFinalFieldHandles(){
        Object reader=access();var fields=Arrays.stream(reader.getClass().getDeclaredFields()).filter(f->!Modifier.isStatic(f.getModifiers())).toList();
        assertEquals(2,fields.stream().filter(f->f.getType()==Field.class).count());
        assertTrue(fields.stream().allMatch(f->Modifier.isFinal(f.getModifiers())&&(f.getType()==Class.class||f.getType()==Field.class)));
        Channel expected=mock(Channel.class);var listener=new GameListener(new Network(expected));
        for(int i=0;i<1000;i++)assertSame(expected,channel(reader,listener));
    }
}
