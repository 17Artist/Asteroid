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

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;

import java.util.Optional;

public class ItemStackNMSImpl implements IItemStackNMS {

    @Override
    public String item2Json(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(itemStack);
        return nms.save(CraftRegistry.getMinecraftRegistry()).toString();
    }

    // 1.20.5：数据组件(components)格式引入时的数据版本；旧数据未内嵌 DataVersion，以此为下界跑数据修复。
    private static final int MIN_COMPONENT_DATA_VERSION = 3837;

    @Override
    public ItemStack json2Item(String json) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(json);
            var registry = CraftRegistry.getMinecraftRegistry();
            Optional<net.minecraft.world.item.ItemStack> parsed =
                    net.minecraft.world.item.ItemStack.parse(registry, tag);
            if (parsed.isEmpty()) {
                // 直接解码失败：多为旧版本(如 1.21.5 之前 attribute_modifiers 等组件仍带 show_in_tooltip)
                // 序列化的数据，用 DataFixerUpper 升级到当前版本后重试。
                parsed = net.minecraft.world.item.ItemStack.parse(registry, upgradeItemTag(tag));
            }
            return CraftItemStack.asBukkitCopy(parsed.orElseThrow());
        } catch (CommandSyntaxException e) {
            throw new RuntimeException("Failed to parse item JSON", e);
        }
    }

    private static CompoundTag upgradeItemTag(CompoundTag tag) {
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        Dynamic<Tag> input =
                new Dynamic<>(NbtOps.INSTANCE, tag);
        Dynamic<Tag> out = DataFixers.getDataFixer()
                .update(References.ITEM_STACK, input, MIN_COMPONENT_DATA_VERSION, current);
        return (CompoundTag) out.getValue();
    }
}
