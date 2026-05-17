plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
    // Versions declared here so the :android subproject can apply them without
    // its own version (avoids "plugin already on classpath with unknown version").
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

group = "io.victor.jarvis"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "3.0.1"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // R5: daily email summary via Gmail SMTP (default-off behind
    // JARVIS_DAILY_EMAIL env). jakarta.mail handles STARTTLS + auth.
    implementation("com.sun.mail:jakarta.mail:2.0.1")

    // Layer A: SQLite + ORM for TutorDb schema + audit log.
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.55.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")

    // Layer B: PDF text-layer extraction for study-material ingestion.
    implementation("org.apache.pdfbox:pdfbox:2.0.30")

    // SLF4J runtime: without an impl, Ktor's StatusPages exceptions get
    // silently swallowed (NOP logger) — that hid a TaskRepo.insert UNIQUE
    // violation for hours during the 2026-05-17 audit recon. Keep this
    // pinned to slf4j-simple so future Unhandled: stack traces hit
    // /var/log/jarvis.log without ops intervention.
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.0.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.0.1")
}

application {
    mainClass.set("jarvis.MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register<JavaExec>("runLogger") {
    group = "application"
    description = "Run the always-on activity logger (5-minute snapshots)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.MainKt")
    args = listOf("logger")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.register<JavaExec>("runLoggerOnce") {
    group = "application"
    description = "Run the activity logger for a single capture and exit."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.MainKt")
    args = listOf("logger", "--once")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.register<JavaExec>("runReflect") {
    group = "application"
    description = "Run the daily reflection job."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.MainKt")
    args = listOf("reflect")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.register<JavaExec>("runWeb") {
    group = "application"
    description = "Run the Jarvis web server (HTMX UI on http://localhost:8080)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.MainKt")
    args = listOf("web")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.register<JavaExec>("runReindex") {
    group = "application"
    description = "Embed any wiki entries not yet in the vector store."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.MainKt")
    args = listOf("reindex")
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}

tasks.register<JavaExec>("migrateWiki") {
    group = "application"
    description = "One-shot CLI: walk wiki.md 'conversation (model)' sections " +
        "and emit ConversationEntry rows to conversations.jsonl. STOP THE " +
        "SERVER FIRST. Pass --dry-run to write a .preview file without mutating real state."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jarvis.WikiToJsonlMigratorKt")
    args = (project.findProperty("migrateArgs") as String?)?.split(" ")?.filter { it.isNotEmpty() } ?: listOf()
    jvmArgs = listOf(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
    )
}
