# GTNH fixes and shader compatibility verification

Version 3.1.0 includes these fixes. The 3.0.0 and 3.0.1 tags and published files
are unchanged. Use the same updated build on the client and server, since the
material handshake now includes the missing-material placeholder.

## Reproductions and fixes

All seven checks in `GtnhRegressionChecks` fail against an isolated checkout of
tag 3.0.0 and pass with the fixes:

- Updating a detached part does not try to use its missing tile.
- An empty multipart tile returns zero light; it previously threw `empty.max`.
- An old tile stops calling a part after the part moves to another tile.
- An old tickable tile stops ticking a part after it moves to another tile.
- An unknown material uses the missing-material placeholder.
- NBT and item drops retain the unknown material's original name. Recipes cannot
  use the placeholder as an ingredient or turn it into another block.
- Rejecting a material handshake leaves the current registry intact.

The singleplayer check verifies that the client's redundant handshake leaves the
server's shared material table alone. Each registry update replaces the entries,
name lookup and wire encoding together, after validation. These tests exercise
the failure conditions directly. We have not reproduced every GTNH report through
normal gameplay or run a race-condition stress test.

Sources: GTNH ForgeMultipart PRs [#58](https://github.com/GTNewHorizons/ForgeMultipart/pull/58),
[#62](https://github.com/GTNewHorizons/ForgeMultipart/pull/62),
[#46](https://github.com/GTNewHorizons/ForgeMultipart/pull/46),
[#37](https://github.com/GTNewHorizons/ForgeMultipart/pull/37),
[#47](https://github.com/GTNewHorizons/ForgeMultipart/pull/47),
[#61](https://github.com/GTNewHorizons/ForgeMultipart/pull/61), and
[#44](https://github.com/GTNewHorizons/ForgeMultipart/pull/44).

## Client and shader checks

The client test starts a separate flat world and places stone, glowstone and
glass microblocks on the integrated server. It waits for the client to receive
them and requires all three materials to reach the world's chunk renderer.
It also checks that a stale block update adds only the incoming part and that
the multipart renderer clears its world reference after drawing.

With Demonica 0.9.1 and its bundled Celeritas build `d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4`,
the test shader pack assigns IDs 6001 and 6002 to stone and glowstone. The test
reads Demonica's BufferBuilder quad contexts. Six quads from each material must
have IDs 6001, 6002 and -1, respectively. Nested draws, drawing errors and
inventory rendering must preserve the previous material override. The test saves
a scene screenshot under its `screenshots`.

Demonica support uses its public material override API and applies only when
drawing microblocks in the world. It is optional and does not require 1.7.10's
CapturingTessellator. These checks cover material IDs and the rendering path.
Other shader packs, GPUs and renderer versions need their own tests.

Logic ticks still use `ITickable`. Dynamic and fast drawing use
`TDynamicRenderPart` and `TFastRenderPart`, independently of logic ticks.

## Running the checks

```shell
./gradlew build
./gradlew runServer -Psmoke_test=true --no-configuration-cache
./gradlew runClient -Pclient_smoke=true --no-configuration-cache
bash .github/scripts/production-smoke.sh
```

For a production client, use PowerShell 7 and an existing Minecraft
1.12 asset root (the directory containing `indexes/1.12.json` and `objects/`):

```powershell
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>'
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>' -CeleritasJar '<renderer jar>'
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>' -DemonicaJar '<Demonica-0.9.1.jar>'
```

Each mode creates its own instance under `run/client-smoke-*` and leaves launcher
profiles unchanged. The instance uses modern Cleanroom mods and disables the
generic legacy-patch-mod warning. Run it in a session with graphics support.
The instance contains `client-smoke.log` and `client-smoke-result.txt`.

Use the production client script for renderer release jars. Loading those jars
directly in MCP userdev failed because their SRG Mixin targets were not remapped.
