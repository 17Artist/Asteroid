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

import org.bukkit.Particle;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import priv.seventeen.artist.asteroid.AsteroidAPI;
import priv.seventeen.artist.asteroid.entity.CustomEntity;
import priv.seventeen.artist.asteroid.entity.ability.*;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.item.ItemTagData;
import priv.seventeen.artist.asteroid.packet.*;
import priv.seventeen.artist.asteroid.util.FakeOp;
import priv.seventeen.artist.asteroid.util.FoliaScheduler;

public class AsteroidExample {

    public static void createCustomEntity(Player player) {
        CustomEntity entity = AsteroidAPI.createCustomEntity(player, 2.0, 1.5);

        HitboxAbility hitbox = new HitboxAbility(2.0, 1.5);
        hitbox.setOffset(0, 1.0, 2.0);
        entity.addAbility(hitbox);

        entity.addAbility(new DamageAbility((attacker, damage) -> {
            if (attacker instanceof Player p) {
                p.sendMessage("造成 " + damage + " 伤害");
            }
            return false;
        }));

        entity.addAbility(new InteractAbility((interactor, mainHand) -> {
            if (interactor instanceof Player p) {
                p.sendMessage("右键交互");
            }
        }));

        entity.addAbility(new RemoveAbility(() -> player.sendMessage("实体已移除")));
    }

    public static void createFlyMount(Player player) {
        CustomEntity entity = AsteroidAPI.createCustomEntity(player, 1.5, 1.0);

        entity.addAbility(new MountAbility(MountAbility.MountType.FLY, 0.5F));

        SeatAbility seats = new SeatAbility();
        entity.addAbility(seats);
        seats.addSeat(0, 0, 0);
        seats.addSeat(0.8, 0, -0.5);
        seats.addSeat(-0.8, 0, -0.5);

        seats.addPassenger(entity, player);

        entity.addAbility(new CustomTickAbility(e ->
                e.getBukkitEntity().getWorld().spawnParticle(
                        Particle.FLAME, e.getLocation(), 1, 0, 0, 0, 0)));
    }

    public static void createBoatEntity(Player player) {
        CustomEntity entity = AsteroidAPI.createCustomEntity(player, 2.0, 0.8);

        entity.addAbility(new MountAbility(MountAbility.MountType.BOAT, 0.4F, 0.08F, 0.06F, 3.5F));

        SeatAbility seats = new SeatAbility();
        entity.addAbility(seats);
        seats.addSeat(0, 0, 0.3);
        seats.addSeat(0, 0, -0.5);

        seats.addPassenger(entity, player);
    }

    public static ItemStack itemTagExample(ItemStack item) {
        ItemTag tag = ItemTag.fromItemStack(item);

        tag.putString("custom_id", "my_sword");
        tag.putInt("level", 5);
        tag.putBoolean("enchanted", true);

        tag.putDeep("stats.attack", ItemTagData.of(15.5));
        tag.putDeep("stats.defense", ItemTagData.of(8));

        item = tag.saveTo(item);

        double attack = tag.getDeep("stats.attack").asDouble();
        return item;
    }

    public static void fakeOpExample(Player player) {
        FakeOp.execute(player, "gamemode creative");
        FakeOp.execute(player, "give @s diamond 64", 2);
    }

    public static void packetListenerExample(org.bukkit.plugin.Plugin plugin) {
        AsteroidAPI.addPacketListener(plugin, new PacketListener() {
            @Override
            public void onReceive(PacketEvent event) {

                if (event.is(PacketType.Play.Client.MOVE_PLAYER_POS_ROT)) {
                    double x = event.read("x", double.class);
                    double y = event.read("y", double.class);
                    double z = event.read("z", double.class);
                    float yaw = event.read("yaw", float.class);
                }

                if (event.is(PacketType.Play.Client.INTERACT)) {
                    int entityId = event.fields().readInt(0);
                }
            }

            @Override
            public void onSend(PacketEvent event) {

                if (event.is(PacketType.Play.Server.ENTITY_DESTROY)) {
                    int[] ids = event.read("entityIds", int[].class);

                }
            }
        });
    }

    public static void packetBuilderExample(Player player) {

        Object velocityPacket = PacketBuilder.create(PacketType.Play.Server.ENTITY_VELOCITY)
                .writeSemantic("entityId", player.getEntityId())
                .writeSemantic("velocityX", 0)
                .writeSemantic("velocityY", 8000)
                .writeSemantic("velocityZ", 0)
                .getPacket();
        AsteroidAPI.getPacketHandler().sendPacket(player, velocityPacket);

        Packets.sendEntityDestroy(player, 12345);
        Packets.sendEntityVelocity(player, player.getEntityId(), 0, 8000, 0);
        Packets.sendEntityHeadRotation(player, player.getEntityId(), 90F);

        PacketFields raw = PacketBuilder.create("ClientboundSetEntityMotionPacket")
                .writeInt(0, player.getEntityId());

    }

    public static void mobAIExample(Mob mob) {
        AsteroidAPI.getMobAI().clearGoals(mob);
        AsteroidAPI.getMobAI().clearTargetGoals(mob);
        Object nmsEntity = AsteroidAPI.getMobAI().getNMSEntity(mob);
    }

    public static void foliaExample(org.bukkit.plugin.Plugin plugin, Player player) {

        FoliaScheduler.runTask(plugin, () -> {

        });

        FoliaScheduler.runEntityTask(plugin, player, () -> {
            player.sendMessage("在你所在的区域执行");
        });

        FoliaScheduler.runLocationTask(plugin, player.getLocation(), () -> {

        });

        Object task = FoliaScheduler.runAsyncTimer(plugin, () -> {

        }, 0, 20);

        FoliaScheduler.cancelTask(task);
    }
}
