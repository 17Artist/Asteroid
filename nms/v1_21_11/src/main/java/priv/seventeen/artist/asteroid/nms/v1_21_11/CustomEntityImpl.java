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
package priv.seventeen.artist.asteroid.nms.v1_21_11;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import priv.seventeen.artist.asteroid.entity.CustomEntity;
import priv.seventeen.artist.asteroid.entity.ability.*;
import priv.seventeen.artist.asteroid.entity.ability.MountAbility.MountType;
import priv.seventeen.artist.asteroid.entity.ability.SeatAbility.Offset;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CustomEntityImpl extends LivingEntity implements CustomEntity {

    private static final VarHandle JUMPING_HANDLE;
    private static final VarHandle BUKKIT_ENTITY;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LivingEntity.class, MethodHandles.lookup());
            JUMPING_HANDLE = lookup.findVarHandle(LivingEntity.class, "bM", boolean.class);

            MethodHandles.Lookup entityLookup = MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup());
            BUKKIT_ENTITY = entityLookup.findVarHandle(Entity.class, "bukkitEntity", CraftEntity.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final Map<Class<? extends Ability>, Ability> abilities = new ConcurrentHashMap<>();
    private final List<TickableAbility> tickableAbilities = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<SeatEntityImpl> seats = new ArrayList<>();

    private @Nullable org.bukkit.entity.Entity owner;
    private double offsetX, offsetY, offsetZ;

    private float hitboxWidth = 0.6F;
    private float hitboxHeight = 1.8F;

    private MountType mountType = MountType.NONE;
    private float moveSpeed = 0.3F;
    private float flyUpSpeed = 0.08F;
    private float flyDownSpeed = 0.06F;
    private float boatTurnSpeed = 3.0F;

    private double vehicleSpeed = 0;

    public CustomEntityImpl(Level level) {
        super(EntityType.ARMOR_STAND, level);
        this.setInvisible(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setSilent(true);
    }

    public CustomEntityImpl(Level level, @Nullable org.bukkit.entity.Entity owner) {
        this(level);
        this.owner = owner;
    }

    @Override
    public int getEntityId() {
        return getId();
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return getUUID();
    }

    @Override
    public @Nullable org.bukkit.entity.Entity getOwner() {
        return owner;
    }

    @Override
    public @NotNull CraftLivingEntity getBukkitEntity() {
        CraftEntity craft = (CraftEntity) BUKKIT_ENTITY.get(this);
        if (craft == null) {
            synchronized (this) {
                craft = (CraftEntity) BUKKIT_ENTITY.get(this);
                if (craft == null) {
                    craft = new CraftLivingEntity((CraftServer) Bukkit.getServer(), this);
                    BUKKIT_ENTITY.set(this, craft);
                }
            }
        }
        return (CraftLivingEntity) craft;
    }

    @Override
    public @NotNull Location getLocation() {
        return new Location(
                getBukkitEntity().getWorld(),
                getX(), getY(), getZ(),
                getYRot(), getXRot()
        );
    }

    @Override
    public <T extends Ability> void addAbility(T ability) {
        abilities.put(ability.getClass(), ability);
        if (ability instanceof TickableAbility tickable) {
            tickableAbilities.add(tickable);
        }
        ability.onAttach(this);
    }

    @Override
    public <T extends Ability> void removeAbility(Class<T> type) {
        Ability removed = abilities.remove(type);
        if (removed != null) {
            if (removed instanceof TickableAbility) {
                tickableAbilities.remove(removed);
            }
            removed.onDetach(this);
        }
    }

    @Override
    public @Nullable <T extends Ability> T getAbility(Class<T> type) {
        return (T) abilities.get(type);
    }

    @Override
    public boolean hasAbility(Class<? extends Ability> type) {
        return abilities.containsKey(type);
    }

    @Override
    public Collection<Ability> getAbilities() {
        return Collections.unmodifiableCollection(abilities.values());
    }

    @Override
    public void remove() {
        RemoveAbility removeAbility = getAbility(RemoveAbility.class);
        if (removeAbility != null && removeAbility.getCallback() != null) {
            removeAbility.getCallback().onRemove();
        }

        for (SeatEntityImpl seat : seats) {
            seat.ejectPassengers();
            seat.discard();
        }
        seats.clear();
        this.discard();
    }

    @Override
    public void nmsSetSize(double width, double height) {
        this.hitboxWidth = (float) width;
        this.hitboxHeight = (float) height;
        this.refreshDimensions();
        ResizeAbility resizeAbility = getAbility(ResizeAbility.class);
        if (resizeAbility != null && resizeAbility.getCallback() != null) {
            resizeAbility.getCallback().onResize(width, height);
        }
    }

    @Override
    public void nmsSetOffset(double x, double y, double z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    @Override
    public void nmsSetMountType(MountType type, float moveSpeed, float flyUpSpeed, float flyDownSpeed, float boatTurnSpeed) {
        this.mountType = type;
        this.moveSpeed = moveSpeed;
        this.flyUpSpeed = flyUpSpeed;
        this.flyDownSpeed = flyDownSpeed;
        this.boatTurnSpeed = boatTurnSpeed;

        this.vehicleSpeed = 0;

        switch (type) {
            case FLY, DIVING -> {
                this.setNoGravity(true);
                this.noPhysics = false;
            }
            case GROUND, BOAT, CAR -> {
                this.setNoGravity(false);
                this.noPhysics = false;
            }
            case NONE -> {
                this.setNoGravity(true);
                this.noPhysics = true;
            }
        }
    }

    @Override
    public void nmsAddSeat(double x, double y, double z) {
        SeatEntityImpl seat = new SeatEntityImpl(this.level(), this, new Offset(x, y, z));
        seats.add(seat);
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(seat);
        }
    }

    @Override
    public void nmsAddPassenger(@Nullable org.bukkit.entity.Entity passenger) {
        if (passenger == null) return;
        net.minecraft.world.entity.Entity nmsPassenger = ((CraftEntity) passenger).getHandle();

        for (SeatEntityImpl seat : seats) {
            if (seat.getPassengers().isEmpty()) {
                nmsPassenger.startRiding(seat, true, true);
                return;
            }
        }

        nmsPassenger.startRiding(this, true, true);
    }

    @Override
    public void nmsClearSeats() {
        for (SeatEntityImpl seat : seats) {
            if (seat != null && !seat.isRemoved()) {
                seat.ejectPassengers();
                seat.discard();
            }
        }
        seats.clear();
    }

    @Override
    public void tick() {

        if (this.mountType != MountType.NONE) {
            LivingEntity passenger = this.getControllingPassenger();
            if (passenger instanceof Player player) {
                handleMountControl(player);
            }
        }

        super.tick();

        FollowOwnerAbility followAbility = getAbility(FollowOwnerAbility.class);

        if (followAbility != null && followAbility.isRemoveOnOwnerInvalid()
                && owner != null && !owner.isValid()) {
            this.remove();
            return;
        }

        if (owner != null && owner.isValid()) {
            if (this.mountType != MountType.NONE) {

                if (owner instanceof CraftEntity ce) {
                    Entity nmsOwner = ce.getHandle();
                    nmsOwner.setPos(this.position());
                    nmsOwner.xo = this.xo;
                    nmsOwner.yo = this.yo;
                    nmsOwner.zo = this.zo;
                    nmsOwner.xOld = this.xOld;
                    nmsOwner.yOld = this.yOld;
                    nmsOwner.zOld = this.zOld;
                    nmsOwner.setYRot(this.getYRot());
                    nmsOwner.yRotO = this.yRotO;
                    nmsOwner.setXRot(this.getXRot());
                    nmsOwner.xRotO = this.xRotO;
                    if (nmsOwner instanceof LivingEntity livingOwner) {
                        livingOwner.yBodyRot = this.yBodyRot;
                        livingOwner.yBodyRotO = this.yBodyRotO;
                        livingOwner.yHeadRot = this.yHeadRot;
                        livingOwner.yHeadRotO = this.yHeadRotO;
                    }
                }
            } else if (followAbility != null && followAbility.isEnabled()) {

                org.bukkit.Location ownerLoc = owner.getLocation();
                double yawRad = Math.toRadians(ownerLoc.getYaw());
                double sin = Math.sin(yawRad);
                double cos = Math.cos(yawRad);
                double x = ownerLoc.getX() + offsetX * cos - offsetZ * sin;
                double y = ownerLoc.getY() + offsetY;
                double z = ownerLoc.getZ() + offsetX * sin + offsetZ * cos;
                this.setPos(x, y, z);
            }
        }

        for (TickableAbility tickable : tickableAbilities) {
            tickable.tick(this);
        }

        for (SeatEntityImpl seat : seats) {
            if (!seat.isRemoved()) {
                seat.updatePosition();
            }
        }
    }

    private void handleMountControl(Player player) {

        boolean isVehicleMode = this.mountType == MountType.BOAT
                || this.mountType == MountType.CAR
                || this.mountType == MountType.DIVING;

        if (!isVehicleMode) {
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.setRot(this.getYRot(), this.getXRot());
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.yBodyRot;
        }

        float forward = 0;
        float strafe = 0;
        boolean jumping;

        if (player instanceof ServerPlayer serverPlayer) {
            net.minecraft.world.entity.player.Input input = serverPlayer.getLastClientInput();
            if (input.forward()) forward += 1.0F;
            if (input.backward()) forward -= 1.0F;
            if (input.left()) strafe += 1.0F;
            if (input.right()) strafe -= 1.0F;
            jumping = input.jump();
        } else {
            forward = player.zza;
            strafe = player.xxa;
            jumping = (boolean) JUMPING_HANDLE.get(player);
        }

        Vec3 motion = this.getDeltaMovement();
        double dy = motion.y;
        boolean onGround = checkOnGround();
        boolean inWater = this.isInWater();

        double dx = 0;
        double dz = 0;

        switch (this.mountType) {
            case BOAT -> {

                float turnMultiplier = (float) (0.3 + Math.abs(this.vehicleSpeed) * 0.7 / this.moveSpeed);
                if (strafe > 0) {
                    this.setYRot(this.getYRot() - this.boatTurnSpeed * turnMultiplier);
                } else if (strafe < 0) {
                    this.setYRot(this.getYRot() + this.boatTurnSpeed * turnMultiplier);
                }
                this.yRotO = this.getYRot();
                this.setRot(this.getYRot(), this.getXRot());
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.yBodyRot;

                handleVehicleAcceleration(forward, inWater ? 0.96 : 0.98);

                double yawRad = Math.toRadians(this.getYRot());
                double speedMultiplier = inWater ? 1.0 : 0.5;
                dx = -this.vehicleSpeed * speedMultiplier * Math.sin(yawRad);
                dz = this.vehicleSpeed * speedMultiplier * Math.cos(yawRad);

                if (inWater) {
                    if (jumping && this.isInWater()) {
                        dy = this.getJumpPower();
                    } else {
                        dy = 0.02;
                    }
                } else if (!this.onGround()) {
                    dy -= 0.08;
                    dy *= 0.98;
                }
                if (this.onGround() && dy < 0) {
                    dy = 0;
                }
            }

            case CAR -> {

                float turnMultiplier = (float) (0.3 + Math.abs(this.vehicleSpeed) * 0.7 / this.moveSpeed);
                if (strafe > 0) {
                    this.setYRot(this.getYRot() - this.boatTurnSpeed * turnMultiplier);
                } else if (strafe < 0) {
                    this.setYRot(this.getYRot() + this.boatTurnSpeed * turnMultiplier);
                }
                this.yRotO = this.getYRot();
                this.setRot(this.getYRot(), this.getXRot());
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.yBodyRot;

                if (inWater) {
                    this.vehicleSpeed *= 0.8;
                    if (Math.abs(this.vehicleSpeed) < 0.001) {
                        this.vehicleSpeed = 0;
                    }
                } else {
                    handleVehicleAcceleration(forward, this.onGround() ? 0.98 : 0.99);
                }

                double yawRad = Math.toRadians(this.getYRot());
                dx = -this.vehicleSpeed * Math.sin(yawRad);
                dz = this.vehicleSpeed * Math.cos(yawRad);

                if (!this.onGround()) {
                    if (inWater) {
                        dy -= 0.12;
                        dy *= 0.95;
                    } else {
                        dy -= 0.08;
                        dy *= 0.98;
                    }
                }
                if (this.onGround() && dy < 0) {
                    dy = 0;
                }
            }

            case DIVING -> {

                float turnMultiplier = (float) (0.3 + Math.abs(this.vehicleSpeed) * 0.7 / this.moveSpeed);
                if (strafe > 0) {
                    this.setYRot(this.getYRot() - this.boatTurnSpeed * turnMultiplier);
                } else if (strafe < 0) {
                    this.setYRot(this.getYRot() + this.boatTurnSpeed * turnMultiplier);
                }
                this.yRotO = this.getYRot();
                this.setRot(this.getYRot(), this.getXRot());
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.yBodyRot;

                handleVehicleAcceleration(forward, inWater ? 0.96 : 0.98);

                double yawRad = Math.toRadians(this.getYRot());
                double speedMultiplier = inWater ? 1.0 : 0.3;
                dx = -this.vehicleSpeed * speedMultiplier * Math.sin(yawRad);
                dz = this.vehicleSpeed * speedMultiplier * Math.cos(yawRad);

                if (inWater) {

                    if (jumping) {
                        if (!this.isUnderWater() && this.isInWater()) {
                            dy = this.getJumpPower();
                        } else {
                            dy = this.flyUpSpeed;
                        }
                    } else if (player.isShiftKeyDown()) {
                        dy = -this.flyDownSpeed;
                    } else {
                        dy = 0;
                    }
                } else {
                    if (!this.onGround()) {
                        dy -= 0.08;
                        dy *= 0.98;
                    }
                    if (this.onGround() && dy < 0) {
                        dy = 0;
                    }
                    if (player.isShiftKeyDown()) {
                        dy = -this.flyDownSpeed;
                    }
                }
            }

            case FLY -> {

                if (forward != 0 || strafe != 0) {
                    double yawRad = Math.toRadians(this.getYRot());
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    dx = (-forward * sin + strafe * cos) * this.moveSpeed;
                    dz = (forward * cos + strafe * sin) * this.moveSpeed;
                }

                if (jumping) {
                    dy = this.flyUpSpeed;
                } else if (player.isShiftKeyDown() && !this.onGround() && !this.isInWater()) {
                    dy = -this.flyDownSpeed;
                } else if (!this.onGround()) {
                    dy = 0;
                }
            }

            case GROUND -> {

                if (forward != 0 || strafe != 0) {
                    double yawRad = Math.toRadians(this.getYRot());
                    double sin = Math.sin(yawRad);
                    double cos = Math.cos(yawRad);
                    double speedMultiplier = inWater ? 0.5 : 1.0;
                    dx = (-forward * sin + strafe * cos) * this.moveSpeed * speedMultiplier;
                    dz = (forward * cos + strafe * sin) * this.moveSpeed * speedMultiplier;
                }

                boolean canJump = onGround || this.isInWater();
                if (jumping && canJump) {
                    dy = this.getJumpPower();
                } else if (!this.onGround()) {
                    dy -= 0.08;
                    dy *= 0.98;
                }
                if (this.onGround() && dy < 0) {
                    dy = 0;
                }
            }

            default -> {}
        }

        this.setDeltaMovement(dx, dy, dz);
    }

    private void handleVehicleAcceleration(float forward, double friction) {
        double acceleration = 0.02;

        if (forward > 0) {
            this.vehicleSpeed += acceleration;
            if (this.vehicleSpeed > this.moveSpeed) {
                this.vehicleSpeed = this.moveSpeed;
            }
        } else if (forward < 0) {
            this.vehicleSpeed -= acceleration * 1.5;
            if (this.vehicleSpeed < -this.moveSpeed * 0.5) {
                this.vehicleSpeed = -this.moveSpeed * 0.5;
            }
        } else {
            this.vehicleSpeed *= friction;
            if (Math.abs(this.vehicleSpeed) < 0.001) {
                this.vehicleSpeed = 0;
            }
        }
    }

    @Override
    public void travel(@NotNull Vec3 movementInput) {
        LivingEntity passenger = this.getControllingPassenger();

        if (this.isVehicle() && passenger != null && this.mountType != MountType.NONE) {

            this.move(MoverType.SELF, this.getDeltaMovement());

            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.multiply(0.91, 0.98, 0.91));
        } else {
            super.travel(movementInput);
        }
    }

    @Override
    protected @NotNull Vec3 getRiddenInput(@NotNull Player player, @NotNull Vec3 travelVector) {
        if (this.mountType == MountType.NONE) {
            return Vec3.ZERO;
        }

        float strafe = player.xxa * 0.5F;
        float forward = player.zza;

        if (this.mountType == MountType.BOAT
                || this.mountType == MountType.CAR
                || this.mountType == MountType.DIVING) {
            return new Vec3(0, 0, forward);
        }

        float vertical = 0;
        if (this.mountType == MountType.FLY && forward != 0) {
            vertical = (float) (-Math.sin(Math.toRadians(player.getXRot())) * forward * 0.5);
        }

        return new Vec3(strafe, vertical, forward);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        if (this.mountType != MountType.NONE && !this.seats.isEmpty()) {
            SeatEntityImpl firstSeat = this.seats.get(0);
            if (!firstSeat.isRemoved() && !firstSeat.getPassengers().isEmpty()) {
                Entity passenger = firstSeat.getPassengers().get(0);
                if (passenger instanceof LivingEntity living) {
                    return living;
                }
            }
        }

        Entity first = getFirstPassenger();
        if (first instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    public boolean isControlledByLocalInstance() {
        return this.getControllingPassenger() instanceof Player;
    }

    protected boolean checkOnGround() {
        if (this.onGround()) {
            return true;
        }
        AABB boundingBox = this.getBoundingBox();
        AABB checkBox = new AABB(
                boundingBox.minX + 0.001,
                boundingBox.minY - 0.1,
                boundingBox.minZ + 0.001,
                boundingBox.maxX - 0.001,
                boundingBox.minY,
                boundingBox.maxZ - 0.001
        );
        Iterable<VoxelShape> collisions = this.level().getBlockCollisions(this, checkBox);
        return collisions.iterator().hasNext();
    }

    public boolean isOnGroundCustom() {
        return checkOnGround();
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource source, float amount) {
        DamageAbility damageAbility = getAbility(DamageAbility.class);
        if (damageAbility != null && damageAbility.getCallback() != null) {
            org.bukkit.entity.Entity attacker = source.getEntity() != null
                    ? source.getEntity().getBukkitEntity() : null;
            if (damageAbility.getCallback().onDamage(attacker, amount)) {
                return false;
            }
        }
        return false;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        InteractAbility interactAbility = getAbility(InteractAbility.class);
        if (interactAbility != null && interactAbility.getCallback() != null) {
            interactAbility.getCallback().onInteract(
                    player.getBukkitEntity(),
                    hand == InteractionHand.MAIN_HAND
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public @NotNull InteractionResult interactAt(@NotNull Player player, @NotNull Vec3 vec, @NotNull InteractionHand hand) {
        InteractAbility interactAbility = getAbility(InteractAbility.class);
        if (interactAbility != null && interactAbility.getCallback() != null) {
            interactAbility.getCallback().onInteract(
                    player.getBukkitEntity(),
                    hand == InteractionHand.MAIN_HAND
            );
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void pushEntities() {}

    @Override
    public void push(@NotNull Entity entity) {}

    @Override
    protected void doPush(@NotNull Entity entity) {}

    @Override
    protected int decreaseAirSupply(int air) {
        return air;
    }

    @Override
    protected int increaseAirSupply(int air) {
        return air;
    }

    @Override
    public @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        return EntityDimensions.scalable(this.hitboxWidth, this.hitboxHeight);
    }

    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public @NotNull ItemStack getItemBySlot(@NotNull EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(@NotNull EquipmentSlot slot, @NotNull ItemStack stack) {

    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public List<SeatEntityImpl> getSeats() {
        return seats;
    }

    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }

    public MountType getMountType() { return mountType; }
}
