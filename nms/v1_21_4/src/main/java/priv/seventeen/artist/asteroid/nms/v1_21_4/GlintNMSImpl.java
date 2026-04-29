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
package priv.seventeen.artist.asteroid.nms.v1_21_4;

import org.bukkit.inventory.meta.ItemMeta;
import priv.seventeen.artist.asteroid.item.IGlintNMS;

public class GlintNMSImpl implements IGlintNMS {

    @Override
    public void setGlint(ItemMeta meta, boolean glint) {
        meta.setEnchantmentGlintOverride(glint);
    }

    @Override
    public void removeGlint(ItemMeta meta) {
        meta.setEnchantmentGlintOverride(null);
    }

    @Override
    public boolean hasGlint(ItemMeta meta) {
        return meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
    }
}
