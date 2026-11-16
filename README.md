# CB Multipart Rescaled

A fork of [CBMultipart](https://github.com/TheCBProject/CBMultipart) for [Cleanroom](https://github.com/CleanroomMC/Cleanroom). It provides an API for dynamically managing multiple functional parts in a single block space.
This fork adds features and bug fixes, and updates the mod for Cleanroom / [Java 25](https://github.com/openjdk/jdk25u) + [Scala 3](https://github.com/scala/scala3).

## Requirements

- [Cleanroom](https://github.com/CleanroomMC/Cleanroom) 0.7.14 or newer
- [Scalar](https://github.com/CleanroomMC/Scalar) 3.10.0 or newer
- [CodeChicken Lib CRE](https://github.com/Refitbench/CodeChickenLib) 3.3.10 or newer

## Changes

- Ported to Cleanroom / Java 25 + Scala 3.
- Updated dependencies for [Scalar](https://github.com/CleanroomMC/Scalar) 3.10, [CodeChicken Lib CRE](https://github.com/Refitbench/CodeChickenLib) and [Had Enough Items (HEI)](https://github.com/CleanroomMC/HadEnoughItems).
- Added [Demonica](https://github.com/ndellagrotte/Demonica) integration so shaders can identify each microblock's original block material.
- Added landing and running particles that match the supporting part's material.
- Added a missing-material placeholder that preserves original names in saves, synchronization, item drops and pick-block results. Missing materials cannot be used in recipes.
- Fixed part and material registry synchronization in singleplayer. Invalid registry lists no longer replace existing data.
- Fixed unintended block conversions caused by outdated client block data.
- Fixed crashes and lost parts when conversion callbacks add or remove parts or replace their tile. Corrected the synchronization order for conversions that produce multiple parts.
- Stopped old tiles from updating parts after tile replacement.
- Fixed concurrent modification errors when parts are added or removed, or tiles are replaced, during a tick.
- Fixed the restoration of scheduled ticks after chunk reloads, including shifted part indices caused by missing parts and schedules lost while loading chunks.
- Stopped client-side parts from entering the server's random tick queue.
- Fixed redstone connection directions and checks for the Observer's output face. Made vanilla blocks and their converted parts use matching connection masks.
- Fixed access to regular inventory parts from hoppers and sided item handlers.
- Fixed crashes when updating detached parts or querying the light level or explosion resistance of empty multipart tiles.
- Fixed packet updates lost when rescheduled during a send, incorrect update mask decoding and queued updates left after world unload.
- Fixed memory leaks caused by retained player references and world references held during rendering.
- Fixed dynamic generation of nested traits, initialized `val` fields, `lazy val` fields and `Long`/`Double` fields.
- Fixed dynamic class generation and bytecode verification for Java extensions.

## For developers

Build Scala extensions against this version with Scala 3.10.x.

Available from [Sumire Labs Maven](https://artifact.sumirelabs.com/maven/releases):

```text
codechicken:CBMultipart-Rescaled:<version>
```

## Credits

- [TheCBProject/CBMultipart](https://github.com/TheCBProject/CBMultipart): Original CBMultipart / Forge Multipart code.
- [kappa-maintainer/CBMultipart](https://github.com/kappa-maintainer/CBMultipart): Cleanroom and Scala 3 port.
- [ACGaming/CBMultipart](https://github.com/ACGaming/CBMultipart): Control-key handler memory leak fix.
- [GTNewHorizons/ForgeMultipart](https://github.com/GTNewHorizons/ForgeMultipart): Multipart and microblock bug fixes adapted for Minecraft 1.12.2.
