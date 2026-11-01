plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Java 17 bytecode, as the apps build theirs, from whichever JDK runs Gradle.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    // The same API (ai.onnxruntime) is in onnxruntime-android and in the desktop jar: each app brings its own.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.31.1")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.31.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}

tasks.test {
    // The fixture of `python -m tools.kotlin_fixture --run runs/<name>`; the tests that need it are skipped without it.
    systemProperty("mathreader.fixture", providers.gradleProperty("fixture").map { rootDir.resolve(it).path }.orElse("").get())
    testLogging { events("passed", "skipped", "failed"); showStandardStreams = true }
}
