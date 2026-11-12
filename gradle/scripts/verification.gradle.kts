import groovy.json.JsonSlurper
import java.util.jar.JarFile
import net.minecraftforge.renamer.gradle.RenameJar

val mod_id = providers.gradleProperty("mod_id").get()
val root_package = providers.gradleProperty("root_package").get()
val configuredCoremod = providers.gradleProperty("coremod_plugin_class").orElse("").get()
val coremodEnabled = providers.gradleProperty("enable_coremod").map(String::toBoolean)
    .orElse(configuredCoremod.isNotBlank()).get()
val coremod_plugin_class = if (coremodEnabled) configuredCoremod.ifBlank { "$root_package.core.LoadingPlugin" } else ""
val mixinEnabled = providers.gradleProperty("enable_mixin").get().toBoolean()
val configuredTransformers = providers.gradleProperty("access_transformers").orElse("").get()
    .split(Regex("\\s+")).filter(String::isNotBlank)
val atEnabled = providers.gradleProperty("use_access_transformer").map(String::toBoolean)
    .orElse(configuredTransformers.isNotEmpty()).get()
val accessTransformers = if (atEnabled) configuredTransformers.ifEmpty { listOf("${mod_id}_at.cfg") } else emptyList()
val nativeRefmap = providers.gradleProperty("cg.userdev.refmap").get()
val modVersion = project.version.toString()
val modAuthors = providers.gradleProperty("mod_authors").get().split(',').map(String::trim).filter(String::isNotBlank)
val runtimeJar = tasks.named<RenameJar>("reobfJar").flatMap { it.output }
val containedNames = configurations.named("contain").flatMap { it.elements }
    .map { files -> files.map { it.asFile.name }.sorted() }

val verifyModJar = tasks.register("verifyModJar") {
    group = "verification"
    description = "Checks Mod metadata and optional features in the SRG distribution jar."
    inputs.file(runtimeJar)
    inputs.property("modId", mod_id)
    inputs.property("modPackage", root_package)
    inputs.property("modVersion", modVersion)
    inputs.property("modAuthors", modAuthors)
    inputs.property("mixinEnabled", mixinEnabled)
    inputs.property("coremodClass", coremod_plugin_class)
    inputs.property("accessTransformers", accessTransformers)
    inputs.property("nativeRefmap", nativeRefmap)
    inputs.property("containedDependencies", containedNames)
    doLast {
        val mod_id = inputs.properties.getValue("modId") as String
        val root_package = inputs.properties.getValue("modPackage") as String
        val modVersion = inputs.properties.getValue("modVersion") as String
        val modAuthors = inputs.properties.getValue("modAuthors") as List<*>
        val mixinEnabled = inputs.properties.getValue("mixinEnabled") as Boolean
        val coremod_plugin_class = inputs.properties.getValue("coremodClass") as String
        val coremodEnabled = coremod_plugin_class.isNotBlank()
        val accessTransformers = (inputs.properties.getValue("accessTransformers") as List<*>).map { it as String }
        val nativeRefmap = inputs.properties.getValue("nativeRefmap") as String
        val expectedContained = (inputs.properties.getValue("containedDependencies") as List<*>).map { it as String }
        JarFile(inputs.files.singleFile).use { jar ->
            val entries = jar.entries().asSequence().toList()
            if (jar.getJarEntry("META-INF/LICENSE.md") == null || jar.getJarEntry("META-INF/README.md") == null) {
                throw GradleException("Missing mod license or attribution notice.")
            }
            fun json(name: String): Any {
                val entry = jar.getJarEntry(name) ?: throw GradleException("Missing jar resource: $name")
                return jar.getInputStream(entry).use { JsonSlurper().parse(it, "UTF-8") }
            }
            val metadata = json("mcmod.info") as List<*>
            val mods = metadata.map { it as Map<*, *> }
            if (mods.map { it["modid"] }.toSet() != setOf(mod_id, "microblockcbe", "minecraftmultipartcbe") ||
                mods.any { it["version"] != modVersion }) {
                throw GradleException("Distribution metadata does not match the Mod ID and Git version.")
            }
            if (mods.any { it["authorList"] != modAuthors }) {
                throw GradleException("All three mod containers must use the configured authorList.")
            }
            val attributes = jar.manifest.mainAttributes
            if (entries.any { it.name.contains("ServerSmokeMod") || it.name.endsWith("/TastyReaderTest.class") }) {
                throw GradleException("Test classes must not be included in the distribution.")
            }
            val tasty = entries.filter { it.name.endsWith(".tasty") }
            if (tasty.isEmpty() || jar.getJarEntry("codechicken/multipart/asm/ScalaSignature.class") != null) {
                throw GradleException("Missing Scala 3 metadata or stale Scala 2 signature reader in distribution.")
            }
            entries.filter { it.name.endsWith(".class") }.forEach { entry ->
                val major = jar.getInputStream(entry).use { input ->
                    java.io.DataInputStream(input).run { readInt(); readUnsignedShort(); readUnsignedShort() }
                }
                if (major != 69) throw GradleException("Expected Java 25 bytecode in ${entry.name}, got $major.")
            }
            if (attributes.getValue("ModType") != "CRL") {
                throw GradleException("Missing Cleanroom-only ModType manifest attribute.")
            }
            val declaredContained = attributes.getValue("ContainedDeps").orEmpty()
                .split(Regex("\\s+")).filter(String::isNotBlank).sorted()
            if (declaredContained != expectedContained || expectedContained.any { jar.getJarEntry(it) == null } ||
                (expectedContained.isNotEmpty() && attributes.getValue("NonModDeps") != "true")) {
                throw GradleException("Embedded dependencies do not match the ContainedDeps manifest attributes.")
            }
            val at = attributes.getValue("FMLAT")
            if (accessTransformers.isNotEmpty()) {
                if (at != accessTransformers.joinToString(" ") || accessTransformers.any {
                    jar.getJarEntry("META-INF/$it") == null
                }) {
                    throw GradleException("Missing access transformer or FMLAT manifest attribute.")
                }
            } else if (at != null || entries.any { it.name.endsWith("_at.cfg") }) {
                throw GradleException("Access transformer leaked into the disabled variant.")
            }
            val coremod = attributes.getValue("FMLCorePlugin")
            if (coremodEnabled) {
                if (coremod != coremod_plugin_class || jar.getJarEntry(coremod.replace('.', '/') + ".class") == null) {
                    throw GradleException("Missing coremod class or incorrect FMLCorePlugin attribute.")
                }
            } else if (coremod != null || entries.any { it.name.startsWith(root_package.replace('.', '/') + "/core/") }) {
                throw GradleException("Coremod classes or manifest attribute leaked into the disabled variant.")
            }
            entries.filter {
                !it.isDirectory && (it.name.endsWith(".class") || it.name in listOf("mcmod.info", "pack.mcmeta") ||
                    it.name.endsWith(".mixin.json"))
            }.forEach { entry ->
                val text = jar.getInputStream(entry).use { it.readBytes().toString(Charsets.ISO_8859_1) }
                if (text.contains("@{")) throw GradleException("Unresolved token in ${entry.name}")
            }
            val configs = attributes.getValue("MixinConfigs")
            if (mixinEnabled) {
                if (configs != "$mod_id.mixin.json") throw GradleException("Incorrect MixinConfigs attribute.")
                val config = json(configs) as Map<*, *>
                val refmap = json(config["refmap"] as String) as Map<*, *>
                if ((refmap["mappings"] as? Map<*, *>).isNullOrEmpty()) {
                    throw GradleException("Missing Mixin mappings in the native refmap.")
                }
                listOf("mixins", "client", "server").flatMap { (config[it] as? List<*>).orEmpty() }.forEach { name ->
                    val classFile = "${config["package"]}.$name".replace('.', '/') + ".class"
                    if (jar.getJarEntry(classFile) == null) throw GradleException("Missing Mixin class: $name")
                }
            } else if (configs != null || entries.any {
                it.name == nativeRefmap || it.name.endsWith(".mixin.json") || it.name.endsWith(".refmap.json") ||
                    it.name.startsWith(root_package.replace('.', '/') + "/mixin/")
            }) {
                throw GradleException("Mixin resources leaked into the disabled variant.")
            }
        }
        logger.lifecycle("Verified distributable Mod jar (Mixin {}).", if (mixinEnabled) "ON" else "OFF")
    }
}
tasks.named("check") { dependsOn(verifyModJar) }
