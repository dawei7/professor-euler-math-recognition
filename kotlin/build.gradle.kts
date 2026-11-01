plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 compiles Kotlin itself; declaring the Kotlin plugin here pins the compiler version it uses.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.20" apply false
}

// Builds go outside the repository.
val buildRoot = providers.gradleProperty("mathReaderBuildRoot").orElse(System.getProperty("java.io.tmpdir") + "/math-reader-build")
allprojects { layout.buildDirectory.set(file(buildRoot.get() + "/" + project.name)) }
