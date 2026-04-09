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

import priv.seventeen.artist.asteroid.AsteroidAPI;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketType {

    public record FieldDescriptor(String name, Class<?> type, int typeIndex) {}

    private record VersionEntry(String className, List<FieldDescriptor> fields) {}

    private final String id;
    private final PacketDirection direction;

    private final Map<String, VersionEntry> versionEntries;

    private volatile Class<?> resolvedClass;
    private volatile List<FieldDescriptor> resolvedFields;

    private PacketType(String id, PacketDirection direction, Map<String, VersionEntry> entries) {
        this.id = id;
        this.direction = direction;
        this.versionEntries = entries;
    }

    public String id() { return id; }
    public PacketDirection direction() { return direction; }

    public List<FieldDescriptor> fields() {
        ensureResolved();
        return resolvedFields;
    }

    public Class<?> resolve() {
        ensureResolved();
        return resolvedClass;
    }

    public boolean matches(Object packet) {
        if (packet == null) return false;
        try {
            return resolve().isInstance(packet);
        } catch (Exception e) {
            return false;
        }
    }

    public FieldDescriptor field(String semanticName) {
        for (FieldDescriptor fd : fields()) {
            if (fd.name().equals(semanticName)) return fd;
        }
        return null;
    }

    @Override
    public String toString() { return id; }

    private void ensureResolved() {
        if (resolvedClass != null) return;
        synchronized (this) {
            if (resolvedClass != null) return;

            VersionEntry entry = resolveEntry();
            String className = entry.className();

            Class<?> clazz = null;

            try { clazz = Class.forName(className); } catch (ClassNotFoundException ignored) {}

            if (clazz == null) {
                for (String pkg : SEARCH_PACKAGES) {
                    try { clazz = Class.forName(pkg + className); break; } catch (ClassNotFoundException ignored) {}
                }
            }

            if (clazz == null && className.contains("$")) {
                String outer = className.substring(0, className.indexOf('$'));
                String inner = className.substring(className.indexOf('$'));
                for (String pkg : SEARCH_PACKAGES) {
                    try { clazz = Class.forName(pkg + outer + inner); break; } catch (ClassNotFoundException ignored) {}
                }
            }

            if (clazz == null) {
                String spigotName = SPIGOT_CLASS_NAMES.get(className);
                if (spigotName != null) {
                    for (String pkg : SEARCH_PACKAGES) {
                        try { clazz = Class.forName(pkg + spigotName); break; } catch (ClassNotFoundException ignored) {}
                    }
                    if (clazz == null && spigotName.contains("$")) {
                        String outer = spigotName.substring(0, spigotName.indexOf('$'));
                        String inner = spigotName.substring(spigotName.indexOf('$'));
                        for (String pkg : SEARCH_PACKAGES) {
                            try { clazz = Class.forName(pkg + outer + inner); break; } catch (ClassNotFoundException ignored) {}
                        }
                    }
                }
            }

            if (clazz == null) {
                throw new RuntimeException("[Asteroid] Cannot resolve packet class for " + id + ": " + className);
            }

            resolvedFields = entry.fields();
            resolvedClass = clazz;
            CLASS_TO_TYPE.put(clazz, this);
        }
    }

    private VersionEntry resolveEntry() {
        String version = AsteroidAPI.getMcVersion();
        if (version != null) {

            VersionEntry exact = versionEntries.get(version);
            if (exact != null) return exact;

            VersionEntry best = null;
            String bestVer = null;
            for (var e : versionEntries.entrySet()) {
                String v = e.getKey();
                if ("default".equals(v)) continue;
                if (compareVersion(v, version) <= 0) {
                    if (bestVer == null || compareVersion(v, bestVer) > 0) {
                        bestVer = v;
                        best = e.getValue();
                    }
                }
            }
            if (best != null) return best;
        }
        VersionEntry def = versionEntries.get("default");
        if (def != null) return def;
        return versionEntries.values().iterator().next();
    }

    private static final Map<Class<?>, PacketType> CLASS_TO_TYPE = new ConcurrentHashMap<>();
    private static final Map<String, PacketType> ALL_TYPES = new ConcurrentHashMap<>();

    public static PacketType fromPacket(Object packet) {
        if (packet == null) return null;
        PacketType cached = CLASS_TO_TYPE.get(packet.getClass());
        if (cached != null) return cached;
        for (PacketType type : ALL_TYPES.values()) {
            try {
                if (type.resolve().isInstance(packet)) {
                    CLASS_TO_TYPE.put(packet.getClass(), type);
                    return type;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static PacketType byId(String id) { return ALL_TYPES.get(id); }

    public static Collection<PacketType> all() { return Collections.unmodifiableCollection(ALL_TYPES.values()); }

    private static final String[] SEARCH_PACKAGES = {
            "net.minecraft.network.protocol.game.",
            "net.minecraft.network.protocol.common.",
            "net.minecraft.network.protocol.login.",
            "net.minecraft.network.protocol.status.",
            "net.minecraft.network.protocol.",
    };

    private static final Map<String, String> SPIGOT_CLASS_NAMES = Map.ofEntries(
            Map.entry("ClientboundAddEntityPacket", "PacketPlayOutSpawnEntity"),
            Map.entry("ClientboundRemoveEntitiesPacket", "PacketPlayOutEntityDestroy"),
            Map.entry("ClientboundTeleportEntityPacket", "PacketPlayOutEntityTeleport"),
            Map.entry("ClientboundMoveEntityPacket$Pos", "PacketPlayOutEntity$PacketPlayOutRelEntityMove"),
            Map.entry("ClientboundMoveEntityPacket$PosRot", "PacketPlayOutEntity$PacketPlayOutRelEntityMoveLook"),
            Map.entry("ClientboundMoveEntityPacket$Rot", "PacketPlayOutEntity$PacketPlayOutEntityLook"),
            Map.entry("ClientboundSetEntityMotionPacket", "PacketPlayOutEntityVelocity"),
            Map.entry("ClientboundRotateHeadPacket", "PacketPlayOutEntityHeadRotation"),
            Map.entry("ClientboundSetEntityDataPacket", "PacketPlayOutEntityMetadata"),
            Map.entry("ClientboundSetEquipmentPacket", "PacketPlayOutEntityEquipment"),
            Map.entry("ClientboundEntityEventPacket", "PacketPlayOutEntityStatus"),
            Map.entry("ClientboundSetPassengersPacket", "PacketPlayOutMount"),
            Map.entry("ClientboundBlockUpdatePacket", "PacketPlayOutBlockChange"),
            Map.entry("ClientboundSectionBlocksUpdatePacket", "PacketPlayOutMultiBlockChange"),
            Map.entry("ClientboundExplodePacket", "PacketPlayOutExplosion"),
            Map.entry("ClientboundPlayerInfoPacket", "PacketPlayOutPlayerInfo"),
            Map.entry("ClientboundPlayerInfoUpdatePacket", "PacketPlayOutPlayerInfo"),
            Map.entry("ClientboundPlayerInfoRemovePacket", "PacketPlayOutPlayerInfoRemove"),
            Map.entry("ClientboundTabListPacket", "PacketPlayOutPlayerListHeaderFooter"),
            Map.entry("ClientboundGameEventPacket", "PacketPlayOutGameStateChange"),
            Map.entry("ClientboundSoundPacket", "PacketPlayOutNamedSoundEffect"),
            Map.entry("ClientboundLevelParticlesPacket", "PacketPlayOutWorldParticles"),
            Map.entry("ClientboundChatPacket", "PacketPlayOutChat"),
            Map.entry("ClientboundSystemChatPacket", "ClientboundSystemChatPacket"),
            Map.entry("ClientboundSetTitleTextPacket", "PacketPlayOutSetTitleText"),
            Map.entry("ClientboundSetSubtitleTextPacket", "PacketPlayOutSetSubtitleText"),
            Map.entry("ClientboundSetActionBarTextPacket", "PacketPlayOutSetActionBarText"),
            Map.entry("ClientboundKeepAlivePacket", "PacketPlayOutKeepAlive"),
            Map.entry("ClientboundDisconnectPacket", "PacketPlayOutKickDisconnect"),
            Map.entry("ClientboundRespawnPacket", "PacketPlayOutRespawn"),
            Map.entry("ClientboundSetCameraPacket", "PacketPlayOutCamera"),
            Map.entry("ServerboundMovePlayerPacket$Pos", "PacketPlayInFlying$PacketPlayInPosition"),
            Map.entry("ServerboundMovePlayerPacket$PosRot", "PacketPlayInFlying$PacketPlayInPositionLook"),
            Map.entry("ServerboundMovePlayerPacket$Rot", "PacketPlayInFlying$PacketPlayInLook"),
            Map.entry("ServerboundPlayerActionPacket", "PacketPlayInBlockDig"),
            Map.entry("ServerboundUseItemOnPacket", "PacketPlayInUseItem"),
            Map.entry("ServerboundUseItemPacket", "PacketPlayInBlockPlace"),
            Map.entry("ServerboundInteractPacket", "PacketPlayInUseEntity"),
            Map.entry("ServerboundSwingPacket", "PacketPlayInArmAnimation"),
            Map.entry("ServerboundKeepAlivePacket", "PacketPlayInKeepAlive"),
            Map.entry("ServerboundChatPacket", "PacketPlayInChat"),
            Map.entry("ServerboundChatCommandPacket", "ServerboundChatCommandPacket"),
            Map.entry("ServerboundClientInformationPacket", "PacketPlayInSettings"),
            Map.entry("ServerboundPlayerInputPacket", "PacketPlayInSteerVehicle")
    );

    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }

    private static FieldDescriptor f(String name, Class<?> type, int typeIndex) {
        return new FieldDescriptor(name, type, typeIndex);
    }

    private static Builder define(String id, PacketDirection dir) {
        return new Builder(id, dir);
    }

    private static final class Builder {
        private final String id;
        private final PacketDirection dir;
        private final Map<String, VersionEntry> entries = new LinkedHashMap<>();

        Builder(String id, PacketDirection dir) { this.id = id; this.dir = dir; }

        Builder version(String version, String className, FieldDescriptor... fields) {
            entries.put(version, new VersionEntry(className, List.of(fields)));
            return this;
        }

        PacketType register() {
            PacketType type = new PacketType(id, dir, entries);
            ALL_TYPES.put(id, type);
            return type;
        }
    }

    public static final class Play {
        private Play() {}

        public static final class Server {
            private Server() {}

            public static final PacketType SPAWN_ENTITY = define("play.server.spawn_entity", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundAddEntityPacket",
                            f("entityId", int.class, 0),
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2))
                    .register();

            public static final PacketType ENTITY_DESTROY = define("play.server.entity_destroy", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundRemoveEntitiesPacket")
                    .register();

            public static final PacketType ENTITY_TELEPORT = define("play.server.entity_teleport", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundTeleportEntityPacket",
                            f("entityId", int.class, 0),
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2),
                            f("yaw", byte.class, 0), f("pitch", byte.class, 1),
                            f("onGround", boolean.class, 0))
                    .version("1.21.4", "ClientboundEntityPositionSyncPacket",
                            f("entityId", int.class, 0))
                    .register();

            public static final PacketType ENTITY_MOVE_POS = define("play.server.entity_move_pos", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundMoveEntityPacket$Pos",
                            f("entityId", int.class, 0),
                            f("deltaX", short.class, 0), f("deltaY", short.class, 1), f("deltaZ", short.class, 2),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType ENTITY_MOVE_POS_ROT = define("play.server.entity_move_pos_rot", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundMoveEntityPacket$PosRot",
                            f("entityId", int.class, 0),
                            f("deltaX", short.class, 0), f("deltaY", short.class, 1), f("deltaZ", short.class, 2),
                            f("yaw", byte.class, 0), f("pitch", byte.class, 1),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType ENTITY_MOVE_ROT = define("play.server.entity_move_rot", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundMoveEntityPacket$Rot",
                            f("entityId", int.class, 0),
                            f("yaw", byte.class, 0), f("pitch", byte.class, 1),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType ENTITY_VELOCITY = define("play.server.entity_velocity", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetEntityMotionPacket",
                            f("entityId", int.class, 0),
                            f("velocityX", int.class, 1), f("velocityY", int.class, 2), f("velocityZ", int.class, 3))
                    .version("1.21.11", "ClientboundSetEntityMotionPacket",
                            f("entityId", int.class, 0))
                    .register();

            public static final PacketType ENTITY_HEAD_ROTATION = define("play.server.entity_head_rotation", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundRotateHeadPacket",
                            f("entityId", int.class, 0), f("headYaw", byte.class, 0))
                    .register();

            public static final PacketType ENTITY_METADATA = define("play.server.entity_metadata", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetEntityDataPacket",
                            f("entityId", int.class, 0))
                    .register();

            public static final PacketType ENTITY_EQUIPMENT = define("play.server.entity_equipment", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetEquipmentPacket",
                            f("entityId", int.class, 0))
                    .register();

            public static final PacketType ENTITY_STATUS = define("play.server.entity_status", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundEntityEventPacket",
                            f("entityId", int.class, 0), f("eventId", byte.class, 0))
                    .register();

            public static final PacketType SET_PASSENGERS = define("play.server.set_passengers", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetPassengersPacket",
                            f("vehicleId", int.class, 0), f("passengerIds", int[].class, 0))
                    .register();

            public static final PacketType BLOCK_CHANGE = define("play.server.block_change", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundBlockUpdatePacket")
                    .register();

            public static final PacketType MULTI_BLOCK_CHANGE = define("play.server.multi_block_change", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSectionBlocksUpdatePacket")
                    .register();

            public static final PacketType EXPLOSION = define("play.server.explosion", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundExplodePacket",
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2),
                            f("power", float.class, 0))
                    .register();

            public static final PacketType PLAYER_INFO_UPDATE = define("play.server.player_info_update", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundPlayerInfoPacket")
                    .version("1.19.3", "ClientboundPlayerInfoUpdatePacket")
                    .register();

            public static final PacketType PLAYER_INFO_REMOVE = define("play.server.player_info_remove", PacketDirection.OUT)
                    .version("1.19.3", "ClientboundPlayerInfoRemovePacket")
                    .register();

            public static final PacketType TAB_LIST = define("play.server.tab_list", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundTabListPacket")
                    .register();

            public static final PacketType GAME_EVENT = define("play.server.game_event", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundGameEventPacket")
                    .register();

            public static final PacketType SOUND = define("play.server.sound", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSoundPacket",
                            f("x", int.class, 0), f("y", int.class, 1), f("z", int.class, 2),
                            f("volume", float.class, 0), f("pitch", float.class, 1))
                    .register();

            public static final PacketType PARTICLE = define("play.server.particle", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundLevelParticlesPacket",
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2),
                            f("count", int.class, 0))
                    .register();

            public static final PacketType SYSTEM_CHAT = define("play.server.system_chat", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundChatPacket")
                    .version("1.19", "ClientboundSystemChatPacket")
                    .register();

            public static final PacketType SET_TITLE_TEXT = define("play.server.set_title_text", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetTitleTextPacket")
                    .register();

            public static final PacketType SET_SUBTITLE_TEXT = define("play.server.set_subtitle_text", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetSubtitleTextPacket")
                    .register();

            public static final PacketType SET_ACTION_BAR_TEXT = define("play.server.set_action_bar_text", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetActionBarTextPacket")
                    .register();

            public static final PacketType KEEP_ALIVE = define("play.server.keep_alive", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundKeepAlivePacket",
                            f("id", long.class, 0))
                    .register();

            public static final PacketType DISCONNECT = define("play.server.disconnect", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundDisconnectPacket")
                    .register();

            public static final PacketType RESPAWN = define("play.server.respawn", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundRespawnPacket")
                    .register();

            public static final PacketType SET_CAMERA = define("play.server.set_camera", PacketDirection.OUT)
                    .version("1.18.2", "ClientboundSetCameraPacket",
                            f("entityId", int.class, 0))
                    .register();
        }

        public static final class Client {
            private Client() {}

            public static final PacketType MOVE_PLAYER_POS = define("play.client.move_player_pos", PacketDirection.IN)
                    .version("1.18.2", "ServerboundMovePlayerPacket$Pos",
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType MOVE_PLAYER_POS_ROT = define("play.client.move_player_pos_rot", PacketDirection.IN)
                    .version("1.18.2", "ServerboundMovePlayerPacket$PosRot",
                            f("x", double.class, 0), f("y", double.class, 1), f("z", double.class, 2),
                            f("yaw", float.class, 0), f("pitch", float.class, 1),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType MOVE_PLAYER_ROT = define("play.client.move_player_rot", PacketDirection.IN)
                    .version("1.18.2", "ServerboundMovePlayerPacket$Rot",
                            f("yaw", float.class, 0), f("pitch", float.class, 1),
                            f("onGround", boolean.class, 0))
                    .register();

            public static final PacketType PLAYER_ACTION = define("play.client.player_action", PacketDirection.IN)
                    .version("1.18.2", "ServerboundPlayerActionPacket")
                    .register();

            public static final PacketType USE_ITEM_ON = define("play.client.use_item_on", PacketDirection.IN)
                    .version("1.18.2", "ServerboundUseItemOnPacket")
                    .register();

            public static final PacketType USE_ITEM = define("play.client.use_item", PacketDirection.IN)
                    .version("1.18.2", "ServerboundUseItemPacket")
                    .register();

            public static final PacketType INTERACT = define("play.client.interact", PacketDirection.IN)
                    .version("1.18.2", "ServerboundInteractPacket",
                            f("entityId", int.class, 0))
                    .register();

            public static final PacketType SWING = define("play.client.swing", PacketDirection.IN)
                    .version("1.18.2", "ServerboundSwingPacket")
                    .register();

            public static final PacketType KEEP_ALIVE = define("play.client.keep_alive", PacketDirection.IN)
                    .version("1.18.2", "ServerboundKeepAlivePacket",
                            f("id", long.class, 0))
                    .register();

            public static final PacketType CHAT = define("play.client.chat", PacketDirection.IN)
                    .version("1.18.2", "ServerboundChatPacket")
                    .register();

            public static final PacketType CHAT_COMMAND = define("play.client.chat_command", PacketDirection.IN)
                    .version("1.19", "ServerboundChatCommandPacket")
                    .register();

            public static final PacketType CLIENT_INFORMATION = define("play.client.client_information", PacketDirection.IN)
                    .version("1.18.2", "ServerboundClientInformationPacket")
                    .register();

            public static final PacketType PLAYER_INPUT = define("play.client.player_input", PacketDirection.IN)
                    .version("1.18.2", "ServerboundPlayerInputPacket")
                    .register();
        }
    }
}
