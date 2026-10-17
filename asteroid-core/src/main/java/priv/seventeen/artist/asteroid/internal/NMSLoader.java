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
import priv.seventeen.artist.asteroid.attribute.IAttributeItemNMS;
import priv.seventeen.artist.asteroid.entity.CustomEntityFactory;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.ai.IMobAI;
import priv.seventeen.artist.asteroid.item.IGlintNMS;
import priv.seventeen.artist.asteroid.item.IItemStackNMS;
import priv.seventeen.artist.asteroid.item.ItemTag;
import priv.seventeen.artist.asteroid.skull.ISkullNMS;
import priv.seventeen.artist.asteroid.util.FakeOp;
import priv.seventeen.artist.asteroid.internal.mount.AbstractNativeModelMountNMS;
import priv.seventeen.artist.asteroid.internal.tracking.NativeTrackingLifecycle;

public final class NMSLoader {

    private static final String BASE_PACKAGE = "priv.seventeen.artist.asteroid.nms.";
    private static AbstractNativeModelMountNMS activeMounts;
    private static PacketHandlerImpl activePackets;
    private static NativeTrackingLifecycle activeTracking;

    private NMSLoader() {}

    public static void load(Plugin plugin) {
        String version = VersionDetector.detectVersion();
        String suffix = VersionDetector.getNmsVersionSuffix();
        String pkg = BASE_PACKAGE + suffix + ".";

        try {
            IEntityNMS entityNMS = instantiate(pkg + "EntityNMSImpl");
            IItemStackNMS itemStackNMS = instantiate(pkg + "ItemStackNMSImpl");
            CustomEntityFactory factory = instantiate(pkg + "CustomEntityFactoryImpl");
            IMobAI mobAI = instantiate(pkg + "MobAIImpl");
            ItemTag.Bridge itemTagBridge = instantiate(pkg + "ItemTagBridgeImpl");
            FakeOp.Executor fakeOpExecutor = instantiate(pkg + "FakeOpExecutorImpl");
            AttributeBridge attributeBridge = instantiate(pkg + "AttributeBridgeImpl");
            ISkullNMS skullNMS = instantiate(pkg + "SkullNMSImpl");
            IGlintNMS glintNMS = instantiate(pkg + "GlintNMSImpl");
            IAttributeItemNMS attributeItemNMS = instantiate(pkg + "AttributeItemNMSImpl");

            PacketHandlerImpl packetHandler = new PacketHandlerImpl();
            PacketHandlerImpl.ChannelProvider channelProvider = instantiate(pkg + "PacketChannelProviderImpl");
            packetHandler.setChannelProvider(channelProvider);
            installLifecycle(entityNMS, packetHandler, plugin);

            AsteroidAPI.init(entityNMS, itemStackNMS, factory, packetHandler, mobAI, itemTagBridge, fakeOpExecutor, attributeBridge, skullNMS, glintNMS, attributeItemNMS);
            AsteroidAPI.setMcVersion(version);
        } catch (Exception e) {
            throw new RuntimeException("[Asteroid] Failed to load NMS implementations for version: " + version, e);
        }
    }

    public static void load() {
        String version = VersionDetector.detectVersion();
        String suffix = VersionDetector.getNmsVersionSuffix();
        String pkg = BASE_PACKAGE + suffix + ".";

        try {
            IEntityNMS entityNMS = instantiate(pkg + "EntityNMSImpl");
            IItemStackNMS itemStackNMS = instantiate(pkg + "ItemStackNMSImpl");
            CustomEntityFactory factory = instantiate(pkg + "CustomEntityFactoryImpl");
            IMobAI mobAI = instantiate(pkg + "MobAIImpl");
            ItemTag.Bridge itemTagBridge = instantiate(pkg + "ItemTagBridgeImpl");
            FakeOp.Executor fakeOpExecutor = instantiate(pkg + "FakeOpExecutorImpl");
            AttributeBridge attributeBridge = instantiate(pkg + "AttributeBridgeImpl");
            ISkullNMS skullNMS = instantiate(pkg + "SkullNMSImpl");
            IGlintNMS glintNMS = instantiate(pkg + "GlintNMSImpl");
            IAttributeItemNMS attributeItemNMS = instantiate(pkg + "AttributeItemNMSImpl");

            PacketHandlerImpl packetHandler = new PacketHandlerImpl();
            PacketHandlerImpl.ChannelProvider channelProvider = instantiate(pkg + "PacketChannelProviderImpl");
            packetHandler.setChannelProvider(channelProvider);
            installLifecycle(entityNMS, packetHandler, null);

            AsteroidAPI.init(entityNMS, itemStackNMS, factory, packetHandler, mobAI, itemTagBridge, fakeOpExecutor, attributeBridge, skullNMS, glintNMS, attributeItemNMS);
            AsteroidAPI.setMcVersion(version);
        } catch (Exception e) {
            throw new RuntimeException("[Asteroid] Failed to load NMS implementations for version: " + version, e);
        }
    }

    private static void installLifecycle(IEntityNMS entityNMS, PacketHandlerImpl packets, Plugin plugin) {
        // Keep a failed exact cleanup receipt; a new adapter must not overlap it.
        if (activeTracking != null) activeTracking.close();
        activeTracking = null;
        if (activeMounts != null) activeMounts.shutdownModelMounts();
        if (activePackets != null) activePackets.shutdown();
        activeMounts = null;
        activePackets = null;
        AbstractNativeModelMountNMS mounts = entityNMS instanceof AbstractNativeModelMountNMS value ? value : null;
        NativeTrackingLifecycle tracking = null;
        try {
            if (plugin != null) packets.registerEvents(plugin);
            if (mounts != null) mounts.bootstrapModelMounts(plugin, packets);
            tracking = NativeTrackingLifecycle.install(plugin, entityNMS);
            activeTracking = tracking;
            activeMounts = mounts;
            activePackets = packets;
        } catch (RuntimeException | Error error) {
            if (tracking != null) try { tracking.close(); } catch (RuntimeException | Error cleanup) { error.addSuppressed(cleanup); }
            if (mounts != null) mounts.shutdownModelMounts();
            packets.shutdown();
            throw error;
        }
    }

    private static <T> T instantiate(String className) throws Exception {
        Class<?> clazz = Class.forName(className);
        return (T) clazz.getDeclaredConstructor().newInstance();
    }
}
