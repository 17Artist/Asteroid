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
