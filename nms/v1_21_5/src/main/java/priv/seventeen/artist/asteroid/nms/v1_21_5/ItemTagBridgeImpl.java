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
package priv.seventeen.artist.asteroid.nms.v1_21_5;

import net.minecraft.nbt.*;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.item.ItemTagData;
import priv.seventeen.artist.asteroid.item.ItemTagType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemTagBridgeImpl implements ItemTag.Bridge {

    @Override
    public ItemTag read(ItemStack item) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        CustomData customData = nms.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return new ItemTag();
        }
        CompoundTag tag = customData.copyTag();
        return compoundToItemTag(tag);
    }

    @Override
    public ItemStack write(ItemStack item, ItemTag tag) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(item);
        CompoundTag compound = itemTagToCompound(tag);
        nms.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(compound));
        return CraftItemStack.asBukkitCopy(nms);
    }

    private ItemTag compoundToItemTag(CompoundTag compound) {
        ItemTag tag = new ItemTag();
        for (String key : compound.keySet()) {
            Tag nbtTag = compound.get(key);
            if (nbtTag != null) {
                tag.put(key, nbtToItemTagData(nbtTag));
            }
        }
        return tag;
    }

    private ItemTagData nbtToItemTagData(Tag nbt) {
        return switch (nbt.getId()) {
            case Tag.TAG_BYTE -> ItemTagData.of(((ByteTag) nbt).byteValue());
            case Tag.TAG_SHORT -> ItemTagData.of(((ShortTag) nbt).shortValue());
            case Tag.TAG_INT -> ItemTagData.of(((IntTag) nbt).intValue());
            case Tag.TAG_LONG -> ItemTagData.of(((LongTag) nbt).longValue());
            case Tag.TAG_FLOAT -> ItemTagData.of(((FloatTag) nbt).floatValue());
            case Tag.TAG_DOUBLE -> ItemTagData.of(((DoubleTag) nbt).doubleValue());
            case Tag.TAG_STRING -> ItemTagData.of(((StringTag) nbt).value());
            case Tag.TAG_BYTE_ARRAY -> ItemTagData.of(((ByteArrayTag) nbt).getAsByteArray());
            case Tag.TAG_INT_ARRAY -> ItemTagData.of(((IntArrayTag) nbt).getAsIntArray());
            case Tag.TAG_LONG_ARRAY -> ItemTagData.of(((LongArrayTag) nbt).getAsLongArray());
            case Tag.TAG_COMPOUND -> ItemTagData.of(compoundToItemTag((CompoundTag) nbt));
            case Tag.TAG_LIST -> {
                ListTag listTag = (ListTag) nbt;
                List<ItemTagData> list = new ArrayList<>(listTag.size());
                for (Tag entry : listTag) {
                    list.add(nbtToItemTagData(entry));
                }
                yield ItemTagData.ofList(list);
            }
            default -> ItemTagData.of(nbt.asString().orElse(""));
        };
    }

    private CompoundTag itemTagToCompound(ItemTag tag) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<String, ItemTagData> entry : tag.entrySet()) {
            compound.put(entry.getKey(), itemTagDataToNbt(entry.getValue()));
        }
        return compound;
    }

    private Tag itemTagDataToNbt(ItemTagData data) {
        return switch (data.getType()) {
            case BYTE -> ByteTag.valueOf(data.asByte());
            case SHORT -> ShortTag.valueOf(data.asShort());
            case INT -> IntTag.valueOf(data.asInt());
            case LONG -> LongTag.valueOf(data.asLong());
            case FLOAT -> FloatTag.valueOf(data.asFloat());
            case DOUBLE -> DoubleTag.valueOf(data.asDouble());
            case STRING -> StringTag.valueOf(data.asString());
            case BYTE_ARRAY -> new ByteArrayTag(data.asByteArray());
            case INT_ARRAY -> new IntArrayTag(data.asIntArray());
            case LONG_ARRAY -> new LongArrayTag(data.asLongArray());
            case COMPOUND -> itemTagToCompound(data.asCompound());
            case LIST -> {
                ListTag listTag = new ListTag();
                for (ItemTagData item : data.asList()) {
                    listTag.add(itemTagDataToNbt(item));
                }
                yield listTag;
            }
            case END -> net.minecraft.nbt.EndTag.INSTANCE;
        };
    }
}
