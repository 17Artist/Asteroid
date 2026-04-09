/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package priv.seventeen.artist.asteroid.packet;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketBuilder {

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    public static PacketFields create(PacketType type) {
        Class<?> clazz = type.resolve();
        return PacketFields.of(newInstance(clazz)).bindType(type);
    }

    public static PacketFields create(PacketType type, Object... args) {
        Class<?> clazz = type.resolve();
        return create(clazz, args).bindType(type);
    }

    public static PacketFields create(String simpleName) {
        Class<?> clazz = resolveClass(simpleName);
        return PacketFields.of(newInstance(clazz));
    }

    public static PacketFields createFull(String fullClassName) {
        try {
            Class<?> clazz = Class.forName(fullClassName);
            return PacketFields.of(newInstance(clazz));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Packet class not found: " + fullClassName, e);
        }
    }

    public static PacketFields create(Class<?> packetClass) {
        return PacketFields.of(newInstance(packetClass));
    }

    public static PacketFields create(Class<?> packetClass, Object... args) {
        try {
            Class<?>[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }
            try {
                Constructor<?> ctor = packetClass.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                return PacketFields.of(ctor.newInstance(args));
            } catch (NoSuchMethodException e) {
                for (Constructor<?> ctor : packetClass.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == args.length) {
                        try {
                            ctor.setAccessible(true);
                            return PacketFields.of(ctor.newInstance(args));
                        } catch (Exception ignored) {}
                    }
                }
                throw new RuntimeException("No matching constructor for " + packetClass.getSimpleName(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create packet: " + packetClass.getSimpleName(), e);
        }
    }

    private static final String[] SEARCH_PACKAGES = {
            "net.minecraft.network.protocol.game.",
            "net.minecraft.network.protocol.common.",
            "net.minecraft.network.protocol.login.",
            "net.minecraft.network.protocol.status.",
            "net.minecraft.network.protocol.handshake.",
            "net.minecraft.network.protocol.",
    };

    private static Class<?> resolveClass(String simpleName) {
        return CLASS_CACHE.computeIfAbsent(simpleName, name -> {
            for (String pkg : SEARCH_PACKAGES) {
                try {
                    return Class.forName(pkg + name);
                } catch (ClassNotFoundException ignored) {}
            }
            throw new RuntimeException("Cannot resolve packet class: " + name
                    + ". Try createFull() with the full class name.");
        });
    }

    private static Object newInstance(Class<?> clazz) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (NoSuchMethodException e) {
            try {
                var unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                var unsafe = unsafeField.get(null);
                var allocateMethod = unsafeClass.getMethod("allocateInstance", Class.class);
                return allocateMethod.invoke(unsafe, clazz);
            } catch (Exception ex) {
                throw new RuntimeException("Cannot instantiate " + clazz.getSimpleName()
                        + ". Use create(Class, Object...) with constructor arguments.", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + clazz.getSimpleName(), e);
        }
    }
}
