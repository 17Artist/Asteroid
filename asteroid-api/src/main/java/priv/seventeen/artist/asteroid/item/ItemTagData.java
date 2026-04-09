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
package priv.seventeen.artist.asteroid.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemTagData {

    private final ItemTagType type;
    private Object data;

    public ItemTagData(ItemTagType type, Object data) {
        this.type = type;
        this.data = data;
    }

    public ItemTagData(byte v) { this(ItemTagType.BYTE, v); }
    public ItemTagData(short v) { this(ItemTagType.SHORT, v); }
    public ItemTagData(int v) { this(ItemTagType.INT, v); }
    public ItemTagData(long v) { this(ItemTagType.LONG, v); }
    public ItemTagData(float v) { this(ItemTagType.FLOAT, v); }
    public ItemTagData(double v) { this(ItemTagType.DOUBLE, v); }
    public ItemTagData(String v) { this(ItemTagType.STRING, v); }
    public ItemTagData(byte[] v) { this(ItemTagType.BYTE_ARRAY, v); }
    public ItemTagData(int[] v) { this(ItemTagType.INT_ARRAY, v); }
    public ItemTagData(long[] v) { this(ItemTagType.LONG_ARRAY, v); }
    public ItemTagData(ItemTagList v) { this(ItemTagType.LIST, v); }
    public ItemTagData(ItemTag v) { this(ItemTagType.COMPOUND, v); }

    public static ItemTagData of(byte v) { return new ItemTagData(v); }
    public static ItemTagData of(short v) { return new ItemTagData(v); }
    public static ItemTagData of(int v) { return new ItemTagData(v); }
    public static ItemTagData of(long v) { return new ItemTagData(v); }
    public static ItemTagData of(float v) { return new ItemTagData(v); }
    public static ItemTagData of(double v) { return new ItemTagData(v); }
    public static ItemTagData of(String v) { return new ItemTagData(v); }
    public static ItemTagData of(byte[] v) { return new ItemTagData(v); }
    public static ItemTagData of(int[] v) { return new ItemTagData(v); }
    public static ItemTagData of(long[] v) { return new ItemTagData(v); }
    public static ItemTagData of(ItemTag v) { return new ItemTagData(v); }
    public static ItemTagData of(ItemTagList v) { return new ItemTagData(v); }
    public static ItemTagData ofList(List<ItemTagData> v) { return new ItemTagData(new ItemTagList(v)); }
    public static ItemTagData ofBoolean(boolean v) { return new ItemTagData((byte) (v ? 1 : 0)); }
    public static ItemTagData ofEnd() { return new ItemTagData(ItemTagType.END, 0); }

    public static ItemTagData toNBT(Object obj) {
        if (obj == null) return ofEnd();
        if (obj instanceof ItemTagData d) return d;
        if (obj instanceof Byte v) return of(v);
        if (obj instanceof Short v) return of(v);
        if (obj instanceof Integer v) return of(v);
        if (obj instanceof Long v) return of(v);
        if (obj instanceof Float v) return of(v);
        if (obj instanceof Double v) return of(v);
        if (obj instanceof String v) return of(v);
        if (obj instanceof byte[] v) return of(v);
        if (obj instanceof int[] v) return of(v);
        if (obj instanceof long[] v) return of(v);
        if (obj instanceof Boolean v) return ofBoolean(v);
        if (obj instanceof List<?> list) {
            ItemTagList tagList = new ItemTagList();
            for (Object item : list) tagList.add(toNBT(item));
            return tagList;
        }
        if (obj instanceof Map<?, ?> map) {
            ItemTag tag = new ItemTag();
            map.forEach((k, v) -> tag.put(k.toString(), toNBT(v)));
            return new ItemTagData(tag);
        }
        if (obj instanceof ConfigurationSection section) {
            return new ItemTagData(fromConfigSection(section));
        }
        throw new IllegalArgumentException("Unsupported NBT type: " + obj + " (" + obj.getClass().getName() + ")");
    }

    public static ItemTag fromConfigSection(ConfigurationSection section) {
        ItemTag tag = new ItemTag();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection sub) {
                tag.put(key, new ItemTagData(fromConfigSection(sub)));
            } else {
                tag.put(key, toNBT(value));
            }
        }
        return tag;
    }

    public ItemTagType getType() { return type; }
    public Object unsafeData() { return data; }
    protected void setData(Object data) { this.data = data; }

    public byte asByte() { return ((Number) data).byteValue(); }
    public short asShort() { return ((Number) data).shortValue(); }
    public int asInt() { return ((Number) data).intValue(); }
    public long asLong() { return ((Number) data).longValue(); }
    public float asFloat() { return ((Number) data).floatValue(); }
    public double asDouble() { return ((Number) data).doubleValue(); }
    public String asString() { return data.toString(); }
    public byte[] asByteArray() { return (byte[]) data; }
    public int[] asIntArray() { return (int[]) data; }
    public long[] asLongArray() { return (long[]) data; }
    public boolean asBoolean() { return asByte() != 0; }
    public ItemTag asCompound() { return (ItemTag) data; }

    public ItemTagList asList() {
        if (data instanceof ItemTagList list) return list;
        return ItemTagList.of(data);
    }

    @Override
    public ItemTagData clone() {
        return switch (type) {
            case END -> ofEnd();
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, STRING -> new ItemTagData(type, data);
            case BYTE_ARRAY -> of(asByteArray().clone());
            case INT_ARRAY -> of(asIntArray().clone());
            case LONG_ARRAY -> of(asLongArray().clone());
            case LIST -> asList().clone();
            case COMPOUND -> {
                ItemTag copy = new ItemTag();
                asCompound().forEach((k, v) -> copy.put(k, v.clone()));
                yield new ItemTagData(copy);
            }
        };
    }

    @Override
    public String toString() {
        return "ItemTagData{type=" + type + ", data=" + data + "}";
    }
}
