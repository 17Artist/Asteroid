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
package priv.seventeen.artist.asteroid.nms.v1_20_6;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.asteroid.entity.ability.MountAbility.MountType;
import priv.seventeen.artist.asteroid.entity.ability.SeatAbility.Offset;

public class SeatEntityImpl extends ArmorStand {

    private final CustomEntityImpl parent;
    private final Offset offset;
    private final int seatIndex;

    public SeatEntityImpl(Level level, CustomEntityImpl parent, Offset offset) {
        super(EntityType.ARMOR_STAND, level);
        this.parent = parent;
        this.offset = offset;
        this.seatIndex = parent.getSeats().size();

        this.setInvisible(true);
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.setSilent(true);
        this.setMarker(true);
        this.persist = false;
    }

    @Override
    public void tick() {
        if (this.parent == null || this.parent.isRemoved()) {
            this.discard();
            return;
        }

        updatePosition();

        if (this.parent.getMountType() == MountType.DIVING) {
            this.getPassengers().forEach(passenger -> {
                if (passenger.getAirSupply() != passenger.getMaxAirSupply()) {
                    passenger.setAirSupply(passenger.getMaxAirSupply());
                }
            });
        }

    }

    public void updatePosition() {
        if (this.parent == null) return;

        Vec3 parentPos = this.parent.position();
        double yawRad = Math.toRadians(this.parent.getYRot());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double x = parentPos.x + offset.x() * cos - offset.z() * sin;
        double y = parentPos.y + offset.y();
        double z = parentPos.z + offset.x() * sin + offset.z() * cos;

        this.setPos(x, y, z);
        this.setYRot(this.parent.getYRot());
        this.setXRot(this.parent.getXRot());
    }

    @Override
    public boolean removePassenger(@NotNull Entity passenger) {
        if (this.parent != null && !this.parent.isRemoved()) {
            MountType mountType = this.parent.getMountType();

            if (mountType == MountType.FLY && !this.parent.onGround()) {
                return false;
            }

            if (mountType == MountType.DIVING && (passenger.isUnderWater() || !this.parent.isOnGroundCustom())) {
                return false;
            }
        }
        return super.removePassenger(passenger);
    }

    public boolean removePassenger(@NotNull Entity passenger, boolean b) {
        if (this.parent != null && !this.parent.isRemoved()) {
            MountType mountType = this.parent.getMountType();

            if (mountType == MountType.FLY && !this.parent.onGround()) {
                return false;
            }

            if (mountType == MountType.DIVING && (passenger.isUnderWater() || !this.parent.isOnGroundCustom())) {
                return false;
            }
        }
        return super.removePassenger(passenger, b);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}

    public CustomEntityImpl getParent() {
        return parent;
    }

    public Offset getOffset() {
        return offset;
    }

    public int getSeatIndex() {
        return seatIndex;
    }

    public boolean isDriverSeat() {
        return seatIndex == 0;
    }
}
