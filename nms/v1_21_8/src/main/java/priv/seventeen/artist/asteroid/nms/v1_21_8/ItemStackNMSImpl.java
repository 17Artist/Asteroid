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
package priv.seventeen.artist.asteroid.nms.v1_21_8;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;

public class ItemStackNMSImpl implements IItemStackNMS {

    @Override
    public String item2Json(ItemStack itemStack) {
        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(itemStack);
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, CraftRegistry.getMinecraftRegistry());
        DataResult<Tag> result = net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, nms);
        return result.getOrThrow().toString();
    }

    @Override
    public ItemStack json2Item(String json) {
        try {
            CompoundTag tag = TagParser.parseCompoundFully(json);
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, CraftRegistry.getMinecraftRegistry());
            DataResult<net.minecraft.world.item.ItemStack> result =
                    net.minecraft.world.item.ItemStack.CODEC.parse(ops, tag);
            net.minecraft.world.item.ItemStack nms = result.getOrThrow();
            return CraftItemStack.asBukkitCopy(nms);
        } catch (CommandSyntaxException e) {
            throw new RuntimeException("Failed to parse item JSON", e);
        }
    }
}
