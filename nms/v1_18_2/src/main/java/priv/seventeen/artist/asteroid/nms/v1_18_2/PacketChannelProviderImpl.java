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

import io.netty.channel.Channel;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.internal.PacketHandlerImpl;

public class PacketChannelProviderImpl implements PacketHandlerImpl.ChannelProvider {

    @Override
    public Channel getChannel(Player player) {
        if (player instanceof CraftPlayer cp) {
            return cp.getHandle().connection.connection.channel;
        }
        return null;
    }
}
