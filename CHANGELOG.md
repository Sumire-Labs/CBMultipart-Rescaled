# Changelog

## Unreleased

### Fixed

- Expose ordinary inventory slots to hoppers and sided item handlers while
  preserving restrictions on sided inventories.
- Read Scala 3 traits nested in objects from their enclosing TASTy files and
  resolve their parent traits by JVM name.
- Generate initialization setters for Scala 3 trait values and reserve enough
  operand stack space for long and double field getters.
- Restore saved ticks using the original part indices when unavailable part types
  are skipped during loading, so ticks do not move to another part.
- Send missing microblock materials' original names to clients so pick-block keeps
  the saved material name.
- Fix microblock description updates sending a material ID where the client
  expects shape data.
- Return zero explosion resistance for an empty multipart tile.
- Keep the current part registry when a server's part list is rejected. Publish
  accepted names, factories and ID encoding together, and skip the redundant
  part-list update in singleplayer.
- Restore scheduled ticks in their actual chunks, including part indices above
  127. Resume loaded ticks, skip stale indices and keep the saved scheduler clock.
- Allow parts to be added or removed during tick callbacks without crashing the
  update loop. Newly added parts start ticking on the next tick.
- Use World's deferred tile registration and removal when a multipart tile changes
  class during a tick, avoiding changes to the list World is iterating.
- Keep packet updates queued during a send for the next flush.
- Read four-byte update masks without extending the sign bit into the upper
  32 bits.

Missing-material packets now include the original name. Use the same updated
build on the client and server.

### Development

- Update the CleanroomDevEnv build setup: select runtime JARs for embedded
  libraries, replace tokens in renamed classes and check class constants for
  unresolved tokens.
- Enable Spotless checks by default.

## 3.1.1 - 2026-11-13

### Added

- Add the CBMultipart Rescaled icon to all three mod entries.

### Changed

- Update the README with changes, requirements and links to dependencies and forks.
- Enable Maven, CurseForge and Modrinth uploads for tagged releases. Include
  dependency and incompatibility information on CurseForge and Modrinth.

## 3.1.0 - 2026-11-12

### Added

- Add optional Demonica support so terrain shaders can identify each microblock's original
  block material.

### Fixed

- Stop old tiles from updating parts that have moved to another tile, including
  tick updates.
- Fix a null-pointer crash when a detached part sends a description update.
- Return zero light for an empty multipart block.
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

### Development

- Add server regression tests and client rendering checks with Celeritas and
  Demonica. Logic ticks and rendering remain separate.

## 3.0.1 - 2026-11-12

The mod changes are the same as [3.1.0](https://github.com/Sumire-Labs/CBMultipart-Rescaled/releases/tag/3.1.0).

## 3.0.0 - 2026-11-12

### Changed

- Port Minecraft 1.12.2 Multipart to Cleanroom with Java 25 and Scala 3.9.0,
  using CleanroomDevEnv and kappa-maintainer's port. Include its particle effects.
- Keep the existing package names, mod IDs and world save format. Scala mods that
  depend on Multipart need rebuilding against the Scala 3 API.
- Use CCL CRE 3.3.10, ChickenASM 4.0.1 and HEI 4.35.0. Dependency versions are pinned.
- Add Sumire Labs to the author lists of all three mod containers.

### Fixed

- Fix player references retained by the control-key handler.
- Fix buttons staying pressed after conversion to multipart blocks.
- Fix generated Java passthrough classes failing bytecode verification.

### Development

- Read only TASTy class headers, without resolving method bodies. This works in
  MCP development and SRG releases, including dedicated servers.
- Add build and integration-test workflows, GitHub Releases, and publishing to
  Maven, Modrinth and CurseForge. Remote uploads are disabled by default; enable
  them after setting the destination and credentials.
