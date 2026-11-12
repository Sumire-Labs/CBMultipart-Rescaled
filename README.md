# CBMultipart Rescaled

A port of CBMultipart / Forge Multipart for Minecraft 1.12.2, built for Cleanroom with Java 25 and Scala 3.

It lets multiple functional parts share a single block space. This includes microblocks and multipart versions of vanilla torches, levers and buttons.

## Changes

- Updated for Cleanroom, Java 25 and Scala 3.
- Added optional Demonica support so shader packs can identify each microblock's original block material.
- Fixed part updates and ticks after tile replacement, crashes when updating detached parts, and light checks on empty multipart blocks.
- Fixed extra block conversions when client block data is out of date.
- Fixed material synchronization in singleplayer and kept invalid material lists from replacing the current list.
- Added a missing-material placeholder that preserves the original material name in saves and item drops. The placeholder cannot be used in recipes.
- Cleared world references after rendering multipart blocks.
- Included fixes from GTNH's ForgeMultipart fork and added regression tests.

## Requirements

- [Java 25 (OpenJDK)](https://github.com/openjdk/jdk25u)
- [Cleanroom](https://github.com/CleanroomMC/Cleanroom) 0.7.14 or newer
- [Scalar](https://github.com/CleanroomMC/Scalar) 3.9.0 or newer within the 3.9.x series
- [CodeChicken Lib CRE](https://github.com/Refitbench/CodeChickenLib) 3.3.10 or newer

Optional shader integration: [Demonica](https://github.com/ndellagrotte/Demonica).

When installing from Modrinth, install Scalar separately from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/scalar).

## For developers

Available from [Sumire Labs Maven](https://artifact.sumirelabs.com/maven/releases):

```text
codechicken:CBMultipart-Rescaled:<version>
```

## Credits

- [TheCBProject/CBMultipart](https://github.com/TheCBProject/CBMultipart): Original CBMultipart / Forge Multipart code.
- [kappa-maintainer/CBMultipart](https://github.com/kappa-maintainer/CBMultipart): Cleanroom and Scala 3 port.
- [ACGaming/CBMultipart](https://github.com/ACGaming/CBMultipart): Control-key handler memory leak fix.
- [GTNewHorizons/ForgeMultipart](https://github.com/GTNewHorizons/ForgeMultipart): Multipart and microblock bug fixes adapted for Minecraft 1.12.2.

## License

[LGPL-2.1-or-later](LICENSE.md)
