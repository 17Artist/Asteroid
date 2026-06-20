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
package priv.seventeen.artist.asteroid.packet;

import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.AsteroidAPI;

public final class Packets {

    private Packets() {}

    public static Object entityDestroy(int... entityIds) {
        return PacketBuilder.create(PacketType.Play.Server.ENTITY_DESTROY, entityIds)
                .getPacket();
    }

    public static Object entityVelocity(int entityId, int vx, int vy, int vz) {
        PacketType type = PacketType.Play.Server.ENTITY_VELOCITY;
        Class<?> clazz = type.resolve();
        // 现代版本 ClientboundSetEntityMotionPacket 是 record(int id, Vec3 movement)，
        // final 字段无法反射写入，改用规范构造器。vx/vy/vz 为协议编码值(×8000)，还原为 block/tick。
        try {
            Class<?> vec3 = Class.forName("net.minecraft.world.phys.Vec3");
            Object movement = vec3.getConstructor(double.class, double.class, double.class)
                    .newInstance(vx / 8000.0, vy / 8000.0, vz / 8000.0);
            java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor(int.class, vec3);
            ctor.setAccessible(true);
            return ctor.newInstance(entityId, movement);
        } catch (Throwable modern) {
            // 旧版本以独立 int 字段存储速度，回退到字段写入。
            PacketFields fields = PacketBuilder.create(type);
            fields.writeSemantic("entityId", entityId);
            if (type.field("velocityX") != null) {
                fields.writeSemantic("velocityX", vx);
                fields.writeSemantic("velocityY", vy);
                fields.writeSemantic("velocityZ", vz);
            }
            return fields.getPacket();
        }
    }

    public static Object entityHeadRotation(int entityId, float yaw) {
        byte byteYaw = (byte) (yaw * 256.0F / 360.0F);
        return PacketBuilder.create(PacketType.Play.Server.ENTITY_HEAD_ROTATION)
                .writeSemantic("entityId", entityId)
                .writeSemantic("headYaw", byteYaw)
                .getPacket();
    }

    public static Object entityStatus(int entityId, byte status) {
        return PacketBuilder.create(PacketType.Play.Server.ENTITY_STATUS)
                .writeSemantic("entityId", entityId)
                .writeSemantic("eventId", status)
                .getPacket();
    }

    public static Object setPassengers(int vehicleId, int... passengerIds) {
        return PacketBuilder.create(PacketType.Play.Server.SET_PASSENGERS)
                .writeSemantic("vehicleId", vehicleId)
                .writeSemantic("passengerIds", passengerIds)
                .getPacket();
    }

    public static Object setCamera(int entityId) {
        return PacketBuilder.create(PacketType.Play.Server.SET_CAMERA)
                .writeSemantic("entityId", entityId)
                .getPacket();
    }

    public static void sendEntityDestroy(Player player, int... entityIds) {
        AsteroidAPI.getPacketHandler().sendPacket(player, entityDestroy(entityIds));
    }

    public static void sendEntityVelocity(Player player, int entityId, int vx, int vy, int vz) {
        AsteroidAPI.getPacketHandler().sendPacket(player, entityVelocity(entityId, vx, vy, vz));
    }

    public static void sendEntityHeadRotation(Player player, int entityId, float yaw) {
        AsteroidAPI.getPacketHandler().sendPacket(player, entityHeadRotation(entityId, yaw));
    }

    public static void sendEntityStatus(Player player, int entityId, byte status) {
        AsteroidAPI.getPacketHandler().sendPacket(player, entityStatus(entityId, status));
    }

    public static void sendSetPassengers(Player player, int vehicleId, int... passengerIds) {
        AsteroidAPI.getPacketHandler().sendPacket(player, setPassengers(vehicleId, passengerIds));
    }

    public static void sendSetCamera(Player player, int entityId) {
        AsteroidAPI.getPacketHandler().sendPacket(player, setCamera(entityId));
    }
}
