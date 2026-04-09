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
package priv.seventeen.artist.asteroid.util;

public final class PaperCompat {

    private static final boolean IS_PAPER;
    private static final boolean IS_FOLIA;

    static {
        IS_PAPER = classExists("io.papermc.paper.configuration.Configuration")
                || classExists("com.destroystokyo.paper.PaperConfig");
        IS_FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    }

    private PaperCompat() {}

    public static boolean isPaper() { return IS_PAPER; }

    public static boolean isFolia() { return IS_FOLIA; }

    public static boolean isSpigot() { return !IS_PAPER; }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
