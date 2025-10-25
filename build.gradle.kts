import groovy.json.JsonSlurper
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "sh.harold"
version = "1.0-SNAPSHOT"

val fulcrumVersion = providers.gradleProperty("fulcrumVersion").orElse("3.3.1").get()

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven(url = "https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.haroldDOTsh.fulcrum:common-api:$fulcrumVersion") // Contracts, ranks, session/message APIs
    compileOnly("com.github.haroldDOTsh.fulcrum:runtime:$fulcrumVersion") // Paper runtime hooks (module development)
}

tasks.register("updateFulcrumRuntime") {
    group = "Fulcrum"
    description = "Downloads the latest full Fulcrum runtime jar from GitHub releases into run/plugins."

    doLast {
        val pluginsDir = layout.projectDirectory.dir("run/plugins").asFile.apply { mkdirs() }
        pluginsDir.listFiles { file -> file.name.matches(Regex("runtime-.*\\.jar")) }
            ?.forEach { file ->
                if (!file.delete()) {
                    logger.warn("Failed to delete ${file.name}")
                }
            }

        val apiUrl = "https://api.github.com/repos/haroldDOTsh/fulcrum/releases/latest"
        val releaseJson = apiUrl.httpGet()
        val parsed = JsonSlurper().parseText(releaseJson) as Map<*, *>
        val assets = parsed["assets"] as? List<*> ?: error("No assets array in latest Fulcrum release response")

        val runtimeAsset = assets.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .firstOrNull { (it["name"] as? String)?.matches(Regex("runtime-.*\\.jar")) == true }
            ?: error("Unable to find a runtime-*.jar asset in the latest Fulcrum release")

        val downloadUrl = runtimeAsset["browser_download_url"] as? String
            ?: error("Runtime asset in latest Fulcrum release is missing a download URL")
        val assetName = runtimeAsset["name"] as? String ?: error("Runtime asset missing name")
        val runtimeVersion = Regex("runtime-(.+)\\.jar").find(assetName)?.groupValues?.get(1)
            ?: error("Unable to extract runtime version from asset name '$assetName'")
        val targetFile = pluginsDir.resolve(assetName)

        downloadUrl.httpDownload(targetFile)
        logger.lifecycle("Downloaded ${targetFile.name} (Fulcrum runtime $runtimeVersion) from $downloadUrl")
    }
}

fun String.httpGet(): String {
    val connection = URI.create(this).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
        connection.setRequestProperty("Authorization", "Bearer $it")
    }
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        stream.use { it.readBytes().toString(Charsets.UTF_8) }.also {
            if (code !in 200..299) error("GitHub API request to $this failed with HTTP $code:\n$it")
        }
    } finally {
        connection.disconnect()
    }
}

fun String.httpDownload(target: File) {
    val connection = URI.create(this).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let {
        connection.setRequestProperty("Authorization", "Bearer $it")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            val message = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) }
            error("Download from $this failed with HTTP $code${message?.let { "\n$message" } ?: ""}")
        }
        connection.inputStream.use { input ->
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        connection.disconnect()
    }
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.8")
    }
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
