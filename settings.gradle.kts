pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "Asteroid"

include("asteroid-api")
include("asteroid-core")
include("asteroid-nms")
include("asteroid-example")

// NMS version modules
file("nms").listFiles()?.filter {
    it.isDirectory && it.name.startsWith("v")
}?.sorted()?.forEach {
    include("nms:${it.name}")
}
