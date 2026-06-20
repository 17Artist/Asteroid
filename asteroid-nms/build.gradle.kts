plugins {
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

configurations {
    create("shade") {
        isTransitive = false
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    "shade"(project(":asteroid-api"))
    "shade"(project(":asteroid-core"))
}

// 所有 NMS 版本打入同一个 jar：
//   - 1.x 模块走 reobf（Spigot 映射），用 reobfJar 产物；
//   - 26.x 模块（Mojang 映射 + Java 25 字节码）禁用了 reobfJar，用普通 jar 产物。
// 不同字节码版本可共存于一个 jar：类按需懒加载，NMSLoader 只精确加载当前服务端版本的实现类，
// 低版本服务端永远不会触碰 26.x 的 Java 25 类（反之亦然），因此不会触发 UnsupportedClassVersionError。
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.getByName("shade"))
    exclude("META-INF/**")
    archiveClassifier.set("")

    project.parent?.subprojects?.filter {
        it.path.startsWith(":nms:")
    }?.forEach { nmsProject ->
        val reobfTask = nmsProject.tasks.findByName("reobfJar")
        if (reobfTask != null && reobfTask.enabled) {
            val reobfJar = nmsProject.tasks.named("reobfJar")
            dependsOn(reobfJar)
            from(zipTree(reobfJar.map { (it as io.papermc.paperweight.tasks.RemapJar).outputJar.get().asFile }))
        } else {
            val jar = nmsProject.tasks.named("jar")
            dependsOn(jar)
            from(zipTree(jar.map { (it as org.gradle.jvm.tasks.Jar).archiveFile.get().asFile }))
        }
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
}

val repoPassword = System.getenv("repo") ?: ""

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifact(tasks.named("shadowJar"))
            artifactId = "asteroid-nms"
        }
    }
    repositories {
        maven {
            url = uri(property("mavenRepoUrl") as String)
            isAllowInsecureProtocol = true
            credentials {
                username = property("mavenRepoUser") as String
                password = repoPassword
            }
        }
    }
}
