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
package priv.seventeen.artist.asteroid;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import priv.seventeen.artist.asteroid.attribute.AttributeBridge;
import priv.seventeen.artist.asteroid.entity.CustomEntity;
import priv.seventeen.artist.asteroid.entity.CustomEntityFactory;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.ai.IMobAI;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.packet.IPacketHandler;
import priv.seventeen.artist.asteroid.packet.PacketListener;
import priv.seventeen.artist.asteroid.util.FakeOp;

public final class AsteroidAPI {

    private static IEntityNMS entityNMS;
    private static IItemStackNMS itemStackNMS;
    private static CustomEntityFactory customEntityFactory;
    private static IPacketHandler packetHandler;
    private static IMobAI mobAI;
    private static AttributeBridge attributeBridge;

    private AsteroidAPI() {}

    public static void init(IEntityNMS entityNMS, IItemStackNMS itemStackNMS,
                            CustomEntityFactory factory, IPacketHandler packetHandler,
                            IMobAI mobAI, ItemTag.Bridge itemTagBridge,
                            FakeOp.Executor fakeOpExecutor, AttributeBridge attributeBridge) {
        AsteroidAPI.entityNMS = entityNMS;
        AsteroidAPI.itemStackNMS = itemStackNMS;
        AsteroidAPI.customEntityFactory = factory;
        AsteroidAPI.packetHandler = packetHandler;
        AsteroidAPI.mobAI = mobAI;
        AsteroidAPI.attributeBridge = attributeBridge;
        ItemTag.setBridge(itemTagBridge);
        FakeOp.setExecutor(fakeOpExecutor);
    }

    public static IEntityNMS getEntityNMS() {
        checkInit(entityNMS, "EntityNMS");
        return entityNMS;
    }

    public static IItemStackNMS getItemStackNMS() {
        checkInit(itemStackNMS, "ItemStackNMS");
        return itemStackNMS;
    }

    public static CustomEntity createCustomEntity(@NotNull Entity owner, double width, double height) {
        checkInit(customEntityFactory, "CustomEntityFactory");
        return customEntityFactory.create(owner, width, height);
    }

    public static CustomEntity createCustomEntity(@NotNull Location location, double width, double height) {
        checkInit(customEntityFactory, "CustomEntityFactory");
        return customEntityFactory.create(location, width, height);
    }

    public static CustomEntityFactory getCustomEntityFactory() {
        checkInit(customEntityFactory, "CustomEntityFactory");
        return customEntityFactory;
    }

    public static IPacketHandler getPacketHandler() {
        checkInit(packetHandler, "PacketHandler");
        return packetHandler;
    }

    public static void addPacketListener(Plugin plugin, PacketListener listener) {
        getPacketHandler().addListener(plugin, listener);
    }

    public static void removePacketListener(Plugin plugin, PacketListener listener) {
        getPacketHandler().removeListener(plugin, listener);
    }

    public static IMobAI getMobAI() {
        checkInit(mobAI, "MobAI");
        return mobAI;
    }

    public static AttributeBridge getAttributeBridge() {
        checkInit(attributeBridge, "AttributeBridge");
        return attributeBridge;
    }

    private static String mcVersion;

    public static void setMcVersion(String version) {
        mcVersion = version;
    }

    public static String getMcVersion() {
        return mcVersion;
    }

    private static void checkInit(Object obj, String name) {
        if (obj == null) {
            throw new IllegalStateException("Asteroid " + name + " not initialized. Call Asteroid.init() first.");
        }
    }
}
