package priv.seventeen.artist.asteroid.internal.movement;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Exact field names from each target dev bundle; never selects a native integer by ordinal. */
public final class NativeDriveFields {
    private NativeDriveFields(){}
    public static VarHandle integer(Class<?> owner,String mojangName,String spigotName){
        for(String name:new String[]{mojangName,spigotName})try{
            var field=owner.getDeclaredField(name);if(field.getType()!=int.class)continue;
            return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).unreflectVarHandle(field);
        }catch(NoSuchFieldException ignored){}
        catch(IllegalAccessException error){throw new IllegalStateException("Native driving field is inaccessible",error);}
        throw new IllegalStateException("Native driving field is unavailable: "+mojangName);
    }
}
