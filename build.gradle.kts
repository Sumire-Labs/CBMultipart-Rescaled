import com.diffplug.gradle.spotless.SpotlessExtension
import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.ReleaseType
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonOutput
import net.minecraftforge.renamer.gradle.RenameJar
import net.minecraftforge.renamer.gradle.RenamerExtension
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import com.cleanroommc.gradle.api.task.mc.RunMinecraft
import java.time.Duration

plugins {
    id("com.cleanroommc.versioning") version "3.4.0"
    `java-library`
    scala
    `maven-publish`
    id("com.cleanroommc.cleanroomgradle")
    id("com.cleanroommc.tokenenvoy") version "3.0.0"
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
    id("com.diffplug.spotless") version "8.11.0" apply false
    id("me.modmuss50.mod-publish-plugin") version "2.2.1" apply false
}

fun propertyString(name: String): String = providers.gradleProperty(name).get()
fun propertyFlag(name: String): Boolean = propertyString(name).toBoolean()

val modId = propertyString("mod_id")
val modName = propertyString("mod_name")
val modPackage = propertyString("root_package")
val modVersion = version.toString()
val assetMoverVersion = providers.gradleProperty("asset_mover_version").get()
val mixinEnabled = propertyFlag("enable_mixin")
val shadowEnabled = propertyFlag("enable_shadow")
val spotlessEnabled = propertyFlag("enable_spotless")
val testingEnabled = propertyFlag("enable_junit_testing")
val configuredTransformers = providers.gradleProperty("access_transformers").orElse("").get()
    .split(Regex("\\s+")).filter(String::isNotBlank)
val atEnabled = providers.gradleProperty("use_access_transformer").map(String::toBoolean)
    .orElse(configuredTransformers.isNotEmpty()).get()
val accessTransformerNames = if (atEnabled) configuredTransformers.ifEmpty { listOf("${modId}_at.cfg") } else emptyList()
val configuredCoremod = providers.gradleProperty("coremod_plugin_class").orElse("").get()
val coremodEnabled = providers.gradleProperty("enable_coremod").map(String::toBoolean)
    .orElse(configuredCoremod.isNotBlank()).get()
val coremodClass = if (coremodEnabled) configuredCoremod.ifBlank { "$modPackage.core.LoadingPlugin" } else ""

group = modPackage
base.archivesName.set("CBMultipart-Rescaled")
scala { scalaVersion.set(propertyString("scala_version")) }
tasks.withType<ScalaCompile>().configureEach {
    options.encoding = "UTF-8"
    scalaCompileOptions.additionalParameters = listOf("-release:25", "-Ximport-suggestion-timeout:0")
}
require(modId.matches(Regex("[a-z][a-z0-9_-]*"))) {
    "mod_id must start with a lowercase letter and contain only lowercase letters, numbers, underscores or hyphens."
}

if (shadowEnabled) apply(plugin = "com.gradleup.shadow")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    if (propertyFlag("generate_sources_jar")) withSourcesJar()
    if (propertyFlag("generate_javadocs_jar")) withJavadocJar()
}

val contain = configurations.create("contain")
val containedFiles = contain.elements
configurations.implementation { extendsFrom(contain) }
if (shadowEnabled) {
    val shade = configurations.create("shade")
    configurations.implementation { extendsFrom(shade) }
}

dependencies {
    implementation(cleanroom.userdev(propertyString("cleanroom_version")) {
        accessTransformers.from(accessTransformerNames.map { "src/main/resources/META-INF/$it" })
    })
    if (propertyFlag("use_asset_mover")) {
        implementation("com.cleanroommc:assetmover:$assetMoverVersion")
    }
    if (testingEnabled) {
        testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}
if (mixinEnabled) {
    // Match the compile-only APIs to the processors selected from this Loader.
    configurations.compileOnly.get().dependencies.addAllLater(providers.provider {
        configurations.annotationProcessor.get().allDependencies.filter {
            it.group == "com.cleanroommc" && it.name in setOf("cleanmix", "mixinextras-common")
        }.map { (it.copy() as ModuleDependency).apply { isTransitive = false } }
    })
}

sourceSets.main {
    val packagePath = modPackage.replace('.', '/')
    val enabledTransformers = accessTransformerNames
    if (!mixinEnabled) java.exclude("$packagePath/mixin/**")
    if (coremodClass.isBlank()) java.exclude("$packagePath/core/**")
    resources.exclude {
        it.path.startsWith("META-INF/") && it.name.endsWith("_at.cfg") && it.name !in enabledTransformers
    }
    if (!mixinEnabled) resources.exclude("*.mixin.json")
}
if (!testingEnabled) sourceSets.test { java.setSrcDirs(emptyList<String>()) }

tokenEnvoy {
    sourceSet("main") {
        set("MOD_ID", modId)
        set("MOD_NAME", modName)
        set("MOD_VERSION", modVersion)
        set("MOD_UPDATE_JSON", propertyString("mod_update_json"))
        set("MOD_PACKAGE", modPackage)
        set("CLIENT_PROXY", "$modPackage.proxy.ClientProxy")
        set("SERVER_PROXY", "$modPackage.proxy.CommonProxy")
        set("MIXIN_REFMAP", providers.gradleProperty("cg.userdev.refmap"))
        set("MOD_ID_JSON", JsonOutput.toJson(modId))
        set("MOD_NAME_JSON", JsonOutput.toJson(modName))
        set("MOD_VERSION_JSON", JsonOutput.toJson(modVersion))
        set("MOD_DESCRIPTION_JSON", JsonOutput.toJson(propertyString("mod_description")))
        set("MOD_AUTHORS_JSON", JsonOutput.toJson(propertyString("mod_authors").split(',').map(String::trim).filter(String::isNotBlank)))
        set("MOD_CREDITS_JSON", JsonOutput.toJson(propertyString("mod_credits")))
        set("MOD_URL_JSON", JsonOutput.toJson(propertyString("mod_url")))
        set("MOD_UPDATE_JSON_JSON", JsonOutput.toJson(propertyString("mod_update_json")))
        set("MOD_LOGO_JSON", JsonOutput.toJson(propertyString("mod_logo_path")))
        set("MOD_ISSUE_TRACKER_JSON", JsonOutput.toJson(propertyString("mod_issue_tracker")))
        set("PACK_DESCRIPTION_JSON", JsonOutput.toJson("$modName Resources"))
        classes { include("**/Reference.class", "**/ExampleMod.class") }
        resources { include("mcmod.info", "pack.mcmeta", "*.mixin.json") }
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    rename("modid.mixin.json", "$modId.mixin.json")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Jar>().configureEach {
    from("LICENSE.md") { into("META-INF") }
    from("README.md") { into("META-INF") }
    from("gradle/TEMPLATE-LICENSE.md") { into("META-INF") }
    if (name == "jar" || name == "shadowJar") {
        val embeddedFiles = containedFiles
        doFirst {
            val files = embeddedFiles.get()
            if (files.isNotEmpty()) {
                (this as Jar).manifest.attributes(
                    "ContainedDeps" to files.joinToString(" ") { it.asFile.name },
                    "NonModDeps" to "true"
                )
            }
        }
    }
}
tasks.test {
    enabled = testingEnabled
    useJUnitPlatform()
    testLogging.showStandardStreams = propertyFlag("show_testing_output")
}

apply(from = "gradle/scripts/dependencies.gradle.kts")
apply(from = "gradle/scripts/mixin.gradle.kts")
apply(from = "gradle/scripts/runs.gradle.kts")
if (providers.gradleProperty("smoke_test").orElse("false").get().toBoolean()) {
    val smokeDirectory = layout.projectDirectory.dir("run/smoke-server").asFile
    val smokeResult = layout.buildDirectory.file("verification/server-smoke.txt").get().asFile
    tasks.named<RunMinecraft>("runServer") {
        timeout.set(Duration.ofMinutes(3))
        dependsOn(tasks.testClasses)
        classpath(sourceSets.test.get().output)
        workingDir(smokeDirectory)
        systemProperty("cbmultipart.smoke.result", smokeResult.absolutePath)
        doFirst {
            smokeDirectory.mkdirs()
            smokeResult.parentFile.mkdirs()
            smokeResult.delete()
            smokeDirectory.resolve("eula.txt").writeText("eula=true\n")
            smokeDirectory.resolve("server.properties").writeText(
                "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=smoke\n" +
                "level-type=FLAT\ngenerate-structures=false\nview-distance=3\nmax-players=1\n"
            )
        }
        doLast {
            check(smokeResult.isFile && smokeResult.readText().trim() == "PASS") {
                "Server smoke tests did not complete; inspect run/smoke-server/logs/latest.log."
            }
        }
    }
}
if (providers.gradleProperty("client_smoke").orElse("false").get().toBoolean()) {
    val clientDirectory = layout.projectDirectory.dir("run/smoke-client").asFile
    val clientResult = layout.buildDirectory.file("verification/client-smoke.txt").get().asFile
    val packResources = layout.projectDirectory.dir("src/test/resources/client").asFile
    tasks.named<RunMinecraft>("runClient") {
        timeout.set(Duration.ofMinutes(5))
        dependsOn(tasks.testClasses)
        classpath(sourceSets.test.get().output)
        workingDir(clientDirectory)
        systemProperty("cbmultipart.client.result", clientResult.absolutePath)
        doFirst {
            clientResult.parentFile.mkdirs()
            clientResult.delete()
            clientDirectory.mkdirs()
            packResources.copyRecursively(clientDirectory, overwrite = true)
        }
        doLast {
            check(clientResult.isFile && clientResult.readText().trim() == "PASS") {
                "Client smoke tests did not complete; inspect run/smoke-client/logs/latest.log."
            }
        }
    }
}
if (spotlessEnabled) {
    apply(plugin = "com.diffplug.spotless")
    configure<SpotlessExtension> {
        encoding("UTF-8")
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            target("src/main/java/**/*.java", "src/test/java/**/*.java")
            toggleOffOn()
            importOrderFile("gradle/spotless/spotless.importorder")
            removeUnusedImports()
            eclipse().configFile("gradle/spotless/spotless.eclipseformat.xml")
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("misc") {
            target("*.gradle.kts", "*.properties", ".gitignore", ".gitattributes",
                "gradle/scripts/*.gradle.kts", ".github/workflows/*.yml")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

idea {
    module {
        inheritOutputDirs = true
        isDownloadSources = true
    }
    project {
        settings {
            runConfigurations {
                create("1. Build", Gradle::class.java) { taskNames = listOf("build") }
                create("2. Run Client", Gradle::class.java) { taskNames = listOf("runClient") }
                create("3. Run Server", Gradle::class.java) { taskNames = listOf("runServer") }
                if (spotlessEnabled) {
                    create("4. Apply Spotless", Gradle::class.java) { taskNames = listOf("spotlessApply") }
                }
            }
        }
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(contain) { into("/") }
    manifest {
        attributes("ModType" to "CRL")
        attributes("Implementation-Version" to modVersion, "Implementation-Title" to modName)
        if (mixinEnabled) attributes("MixinConfigs" to "$modId.mixin.json")
        if (accessTransformerNames.isNotEmpty()) attributes("FMLAT" to accessTransformerNames.joinToString(" "))
        if (coremodClass.isNotBlank()) {
            attributes("FMLCorePlugin" to coremodClass)
            if (propertyFlag("coremod_includes_mod")) {
                attributes("FMLCorePluginContainsFMLMod" to "true", "ForceLoadAsMod" to "true")
            }
        }
    }
}
if (shadowEnabled) {
    val shadowJar = tasks.named<ShadowJar>("shadowJar") {
        dependsOn(tasks.jar)
        configurations = listOf(project.configurations.getByName("shade"))
        archiveClassifier.set("shadow-mcp")
        destinationDirectory.set(layout.buildDirectory.dir("intermediates/shadow"))
        from(contain) { into("/") }
    }
    tasks.named<RenameJar>("reobfJar") {
        from(shadowJar)
        archiveClassifier.set("")
    }
}

val modDescription = propertyString("mod_description")
val modUrl = propertyString("mod_url")
val mavenName = propertyString("maven_name")
val mavenUrl = propertyString("maven_url")
val curseforgeProjectId = propertyString("curseforge_project_id")
val modrinthProjectId = propertyString("modrinth_project_id")
val remotePublishing = propertyFlag("publish_to_maven")
val localPublishing = propertyFlag("publish_to_local_maven")
val reobfJar = tasks.named<RenameJar>("reobfJar")
val smokeJar = tasks.register<Jar>("smokeJar") {
    from(sourceSets.test.get().output)
    include("codechicken/multipart/ServerSmokeMod*", "codechicken/multipart/SmokePassThrough*",
        "codechicken/multipart/GtnhRegressionChecks*", "codechicken/multipart/ClientSmokeChecks*", "mcmod.info")
    archiveClassifier.set("smoke-mcp")
    destinationDirectory.set(layout.buildDirectory.dir("smoke"))
}
extensions.getByType<RenamerExtension>().classes("reobfSmokeJar", smokeJar) {
    map.setFrom(tasks.named<RenameJar>("reobfJar").map { it.map })
    libraries.from(sourceSets.main.get().output, configurations.compileClasspath)
    archiveClassifier.set("smoke-tests")
    output.set(layout.buildDirectory.file("smoke/CBMultipart-Rescaled-smoke-tests.jar"))
}
val productionMods = configurations.create("productionSmokeMods")
dependencies {
    add(productionMods.name, "codechicken.lib:codechickenlib:${propertyString("ccl_version")}") { isTransitive = false }
    add(productionMods.name, "curse.maven:scalar-1012168:${propertyString("scalar_file")}") { isTransitive = false }
}
tasks.register<Sync>("stageProductionSmoke") {
    from(productionMods, reobfJar.flatMap { it.output }, tasks.named<RenameJar>("reobfSmokeJar").flatMap { it.output })
    into(layout.buildDirectory.dir("production-smoke/mods"))
}

if (remotePublishing || localPublishing) {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                // Build-only userdev and development remapping attributes must not
                // leak into consumers' dependency graphs. Mod dependencies are explicit.
                artifact(reobfJar.flatMap { it.output })
                if (propertyFlag("generate_sources_jar")) artifact(tasks.named("sourcesJar"))
                groupId = modPackage
                artifactId = "CBMultipart-Rescaled"
                artifact(tasks.jar) { classifier = "dev" }
                pom {
                    name.set(modName)
                    description.set(modDescription)
                    if (modUrl.isNotBlank()) url.set(modUrl)
                    licenses {
                        license {
                            name.set("GNU Lesser General Public License v2.1 or later")
                            url.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html")
                            distribution.set("repo")
                        }
                    }
                }
            }
        }
        if (remotePublishing && !localPublishing) {
            require(mavenUrl.isNotBlank()) { "Set maven_url before enabling publish_to_maven." }
            repositories {
                maven {
                    name = mavenName
                    url = uri(mavenUrl)
                    credentials {
                        username = providers.environmentVariable("MAVEN_USERNAME")
                            .orElse(providers.gradleProperty("${mavenName}Username")).orNull
                        password = providers.environmentVariable("MAVEN_PASSWORD")
                            .orElse(providers.gradleProperty("${mavenName}Password")).orNull
                    }
                }
            }
        }
    }
}

apply(plugin = "me.modmuss50.mod-publish-plugin")
configure<ModPublishExtension> {
    file.set(reobfJar.flatMap { it.output })
    version.set(modVersion)
    displayName.set("$modName $modVersion")
    type.set(versioning.stage.map { stage ->
        when (stage.name) {
            "ALPHA" -> ReleaseType.ALPHA
            "BETA", "RC" -> ReleaseType.BETA
            else -> ReleaseType.STABLE
        }
    })
    modLoaders.add("forge")
    maxRetries.set(5)
    val notes = providers.environmentVariable("CHANGELOG_FILE").orElse("RELEASE_NOTES.md")
    changelog.set(providers.fileContents(layout.projectDirectory.file(notes)).asText.orElse(""))
    if (curseforgeProjectId.isNotBlank()) {
        curseforge {
            projectId.set(curseforgeProjectId)
            accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
            minecraftVersions.add("1.12.2")
            client.set(true)
            server.set(true)
            javaVersions.add(JavaVersion.VERSION_25)
            requires("codechicken-lib-cre", "scalar")
            optional("had-enough-items")
            optional("demonica-shaders")
            incompatible("cb-multipart")
            incompatible("codechicken-lib-1-8")
            incompatible("scalar-legacy")
        }
    }
    if (modrinthProjectId.isNotBlank()) {
        modrinth {
            projectId.set(modrinthProjectId)
            accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
            minecraftVersions.add("1.12.2")
            requires("codechicken-lib-cre")
            // Scalar has no confirmed Modrinth project; README links its CurseForge download.
            optional("had-enough-items")
            incompatible("cb-multipart")
            incompatible("codechicken-lib")
        }
    }
}
apply(from = "gradle/scripts/verification.gradle.kts")
apply(from = "gradle/scripts/extra.gradle.kts")

val releaseType = versioning.stage.map { stage ->
    stage.name.lowercase().let { if (it == "rc") "beta" else it }
}
tasks.register("printReleaseType") {
    inputs.property("releaseType", releaseType)
    doLast { println(inputs.properties.getValue("releaseType")) }
}
val publishMaven = remotePublishing && !localPublishing
val publishCurseforge = curseforgeProjectId.isNotBlank()
val publishModrinth = modrinthProjectId.isNotBlank()
tasks.register("printReleaseInfo") {
    inputs.property("info", releaseType.map { type ->
        JsonOutput.toJson(mapOf(
            "version" to modVersion, "type" to type, "maven" to publishMaven,
            "curseforge" to publishCurseforge, "modrinth" to publishModrinth
        ))
    })
    doLast { println(inputs.properties.getValue("info")) }
}
