plugins {
    java
    `maven-publish`
    // 在根项目声明 apply false，统一 classloader 解决兄弟模块 BuildService 冲突
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

allprojects {
    group = property("group") as String
    version = property("version") as String

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.arcartx.com/repository/maven-releases/")
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// API 和 Core 模块编译目标 Java 17（保证低版本服务端兼容）
// NMS 模块由 paperweight 自行管理编译目标
// Example 模块使用 Java 21（依赖 1.21.4 paper-api）
listOf("asteroid-api", "asteroid-core", "asteroid-nms").forEach { name ->
    project(":$name") {
        tasks.withType<JavaCompile> {
            options.release.set(17)
        }
    }
}

tasks.register("deploy") {
    group = "asteroid"
    description = "Publish asteroid-api and asteroid-nms to Maven repository"
    dependsOn(":asteroid-api:publish", ":asteroid-nms:publish")
}
