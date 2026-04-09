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
package priv.seventeen.artist.asteroid.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import priv.seventeen.artist.asteroid.entity.ability.Ability;
import priv.seventeen.artist.asteroid.entity.ability.MountAbility;

import java.util.*;

public interface CustomEntity {

    int getEntityId();

    @NotNull
    UUID getUniqueId();

    @Nullable
    Entity getOwner();

    @NotNull
    LivingEntity getBukkitEntity();

    @NotNull
    Location getLocation();

    <T extends Ability> void addAbility(T ability);

    <T extends Ability> void removeAbility(Class<T> type);

    @Nullable
    <T extends Ability> T getAbility(Class<T> type);

    boolean hasAbility(Class<? extends Ability> type);

    Collection<Ability> getAbilities();

    void remove();

    boolean isRemoved();

    void nmsSetSize(double width, double height);

    void nmsSetOffset(double x, double y, double z);

    void nmsSetMountType(MountAbility.MountType type, float moveSpeed, float flyUpSpeed, float flyDownSpeed, float boatTurnSpeed);

    void nmsAddSeat(double x, double y, double z);

    void nmsAddPassenger(@Nullable Entity passenger);

    void nmsClearSeats();
}
