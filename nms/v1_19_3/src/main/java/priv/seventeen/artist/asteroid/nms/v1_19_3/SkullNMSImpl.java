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
package priv.seventeen.artist.asteroid.nms.v1_19_3;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.inventory.meta.SkullMeta;
import priv.seventeen.artist.asteroid.skull.ISkullNMS;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Collection;
import java.util.UUID;

public class SkullNMSImpl implements ISkullNMS {

    private static final Field PROFILE_FIELD;

    static {
        try {
            // CraftMetaSkull 在这些版本中有 profile 字段
            Class<?> craftMetaSkull = Class.forName("org.bukkit.craftbukkit.v1_19_R2.inventory.CraftMetaSkull");
            PROFILE_FIELD = craftMetaSkull.getDeclaredField("profile");
            PROFILE_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SkullNMS reflection", e);
        }
    }

    @Override
    public void setTexture(SkullMeta meta, String urlOrBase64) {
        try {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "asteroid");
            String base64;
            if (urlOrBase64.startsWith("http://") || urlOrBase64.startsWith("https://")) {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + urlOrBase64 + "\"}}}";
                base64 = Base64.getEncoder().encodeToString(json.getBytes());
            } else {
                base64 = urlOrBase64;
            }
            profile.getProperties().put("textures", new Property("textures", base64));
            PROFILE_FIELD.set(meta, profile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set skull texture", e);
        }
    }

    @Override
    public String getTexture(SkullMeta meta) {
        try {
            GameProfile profile = (GameProfile) PROFILE_FIELD.get(meta);
            if (profile == null) return null;
            Collection<Property> textures = profile.getProperties().get("textures");
            if (textures == null || textures.isEmpty()) return null;
            Property property = textures.iterator().next();
            String base64 = property.getValue();
            String json = new String(Base64.getDecoder().decode(base64));
            // 简单解析 URL
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
