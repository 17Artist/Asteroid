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

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;

public class ItemTag extends LinkedHashMap<String, ItemTagData> {

    public interface Bridge {

        ItemTag read(ItemStack item);

        ItemStack write(ItemStack item, ItemTag tag);
    }

    private static Bridge bridge;

    public static void setBridge(Bridge bridge) {
        ItemTag.bridge = bridge;
    }

    public static ItemTag fromItemStack(ItemStack item) {
        if (bridge == null) throw new IllegalStateException("ItemTag bridge not initialized");
        return bridge.read(item);
    }

    public ItemStack saveTo(ItemStack item) {
        if (bridge == null) throw new IllegalStateException("ItemTag bridge not initialized");
        return bridge.write(item, this);
    }

    public ItemTagData getDeep(String path) {
        String[] keys = path.split("\\.");
        ItemTag current = this;
        for (int i = 0; i < keys.length - 1; i++) {
            ItemTagData data = current.get(keys[i]);
            if (data == null || data.getType() != ItemTagType.COMPOUND) return null;
            current = data.asCompound();
        }
        return current.get(keys[keys.length - 1]);
    }

    public void putDeep(String path, ItemTagData data) {
        String[] keys = path.split("\\.");
        ItemTag current = this;
        for (int i = 0; i < keys.length - 1; i++) {
            ItemTagData existing = current.get(keys[i]);
            if (existing == null || existing.getType() != ItemTagType.COMPOUND) {
                ItemTag child = new ItemTag();
                current.put(keys[i], ItemTagData.of(child));
                current = child;
            } else {
                current = existing.asCompound();
            }
        }
        current.put(keys[keys.length - 1], data);
    }

    public void removeDeep(String path) {
        String[] keys = path.split("\\.");
        ItemTag current = this;
        for (int i = 0; i < keys.length - 1; i++) {
            ItemTagData data = current.get(keys[i]);
            if (data == null || data.getType() != ItemTagType.COMPOUND) return;
            current = data.asCompound();
        }
        current.remove(keys[keys.length - 1]);
    }

    public void putByte(String key, byte value) { put(key, ItemTagData.of(value)); }
    public void putShort(String key, short value) { put(key, ItemTagData.of(value)); }
    public void putInt(String key, int value) { put(key, ItemTagData.of(value)); }
    public void putLong(String key, long value) { put(key, ItemTagData.of(value)); }
    public void putFloat(String key, float value) { put(key, ItemTagData.of(value)); }
    public void putDouble(String key, double value) { put(key, ItemTagData.of(value)); }
    public void putString(String key, String value) { put(key, ItemTagData.of(value)); }
    public void putByteArray(String key, byte[] value) { put(key, ItemTagData.of(value)); }
    public void putIntArray(String key, int[] value) { put(key, ItemTagData.of(value)); }
    public void putLongArray(String key, long[] value) { put(key, ItemTagData.of(value)); }
    public void putCompound(String key, ItemTag value) { put(key, ItemTagData.of(value)); }
    public void putList(String key, ItemTagList value) { put(key, value); }
    public void putBoolean(String key, boolean value) { put(key, ItemTagData.ofBoolean(value)); }

    public void putAny(String key, Object value) { put(key, ItemTagData.toNBT(value)); }

    public void putDeepAny(String path, Object value) { putDeep(path, ItemTagData.toNBT(value)); }

    public byte getByte(String key) { ItemTagData d = get(key); return d != null ? d.asByte() : 0; }
    public short getShort(String key) { ItemTagData d = get(key); return d != null ? d.asShort() : 0; }
    public int getInt(String key) { ItemTagData d = get(key); return d != null ? d.asInt() : 0; }
    public long getLong(String key) { ItemTagData d = get(key); return d != null ? d.asLong() : 0; }
    public float getFloat(String key) { ItemTagData d = get(key); return d != null ? d.asFloat() : 0; }
    public double getDouble(String key) { ItemTagData d = get(key); return d != null ? d.asDouble() : 0; }
    public String getString(String key) { ItemTagData d = get(key); return d != null ? d.asString() : ""; }
    public boolean getBoolean(String key) { ItemTagData d = get(key); return d != null && d.asBoolean(); }
    public ItemTag getCompound(String key) { ItemTagData d = get(key); return d != null ? d.asCompound() : new ItemTag(); }
    public ItemTagList getTagList(String key) { ItemTagData d = get(key); return d != null ? d.asList() : new ItemTagList(); }

    public ItemTag deepClone() {
        ItemTag copy = new ItemTag();
        this.forEach((k, v) -> copy.put(k, v.clone()));
        return copy;
    }
}
