plugins {
    base
    alias(libs.plugins.android.application) apply false
}

val javaFormatter = configurations.create("javaFormatter") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add(javaFormatter.name, "com.google.googlejavaformat:google-java-format:2.0.0:all-deps")
}

val javaSources = fileTree("app/src") {
    include("**/*.java")
}

fun JavaExec.configureJavaFormatter(vararg options: String) {
    group = "formatting"
    classpath = javaFormatter
    mainClass.set("com.google.googlejavaformat.java.Main")
    jvmArgs(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )
    args("--aosp", "--skip-reflowing-long-strings", "--cleanup=false")
    args(options.toList())
    inputs.files(javaSources)
    args(javaSources.files.sortedBy { it.path }.map { it.absolutePath })
}

tasks.register<JavaExec>("formatJava") {
    description = "Format all production and test Java sources using the pinned AOSP style."
    configureJavaFormatter("--replace")
}

tasks.register<JavaExec>("checkJavaFormat") {
    description = "Fail when production or test Java sources need formatting."
    configureJavaFormatter("--dry-run", "--set-exit-if-changed")
    doFirst {
        val wildcardImport = Regex("(?m)^import\\s+(?:static\\s+)?[\\w.]+\\.\\*\\s*;")
        val violations = inputs.files.files.filter { wildcardImport.containsMatchIn(it.readText()) }
        check(violations.isEmpty()) {
            "Use explicit Java imports in: ${violations.joinToString { it.path }}"
        }
    }
}

tasks.named("check") {
    dependsOn("checkJavaFormat", ":app:check")
}
