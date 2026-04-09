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
package priv.seventeen.artist.asteroid.nms.v1_21_3;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.asteroid.entity.CustomEntity;
import priv.seventeen.artist.asteroid.entity.CustomEntityFactory;

public class CustomEntityFactoryImpl implements CustomEntityFactory {

    @Override
    public CustomEntity create(@NotNull Entity owner, double width, double height) {
        Location loc = owner.getLocation();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();
        CustomEntityImpl entity = new CustomEntityImpl(level, owner);
        entity.setPos(loc.getX(), loc.getY(), loc.getZ());
        entity.nmsSetSize(width, height);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    public CustomEntity create(@NotNull Location location, double width, double height) {
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        CustomEntityImpl entity = new CustomEntityImpl(level);
        entity.setPos(location.getX(), location.getY(), location.getZ());
        entity.nmsSetSize(width, height);
        level.addFreshEntity(entity);
        return entity;
    }
}
