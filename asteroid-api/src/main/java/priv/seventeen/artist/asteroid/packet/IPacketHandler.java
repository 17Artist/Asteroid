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
import org.bukkit.plugin.Plugin;

public interface IPacketHandler {

    void addListener(Plugin plugin, PacketListener listener);

    void removeListener(Plugin plugin, PacketListener listener);

    void removeListeners(Plugin plugin);

    void inject(Player player);

    void uninject(Player player);

    void sendPacket(Player player, Object packet);
}
