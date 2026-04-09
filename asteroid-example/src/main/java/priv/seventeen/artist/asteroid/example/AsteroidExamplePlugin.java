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
package priv.seventeen.artist.asteroid.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.asteroid.internal.NMSLoader;

public class AsteroidExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {

        NMSLoader.load(this);
        getLogger().info("Asteroid NMS Library loaded for MC " + priv.seventeen.artist.asteroid.AsteroidAPI.getMcVersion());

        AsteroidExample.packetListenerExample(this);

        getServer().getScheduler().runTaskLater(this, () -> {
            new AsteroidFullTest().runAll();
        }, 20L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: /asteroid <entity|mount|boat|itemtag|fakeop|ai|folia>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "entity" -> {
                AsteroidExample.createCustomEntity(player);
                player.sendMessage("Custom entity created!");
            }
            case "mount" -> {
                AsteroidExample.createFlyMount(player);
                player.sendMessage("Fly mount created!");
            }
            case "boat" -> {
                AsteroidExample.createBoatEntity(player);
                player.sendMessage("Boat entity created!");
            }
            case "itemtag" -> {
                var item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    player.sendMessage("Hold an item first!");
                } else {
                    var result = AsteroidExample.itemTagExample(item);
                    player.getInventory().setItemInMainHand(result);
                    player.sendMessage("ItemTag written!");
                }
            }
            case "fakeop" -> {
                AsteroidExample.fakeOpExample(player);
                player.sendMessage("FakeOp executed!");
            }
            case "ai" -> {

                player.sendMessage("Use on a mob entity.");
            }
            case "folia" -> {
                AsteroidExample.foliaExample(this, player);
                player.sendMessage("Folia scheduler test executed!");
            }
            case "packet" -> {
                AsteroidExample.packetBuilderExample(player);
                player.sendMessage("Packet builder test executed!");
            }
            default -> player.sendMessage("Unknown subcommand: " + args[0]);
        }
        return true;
    }
}
