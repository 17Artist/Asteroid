import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    id("io.papermc.paperweight.userdev")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// 用 Java 25 工具链编译以读取 Mojang 26 的 classpath，但输出 Java 21 字节码：
// 这些类仅引用 MC 26 符号，运行时才在 Java 25 服务端解析。降低字节码版本后，
// Paper 旧版本(1.20.5+)加载插件时用 ASM 做重映射能读取本 jar 中的它们，
// 不会再因 "Unsupported class file major version 69" 导致整包重映射失败。
tasks.withType<JavaCompile> {
    options.release.set(21)
}
// paper-api 26.2 的 Gradle 元数据声明 JVM 25；release=21 会让依赖解析按 JVM 21 查找而失败。
// 强制编译类路径按 JVM 25 解析（javac 以 --release 21 输出字节码，仍可读取高版本 classpath）。
configurations.named("compileClasspath") {
    attributes {
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

paperweight {
    reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

dependencies {
    paperweight.paperDevBundle("26.2.build.25-alpha")
    compileOnly(project(":asteroid-api"))
    compileOnly(project(":asteroid-core"))
}

tasks.reobfJar {
    enabled = false
}
