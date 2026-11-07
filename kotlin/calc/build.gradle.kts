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

// HotSpot's C2 in JDK 21.0.11 crashes the JVM (EXCEPTION_ACCESS_VIOLATION in jvm.dll) on the engine's
// `if (e is Expr.Add) e.terms else listOf(e)` merges through its ReduceAllocationMerges optimisation (replayed with
// -XX:+ReplayCompiles: off, every recorded crash compiles). The apps' JVMs run with the same options; an older or newer JVM
// that lacks the flag ignores it.
val c2Workaround = listOf("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:-ReduceAllocationMerges")

tasks.test {
    jvmArgs(c2Workaround)
    testLogging { events("passed", "skipped", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// The developer's comparison with SymPy (tools/calc_differential.py): the engine's answers to a file of tasks, from the
// tests' classes. Never shipped.
tasks.register<JavaExec>("differential") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.professoreuler.mathcalc.DifferentialKt")
    jvmArgs(c2Workaround)
}
