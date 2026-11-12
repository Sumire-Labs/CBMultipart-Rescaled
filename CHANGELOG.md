# Changelog

## 3.1.0 - 2026-11-12

- Port GTNH fixes for callbacks after tile replacement, detached part updates,
  empty-tile lighting, and stale client-side block conversion. Apply the tile
  ownership check to tick dispatch as well.
- Publish material registry changes as a complete snapshot and skip redundant
  material synchronization in integrated singleplayer.
- Add a missing-material placeholder that preserves the original material name
  through NBT and item drops; exclude it from cutting and crafting recipes.
- Release the render state's world reference after multipart rendering.
- Add optional Demonica material-ID scopes for microblock terrain quads, including
  nested render and exception cleanup. No shader mod is required to run the mod.
- Add server and production-client regression tests, including Celeritas and
  Demonica scene rendering. Existing logic-tick/render separation is unchanged.

## 3.0.1 - 2026-11-12

- Initial distribution of the changes subsequently included in 3.1.0.

## 3.0.0 - 2026-11-12

- Move the 1.12.2 branch to CleanroomDevEnv, Java 25 and Scala 3.9.0.
- Preserve the existing packages, mod IDs and multipart save format. Dependent
  Scala mods must be rebuilt against the Scala 3 API.
- Incorporate kappa-maintainer's Cleanroom port and particle effects.
- Read TASTy class headers without resolving method bodies, supporting both MCP
  development and SRG distributions on dedicated servers.
- Update to CCL CRE 3.3.10, ChickenASM 4.0.1 and HEI 4.35.0; pin their versions.
- Fix control-state player retention, pressed-button conversion and generated
  Java passthrough stack-map frames.
- Add Sumire Labs to all three mod containers' author lists.
- Add build, integration-test, GitHub Release, Maven, Modrinth and CurseForge
  workflows. Remote Maven, Modrinth and CurseForge publication are disabled
  until their destinations and credentials are configured.
