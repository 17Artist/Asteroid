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
package priv.seventeen.artist.asteroid.attribute;

import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public interface IAttributeItemNMS {

    /**
     * 向 ItemMeta 添加属性修饰符
     *
     * @param meta      物品 Meta
     * @param attribute 属性名称（如 "minecraft:generic.max_health"）
     * @param name      修饰符名称/key
     * @param amount    修饰符数值
     * @param operation 操作类型（0=ADD_VALUE, 1=ADD_MULTIPLIED_BASE, 2=ADD_MULTIPLIED_TOTAL）
     * @param slot      装备槽位（如 "hand", "head", "chest", "legs", "feet", "offhand"，null 表示所有槽位）
     */
    void addModifier(@NotNull ItemMeta meta, @NotNull String attribute, @NotNull String name,
                     double amount, int operation, @org.jetbrains.annotations.Nullable String slot);

    /**
     * 从 ItemMeta 移除指定属性的修饰符
     *
     * @param meta      物品 Meta
     * @param attribute 属性名称
     */
    void removeModifier(@NotNull ItemMeta meta, @NotNull String attribute);
}
