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
package priv.seventeen.artist.asteroid.internal;

import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.AsteroidAPI;
import priv.seventeen.artist.asteroid.attribute.AttributeBridge;
import priv.seventeen.artist.asteroid.entity.CustomEntityFactory;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.ai.IMobAI;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.util.FakeOp;

import java.util.logging.Logger;

public final class NMSLoader {

    private static final Logger LOGGER = Logger.getLogger("Asteroid");
    private static final String BASE_PACKAGE = "priv.seventeen.artist.asteroid.nms.";

    private NMSLoader() {}

    public static void load(Plugin plugin) {
        String version = VersionDetector.detectVersion();
        String suffix = VersionDetector.getNmsVersionSuffix();
        String pkg = BASE_PACKAGE + suffix + ".";

        LOGGER.info("[Asteroid] Loading NMS implementations for " + version + " (package: " + suffix + ")");

        try {
            IEntityNMS entityNMS = instantiate(pkg + "EntityNMSImpl");
            IItemStackNMS itemStackNMS = instantiate(pkg + "ItemStackNMSImpl");
            CustomEntityFactory factory = instantiate(pkg + "CustomEntityFactoryImpl");
            IMobAI mobAI = instantiate(pkg + "MobAIImpl");
            ItemTag.Bridge itemTagBridge = instantiate(pkg + "ItemTagBridgeImpl");
            FakeOp.Executor fakeOpExecutor = instantiate(pkg + "FakeOpExecutorImpl");
            AttributeBridge attributeBridge = instantiate(pkg + "AttributeBridgeImpl");

            PacketHandlerImpl packetHandler = new PacketHandlerImpl();
            PacketHandlerImpl.ChannelProvider channelProvider = instantiate(pkg + "PacketChannelProviderImpl");
            packetHandler.setChannelProvider(channelProvider);
            packetHandler.registerEvents(plugin);

            AsteroidAPI.init(entityNMS, itemStackNMS, factory, packetHandler, mobAI, itemTagBridge, fakeOpExecutor, attributeBridge);
            AsteroidAPI.setMcVersion(version);

            LOGGER.info("[Asteroid] NMS implementations loaded successfully.");
        } catch (Exception e) {
            throw new RuntimeException("[Asteroid] Failed to load NMS implementations for version: " + version, e);
        }
    }

    public static void load() {
        String version = VersionDetector.detectVersion();
        String suffix = VersionDetector.getNmsVersionSuffix();
        String pkg = BASE_PACKAGE + suffix + ".";

        LOGGER.info("[Asteroid] Loading NMS implementations for " + version + " (package: " + suffix + ")");

        try {
            IEntityNMS entityNMS = instantiate(pkg + "EntityNMSImpl");
            IItemStackNMS itemStackNMS = instantiate(pkg + "ItemStackNMSImpl");
            CustomEntityFactory factory = instantiate(pkg + "CustomEntityFactoryImpl");
            IMobAI mobAI = instantiate(pkg + "MobAIImpl");
            ItemTag.Bridge itemTagBridge = instantiate(pkg + "ItemTagBridgeImpl");
            FakeOp.Executor fakeOpExecutor = instantiate(pkg + "FakeOpExecutorImpl");
            AttributeBridge attributeBridge = instantiate(pkg + "AttributeBridgeImpl");

            PacketHandlerImpl packetHandler = new PacketHandlerImpl();
            PacketHandlerImpl.ChannelProvider channelProvider = instantiate(pkg + "PacketChannelProviderImpl");
            packetHandler.setChannelProvider(channelProvider);

            AsteroidAPI.init(entityNMS, itemStackNMS, factory, packetHandler, mobAI, itemTagBridge, fakeOpExecutor, attributeBridge);
            AsteroidAPI.setMcVersion(version);

            LOGGER.info("[Asteroid] NMS implementations loaded successfully.");
        } catch (Exception e) {
            throw new RuntimeException("[Asteroid] Failed to load NMS implementations for version: " + version, e);
        }
    }

    private static <T> T instantiate(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        return (T) clazz.getDeclaredConstructor().newInstance();
    }
}
