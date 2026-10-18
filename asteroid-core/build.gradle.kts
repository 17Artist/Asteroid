dependencies {
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.97.Final")
    compileOnly(project(":asteroid-api"))
    testImplementation(project(":asteroid-api"))
    testImplementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    testImplementation("io.netty:netty-all:4.1.97.Final")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.mockito:mockito-core:5.14.2")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    maxHeapSize = "192m"
    jvmArgs("-XX:ActiveProcessorCount=1")
}

java { withSourcesJar() }

val repoPassword = System.getenv("repo") ?: ""

// Core is also consumed directly when reviewing/compiling version-local implementations.
// Declaring maven-publish on the root alone does not create a publication for this module.
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "asteroid-core"
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
