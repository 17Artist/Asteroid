import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    id("io.papermc.paperweight.userdev")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

paperweight {
    reobfArtifactConfiguration = ReobfArtifactConfiguration.REOBF_PRODUCTION
}

dependencies {
    paperweight.paperDevBundle("1.20.1-R0.1-SNAPSHOT")
    compileOnly(project(":asteroid-api"))
    compileOnly(project(":asteroid-core"))
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

tasks.named<io.papermc.paperweight.tasks.RemapJar>("reobfJar") {
    outputJar.set(layout.buildDirectory.file("libs/${project.name}-${project.version}-reobf.jar"))
}

// Actual mapped ABI/configuration regressions; not real navigation/flight acceptance.
configurations.testImplementation { extendsFrom(configurations.compileOnly.get()) }
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "512m"
    jvmArgs("-XX:ActiveProcessorCount=2")
}
