# Changelog

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
