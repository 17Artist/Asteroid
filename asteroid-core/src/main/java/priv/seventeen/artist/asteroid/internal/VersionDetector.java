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

import org.bukkit.Bukkit;

import java.util.logging.Logger;

public final class VersionDetector {

    private static final Logger LOGGER = Logger.getLogger("Asteroid");

    private static String mcVersion;
    private static String nmsPackageVersion;

    private VersionDetector() {}

    public static String detectVersion() {
        if (mcVersion != null) return mcVersion;

        String serverVersion = Bukkit.getServer().getVersion();

        String[] parts = serverVersion.split("MC:");
        if (parts.length < 2) {
            throw new RuntimeException("Cannot detect MC version from: " + serverVersion);
        }
        mcVersion = parts[1].trim().replace(")", "").trim();
        LOGGER.info("[Asteroid] Detected MC version: " + mcVersion);
        return mcVersion;
    }

    public static String getNmsVersionSuffix() {
        String version = detectVersion();
        return "v" + version.replace(".", "_");
    }

    public static String getCraftBukkitPackageVersion() {
        if (nmsPackageVersion != null) return nmsPackageVersion;

        try {

            String craftServerClass = Bukkit.getServer().getClass().getPackage().getName();

            String[] parts = craftServerClass.split("\\.");
            if (parts.length >= 4 && parts[3].startsWith("v")) {
                nmsPackageVersion = parts[3];
            } else {
                nmsPackageVersion = "";
            }
        } catch (Exception e) {
            nmsPackageVersion = "";
        }
        return nmsPackageVersion;
    }

    public static int compareVersion(String a, String b) {
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

    public static boolean isAtLeast(String version) {
        return compareVersion(detectVersion(), version) >= 0;
    }
}
