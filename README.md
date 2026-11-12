# CBMultipart Rescaled

CBMultipart / Forge Multipart for Minecraft 1.12.2, Cleanroom and Scala 3.
Multiple functional parts can share one block space, including microblocks and
vanilla torch, lever and button parts.

## Requirements

- Java 25 and Cleanroom 0.7.14-alpha (the development baseline).
- [Scalar 3.9.0](https://www.curseforge.com/minecraft/mc-mods/scalar/files/8800375).
  Use the Scala 3 provider, not Scalar Legacy.
- [CodeChicken Lib CRE 3.3.10](https://github.com/Refitbench/CodeChickenLib/releases/tag/3.3.10)
  or a compatible newer release. Original Forge CCL and older CRE builds are not
  supported with this Cleanroom baseline.

Packages and mod IDs (`forgemultipartcbe`, `microblockcbe`, `minecraftmultipartcbe`)
are preserved. Scala 2 trait compatibility is not a goal: dependent Scala mods
must be rebuilt against this Scala 3 API. Existing save IDs and NBT layouts are
preserved; back up worlds before testing a development build.

## Development

Based on [CleanroomDevEnv](https://github.com/Sumire-Labs/CleanroomDevEnv), using
CleanroomGradle 0.23.0, Gradle 9.9 and Scala 3.9.0. Minecraft mappings come from
native Cleanroom userdev instead of the old ForgeGradle snapshot.
Java sources under `src/main/scala` participate in Scala/Java joint compilation.

Multipart-specific versions are pinned in `gradle.properties`: `ccl_version=3.3.10`
(CCL CRE, required at runtime), `cc_asm_version=4.0.1` (ChickenASM, embedded), and
`jei_version=4.35.0` (the Cleanroom HEI build using the JEI API). The legacy
`3.2.3.+` / `1.0.2.7` / `4.8.0.+` dependencies and old automatic-download
descriptor are replaced by these declarations. Generic CleanroomDevEnv does not
require these Multipart-specific libraries.

```shell
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runServer -Psmoke_test=true --no-configuration-cache
```

On Windows use `gradlew.bat`. Select a JDK 25 with `JAVA_HOME`.
Development runs default to offline authentication; set `development_offline=false`
for the template's authenticated flow. CCL's development mapping properties use
the same userdev mappings as the project.

`build` runs TASTy reader tests and checks the SRG jar's metadata, Java 25 bytecode,
TASTy resources, access transformers and embedded libraries. The smoke run uses a
separate localhost world in `run/smoke-server`, accepts the Minecraft EULA for that
test instance, generates all 64 server trait combinations, checks button conversion
and an NBT round trip, and shuts down automatically. A completion marker is
required because mod-loading crashes can otherwise exit zero. Tests are excluded
from the distribution. `reobfSmokeJar` creates a separate integration-test mod in
`build/smoke` for testing an installed Cleanroom server.

The TASTy header reader uses Scala's container/name-table parser, embedded with
ChickenASM as non-mod libraries. Scalar supplies the Scala runtime. Only class
headers are read; method bodies are never resolved against Minecraft's classes.
This works in both MCP development and SRG/obfuscated installations, including
dedicated servers without client classes. It also avoids scanning Foundation's
synthetic `asmgen:` resources. The embedded Scala reader makes the jar larger
than the original Scala 2 release.

## Maven and releases

Coordinates: `codechicken:CBMultipart-Rescaled:<version>`.

- Default artifact: SRG distribution jar, for installation or `cleanroom.deobf(...)`.
- `dev` classifier: MCP development jar.
- `sources` classifier: source code.

The POM intentionally has no transitive mod/build dependencies. Consumers must
declare their own Cleanroom userdev, Scala runtime/provider and compatible CCL.
It does not pull another project's userdev or remapped development dependencies.

```shell
./gradlew publishToMavenLocal -Ppublish_to_local_maven=true
```

Remote publication is disabled by default. Set `publish_to_maven=true`, check
`maven_url` in `gradle.properties`, and configure `MAVEN_USERNAME` and
`MAVEN_PASSWORD` as GitHub Actions secrets to enable it.

Modrinth and CurseForge remain disabled until `modrinth_project_id` and/or
`curseforge_project_id` are populated. Set `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN`
for enabled destinations. Only the SRG jar is uploaded as the mod file. Required
dependency metadata must refer to Scalar and CCL CRE.

Version tags (for example `3.0.0`) trigger the release workflow. It builds,
tests the dedicated server, checks enabled destinations' credentials, and publishes
Maven artifacts, mod files and a GitHub Release. Without a `CHANGELOG.md`, release
notes are generated from GitHub. The build does not create a release tag.

CI also checks formatting, dedicated-server integration and local Maven publication.
On Linux, `bash .github/scripts/production-smoke.sh` installs an isolated Cleanroom
server and repeats integration tests with the actual SRG mod jars. This check also
runs before release publication.

## Provenance and license

Based on TheCBProject's final 1.12.x source and kappa-maintainer's Cleanroom/Scala 3
port (kappa-maintainer/CBMultipart at `a80a2a1bc5014d6fbe0a4cff822b281595694007`,
upstream 1.12.x at `5c81389f568b8e71dd42acadd73e30745867ab36`). Includes ACGaming's
control-state memory leak fix (`3fae7a32deb596e40b84034640e9195e34f16fbc`) and newer
upstream's button conversion fix (`f5eda5eadf216ddec6cd98906f2ab595ff37ca60`).
Scala's TASTy container reader and format definitions come from Scala 3.9.0;
the embedded libraries preserve their own license notices.

LGPL-2.1-or-later; see [LICENSE.md](LICENSE.md). The template's MIT notice is
preserved in `gradle/TEMPLATE-LICENSE.md`.
