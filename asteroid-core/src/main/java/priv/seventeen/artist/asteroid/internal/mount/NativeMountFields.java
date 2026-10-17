package priv.seventeen.artist.asteroid.internal.mount;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Exact mapped-name pair from the target dev bundle; never guesses by boolean-field ordinal. */
public final class NativeMountFields {
    private NativeMountFields() {}
    public static VarHandle booleanField(Class<?> owner, String mojangName, String spigotName) {
        for (String name : new String[]{mojangName, spigotName}) {
            try {
                var field = owner.getDeclaredField(name);
                if (field.getType() != boolean.class) continue;
                return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).unreflectVarHandle(field);
            } catch (NoSuchFieldException ignored) {
                // One namespace is expected to be absent.
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Native passenger floating field is inaccessible", error);
            }
        }
        throw new IllegalStateException("Native passenger floating field is unavailable");
    }
}
