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
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging { events("passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// The developer's comparison with SymPy (tools/calc_differential.py): the engine's answers to a file of tasks, from the
// tests' classes. Never shipped.
tasks.register<JavaExec>("differential") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.professoreuler.mathcalc.DifferentialKt")
}
