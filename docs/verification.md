# GTNH fixes and shader compatibility verification

These changes ship in 3.1.0. The existing 3.0.0 and 3.0.1 tags and published artifacts are
unchanged. Clients and servers must use the same updated build: the material
handshake now includes the missing-material placeholder.

## Reproductions and fixes

The seven checks in `GtnhRegressionChecks` fail against an isolated checkout of
tag 3.0.0 and pass with the changes:

- A detached part sends a description update without dereferencing a missing tile.
- An empty multipart tile reports zero light instead of throwing `empty.max`.
- A replaced tile stops invoking callbacks on a part bound to a different tile.
- A replaced tickable tile stops updating a part bound to a different tile.
- An unknown material resolves to a dedicated placeholder, never a real material.
- An unknown name survives NBT and item drops, and the placeholder cannot become
  a crafting ingredient or produce another block.
- A rejected material handshake leaves the active registry intact.

The integrated-client check also verifies that its redundant handshake cannot
rewrite the server's shared material table. Registry entries, name lookup, and
wire encoding are published together after validation. The tests exercise the
failure conditions; they do not claim that every original GTNH report has been
reproduced through ordinary player actions or a probabilistic race stress test.

Sources: GTNH ForgeMultipart PRs [#58](https://github.com/GTNewHorizons/ForgeMultipart/pull/58),
[#62](https://github.com/GTNewHorizons/ForgeMultipart/pull/62),
[#46](https://github.com/GTNewHorizons/ForgeMultipart/pull/46),
[#37](https://github.com/GTNewHorizons/ForgeMultipart/pull/37),
[#47](https://github.com/GTNewHorizons/ForgeMultipart/pull/47),
[#61](https://github.com/GTNewHorizons/ForgeMultipart/pull/61), and
[#44](https://github.com/GTNewHorizons/ForgeMultipart/pull/44).

## Client and shader checks

The client fixture starts an isolated flat world, places stone, glowstone and
glass microblocks on the integrated server, waits for synchronization, and
requires all three materials to pass through the actual world chunk renderer.
It checks a stale convertible client block receives only the incoming part and
that the multipart renderer clears its world reference.

With Demonica 0.9.1 and its bundled Celeritas build `d94ec1e59df7b5fe38ee76f1bdcb03f851587aa4`,
the fixture shader pack assigns IDs 6001 and 6002 to stone and glowstone. The test
reads the actual Demonica BufferBuilder quad contexts: six stone quads, six
glowstone quads, and six missing-material quads must carry the correct distinct
IDs. Nested drawing, exceptional exit, and inventory rendering must preserve the
enclosing override. A scene screenshot is saved under the fixture's `screenshots`.

The compatibility bridge uses Demonica's public material override API, without
the 1.7.10 CapturingTessellator dependency. It is optional and only changes world
microblock drawing. This fixture verifies the metadata and rendering route; it
is not an exhaustive test of every shader pack, GPU, or renderer version.

Logic ticks continue to use `ITickable`; dynamic and fast drawing use
`TDynamicRenderPart` and `TFastRenderPart`. No new tick/render coupling is added.

## Running the checks

```shell
./gradlew build
./gradlew runServer -Psmoke_test=true --no-configuration-cache
./gradlew runClient -Pclient_smoke=true --no-configuration-cache
bash .github/scripts/production-smoke.sh
```

For an installed production client, use PowerShell 7 and an existing Minecraft
1.12 asset root (the directory containing `indexes/1.12.json` and `objects/`):

```powershell
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>'
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>' -CeleritasJar '<renderer jar>'
./.github/scripts/client-smoke.ps1 -AssetsDirectory '<1.12 asset root>' -JavaExecutable '<Java 25 executable>' -DemonicaJar '<Demonica-0.9.1.jar>'
```

Each mode uses its own instance under `run/client-smoke-*` and leaves launcher
profiles alone. The test instance disables the generic legacy-patch-mod warning
because its fixtures use modern Cleanroom mods. A graphics-capable session is
required. `client-smoke.log` and `client-smoke-result.txt` are in that instance.
The production path also avoids SRG Mixin-target remapping issues encountered
when loading the renderer release jars directly in MCP userdev.
