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

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.getByName("shade"))
    exclude("META-INF/**")
    archiveClassifier.set("")

    project.parent?.subprojects?.filter {
        it.path.startsWith(":nms:") && it.name != "v26_1"
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

val v26_1Project = project.parent?.subprojects?.find { it.name == "v26_1" }
if (v26_1Project != null) {
    tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar26") {
        configurations = listOf(project.configurations.getByName("shade"))
        exclude("META-INF/**")
        archiveClassifier.set("mc26")

        val jar = v26_1Project.tasks.named("jar")
        dependsOn(jar)
        from(zipTree(jar.map { (it as org.gradle.jvm.tasks.Jar).archiveFile.get().asFile }))
    }

    tasks.named("build") {
        dependsOn("shadowJar", "shadowJar26")
    }
} else {
    tasks.named("build") {
        dependsOn("shadowJar")
    }
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
