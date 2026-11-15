# Changelog

This changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [4.0.0] - 2026-11-15

### Changed

- Require Scalar 3.10.0 or newer within the 3.x series. Scalar 3.9 is no longer
  supported.
- Build with Scala 3.10.0. Compile Scala extensions against this release with
  Scala 3.10.x.

### Fixed

- Fix startup being blocked with Scalar 3.10.0. Update the bundled TASTy reader
  to read traits compiled with Scala 3.10 as well as earlier Scala 3 versions.

## [3.1.2] - 2026-11-14

### Changed

- Include the original material name when synchronizing missing microblock materials.
  Update both the client and server to this version.
- Update the CleanroomDevEnv build setup to select runtime JARs for embedded
  libraries, replace tokens in renamed classes and check class constants for
  unresolved tokens.
- Enable Spotless checks by default.

### Fixed

- Fix crashes and lost parts when conversion callbacks remove parts or replace
  their tile. Send all converted parts to clients before running the callbacks,
  and keep the tile traits required by newly placed parts.
  ([upstream #112](https://github.com/TheCBProject/CBMultipart/issues/112))
- Make vanilla and converted redstone parts use the same visual connection masks.
  Fix the direction passed to sided blocks and restrict Observer connections to
  its output face.
  ([upstream #149](https://github.com/TheCBProject/CBMultipart/pull/149),
  [#189](https://github.com/TheCBProject/CBMultipart/pull/189))
- Stop client parts from entering the server's random-tick queue.
  ([upstream #149](https://github.com/TheCBProject/CBMultipart/pull/149))
- Let hoppers and sided item handlers access ordinary inventory parts. Keep the
  access restrictions of sided inventories.
- Restore scheduled ticks in the correct chunk, including part indices above
  127. Resume loaded ticks, skip stale indices and preserve the saved scheduler
  clock.
- Keep saved ticks attached to their original parts when missing part types are
  skipped during loading.
- Keep tick schedules for chunks loaded during another part's tick callback.
- Fix crashes when parts are added or removed during a tick. Start newly added
  parts on the next tick and defer tile registration and removal through World
  when a tile changes class.
- Keep packet updates queued during a send for the next flush. Discard updates
  for unloaded worlds without clearing updates for other worlds.
- Read four-byte update masks without extending their sign bit into the upper
  32 bits.
- Preserve missing material names on clients so pick-block keeps the original
  name.
- Fix microblock description updates sending a material ID where the client
  expects shape data.
- Return zero explosion resistance for empty multipart tiles.
- Validate server part lists before replacing the current registry. Replace
  names, factories and ID encoding together, and skip the redundant part-list
  update in singleplayer.
- Read Scala 3 traits nested in objects from their enclosing TASTy files and
  resolve parent traits by JVM name.
- Initialize public and private trait values correctly. Fix invalid getter
  bytecode for long and double fields.
- Initialize lazy trait values once after a successful evaluation and retry failed
  initializers. Ignore enclosing TASTy files that do not define the requested
  class, such as those found while inspecting generated Java helpers.

## [3.1.1] - 2026-11-13

### Added

- Add the CBMultipart Rescaled icon to all three mod entries.

### Changed

- Update the README with changes, requirements and links to dependencies and forks.
- Enable Maven, CurseForge and Modrinth uploads for tagged releases. Include
  dependency and incompatibility information on CurseForge and Modrinth.

## [3.1.0] - 2026-11-12

### Added

- Add optional Demonica support so terrain shaders can identify each microblock's
  original block material.

### Changed

- Add server regression tests and client rendering checks with Celeritas and
  Demonica. Logic ticks and rendering remain separate.

### Fixed

- Stop old tiles from updating parts that have moved to another tile, including
  tick updates.
- Fix a null-pointer crash when a detached part sends a description update.
- Return zero light for empty multipart blocks.
- Fix extra block conversions when a client receives a part update while it still
  has an outdated block at that position.
- Stop the singleplayer client from rebuilding the server's shared material list
  during login. Apply material-list changes only after the full list is valid.
- Show a missing texture for unavailable microblock materials. Keep their original
  names in NBT and item drops, and prevent crafting with the placeholder.
- Clear the world reference after drawing multipart blocks.
- Restore the previous shader material after each microblock draw, including
  nested draws and drawing errors.

These fixes include changes from GTNH's ForgeMultipart fork.

## [3.0.1] - 2026-11-12

The mod changes are the same as [3.1.0](https://github.com/Sumire-Labs/CBMultipart-Rescaled/releases/tag/3.1.0).

## [3.0.0] - 2026-11-12

### Changed

- Port Minecraft 1.12.2 Multipart to Cleanroom with Java 25 and Scala 3.9.0,
  using CleanroomDevEnv and kappa-maintainer's port. Include its particle effects.
- Keep the existing package names, mod IDs and world save format. Scala mods that
  depend on Multipart need rebuilding against the Scala 3 API.
- Use CCL CRE 3.3.10, ChickenASM 4.0.1 and HEI 4.35.0. Pin dependency versions.
- Add Sumire Labs to the author lists of all three mod containers.
- Read TASTy class headers without resolving method bodies. This works in MCP
  development and SRG releases, including dedicated servers.
- Add build and integration-test workflows, GitHub Releases, and publishing to
  Maven, Modrinth and CurseForge. Remote uploads are disabled by default in this
  release; enable them after setting the destination and credentials.

### Fixed

- Fix player references retained by the control-key handler.
- Fix buttons staying pressed after conversion to multipart blocks.
- Fix generated Java passthrough classes failing bytecode verification.

[Unreleased]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/4.0.0...HEAD
[4.0.0]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/3.1.2...4.0.0
[3.1.2]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/3.1.1...3.1.2
[3.1.1]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/3.1.0...3.1.1
[3.1.0]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/3.0.1...3.1.0
[3.0.1]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/compare/3.0.0...3.0.1
[3.0.0]: https://github.com/Sumire-Labs/CBMultipart-Rescaled/releases/tag/3.0.0
