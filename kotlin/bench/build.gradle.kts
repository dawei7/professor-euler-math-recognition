import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
}

android {
    namespace = "com.professoreuler.mathreader.bench"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.professoreuler.mathreader.bench"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        // Phones and tablets: ONNX Runtime's library is 34 MB for each processor type.
        ndk { abiFilters += providers.gradleProperty("benchAbi").orElse("arm64-v8a").get() }
    }
    buildTypes {
        // Timed as an app is: not debuggable, which ART runs slower. Signed with the debug key, never published.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":reader"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")
}

/** The model and the inks to time, from an export with its fixture (`-PbenchAssets=<run>/onnx`). */
abstract class BenchAssets : DefaultTask() {
    @get:Input abstract val from: Property<String>
    @get:OutputDirectory abstract val into: DirectoryProperty

    @TaskAction
    fun copy() {
        val source = File(from.get())
        val out = into.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        for (name in listOf("encoder.onnx", "step.int8mm.onnx", "vocab.json", "fixture.json")) {
            val file = File(source, name)
            check(file.isFile) { "$file is missing: pass -PbenchAssets=<run>/onnx after tools.kotlin_fixture" }
            file.copyTo(File(out, name))
        }
    }
}

val benchAssets = tasks.register<BenchAssets>("benchAssets") {
    from.set(providers.gradleProperty("benchAssets").map { rootDir.resolve(it).path }.orElse(""))
    outputs.upToDateWhen { false }
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(benchAssets, BenchAssets::into) }
}
