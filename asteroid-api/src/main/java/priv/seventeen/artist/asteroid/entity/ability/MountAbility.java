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

public class MountAbility implements TickableAbility {

    public enum MountType {
        NONE,

        GROUND,

        FLY,

        BOAT,

        DIVING,

        CAR
    }

    private MountType mountType;
    private float moveSpeed;
    private float flyUpSpeed;
    private float flyDownSpeed;
    private float boatTurnSpeed;

    public MountAbility(MountType mountType) {
        this(mountType, 0.3F, 0.08F, 0.06F, 3.0F);
    }

    public MountAbility(MountType mountType, float moveSpeed) {
        this(mountType, moveSpeed, 0.08F, 0.06F, 3.0F);
    }

    public MountAbility(MountType mountType, float moveSpeed, float flyUpSpeed, float flyDownSpeed, float boatTurnSpeed) {
        this.mountType = mountType;
        this.moveSpeed = moveSpeed;
        this.flyUpSpeed = flyUpSpeed;
        this.flyDownSpeed = flyDownSpeed;
        this.boatTurnSpeed = boatTurnSpeed;
    }

    @Override
    public String id() { return "mount"; }

    @Override
    public void onAttach(CustomEntity entity) {
        entity.nmsSetMountType(mountType, moveSpeed, flyUpSpeed, flyDownSpeed, boatTurnSpeed);
    }

    @Override
    public void onDetach(CustomEntity entity) {
        entity.nmsSetMountType(MountType.NONE, 0, 0, 0, 0);
    }

    @Override
    public void tick(CustomEntity entity) {

    }

    public MountType getMountType() { return mountType; }

    public void setMountType(CustomEntity entity, MountType type) {
        this.mountType = type;
        entity.nmsSetMountType(type, moveSpeed, flyUpSpeed, flyDownSpeed, boatTurnSpeed);
    }

    public void setSpeed(CustomEntity entity, float moveSpeed, float flyUpSpeed, float flyDownSpeed, float boatTurnSpeed) {
        this.moveSpeed = moveSpeed;
        this.flyUpSpeed = flyUpSpeed;
        this.flyDownSpeed = flyDownSpeed;
        this.boatTurnSpeed = boatTurnSpeed;
        entity.nmsSetMountType(mountType, moveSpeed, flyUpSpeed, flyDownSpeed, boatTurnSpeed);
    }

    public float getMoveSpeed() { return moveSpeed; }
    public float getFlyUpSpeed() { return flyUpSpeed; }
    public float getFlyDownSpeed() { return flyDownSpeed; }
    public float getBoatTurnSpeed() { return boatTurnSpeed; }
}
