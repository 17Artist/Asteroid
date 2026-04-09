# Asteroid

Minecraft NMS 跨版本操作库，覆盖 1.18.2 ~ 26.1。


## 支持版本

1.18.2 / 1.19 / 1.19.3 / 1.19.4 / 1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.11 / 26.1

兼容 Paper / Spigot / Folia。

## 引入依赖

```kotlin
repositories {
    maven("https://repo.arcartx.com/repository/maven-releases/")
}

dependencies {
    implementation("priv.seventeen.artist.asteroid:asteroid-nms:1.0.1")
}
```

`asteroid-nms` 是 shadowJar，包含 API 接口 + 核心逻辑 + 全版本 NMS 实现。使用 Shadow / ShadowJar 插件将其 shade 进你的插件即可。

如果只需要编译时接口（不打包 NMS 实现）：

```kotlin
compileOnly("priv.seventeen.artist.asteroid:asteroid-api:1.0.1")
```

## 初始化

在插件 `onEnable` 中调用：

```java
NMSLoader.load(this);
```

传入 Plugin 实例会自动注册数据包监听的事件。不传参数则需要手动调用 `inject()`。

## 功能

- 自定义实体 + 模块化能力系统（碰撞箱、坐骑、多座位、伤害回调、交互回调等）
- 原版属性桥接（AttributeBridge，跨版本统一的 AttributeModifier 操作）
- 数据包拦截与构造（Netty Pipeline 注入，语义化 PacketType）
- ItemTag NBT / DataComponent 统一读写
- 实体 NMS 操作（碰撞箱、位置、朝向、可见玩家遍历）
- 生物 AI Goal 操作
- 物品 JSON 序列化 / 反序列化
- FakeOp 临时权限执行
- Folia / Paper / Spigot 统一调度器

详细用法见 [API 文档](docs/API.md)。


## License

Apache License 2.0
