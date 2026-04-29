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
package priv.seventeen.artist.asteroid.skull;

import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ISkullNMS {

    /**
     * 通过 URL 或 Base64 设置头颅纹理
     *
     * @param meta       头颅 Meta
     * @param urlOrBase64 纹理 URL 或 Base64 编码的纹理数据
     */
    void setTexture(@NotNull SkullMeta meta, @NotNull String urlOrBase64);

    /**
     * 获取头颅纹理 URL
     *
     * @param meta 头颅 Meta
     * @return 纹理 URL，如果没有则返回 null
     */
    @Nullable
    String getTexture(@NotNull SkullMeta meta);
}
