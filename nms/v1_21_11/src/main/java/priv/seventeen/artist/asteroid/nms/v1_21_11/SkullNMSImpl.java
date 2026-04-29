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
package priv.seventeen.artist.asteroid.nms.v1_21_11;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import priv.seventeen.artist.asteroid.skull.ISkullNMS;

import java.net.URI;
import java.net.URL;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;

public class SkullNMSImpl implements ISkullNMS {

    @Override
    public void setTexture(SkullMeta meta, String urlOrBase64) {
        try {
            // 1.20.6+ 使用反射写入 GameProfile，因为 PlayerProfile API 的 URL 设置有时不可靠
            // 但优先尝试 setOwnerProfile
            GameProfile profile = new GameProfile(UUID.randomUUID(), "asteroid");
            String base64;
            if (urlOrBase64.startsWith("http://") || urlOrBase64.startsWith("https://")) {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + urlOrBase64 + "\"}}}";
                base64 = Base64.getEncoder().encodeToString(json.getBytes());
            } else {
                base64 = urlOrBase64;
            }
            profile.properties().put("textures", new Property("textures", base64));
            // 通过反射写入 CraftMetaSkull.profile
            Class<?> craftMetaSkull = Class.forName("org.bukkit.craftbukkit.inventory.CraftMetaSkull");
            Field profileField = craftMetaSkull.getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set skull texture", e);
        }
    }

    @Override
    public String getTexture(SkullMeta meta) {
        try {
            Class<?> craftMetaSkull = Class.forName("org.bukkit.craftbukkit.inventory.CraftMetaSkull");
            Field profileField = craftMetaSkull.getDeclaredField("profile");
            profileField.setAccessible(true);
            GameProfile profile = (GameProfile) profileField.get(meta);
            if (profile == null) return null;
            Collection<Property> textures = profile.properties().get("textures");
            if (textures == null || textures.isEmpty()) return null;
            Property property = textures.iterator().next();
            String base64 = property.value();
            String json = new String(Base64.getDecoder().decode(base64));
            int urlStart = json.indexOf("\"url\":\"");
            if (urlStart == -1) return base64;
            urlStart += 7;
            int urlEnd = json.indexOf("\"", urlStart);
            if (urlEnd == -1) return base64;
            return json.substring(urlStart, urlEnd);
        } catch (Exception e) {
            return null;
        }
    }
}
