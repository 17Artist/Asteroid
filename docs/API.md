# Asteroid API 文档

## 初始化

```java
import priv.seventeen.artist.asteroid.internal.NMSLoader;

public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        NMSLoader.load(this); // 自动检测版本，加载 NMS 实现，注册数据包监听事件
    }
}
```

所有 API 通过 `AsteroidAPI` 静态方法访问。

---

## 自定义实体

基于 LivingEntity 的自定义实体，通过 Ability 组合模式按需加载功能。

### 创建

```java
// 绑定 owner（实体可跟随 owner）
CustomEntity entity = AsteroidAPI.createCustomEntity(player, 2.0, 1.5);

// 在指定位置创建
CustomEntity entity = AsteroidAPI.createCustomEntity(location, 2.0, 1.5);
```

### 能力

```java
// 碰撞箱 + 偏移
HitboxAbility hitbox = new HitboxAbility(2.0, 1.5);
hitbox.setOffset(0, 1.0, 2.0);
entity.addAbility(hitbox);

// 伤害回调（返回 true 取消伤害）
entity.addAbility(new DamageAbility((attacker, damage) -> false));

// 交互回调
entity.addAbility(new InteractAbility((interactor, mainHand) -> {
    interactor.sendMessage("右键交互");
}));

// 移除回调
entity.addAbility(new RemoveAbility(() -> {}));

// 跟随 owner
entity.addAbility(new FollowOwnerAbility());

// 自定义 tick
entity.addAbility(new CustomTickAbility(e -> {
    e.getBukkitEntity().getWorld().spawnParticle(Particle.FLAME, e.getLocation(), 1);
}));

// 查询 / 移除
entity.hasAbility(HitboxAbility.class);
entity.removeAbility(HitboxAbility.class);
entity.getAbilities(); // 所有已挂载的能力
```

### 坐骑

5 种模式：`GROUND`（地面）、`FLY`（飞行）、`BOAT`（船）、`CAR`（汽车）、`DIVING`（潜艇）。

```java
CustomEntity mount = AsteroidAPI.createCustomEntity(player, 1.5, 1.0);

// 飞行坐骑
mount.addAbility(new MountAbility(MountAbility.MountType.FLY, 0.5F));

// 多座位
SeatAbility seats = new SeatAbility();
mount.addAbility(seats);
seats.addSeat(0, 0, 0);        // 驾驶位
seats.addSeat(0.8, 0, -0.5);   // 右后
seats.addSeat(-0.8, 0, -0.5);  // 左后
seats.addPassenger(mount, player);

// 船（惯性系统，A/D 转向，水中浮力）
mount.addAbility(new MountAbility(MountAbility.MountType.BOAT, 0.4F, 0.08F, 0.06F, 3.5F));

// 潜艇（空格上浮，Shift 下潜）
mount.addAbility(new MountAbility(MountAbility.MountType.DIVING, 0.3F));
```

### 移除

```java
entity.remove(); // 触发 RemoveAbility 回调，清理座位
```

---

## 属性桥接

跨版本统一的原版 AttributeModifier 操作。1.20.4 及以下使用 UUID 标识，1.21+ 使用 ResourceLocation 标识，API 层完全透明。

```java
AttributeBridge bridge = AsteroidAPI.getAttributeBridge();

// 属性 ID：1.20.4 及以下用 "minecraft:generic.max_health"，1.21+ 用 "minecraft:max_health"
// 可通过 getAvailableAttributes() 获取当前版本的完整列表

// 设置修改器（operation: 0=加法, 1=基础乘法, 2=最终乘法）
bridge.setModifier(entity, "minecraft:max_health", "myplugin:bonus_hp", 10.0, 0);

// 查询
bridge.getBaseValue(entity, "minecraft:max_health");   // 基础值
bridge.getFinalValue(entity, "minecraft:max_health");   // 最终值（含修改器）
bridge.hasAttribute(entity, "minecraft:max_health");    // 是否拥有该属性

// 移除
bridge.removeModifier(entity, "minecraft:max_health", "myplugin:bonus_hp");
bridge.removeAllModifiers(entity, "myplugin:");  // 按前缀批量移除

// 获取当前版本所有可用属性
List<String> attrs = bridge.getAvailableAttributes();
```

---

## 数据包

### 监听

```java
AsteroidAPI.addPacketListener(plugin, new PacketListener() {
    @Override
    public void onReceive(PacketEvent event) {
        // 语义化判断 + 读取
        if (event.is(PacketType.Play.Client.INTERACT)) {
            int entityId = event.read("entityId", int.class);
        }

        // 按类型索引读取（不依赖 PacketType 注册）
        if (event.getPacketName().contains("MovePlayer")) {
            double x = event.fields().readDouble(0);
        }
    }

    @Override
    public void onSend(PacketEvent event) {
        if (event.is(PacketType.Play.Server.ENTITY_DESTROY)) {
            event.setCancelled(true);
        }
    }
});

// 移除监听
AsteroidAPI.removePacketListener(plugin, listener);
```

### 构造与发送

```java
// 工厂方法
Packets.sendEntityDestroy(player, 12345);
Packets.sendEntityVelocity(player, entityId, 0, 8000, 0);
Packets.sendEntityHeadRotation(player, entityId, 90F);
Packets.sendSetPassengers(player, vehicleId, passengerId1, passengerId2);
Packets.sendSetCamera(player, entityId);

// 语义化构造
Object packet = PacketBuilder.create(PacketType.Play.Server.ENTITY_VELOCITY)
    .writeSemantic("entityId", player.getEntityId())
    .writeSemantic("velocityX", 0)
    .writeSemantic("velocityY", 8000)
    .writeSemantic("velocityZ", 0)
    .getPacket();
AsteroidAPI.getPacketHandler().sendPacket(player, packet);

// 底层字段读写
PacketFields fields = PacketFields.of(nmsPacket);
int id = fields.readInt(0);
fields.writeInt(0, newId);
```

### 手动注入

```java
AsteroidAPI.getPacketHandler().inject(player);
AsteroidAPI.getPacketHandler().uninject(player);
```

---

## ItemTag

跨版本统一的 NBT / DataComponent 读写。1.20.4 及以下走 NBTTagCompound，1.20.5+ 走 DataComponent (custom_data)，API 完全透明。

```java
// 读取
ItemTag tag = ItemTag.fromItemStack(item);

// 基本类型
tag.putString("id", "my_sword");
tag.putInt("level", 5);
tag.putBoolean("enchanted", true);
tag.putDouble("damage", 15.5);

// 深度路径（自动创建中间节点）
tag.putDeep("stats.attack", ItemTagData.of(15.5));
tag.putDeep("stats.defense", ItemTagData.of(8));

// 保存回物品（返回新 ItemStack）
item = tag.saveTo(item);

// 读取
String id = tag.getString("id");
int level = tag.getInt("level");
double attack = tag.getDeep("stats.attack").asDouble();

// 删除
tag.removeDeep("stats.attack");

// 自动类型推断
tag.putAny("key", anyObject);
tag.putDeepAny("path.to.key", anyObject);

// 深拷贝
ItemTag copy = tag.deepClone();
```

---

## 实体 NMS 操作

```java
IEntityNMS nms = AsteroidAPI.getEntityNMS();

nms.setSize(entity, 2.0F, 3.0F);           // 修改碰撞箱
nms.setPosition(entity, x, y, z);           // 设置位置（不触发 Bukkit 事件）
nms.setRotation(entity, yaw, pitch);        // 设置朝向
nms.isMoveKeyDown(player);                  // 检测玩家是否按下移动键

// 遍历能看到该实体的所有玩家
nms.doWithSeenBy(entity, player -> {
    // 发送自定义数据包等
});
```

---

## 生物 AI

```java
IMobAI ai = AsteroidAPI.getMobAI();

ai.clearGoals(mob);                         // 清除所有 AI Goal
ai.clearTargetGoals(mob);                   // 清除所有 Target Goal
ai.addGoal(mob, 1, nmsGoal);               // 添加自定义 Goal（NMS 对象）
ai.addTargetGoal(mob, 1, nmsGoal);
ai.removeGoal(mob, goalClass);              // 按类型移除
ai.removeTargetGoal(mob, goalClass);

Object nmsEntity = ai.getNMSEntity(mob);    // 获取 NMS 实体
Object goalSelector = ai.getGoalSelector(mob);
Object targetSelector = ai.getTargetSelector(mob);
```

---

## 物品序列化

```java
IItemStackNMS itemNms = AsteroidAPI.getItemStackNMS();

String json = itemNms.item2Json(itemStack);     // 物品 → JSON（SNBT 格式）
ItemStack item = itemNms.json2Item(json);        // JSON → 物品
```

---

## FakeOp

临时赋予 OP 权限执行命令，执行完自动恢复：

```java
FakeOp.execute(player, "gamemode creative");       // 默认等级 4
FakeOp.execute(player, "give @s diamond 64", 2);   // 指定等级
```

---

## Folia 调度器

自动检测运行环境（Folia / Paper / Spigot），选择正确的调度方式：

```java
// 全局 / 主线程
FoliaScheduler.runTask(plugin, () -> {});
FoliaScheduler.runTaskLater(plugin, () -> {}, 20L);
FoliaScheduler.runTaskTimer(plugin, () -> {}, 0L, 20L);

// 实体区域（Folia 下在实体所在区域线程执行）
FoliaScheduler.runEntityTask(plugin, entity, () -> {});

// 位置区域
FoliaScheduler.runLocationTask(plugin, location, () -> {});

// 异步
FoliaScheduler.runAsync(plugin, () -> {});
FoliaScheduler.runAsyncLater(plugin, () -> {}, 20L);
Object task = FoliaScheduler.runAsyncTimer(plugin, () -> {}, 0L, 20L);

// 取消
FoliaScheduler.cancelTask(task);
```

### 平台检测

```java
PaperCompat.isPaper();   // 是否 Paper
PaperCompat.isFolia();   // 是否 Folia
PaperCompat.isSpigot();  // 是否纯 Spigot
```
