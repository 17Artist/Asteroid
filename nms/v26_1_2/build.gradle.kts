import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    id("io.papermc.paperweight.userdev")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

paperweight {
    reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.72-stable")
    compileOnly(project(":asteroid-api"))
    compileOnly(project(":asteroid-core"))
}

tasks.reobfJar {
    enabled = false
}
