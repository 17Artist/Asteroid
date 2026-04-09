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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import priv.seventeen.artist.asteroid.AsteroidAPI;
import priv.seventeen.artist.asteroid.attribute.AttributeBridge;
import priv.seventeen.artist.asteroid.entity.CustomEntity;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.ability.*;
import priv.seventeen.artist.asteroid.entity.ai.IMobAI;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.item.ItemTagData;
import priv.seventeen.artist.asteroid.packet.*;

import java.util.List;
import java.util.logging.Logger;

public class AsteroidFullTest {

    private static final Logger LOG = Logger.getLogger("AsteroidTest");
    private int passed = 0;
    private int failed = 0;

    private void ok(String name) {
        passed++;
        LOG.info("[PASS] " + name);
    }

    private void fail(String name, Throwable e) {
        failed++;
        LOG.severe("[FAIL] " + name + ": " + e.getMessage());
        e.printStackTrace();
    }

    private void fail(String name, String reason) {
        failed++;
        LOG.severe("[FAIL] " + name + ": " + reason);
    }

    public void runAll() {
        LOG.info("========== Asteroid Full Test ==========");
        LOG.info("MC Version: " + AsteroidAPI.getMcVersion());

        testItemStackNMS();
        testItemTag();
        testAttributeBridge();
        testEntityNMS();
        testCustomEntity();
        testMobAI();
        testPacketType();
        testPacketBuilder();
        testPacketListener();
        testFoliaScheduler();

        LOG.info("========================================");
        LOG.info("Results: " + passed + " passed, " + failed + " failed, " + (passed + failed) + " total");
        LOG.info("========================================");
    }

    private World getWorld() {
        return Bukkit.getWorlds().get(0);
    }

    private Location getSpawn() {
        return getWorld().getSpawnLocation().add(0, 1, 0);
    }

    private void testItemStackNMS() {
        IItemStackNMS nms = AsteroidAPI.getItemStackNMS();

        try {
            ItemStack diamond = new ItemStack(Material.DIAMOND_SWORD);
            String json = nms.item2Json(diamond);
            if (json == null || json.isEmpty()) throw new RuntimeException("json is empty");
            ItemStack restored = nms.json2Item(json);
            if (restored == null || restored.getType() != Material.DIAMOND_SWORD)
                throw new RuntimeException("restored item mismatch: " + restored);
            ok("ItemStackNMS.item2Json + json2Item");
        } catch (Throwable e) {
            fail("ItemStackNMS.item2Json + json2Item", e);
        }
    }

    private void testItemTag() {
        try {
            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            ItemTag tag = ItemTag.fromItemStack(item);
            tag.putString("test_key", "hello");
            tag.putInt("test_int", 42);
            tag.putBoolean("test_bool", true);
            tag.putDeep("nested.value", ItemTagData.of(3.14));
            item = tag.saveTo(item);

            ItemTag read = ItemTag.fromItemStack(item);
            if (!"hello".equals(read.getString("test_key")))
                throw new RuntimeException("string mismatch: " + read.getString("test_key"));
            if (read.getInt("test_int") != 42)
                throw new RuntimeException("int mismatch: " + read.getInt("test_int"));
            if (!read.getBoolean("test_bool"))
                throw new RuntimeException("bool mismatch");
            ItemTagData nested = read.getDeep("nested.value");
            if (nested == null || Math.abs(nested.asDouble() - 3.14) > 0.001)
                throw new RuntimeException("nested mismatch: " + nested);
            ok("ItemTag read/write/deep");
        } catch (Throwable e) {
            fail("ItemTag read/write/deep", e);
        }
    }

    private String maxHealthAttr() {
        List<String> attrs = AsteroidAPI.getAttributeBridge().getAvailableAttributes();
        for (String a : attrs) {
            if (a.contains("max_health")) return a;
        }
        return "minecraft:generic.max_health";
    }

    private String moveSpeedAttr() {
        List<String> attrs = AsteroidAPI.getAttributeBridge().getAvailableAttributes();
        for (String a : attrs) {
            if (a.contains("movement_speed")) return a;
        }
        return "minecraft:generic.movement_speed";
    }

    private void testAttributeBridge() {
        AttributeBridge bridge = AsteroidAPI.getAttributeBridge();
        Location spawn = getSpawn();

        Zombie zombie = (Zombie) getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
        try {
            String hpAttr = maxHealthAttr();
            String spdAttr = moveSpeedAttr();

            if (!bridge.hasAttribute(zombie, hpAttr))
                throw new RuntimeException("zombie should have max_health (" + hpAttr + ")");
            ok("AttributeBridge.hasAttribute");

            double base = bridge.getBaseValue(zombie, hpAttr);
            if (base != 20.0) throw new RuntimeException("base should be 20, got " + base);
            ok("AttributeBridge.getBaseValue");

            bridge.setModifier(zombie, hpAttr, "asteroid:test", 10.0, 0);
            double finalVal = bridge.getFinalValue(zombie, hpAttr);
            if (Math.abs(finalVal - 30.0) > 0.01)
                throw new RuntimeException("final should be 30, got " + finalVal);
            ok("AttributeBridge.setModifier + getFinalValue");

            bridge.removeModifier(zombie, hpAttr, "asteroid:test");
            finalVal = bridge.getFinalValue(zombie, hpAttr);
            if (Math.abs(finalVal - 20.0) > 0.01)
                throw new RuntimeException("after remove should be 20, got " + finalVal);
            ok("AttributeBridge.removeModifier");

            bridge.setModifier(zombie, hpAttr, "asteroid:a", 5.0, 0);
            bridge.setModifier(zombie, spdAttr, "asteroid:b", 0.1, 0);
            bridge.removeAllModifiers(zombie, "asteroid:");
            double hp = bridge.getFinalValue(zombie, hpAttr);
            if (Math.abs(hp - 20.0) > 0.01)
                throw new RuntimeException("removeAll failed, hp=" + hp);
            ok("AttributeBridge.removeAllModifiers");

            List<String> attrs = bridge.getAvailableAttributes();
            if (attrs == null || attrs.isEmpty())
                throw new RuntimeException("no available attributes");
            ok("AttributeBridge.getAvailableAttributes (" + attrs.size() + " attrs)");
        } catch (Throwable e) {
            fail("AttributeBridge", e);
        } finally {
            zombie.remove();
        }
    }

    private void testEntityNMS() {
        IEntityNMS nms = AsteroidAPI.getEntityNMS();
        Location spawn = getSpawn();

        Cow cow = (Cow) getWorld().spawnEntity(spawn, EntityType.COW);
        try {
            nms.setPosition(cow, spawn.getX() + 5, spawn.getY(), spawn.getZ());
            Location loc = cow.getLocation();
            if (Math.abs(loc.getX() - (spawn.getX() + 5)) > 0.1)
                throw new RuntimeException("position mismatch: " + loc.getX());
            ok("EntityNMS.setPosition");

            nms.setRotation(cow, 90f, 45f);
            ok("EntityNMS.setRotation");

            nms.setSize(cow, 2.0f, 3.0f);
            ok("EntityNMS.setSize");

            nms.doWithSeenBy(cow, p -> {});
            ok("EntityNMS.doWithSeenBy");
        } catch (Throwable e) {
            fail("EntityNMS", e);
        } finally {
            cow.remove();
        }
    }

    private void testCustomEntity() {
        Location spawn = getSpawn();

        try {
            CustomEntity entity = AsteroidAPI.createCustomEntity(spawn, 1.0, 1.0);
            if (entity == null) throw new RuntimeException("entity is null");
            if (entity.getEntityId() <= 0) throw new RuntimeException("invalid entity id");
            if (entity.getUniqueId() == null) throw new RuntimeException("null uuid");
            if (entity.getBukkitEntity() == null) throw new RuntimeException("null bukkit entity");
            if (entity.getLocation() == null) throw new RuntimeException("null location");
            ok("CustomEntity.create + basic getters");

            entity.addAbility(new HitboxAbility(2.0, 1.5));
            if (!entity.hasAbility(HitboxAbility.class)) throw new RuntimeException("ability not added");
            ok("CustomEntity.addAbility + hasAbility");

            entity.addAbility(new FollowOwnerAbility());
            entity.removeAbility(FollowOwnerAbility.class);
            if (entity.hasAbility(FollowOwnerAbility.class)) throw new RuntimeException("ability not removed");
            ok("CustomEntity.removeAbility");

            entity.nmsSetSize(3.0, 2.0);
            ok("CustomEntity.nmsSetSize");

            entity.nmsSetOffset(1, 0, 0);
            ok("CustomEntity.nmsSetOffset");

            entity.remove();
            ok("CustomEntity.remove");
        } catch (Throwable e) {
            fail("CustomEntity", e);
        }
    }

    private void testMobAI() {
        IMobAI ai = AsteroidAPI.getMobAI();
        Location spawn = getSpawn();

        Zombie zombie = (Zombie) getWorld().spawnEntity(spawn, EntityType.ZOMBIE);
        try {
            Object nmsEntity = ai.getNMSEntity(zombie);
            if (nmsEntity == null) throw new RuntimeException("nms entity is null");
            ok("MobAI.getNMSEntity");

            Object goalSelector = ai.getGoalSelector(zombie);
            if (goalSelector == null) throw new RuntimeException("goal selector is null");
            ok("MobAI.getGoalSelector");

            Object targetSelector = ai.getTargetSelector(zombie);
            if (targetSelector == null) throw new RuntimeException("target selector is null");
            ok("MobAI.getTargetSelector");

            ai.clearGoals(zombie);
            ok("MobAI.clearGoals");

            ai.clearTargetGoals(zombie);
            ok("MobAI.clearTargetGoals");
        } catch (Throwable e) {
            fail("MobAI", e);
        } finally {
            zombie.remove();
        }
    }

    private void testPacketType() {
        try {
            Class<?> clazz = PacketType.Play.Server.ENTITY_DESTROY.resolve();
            if (clazz == null) throw new RuntimeException("resolve returned null");
            ok("PacketType.resolve (ENTITY_DESTROY -> " + clazz.getSimpleName() + ")");

            clazz = PacketType.Play.Server.ENTITY_VELOCITY.resolve();
            if (clazz == null) throw new RuntimeException("resolve returned null");
            ok("PacketType.resolve (ENTITY_VELOCITY -> " + clazz.getSimpleName() + ")");

            clazz = PacketType.Play.Client.INTERACT.resolve();
            if (clazz == null) throw new RuntimeException("resolve returned null");
            ok("PacketType.resolve (INTERACT -> " + clazz.getSimpleName() + ")");

            clazz = PacketType.Play.Server.ENTITY_TELEPORT.resolve();
            if (clazz == null) throw new RuntimeException("resolve returned null");
            ok("PacketType.resolve (ENTITY_TELEPORT -> " + clazz.getSimpleName() + ")");
        } catch (Throwable e) {
            fail("PacketType.resolve", e);
        }
    }

    private void testPacketBuilder() {
        try {
            Object packet = Packets.entityVelocity(1, 0, 8000, 0);
            if (packet == null) throw new RuntimeException("packet is null");
            ok("Packets.entityVelocity");
        } catch (Throwable e) { fail("Packets.entityVelocity", e); }

        try {
            Object packet = Packets.entityHeadRotation(1, 90f);
            if (packet == null) throw new RuntimeException("packet is null");
            ok("Packets.entityHeadRotation");
        } catch (Throwable e) { fail("Packets.entityHeadRotation", e); }

        try {
            Object packet = Packets.setPassengers(1, 2, 3);
            if (packet == null) throw new RuntimeException("packet is null");
            ok("Packets.setPassengers");
        } catch (Throwable e) { fail("Packets.setPassengers", e); }

        try {
            Object packet = Packets.entityDestroy(99999);
            if (packet == null) throw new RuntimeException("packet is null");
            ok("Packets.entityDestroy");
        } catch (Throwable e) { fail("Packets.entityDestroy", e); }
    }

    private void testPacketListener() {
        try {
            PacketListener listener = new PacketListener() {
                @Override
                public void onReceive(PacketEvent event) {}
                @Override
                public void onSend(PacketEvent event) {}
            };
            AsteroidAPI.getPacketHandler().addListener(
                    Bukkit.getPluginManager().getPlugin("Asteroid"), listener);
            AsteroidAPI.getPacketHandler().removeListener(
                    Bukkit.getPluginManager().getPlugin("Asteroid"), listener);
            ok("PacketHandler.addListener + removeListener");
        } catch (Throwable e) {
            fail("PacketHandler.addListener + removeListener", e);
        }
    }

    private void testFoliaScheduler() {
        try {
            priv.seventeen.artist.asteroid.util.PaperCompat.isPaper();
            priv.seventeen.artist.asteroid.util.PaperCompat.isFolia();
            priv.seventeen.artist.asteroid.util.PaperCompat.isSpigot();
            ok("PaperCompat detection (paper=" + priv.seventeen.artist.asteroid.util.PaperCompat.isPaper()
                    + ", folia=" + priv.seventeen.artist.asteroid.util.PaperCompat.isFolia() + ")");
        } catch (Throwable e) {
            fail("PaperCompat", e);
        }
    }
}
