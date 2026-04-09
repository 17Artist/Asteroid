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
package priv.seventeen.artist.asteroid.nms.v1_18_2;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

public class EntityNMSImpl implements IEntityNMS {

    private static final VarHandle SIZE_HANDLE;
    private static final VarHandle HEIGHT_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    net.minecraft.world.entity.Entity.class, MethodHandles.lookup());
            SIZE_HANDLE = lookup.findVarHandle(net.minecraft.world.entity.Entity.class, "aZ", EntityDimensions.class);
            HEIGHT_HANDLE = lookup.findVarHandle(net.minecraft.world.entity.Entity.class, "ba", float.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void setSize(Entity entity, float width, float height) {
        if (entity instanceof CraftEntity ce) {
            net.minecraft.world.entity.Entity nms = ce.getHandle();
            if (width <= 0 || height <= 0) { nms.refreshDimensions(); return; }
            SIZE_HANDLE.set(nms, EntityDimensions.scalable(width, height));
            HEIGHT_HANDLE.set(nms, height * (1.74F / 2F));
            nms.setPos(nms.position());
        }
    }

    @Override
    public void doWithSeenBy(Entity entity, Consumer<Player> consumer) {
        if (entity.getWorld() instanceof CraftWorld cw) {
            ServerLevel level = cw.getHandle();
            ChunkMap.TrackedEntity tracker = level.getChunkSource().chunkMap.entityMap.get(entity.getEntityId());
            if (tracker == null) return;
            tracker.seenBy.forEach(p -> consumer.accept(p.getPlayer().getBukkitEntity()));
        }
    }

    @Override
    public void setPosition(Entity entity, double x, double y, double z) {
        if (entity instanceof CraftEntity ce) ce.getHandle().setPos(x, y, z);
    }

    @Override
    public void setRotation(Entity entity, float yaw, float pitch) {
        if (entity instanceof CraftEntity ce) {
            ce.getHandle().setXRot(pitch);
            ce.getHandle().setYRot(yaw);
        }
    }

    @Override
    public boolean isMoveKeyDown(Player player) {
        if (player instanceof CraftPlayer cp) {
            return cp.getHandle().xxa != 0 || cp.getHandle().zza != 0;
        }
        return false;
    }
}
