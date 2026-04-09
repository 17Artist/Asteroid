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

import priv.seventeen.artist.asteroid.entity.CustomEntity;

public class HitboxAbility implements Ability {

    private double width;
    private double height;
    private double offsetX, offsetY, offsetZ;
    private CustomEntity boundEntity;

    public HitboxAbility(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public String id() { return "hitbox"; }

    @Override
    public void onAttach(CustomEntity entity) {
        this.boundEntity = entity;
        entity.nmsSetSize(width, height);
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            entity.nmsSetOffset(offsetX, offsetY, offsetZ);
        }
    }

    @Override
    public void onDetach(CustomEntity entity) {
        this.boundEntity = null;
    }

    public void setSize(CustomEntity entity, double width, double height) {
        this.width = width;
        this.height = height;
        entity.nmsSetSize(width, height);
    }

    public void setOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        if (boundEntity != null) {
            boundEntity.nmsSetOffset(x, y, z);
        }
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
}
