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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class FoliaScheduler {

    private FoliaScheduler() {}

    public static Object runTask(Plugin plugin, Runnable task) {
        if (PaperCompat.isFolia()) {
            return foliaGlobalRun(plugin, task);
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public static Object runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        if (PaperCompat.isFolia()) {
            return foliaGlobalRunDelayed(plugin, task, delayTicks);
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static Object runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (PaperCompat.isFolia()) {
            return foliaGlobalRunAtFixedRate(plugin, task, delayTicks, periodTicks);
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static Object runEntityTask(Plugin plugin, Entity entity, Runnable task) {
        if (PaperCompat.isFolia()) {
            return foliaEntityRun(plugin, entity, task);
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public static Object runLocationTask(Plugin plugin, Location location, Runnable task) {
        if (PaperCompat.isFolia()) {
            return foliaRegionRun(plugin, location, task);
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    public static Object runAsync(Plugin plugin, Runnable task) {
        if (PaperCompat.isFolia()) {
            return foliaAsyncRunNow(plugin, task);
        }
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static Object runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (PaperCompat.isFolia()) {
            return foliaAsyncRunDelayed(plugin, task, delayTicks * 50, TimeUnit.MILLISECONDS);
        }
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    public static Object runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (PaperCompat.isFolia()) {
            return foliaAsyncRunAtFixedRate(plugin, task, delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS);
        }
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    public static void cancelTask(Object taskHandle) {
        if (taskHandle == null) return;

        if (PaperCompat.isFolia()) {

            try {
                Method cancelMethod = taskHandle.getClass().getMethod("cancel");
                cancelMethod.invoke(taskHandle);
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.WARNING, "[Asteroid] Failed to cancel Folia task", e);
            }
        } else {

            if (taskHandle instanceof org.bukkit.scheduler.BukkitTask) {
                ((org.bukkit.scheduler.BukkitTask) taskHandle).cancel();
            }
        }
    }

    private static Object foliaGlobalRun(Plugin plugin, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            return runMethod.invoke(scheduler, plugin, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia GlobalRegionScheduler.run", e);
            return null;
        }
    }

    private static Object foliaGlobalRunDelayed(Plugin plugin, Runnable task, long delayTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delayTicks));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia GlobalRegionScheduler.runDelayed", e);
            return null;
        }
    }

    private static Object foliaGlobalRunAtFixedRate(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delayTicks), Math.max(1, periodTicks));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia GlobalRegionScheduler.runAtFixedRate", e);
            return null;
        }
    }

    private static Object foliaEntityRun(Plugin plugin, Entity entity, Runnable task) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Consumer<Object> consumer = scheduledTask -> task.run();

            Runnable retired = () -> {};
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            return method.invoke(scheduler, plugin, consumer, retired);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia EntityScheduler.run", e);
            return null;
        }
    }

    private static Object foliaRegionRun(Plugin plugin, Location location, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
            return method.invoke(scheduler, plugin, location, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia RegionScheduler.run", e);
            return null;
        }
    }

    private static Object foliaAsyncRunNow(Plugin plugin, Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            return method.invoke(scheduler, plugin, consumer);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia AsyncScheduler.runNow", e);
            return null;
        }
    }

    private static Object foliaAsyncRunDelayed(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
            return method.invoke(scheduler, plugin, consumer, delay, unit);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia AsyncScheduler.runDelayed", e);
            return null;
        }
    }

    private static Object foliaAsyncRunAtFixedRate(Plugin plugin, Runnable task, long delay, long period, TimeUnit unit) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Consumer<Object> consumer = scheduledTask -> task.run();
            Method method = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            return method.invoke(scheduler, plugin, consumer, Math.max(1, delay), Math.max(1, period), unit);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[Asteroid] Failed to invoke Folia AsyncScheduler.runAtFixedRate", e);
            return null;
        }
    }
}
