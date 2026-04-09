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
package priv.seventeen.artist.asteroid.entity.ability;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;
import priv.seventeen.artist.asteroid.entity.CustomEntity;

import java.util.ArrayList;
import java.util.List;

public class SeatAbility implements TickableAbility {

    public record Offset(double x, double y, double z) {}

    private final List<Offset> offsets = new ArrayList<>();
    private CustomEntity boundEntity;

    private int syncedCount = 0;

    public SeatAbility() {}

    @Override
    public String id() { return "seat"; }

    @Override
    public void onAttach(CustomEntity entity) {
        this.boundEntity = entity;

        for (int i = syncedCount; i < offsets.size(); i++) {
            Offset offset = offsets.get(i);
            entity.nmsAddSeat(offset.x(), offset.y(), offset.z());
        }
        syncedCount = offsets.size();
    }

    @Override
    public void onDetach(CustomEntity entity) {

        entity.nmsClearSeats();
        this.boundEntity = null;
        this.syncedCount = 0;
    }

    @Override
    public void tick(CustomEntity entity) {

    }

    public void addSeat(double x, double y, double z) {
        offsets.add(new Offset(x, y, z));
        if (boundEntity != null) {
            boundEntity.nmsAddSeat(x, y, z);
            syncedCount++;
        }
    }

    public void addSeat(Offset offset) {
        addSeat(offset.x(), offset.y(), offset.z());
    }

    public void addPassenger(CustomEntity entity, @Nullable Entity passenger) {
        entity.nmsAddPassenger(passenger);
    }

    public List<Offset> getOffsets() { return offsets; }
}
