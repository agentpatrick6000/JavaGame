import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
}

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.3"
val jomlVersion = "1.10.5"

val lwjglNatives = when {
    OperatingSystem.current().isMacOsX -> "natives-macos-arm64"
    OperatingSystem.current().isLinux  -> "natives-linux"
    else                                -> "natives-windows"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.voxelgame.Main")
}

dependencies {
    // LWJGL BOM
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // LWJGL modules
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")

    // LWJGL natives
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")

    // JOML (math)
    implementation("org.joml:joml:$jomlVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.14")

    // Java-WebSocket (agent interface)
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    
    // FastUtil (primitive collections - no boxing)
    implementation("it.unimi.dsi:fastutil:8.5.12")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<JavaExec>("run") {
    // macOS needs this JVM arg for GLFW
    if (OperatingSystem.current().isMacOsX) {
        jvmArgs("-XstartOnFirstThread")
    }
    // Enough heap for chunk loading + BFS light propagation
    jvmArgs("-Xmx4g", "-Xms512m")
}

// Fat JAR task - bundles all dependencies into single executable JAR
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "com.voxelgame.Main"
        // macOS needs this for GLFW
        if (OperatingSystem.current().isMacOsX) {
            attributes["JVM-Args"] = "-XstartOnFirstThread"
        }
    }
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
