package priv.seventeen.artist.asteroid.internal.mount;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS;
import priv.seventeen.artist.asteroid.entity.NativeModelMountNMS.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** One bounded ownership state machine shared by every native version. */
final class MountLeaseRegistry implements MountPacketObserver, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Asteroid");
    static final int MAX_OWNERS = 128, MAX_RIDERS = 256, MAX_HOST_RIDERS = 32;
    static final int MAX_VIEWS = 8192, MAX_VIEWER_VIEWS = 512;
    private final NativeMountPlatform platform;
    private final MountEnvironment environment;
    private final Map<UUID, Owner> owners = new HashMap<>();
    private final Map<UUID, Rider> riders = new HashMap<>();
    private final Map<UUID, Rider> tokens = new HashMap<>();
    private final Map<Object, Connection> connections = new ConcurrentHashMap<>();
    private int viewCount;
    private boolean open = true;

    MountLeaseRegistry(NativeMountPlatform platform, MountEnvironment environment) {
        this.platform = Objects.requireNonNull(platform);
        this.environment = Objects.requireNonNull(environment);
    }

    OwnerLease open(Plugin plugin, Entity host, UUID epoch, Consumer<Throwable> failure) {
        environment.checkThread();
        Objects.requireNonNull(plugin); Objects.requireNonNull(host);
        Objects.requireNonNull(epoch); Objects.requireNonNull(failure);
        if (!open || !plugin.isEnabled()) throw new IllegalStateException("Passenger owner is disabled");
        requireLoaded(host);
        UUID id = host.getUniqueId();
        if (owners.containsKey(id) || riders.containsKey(id)) throw new IllegalStateException("Host already participates in a passenger lease");
        if (owners.size() >= MAX_OWNERS) throw new IllegalStateException("Passenger owner budget exhausted");
        Object handle = platform.handle(host);
        if (platform.hasVehicle(handle)) throw new IllegalStateException("Passenger host has a foreign vehicle");
        platform.preflight();
        Owner owner = new Owner(plugin, host, handle, epoch, failure);
        owners.put(id, owner);
        return owner;
    }

    private static void requireLoaded(Entity entity) {
        if (!entity.isValid() || entity.isDead() || entity instanceof Player p && !p.isOnline()) {
            throw new IllegalStateException("Passenger entity is not loaded and alive");
        }
    }

    private boolean loaded(Entity entity, Object handle, UUID world, UUID id, int nativeId) {
        return entity.isValid() && !entity.isDead() && (!(entity instanceof Player p) || p.isOnline())
                && id.equals(entity.getUniqueId()) && entity.getEntityId() == nativeId
                && world.equals(entity.getWorld().getUID()) && platform.sameHandle(entity, handle);
    }

    /** One host task, bounded by owned records. Never scans worlds or online players. */
    void maintain() {
        environment.checkThread();
        for (Owner owner : List.copyOf(owners.values())) {
            if (!owner.valid()) { owner.close(); continue; }
            for (Rider rider : List.copyOf(owner.owned.values())) {
                if (!rider.valid()) { rider.close(); continue; }
                for (View view : List.copyOf(rider.views.values())) {
                    if (!view.valid()) { rider.removeView(view); continue; }
                    if (view.needsPublish.compareAndSet(true, false)) {
                        try { rider.publish(view); }
                        catch (RuntimeException ignored) { break; } // publish revoked and reported this rider
                    }
                }
            }
        }
    }

    void removeEntity(UUID id) {
        environment.checkThread();
        Owner owner = owners.get(id);
        if (owner != null) owner.close();
        Rider rider = riders.get(id);
        if (rider != null) rider.close();
        for (Rider remaining : List.copyOf(riders.values())) {
            View view = remaining.views.get(id);
            if (view != null) remaining.removeView(view);
        }
    }

    void removePlugin(Plugin plugin) {
        environment.checkThread();
        for (Owner owner : List.copyOf(owners.values())) if (owner.plugin == plugin) owner.close();
    }

    @Override public void close() {
        environment.checkThread();
        if (!open) return;
        open = false;
        for (Owner owner : List.copyOf(owners.values())) owner.close();
        connections.clear();
    }

    @Override public void acceptedInput(Object channel, Object packet) {
        Connection connection = connections.get(channel);
        if (connection == null || connection.closed.get()) return;
        Rider rider = connection.input;
        if (rider == null || rider.closed.get()) return;
        try {
            long previous = rider.mailbox.read().sequence();
            if (previous == Long.MAX_VALUE) throw new IllegalStateException("Passenger input sequence exhausted");
            Input decoded = platform.decodeInput(packet, previous + 1);
            if (decoded != null) rider.mailbox.publish(decoded);
        } catch (RuntimeException error) { rider.failed.compareAndSet(null, error); }
    }

    @Override public void acceptedOutput(Object channel, Object packet) {
        Connection connection = connections.get(channel);
        if (connection == null || connection.closed.get()) return;
        int spawned = platform.spawnedEntityId(packet);
        View added = connection.views.get(spawned);
        if (added != null && !added.closed.get()) {
            added.nativeVisible.set(true);
            added.needsPublish.set(true);
        }
        for (int removed : platform.removedEntityIds(packet)) {
            View view = connection.views.get(removed);
            if (view == null || view.closed.get()) continue;
            view.nativeVisible.set(false);
            view.needsPublish.set(false);
            if (view.published.getAndSet(false)) {
                try { environment.cleanup(channel, view.removePackets, view.rider::failAsync); }
                catch (RuntimeException error) { view.rider.failAsync(error); }
            }
        }
    }

    @Override public void disconnected(Object channel) {
        Connection connection = connections.get(channel);
        if (connection == null || !connection.closed.compareAndSet(false, true)) return;
        Rider input = connection.input;
        if (input != null) {
            input.mailbox.close();
            input.failAsync(new IllegalStateException("Passenger connection closed or was replaced"));
        }
        for (View view : connection.views.values()) {
            view.nativeVisible.set(false);
            view.needsPublish.set(false);
        }
    }

    private Connection connection(Object channel) {
        Connection current = connections.computeIfAbsent(channel, ignored -> new Connection(channel));
        if (current.closed.get()) throw new IllegalStateException("Passenger connection was replaced");
        return current;
    }

    private void trim(Connection connection) {
        if (connection.input == null && connection.views.isEmpty()) connections.remove(connection.channel, connection);
    }

    private final class Connection {
        final Object channel;
        final AtomicBoolean closed = new AtomicBoolean();
        final Map<Integer, View> views = new ConcurrentHashMap<>();
        volatile Rider input;
        Connection(Object channel) { this.channel = channel; }
    }

    private final class Owner implements OwnerLease {
        final Plugin plugin;
        final Entity host;
        final Object handle;
        final UUID id, world, epoch;
        final int nativeId;
        final Consumer<Throwable> failure;
        final Map<UUID, Rider> owned = new LinkedHashMap<>();
        boolean closed;
        Owner(Plugin plugin, Entity host, Object handle, UUID epoch, Consumer<Throwable> failure) {
            this.plugin = plugin; this.host = host; this.handle = handle; this.epoch = epoch; this.failure = failure;
            id = host.getUniqueId(); nativeId = host.getEntityId(); world = host.getWorld().getUID();
        }
        boolean valid() {
            return !closed && owners.get(id) == this && plugin.isEnabled() && loaded(host, handle, world, id, nativeId)
                    && !platform.hasVehicle(handle);
        }
        @Override public Entity host() { environment.checkThread(); return host; }
        @Override public UUID ownerEpoch() { environment.checkThread(); return epoch; }
        @Override public boolean active() { environment.checkThread(); return valid(); }
        @Override public RiderLease bind(Entity entity, UUID token, Anchor anchor) {
            environment.checkThread();
            Objects.requireNonNull(entity); Objects.requireNonNull(token); Objects.requireNonNull(anchor);
            if (!valid()) { close(); throw new IllegalStateException("Passenger owner is no longer valid"); }
            requireLoaded(entity);
            UUID riderId = entity.getUniqueId();
            if (!world.equals(entity.getWorld().getUID())) throw new IllegalArgumentException("Passenger belongs to another world");
            if (riderId.equals(id) || owners.containsKey(riderId) || riders.containsKey(riderId) || tokens.containsKey(token)) {
                throw new IllegalStateException("Passenger, token or nested host is already owned");
            }
            if (riders.size() >= MAX_RIDERS || owned.size() >= MAX_HOST_RIDERS) throw new IllegalStateException("Passenger budget exhausted");
            Object riderHandle = platform.handle(entity);
            if (platform.hasVehicle(riderHandle)) throw new IllegalStateException("Passenger has a foreign native vehicle");
            Connection input = null;
            if (entity instanceof Player player) {
                Object channel = platform.connection(player);
                if (!environment.ready(channel)) throw new IllegalStateException("Passenger channel is not ready");
                input = connection(channel);
                if (input.input != null) throw new IllegalStateException("Passenger connection already owned");
                if (input.views.size() >= MAX_VIEWER_VIEWS || viewCount >= MAX_VIEWS) {
                    trim(input); throw new IllegalStateException("Passenger observer budget exhausted");
                }
            }
            NativeMountPlatform.Transport transport;
            try { transport = platform.createTransport(handle, riderHandle, anchor); }
            catch (RuntimeException | Error error) { if (input != null) trim(input); throw error; }
            Rider rider = new Rider(this, entity, riderHandle, token, transport, input, anchor);
            owned.put(riderId, rider); riders.put(riderId, rider); tokens.put(token, rider);
            if (input != null) input.input = rider;
            try {
                if (entity instanceof Player player) rider.show(player);
                platform.follow(riderHandle, transport, anchor);
                return rider;
            } catch (RuntimeException | Error error) { rider.close(); throw error; }
        }
        void report(Throwable error) {
            try { failure.accept(error); }
            catch (RuntimeException callbackError) { plugin.getLogger().log(Level.WARNING, "Passenger failure callback failed", callbackError); }
        }
        @Override public void close() {
            environment.checkThread();
            if (closed) return;
            closed = true;
            owners.remove(id, this);
            for (Rider rider : List.copyOf(owned.values())) rider.close();
        }
    }

    private final class Rider implements RiderLease {
        final Owner owner;
        final Entity entity;
        final Object handle;
        final UUID id, token;
        final int nativeId;
        final NativeMountPlatform.Transport transport;
        final Connection input;
        final MountInputMailbox mailbox = new MountInputMailbox();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<Throwable> failed = new AtomicReference<>();
        final Map<UUID, View> views = new LinkedHashMap<>();
        Anchor anchor;
        Rider(Owner owner, Entity entity, Object handle, UUID token, NativeMountPlatform.Transport transport,
              Connection input, Anchor anchor) {
            this.owner = owner; this.entity = entity; this.handle = handle; this.token = token;
            this.transport = transport; this.input = input; this.anchor = anchor;
            id = entity.getUniqueId(); nativeId = entity.getEntityId();
        }
        void failAsync(Throwable error) {
            if (failed.compareAndSet(null, error) && closed.get()) {
                // A late cleanup future has no live owner task left to call. Never invoke the
                // consumer's Bukkit-facing callback on Netty; retain a once-per-lease diagnostic.
                LOGGER.log(Level.WARNING, "[Asteroid] Virtual passenger cleanup was not delivered", error);
            }
        }
        boolean valid() {
            return !closed.get() && failed.get() == null && riders.get(id) == this && owner.valid()
                    && loaded(entity, handle, owner.world, id, nativeId) && !platform.hasVehicle(handle)
                    && (input == null || !input.closed.get() && input.input == this
                    && platform.connection((Player) entity) == input.channel && environment.ready(input.channel));
        }
        void requireActive() {
            if (!valid()) { close(); throw new IllegalStateException("Passenger binding is no longer valid"); }
        }
        @Override public Entity rider() { environment.checkThread(); return entity; }
        @Override public UUID token() { environment.checkThread(); return token; }
        @Override public TransportIds transportIds() { environment.checkThread(); return transport.ids(); }
        @Override public boolean active() { environment.checkThread(); return valid(); }
        @Override public Input latestInput() {
            environment.checkThread();
            if (!valid()) { close(); return Input.EMPTY; }
            return mailbox.read().withNativeSprint(platform.sprint(handle));
        }
        @Override public void follow(Anchor anchor) {
            environment.checkThread(); Objects.requireNonNull(anchor); requireActive();
            try { platform.follow(handle, transport, anchor); this.anchor = anchor; }
            catch (RuntimeException | Error error) { failAsync(error); close(); throw error; }
        }
        @Override public void show(Player viewer) {
            environment.checkThread(); Objects.requireNonNull(viewer); requireActive(); requireLoaded(viewer);
            if (!owner.world.equals(viewer.getWorld().getUID())) throw new IllegalArgumentException("Passenger observer belongs to another world");
            Object channel = platform.connection(viewer);
            if (!environment.ready(channel)) throw new IllegalStateException("Passenger observer channel is not ready");
            View previous = views.get(viewer.getUniqueId());
            if (previous != null && previous.connection.channel == channel && previous.valid()) return;
            if (previous != null) removeView(previous);
            Connection connection = connection(channel);
            if (connection.views.size() >= MAX_VIEWER_VIEWS || viewCount >= MAX_VIEWS) {
                trim(connection); throw new IllegalStateException("Passenger observer budget exhausted");
            }
            View view = new View(this, viewer, connection, nativeId, transport.removePackets());
            views.put(viewer.getUniqueId(), view); connection.views.put(view.riderId, view); viewCount++;
            try { publish(view); }
            catch (RuntimeException | Error error) { removeView(view); throw error; }
        }
        void publish(View view) {
            if (!view.valid() || !view.nativeVisible.get()) return;
            try {
                Object[] packets = transport.spawnPackets(anchor);
                view.published.set(true);
                environment.send(view.connection.channel, packets,
                        () -> !closed.get() && !view.closed.get() && !view.connection.closed.get() && view.nativeVisible.get(), this::failAsync);
            } catch (RuntimeException error) { failAsync(error); close(); throw error; }
        }
        @Override public void replay(Player viewer) {
            environment.checkThread(); Objects.requireNonNull(viewer); requireActive(); requireLoaded(viewer);
            if (!owner.world.equals(viewer.getWorld().getUID())) throw new IllegalArgumentException("Passenger observer belongs to another world");
            View view = views.get(viewer.getUniqueId());
            if (view == null || !view.valid() || platform.connection(viewer) != view.connection.channel
                    || view.connection.views.get(view.riderId) != view) {
                throw new IllegalStateException("Passenger replay requires an existing owned current-channel view");
            }
            if (!view.nativeVisible.get()) throw new IllegalStateException("Passenger native rider is not visible to this observer");
            try {
                Object[] spawn = transport.spawnPackets(anchor);
                Object[] batch = Arrays.copyOf(view.removePackets, view.removePackets.length + spawn.length);
                System.arraycopy(spawn, 0, batch, view.removePackets.length, spawn.length);
                // Consume an observed re-track here: maintenance must not add a later duplicate
                // native show AFTER the consumer has sent its recovered sparse model binding.
                view.needsPublish.set(false);
                view.published.set(true);
                environment.send(view.connection.channel, batch,
                        () -> !closed.get() && !view.closed.get() && !view.connection.closed.get()
                                && view.nativeVisible.get() && view.connection.views.get(view.riderId) == view,
                        this::failAsync);
            } catch (RuntimeException | Error error) { failAsync(error); close(); throw error; }
        }
        @Override public void hide(Player viewer) {
            environment.checkThread(); Objects.requireNonNull(viewer);
            if (!closed.get() && viewer.getUniqueId().equals(id)) throw new IllegalStateException("Cannot hide a passenger's own active vehicle");
            View view = views.get(viewer.getUniqueId());
            if (view != null) removeView(view);
        }
        void removeView(View view) {
            if (!view.closed.compareAndSet(false, true)) return;
            views.remove(view.viewer.getUniqueId(), view);
            view.connection.views.remove(view.riderId, view);
            view.needsPublish.set(false); viewCount--; trim(view.connection);
            if (view.published.getAndSet(false) && environment.ready(view.connection.channel)) {
                try { environment.cleanup(view.connection.channel, view.removePackets, this::failAsync); }
                catch (RuntimeException error) { failAsync(error); }
            }
        }
        @Override public void close() {
            environment.checkThread();
            if (!closed.compareAndSet(false, true)) return;
            mailbox.close();
            riders.remove(id, this); tokens.remove(token, this); owner.owned.remove(id, this);
            if (input != null) { if (input.input == this) input.input = null; trim(input); }
            for (View view : List.copyOf(views.values())) removeView(view);
            try { transport.close(); }
            catch (RuntimeException error) { failAsync(error); }
            Throwable error = failed.get();
            if (error != null) owner.report(error);
        }
    }

    private final class View {
        final Rider rider;
        final Player viewer;
        final Connection connection;
        final int riderId;
        final Object[] removePackets;
        final AtomicBoolean closed = new AtomicBoolean(), published = new AtomicBoolean();
        final AtomicBoolean nativeVisible = new AtomicBoolean(true), needsPublish = new AtomicBoolean();
        View(Rider rider, Player viewer, Connection connection, int riderId, Object[] removePackets) {
            this.rider = rider; this.viewer = viewer; this.connection = connection; this.riderId = riderId;
            this.removePackets = removePackets;
        }
        boolean valid() {
            return !closed.get() && !connection.closed.get() && viewer.isOnline() && viewer.isValid() && !viewer.isDead()
                    && rider.owner.world.equals(viewer.getWorld().getUID()) && platform.connection(viewer) == connection.channel
                    && environment.ready(connection.channel);
        }
    }
}
