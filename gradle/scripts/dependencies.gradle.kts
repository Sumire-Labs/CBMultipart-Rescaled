import com.cleanroommc.gradle.api.ext.CleanroomExtension

val cleanroom = extensions.getByType<CleanroomExtension>()

repositories {
    // The maintained Cleanroom CCL release is available as a GitHub artifact.
    ivy("https://github.com/Refitbench/CodeChickenLib/releases/download") {
        name = "CodeChickenLibReleases"
        patternLayout { artifact("[revision]/[artifact]-[revision]-universal.[ext]") }
        metadataSources { artifact() }
        content { includeModule("codechicken.lib", "codechickenlib") }
    }
    mavenCentral()
    maven("https://maven.arcseekers.com/releases") {
        metadataSources { mavenPom(); artifact() }
    }
    maven("https://maven.covers1624.net")
    maven("https://curse.cleanroommc.com")
    maven("https://cursemaven.com")
    maven("https://maven.cleanroommc.com") {
        content { includeGroup("mezz"); includeGroup("codechicken") }
        metadataSources { mavenPom(); artifact() }
    }
}

dependencies {
    val scalaVersion = providers.gradleProperty("scala_version").get()
    // Use Scala's container/name-table reader, without resolving trees or method bodies.
    // The reader must be available in installed jars as well as development runs.
    add("contain", "org.scala-lang:scala3-compiler_3:$scalaVersion") {
        exclude(group = "org.scala-lang", module = "scala3-library_3")
        exclude(group = "org.scala-lang", module = "scala-library")
    }
    add("runtimeOnly", (cleanroom.deobf("curse.maven:scalar-1012168:${providers.gradleProperty("scalar_file").get()}") as ModuleDependency).apply {
        isTransitive = false
    })
    add("implementation", cleanroom.deobf("codechicken.lib:codechickenlib:${providers.gradleProperty("ccl_version").get()}"))
    // Plain ASM utility library, needed by the generated mixin compiler in production.
    add("contain", "codechicken:ChickenASM:${providers.gradleProperty("cc_asm_version").get()}") { isTransitive = false }
    add("implementation", cleanroom.deobf("mezz:jei:${providers.gradleProperty("jei_version").get()}@jar"))
}
