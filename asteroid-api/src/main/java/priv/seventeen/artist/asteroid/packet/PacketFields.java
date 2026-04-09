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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketFields {

    private static final Map<Class<?>, Field[]> FIELD_CACHE = new ConcurrentHashMap<>();

    private final Object packet;
    private final Field[] fields;
    private final boolean isRecord;
    private PacketType boundType;

    private PacketFields(Object packet) {
        this.packet = packet;
        this.fields = getFields(packet.getClass());
        this.isRecord = packet.getClass().isRecord();
    }

    PacketFields bindType(PacketType type) {
        this.boundType = type;
        return this;
    }

    public static PacketFields of(Object packet) {
        return new PacketFields(packet);
    }

    public boolean isRecord() { return isRecord; }

    public Object read(int index) {
        try {
            return fields[index].get(packet);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read field at index " + index + " from " + packet.getClass().getSimpleName(), e);
        }
    }

    public <T> T read(int index, Class<T> type) {
        return (T) read(index);
    }

    public PacketFields write(int index, Object value) {
        forceSet(fields[index], packet, value);
        return this;
    }

    public <T> T readTyped(Class<T> type, int typeIndex) {
        int count = 0;
        for (Field field : fields) {
            if (matchesType(field.getType(), type)) {
                if (count == typeIndex) {
                    try {
                        return (T) field.get(packet);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to read typed field", e);
                    }
                }
                count++;
            }
        }
        throw new IllegalArgumentException("No field of type " + type.getSimpleName() + " at index " + typeIndex);
    }

    public <T> PacketFields writeTyped(Class<T> type, int typeIndex, Object value) {
        int count = 0;
        for (Field field : fields) {
            if (matchesType(field.getType(), type)) {
                if (count == typeIndex) {
                    forceSet(field, packet, value);
                    return this;
                }
                count++;
            }
        }
        throw new IllegalArgumentException("No field of type " + type.getSimpleName() + " at index " + typeIndex);
    }

    public <T> T readNamed(String name) {

        for (Field field : fields) {
            if (field.getName().equals(name)) {
                try {
                    return (T) field.get(packet);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read field: " + name, e);
                }
            }
        }

        for (Field field : fields) {
            if (field.getName().contains(name)) {
                try {
                    return (T) field.get(packet);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read field: " + name, e);
                }
            }
        }
        throw new IllegalArgumentException("No field named '" + name + "' in " + packet.getClass().getSimpleName()
                + ". In obfuscated environments, use readTyped() or semantic API instead.");
    }

    public PacketFields writeNamed(String name, Object value) {
        for (Field field : fields) {
            if (field.getName().equals(name)) {
                forceSet(field, packet, value);
                return this;
            }
        }
        for (Field field : fields) {
            if (field.getName().contains(name)) {
                forceSet(field, packet, value);
                return this;
            }
        }
        throw new IllegalArgumentException("No field named '" + name + "' in " + packet.getClass().getSimpleName()
                + ". In obfuscated environments, use writeTyped() or semantic API instead.");
    }

    public int readInt(int typeIndex) { return readTyped(int.class, typeIndex); }

    public float readFloat(int typeIndex) { return readTyped(float.class, typeIndex); }

    public double readDouble(int typeIndex) { return readTyped(double.class, typeIndex); }

    public boolean readBoolean(int typeIndex) { return readTyped(boolean.class, typeIndex); }

    public String readString(int typeIndex) { return readTyped(String.class, typeIndex); }

    public PacketFields writeInt(int typeIndex, int value) { return writeTyped(int.class, typeIndex, value); }

    public PacketFields writeFloat(int typeIndex, float value) { return writeTyped(float.class, typeIndex, value); }

    public PacketFields writeDouble(int typeIndex, double value) { return writeTyped(double.class, typeIndex, value); }

    public PacketFields writeBoolean(int typeIndex, boolean value) { return writeTyped(boolean.class, typeIndex, value); }

    public PacketFields writeString(int typeIndex, String value) { return writeTyped(String.class, typeIndex, value); }

    public <T> T readSemantic(String semanticName) {
        PacketType.FieldDescriptor fd = requireField(semanticName);
        return (T) readTyped(fd.type(), fd.typeIndex());
    }

    public PacketFields writeSemantic(String semanticName, Object value) {
        PacketType.FieldDescriptor fd = requireField(semanticName);
        writeTyped(fd.type(), fd.typeIndex(), value);
        return this;
    }

    private PacketType.FieldDescriptor requireField(String name) {
        if (boundType == null) {
            throw new IllegalStateException("No PacketType bound. Use PacketBuilder.create(PacketType) to bind.");
        }
        PacketType.FieldDescriptor fd = boundType.field(name);
        if (fd == null) {
            throw new IllegalArgumentException("No field '" + name + "' in " + boundType.id());
        }
        return fd;
    }

    public String getPacketName() { return packet.getClass().getSimpleName(); }

    public Object getPacket() { return packet; }

    public int fieldCount() { return fields.length; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : fields) {
            try {
                map.put(field.getName() + "(" + field.getType().getSimpleName() + ")", field.get(packet));
            } catch (Exception e) {
                map.put(field.getName(), "<error>");
            }
        }
        return map;
    }

    @Override
    public String toString() {
        return getPacketName() + toMap();
    }

    private static Field[] getFields(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            List<Field> result = new ArrayList<>();

            List<Class<?>> hierarchy = new ArrayList<>();
            Class<?> cur = c;
            while (cur != null && cur != Object.class) {
                hierarchy.add(cur);
                cur = cur.getSuperclass();
            }

            for (int i = hierarchy.size() - 1; i >= 0; i--) {
                for (Field f : hierarchy.get(i).getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    result.add(f);
                }
            }
            return result.toArray(new Field[0]);
        });
    }

    private static void forceSet(Field field, Object target, Object value) {
        try {

            field.set(target, value);
        } catch (IllegalAccessException e) {

            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                        field.getDeclaringClass(), MethodHandles.lookup());
                VarHandle vh = lookup.findVarHandle(
                        field.getDeclaringClass(), field.getName(), field.getType());
                vh.set(target, value);
            } catch (Exception ex) {

                try {
                    var unsafeClass = Class.forName("sun.misc.Unsafe");
                    var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                    unsafeField.setAccessible(true);
                    var unsafe = unsafeField.get(null);
                    long offset = (long) unsafeClass.getMethod("objectFieldOffset", Field.class)
                            .invoke(unsafe, field);

                    java.lang.reflect.Method putMethod;
                    try {
                        putMethod = unsafeClass.getMethod("putReference", Object.class, long.class, Object.class);
                    } catch (NoSuchMethodException nsm) {
                        putMethod = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
                    }
                    putMethod.invoke(unsafe, target, offset, value);
                } catch (Exception fatal) {
                    throw new RuntimeException(
                            "Cannot write field '" + field.getName() + "' in " + target.getClass().getSimpleName()
                                    + " (record/final). Use PacketBuilder.create(Class, args...) with constructor instead.",
                            fatal);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write field '" + field.getName() + "'", e);
        }
    }

    private static boolean matchesType(Class<?> fieldType, Class<?> queryType) {
        if (queryType.isAssignableFrom(fieldType)) return true;

        if (fieldType == int.class && queryType == int.class) return true;
        if (fieldType == long.class && queryType == long.class) return true;
        if (fieldType == short.class && queryType == short.class) return true;
        if (fieldType == byte.class && queryType == byte.class) return true;
        if (fieldType == float.class && queryType == float.class) return true;
        if (fieldType == double.class && queryType == double.class) return true;
        if (fieldType == boolean.class && queryType == boolean.class) return true;
        if (fieldType == char.class && queryType == char.class) return true;

        if (fieldType == int.class && queryType == Integer.class) return true;
        if (fieldType == Integer.class && queryType == int.class) return true;
        if (fieldType == long.class && queryType == Long.class) return true;
        if (fieldType == Long.class && queryType == long.class) return true;
        if (fieldType == short.class && queryType == Short.class) return true;
        if (fieldType == Short.class && queryType == short.class) return true;
        if (fieldType == byte.class && queryType == Byte.class) return true;
        if (fieldType == Byte.class && queryType == byte.class) return true;
        if (fieldType == float.class && queryType == Float.class) return true;
        if (fieldType == Float.class && queryType == float.class) return true;
        if (fieldType == double.class && queryType == Double.class) return true;
        if (fieldType == Double.class && queryType == double.class) return true;
        if (fieldType == boolean.class && queryType == Boolean.class) return true;
        if (fieldType == Boolean.class && queryType == boolean.class) return true;
        if (fieldType == char.class && queryType == Character.class) return true;
        if (fieldType == Character.class && queryType == char.class) return true;
        return false;
    }
}
