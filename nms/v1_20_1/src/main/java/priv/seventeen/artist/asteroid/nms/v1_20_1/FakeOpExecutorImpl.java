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
package priv.seventeen.artist.asteroid.nms.v1_20_1;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import priv.seventeen.artist.asteroid.util.FakeOp;

public class FakeOpExecutorImpl implements FakeOp.Executor {

    @Override
    public void execute(Player player, String command, int permissionLevel) {
        if (!(player instanceof CraftPlayer cp)) return;
        ServerPlayer sp = cp.getHandle();
        boolean wasOp = sp.server.getPlayerList().isOp(sp.getGameProfile());
        try {
            if (!wasOp) {

                sp.server.getPlayerList().getOps().add(
                    new ServerOpListEntry(sp.getGameProfile(), permissionLevel, false)
                );

                player.recalculatePermissions();
            }
            Bukkit.dispatchCommand(player, command);
        } finally {
            if (!wasOp) {
                sp.server.getPlayerList().deop(sp.getGameProfile());
                player.recalculatePermissions();
            }
        }
    }
}
