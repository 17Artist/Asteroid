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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PacketEvent {

    private final Player player;
    private final Object packet;
    private final PacketDirection direction;
    private boolean cancelled;

    private PacketType cachedType;
    private PacketFields cachedFields;

    public PacketEvent(@NotNull Player player, @NotNull Object packet, @NotNull PacketDirection direction) {
        this.player = player;
        this.packet = packet;
        this.direction = direction;
    }

    @NotNull
    public Player getPlayer() { return player; }

    @NotNull
    public Object getPacket() { return packet; }

    @NotNull
    public PacketDirection getDirection() { return direction; }

    @NotNull
    public String getPacketName() { return packet.getClass().getSimpleName(); }

    public boolean isCancelled() { return cancelled; }

    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public boolean is(@NotNull PacketType type) {
        return type.matches(packet);
    }

    @Nullable
    public PacketType type() {
        if (cachedType == null) {
            cachedType = PacketType.fromPacket(packet);
        }
        return cachedType;
    }

    @NotNull
    public PacketFields fields() {
        if (cachedFields == null) {
            cachedFields = PacketFields.of(packet);
        }
        return cachedFields;
    }

    public <T> T read(@NotNull String semanticName, @NotNull Class<T> type) {
        PacketType pt = type();
        if (pt == null) {
            throw new IllegalStateException("No PacketType registered for " + getPacketName());
        }
        PacketType.FieldDescriptor fd = pt.field(semanticName);
        if (fd == null) {
            throw new IllegalArgumentException("No field '" + semanticName + "' in " + pt.id());
        }
        return (T) fields().readTyped(fd.type(), fd.typeIndex());
    }

    public void write(@NotNull String semanticName, @NotNull Object value) {
        PacketType pt = type();
        if (pt == null) {
            throw new IllegalStateException("No PacketType registered for " + getPacketName());
        }
        PacketType.FieldDescriptor fd = pt.field(semanticName);
        if (fd == null) {
            throw new IllegalArgumentException("No field '" + semanticName + "' in " + pt.id());
        }
        fields().writeTyped(fd.type(), fd.typeIndex(), value);
    }
}
