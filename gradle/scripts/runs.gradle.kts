import com.cleanroommc.gradle.api.ext.CleanroomExtension
import com.cleanroommc.gradle.api.task.mc.RunMinecraft
import com.cleanroommc.gradle.api.task.userdev.ExtractUserdevFile

// CCL still looks for ForgeGradle's development mapping properties.
// Point it at this userdev's mappings rather than its obsolete bundled paths.
val srgToMcp = tasks.named<ExtractUserdevFile>("extractUserdevSrgToMcp")
val obfToSrg = tasks.register<ExtractUserdevFile>("extractUserdevObfToSrg") {
    userdevArtifact.set(srgToMcp.flatMap { it.userdevArtifact })
    entryPath.set("userdev/obf2srg.tsrg")
    output.set(layout.buildDirectory.file("cleanroom_gradle/userdev/obf2srg.tsrg"))
}
val srgToMcpPath = layout.buildDirectory.file("cleanroom_gradle/userdev/srg2mcp.tsrg").get().asFile.absolutePath
val obfToSrgPath = layout.buildDirectory.file("cleanroom_gradle/userdev/obf2srg.tsrg").get().asFile.absolutePath

val mod_id = providers.gradleProperty("mod_id").get()
val minecraft_username = providers.gradleProperty("minecraft_username").get()
val extra_jvm_args = providers.gradleProperty("extra_jvm_args").get()
val enable_mixin = providers.gradleProperty("enable_mixin").get()
val enable_mixin_debug = providers.gradleProperty("enable_mixin_debug").get()
val enable_foundation_debug = providers.gradleProperty("enable_foundation_debug").get()
val configuredCoremod = providers.gradleProperty("coremod_plugin_class").orElse("").get()
val coremodEnabled = providers.gradleProperty("enable_coremod").map(String::toBoolean)
    .orElse(configuredCoremod.isNotBlank()).get()
val coremod_plugin_class = if (coremodEnabled) configuredCoremod.ifBlank {
    "${providers.gradleProperty("root_package").get()}.core.LoadingPlugin"
} else ""

configure<CleanroomExtension> {
    runs {
        listOf("client", "server").forEach { runName ->
            named(runName) {
                configure(RunMinecraft::class.java) {
                    dependsOn(srgToMcp, obfToSrg)
                    systemProperty("net.minecraftforge.gradle.GradleStart.srg.notch-srg", obfToSrgPath)
                    systemProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", srgToMcpPath)
                    offline.set(providers.gradleProperty("development_offline").map(String::toBoolean).orElse(true))
                    // Matches the official template's Foundation development-launch workaround.
                    systemProperty("foundation.loadsall", true)
                    setJvmArgs(jvmArgs.orEmpty().filter {
                        !it.startsWith("-Dmixin.debug.export=") && !it.startsWith("-Dmixin.checks.interfaces=")
                    })
                    username.set(minecraft_username)
                    if (extra_jvm_args.isNotBlank()) jvmArgs(extra_jvm_args.trim().split(Regex("\\s+")))
                    systemProperty("mixin.debug.export", enable_mixin_debug)
                    systemProperty("mixin.checks.interfaces", enable_mixin_debug)
                    if (enable_mixin.toBoolean()) systemProperty("crl.dev.mixin", "$mod_id.mixin.json")
                    if (enable_foundation_debug.toBoolean()) {
                        systemProperty("foundation.dump", true)
                        systemProperty("foundation.verbose", true)
                    }
                    if (coremod_plugin_class.isNotBlank()) systemProperty("fml.coreMods.load", coremod_plugin_class)
                }
            }
        }
    }
}
