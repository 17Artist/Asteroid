package priv.seventeen.artist.asteroid.nms.v1_21_1;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.AsteroidAPI;
import priv.seventeen.artist.asteroid.entity.IEntityNMS;
import priv.seventeen.artist.asteroid.entity.NativeDamageSourceNMS;
import priv.seventeen.artist.asteroid.internal.damage.NativeDamageTagEpoch;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Exact Paper 1.21.1 read-only source/tag epoch. No hurt interception, agent, or server mutation. */
final class NativeDamageSourceBridge {
    private static final String IMPLEMENTATION_SHA = "020b6831758cfbaad2890d77eb05d664758319c12a1b190f6abf40323e96120e";
    private NativeDamageSourceBridge() {}

    /** Package-private reuse of the exact Paper proof and read-only tag getter; never writes tags. */
    static VarHandle bindTags() { return Binding.TAGS; }

    // Initialized only when this explicitly requested capability is first bound, never on an ordinary hit.
    private static final class Binding {
        static final VarHandle TAGS = bind();
        private static VarHandle bind() {
            try {
                if (!Bukkit.isPrimaryThread() || !(Bukkit.getServer() instanceof CraftServer)
                    || !"Paper".equals(Bukkit.getServer().getName()) || !Bukkit.getBukkitVersion().startsWith("1.21.1-"))
                    throw new UnsupportedOperationException("Damage source epoch requires the reviewed Paper 1.21.1 runtime");
                Path jar = Path.of(Bukkit.getServer().getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                if (!Files.isRegularFile(jar) || Files.size(jar) > 256L * 1024 * 1024)
                    throw new UnsupportedOperationException("Damage source implementation proof is unavailable");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var in = Files.newInputStream(jar)) {
                    byte[] buffer = new byte[65536]; for (int n; (n = in.read(buffer)) != -1;) digest.update(buffer, 0, n);
                }
                if (!IMPLEMENTATION_SHA.equals(HexFormat.of().formatHex(digest.digest())))
                    throw new UnsupportedOperationException("Unreviewed Paper damage implementation");
                // Real bindTags always allocates a new unmodifiable ReferenceOpenHashSet wrapper.
                // Lookup happens once. Hit-time code uses only GET and reference identity, never set/scan/invoke.
                return MethodHandles.privateLookupIn(Holder.Reference.class, MethodHandles.lookup())
                    .findVarHandle(Holder.Reference.class, "tags", Set.class);
            } catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new UnsupportedOperationException("Cannot bind native damage tag epoch", failure); }
        }
    }

    static NativeDamageSourceNMS.DirectSources open(IEntityNMS provider, Plugin consumer, Plugin nativeOwner) {
        Objects.requireNonNull(provider); Objects.requireNonNull(consumer); Objects.requireNonNull(nativeOwner);
        checkThread();
        if (!consumer.isEnabled() || !nativeOwner.isEnabled()
            || Bukkit.getPluginManager().getPlugin(consumer.getName()) != consumer
            || Bukkit.getPluginManager().getPlugin(nativeOwner.getName()) != nativeOwner
            || AsteroidAPI.getEntityNMS() != provider)
            throw new IllegalStateException("Native damage source owner is not current");
        VarHandle tags = Binding.TAGS;
        var registry = ((CraftServer) Bukkit.getServer()).getServer().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        Holder.Reference<DamageType> mob = registry.getHolderOrThrow(DamageTypes.MOB_ATTACK);
        Holder.Reference<DamageType> player = registry.getHolderOrThrow(DamageTypes.PLAYER_ATTACK);
        return new Frozen(provider, consumer, nativeOwner, tags, mob, player);
    }

    private static void checkThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Native damage source epoch requires the primary thread");
    }

    private static final class Frozen implements NativeDamageSourceNMS.DirectSources {
        private final Thread thread = Thread.currentThread();
        private final IEntityNMS provider; private final Plugin consumer, nativeOwner; private final VarHandle tags;
        private final NativeDamageTagEpoch epoch;
        private final Source mob, player;
        Frozen(IEntityNMS provider, Plugin consumer, Plugin nativeOwner, VarHandle tags,
               Holder.Reference<DamageType> mob, Holder.Reference<DamageType> player) {
            this.provider = provider; this.consumer = consumer; this.nativeOwner = nativeOwner; this.tags = tags;
            epoch = new NativeDamageTagEpoch(tags.get(mob), tags.get(player));
            this.mob = new Source("minecraft:mob_attack", mob); this.player = new Source("minecraft:player_attack", player);
            if (!current()) throw new IllegalStateException("Native damage source epoch changed while binding");
        }
        private void check() { checkThread(); if (Thread.currentThread() != thread) throw new IllegalStateException("Wrong damage source owner"); }
        @Override public NativeDamageSourceNMS.SourceEpoch mobAttack() { check(); return mob; }
        @Override public NativeDamageSourceNMS.SourceEpoch playerAttack() { check(); return player; }
        @Override public boolean current() {
            check();
            boolean owned = consumer.isEnabled() && nativeOwner.isEnabled()
                && Bukkit.getPluginManager().getPlugin(consumer.getName()) == consumer
                && Bukkit.getPluginManager().getPlugin(nativeOwner.getName()) == nativeOwner
                && AsteroidAPI.getEntityNMS() == provider;
            return epoch.current(owned, tags.get(mob.holder), tags.get(player.holder));
        }
        @Override public void close() { check(); epoch.close(); }
        private final class Source implements NativeDamageSourceNMS.SourceEpoch {
            private final String key; private final Holder.Reference<DamageType> holder; private final boolean bypass;
            Source(String key, Holder.Reference<DamageType> holder) { this.key = key; this.holder = holder; bypass = holder.is(DamageTypeTags.BYPASSES_COOLDOWN); }
            @Override public String key() { check(); return key; }
            @Override public boolean bypassesCooldown() { check(); return bypass; }
            @Override public boolean current() { return Frozen.this.current(); }
            @Override public boolean matches(EntityDamageByEntityEvent event) {
                check();
                if (!current() || event == null || !(event.getDamager() instanceof CraftEntity direct)
                    || !(event.getDamageSource() instanceof CraftDamageSource source)) return false;
                var actual = source.getHandle();
                return actual.typeHolder() == holder && actual.getDirectEntity() == direct.getHandle()
                    && actual.getEntity() == direct.getHandle();
            }
        }
    }
}
