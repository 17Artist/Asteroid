package priv.seventeen.artist.asteroid.internal;

import io.netty.channel.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

/** Two cached, exact-type instance fields; never retains a listener, connection or player. */
public final class PacketChannelAccess<L,C> {
    private final Class<L> listenerType;
    private final Class<C> connectionType;
    private final Field connectionField;
    private final Field channelField;

    /** Resolve once in the selected version provider's static holder, in the runtime namespace. */
    public PacketChannelAccess(Class<L> listenerType, Class<C> connectionType) {
        this.listenerType=Objects.requireNonNull(listenerType,"listenerType");
        this.connectionType=Objects.requireNonNull(connectionType,"connectionType");
        connectionField=uniqueInstanceField(listenerType,connectionType);
        channelField=uniqueInstanceField(connectionType,Channel.class);
    }

    public Channel channel(L listener) {
        if(listener==null)throw new IllegalStateException("Native packet listener is unavailable");
        try {
            C connection=connectionType.cast(connectionField.get(listenerType.cast(listener)));
            if(connection==null)throw new IllegalStateException("Native packet connection is unavailable");
            Channel channel=Channel.class.cast(channelField.get(connection));
            if(channel==null)throw new IllegalStateException("Native packet channel is unavailable");
            return channel;
        } catch(IllegalAccessException error) {
            throw new IllegalStateException("Cached native packet channel access failed",error);
        }
    }

    private static Field uniqueInstanceField(Class<?> owner, Class<?> expected) {
        Field found=null;
        for(Class<?> cursor=owner;cursor!=null;cursor=cursor.getSuperclass()) {
            for(Field field:cursor.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())||field.getType()!=expected)continue;
                if(found!=null)throw new IllegalStateException("Ambiguous native packet field: "+owner.getName()+" -> "+expected.getName());
                found=field;
            }
        }
        if(found==null)throw new IllegalStateException("Missing native packet field: "+owner.getName()+" -> "+expected.getName());
        if(!found.trySetAccessible())throw new IllegalStateException("Inaccessible native packet field: "+owner.getName()+" -> "+expected.getName());
        return found;
    }
}
