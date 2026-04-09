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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class ItemTagList extends ItemTagData implements List<ItemTagData> {

    private final List<ItemTagData> value;

    public ItemTagList() {
        super(ItemTagType.LIST, null);
        this.value = new ArrayList<>();
    }

    public ItemTagList(List<ItemTagData> list) {
        super(ItemTagType.LIST, null);
        this.value = new ArrayList<>(list);
    }

    public static ItemTagList of(Object data) {
        if (data instanceof ItemTagList list) return list;
        if (data instanceof List<?> list) {
            ItemTagList result = new ItemTagList();
            for (Object item : list) {
                if (item instanceof ItemTagData d) result.add(d);
            }
            return result;
        }
        return new ItemTagList();
    }

    @Override
    public ItemTagList asList() { return this; }

    @Override
    public ItemTagList clone() {
        ItemTagList copy = new ItemTagList();
        for (ItemTagData item : value) {
            copy.add(item.clone());
        }
        return copy;
    }

    @Override public int size() { return value.size(); }
    @Override public boolean isEmpty() { return value.isEmpty(); }
    @Override public boolean contains(Object o) { return value.contains(o); }
    @Override public Iterator<ItemTagData> iterator() { return value.iterator(); }
    @Override public Object[] toArray() { return value.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return value.toArray(a); }
    @Override public boolean add(ItemTagData data) { return value.add(data); }
    @Override public boolean remove(Object o) { return value.remove(o); }
    @Override public boolean containsAll(Collection<?> c) { return value.containsAll(c); }
    @Override public boolean addAll(Collection<? extends ItemTagData> c) { return value.addAll(c); }
    @Override public boolean addAll(int index, Collection<? extends ItemTagData> c) { return value.addAll(index, c); }
    @Override public boolean removeAll(Collection<?> c) { return value.removeAll(c); }
    @Override public boolean retainAll(Collection<?> c) { return value.retainAll(c); }
    @Override public void clear() { value.clear(); }
    @Override public ItemTagData get(int index) { return value.get(index); }
    @Override public ItemTagData set(int index, ItemTagData element) { return value.set(index, element); }
    @Override public void add(int index, ItemTagData element) { value.add(index, element); }
    @Override public ItemTagData remove(int index) { return value.remove(index); }
    @Override public int indexOf(Object o) { return value.indexOf(o); }
    @Override public int lastIndexOf(Object o) { return value.lastIndexOf(o); }
    @Override public ListIterator<ItemTagData> listIterator() { return value.listIterator(); }
    @Override public ListIterator<ItemTagData> listIterator(int index) { return value.listIterator(index); }
    @Override public List<ItemTagData> subList(int fromIndex, int toIndex) { return value.subList(fromIndex, toIndex); }

    @Override
    public String toString() {
        return value.toString();
    }
}
