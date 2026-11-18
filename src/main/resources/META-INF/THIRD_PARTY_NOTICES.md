# Bundled third-party software

Create Shining Stage bundles the unmodified official Maven artifact
`foundry.veil:veil-neoforge-1.21.1:4.3.2` as a separate Jar-in-Jar library.
Its own nested libraries are retained unmodified. NeoForge may select another
compatible copy supplied by another installed mod.

The mod's All Rights Reserved declaration does not apply to these libraries.
Their respective licenses govern them. You may replace these libraries with
compatible modified versions, and reverse engineer the combined work as needed
to debug those modifications, as provided by the LGPL. No additional restriction
from this mod overrides those rights.

## Veil 4.3.2

- Authors: FoundryMC and contributors (see the upstream source and its notices).
- License: GNU Lesser General Public License, version 3.
- License texts: `META-INF/licenses/LGPL-3.0.txt` and `META-INF/licenses/GPL-3.0.txt`.
- The original `LICENSE_Veil` is also retained inside the nested Veil JAR.
- Corresponding source, including build scripts, at release commit
  `540ad3778c1a5dafe905420adb21cb05e459b5c4`:
  https://github.com/FoundryMC/Veil/tree/540ad3778c1a5dafe905420adb21cb05e459b5c4
- Complete source archive:
  https://github.com/FoundryMC/Veil/archive/540ad3778c1a5dafe905420adb21cb05e459b5c4.zip
- Published Java sources:
  https://maven.blamejared.com/foundry/veil/veil-neoforge-1.21.1/4.3.2/veil-neoforge-1.21.1-4.3.2-sources.jar

## glsl-processor 0.2.3 (nested in Veil)

- Authors: Ocelot and contributors (see the upstream source and its notices).
- License: GNU Lesser General Public License, version 3; the full texts above apply.
- Corresponding source, including build scripts, at release commit
  `b3407f438f39b4111a1880b87afb7ca04fb5075e`:
  https://github.com/Ocelot5836/glsl-processor/tree/b3407f438f39b4111a1880b87afb7ca04fb5075e
- Complete source archive:
  https://github.com/Ocelot5836/glsl-processor/archive/b3407f438f39b4111a1880b87afb7ca04fb5075e.zip

## molang-compiler 3.1.1.19 (nested in Veil)

- Copyright (c) 2023 Ocelot.
- License: MIT; full copyright and license notice in
  `META-INF/licenses/molang-compiler-MIT.txt`.
- Project: https://github.com/Ocelot5836/molang-compiler
- Published sources:
  https://maven.blamejared.com/gg/moonflower/molang-compiler/3.1.1.19/molang-compiler-3.1.1.19-sources.jar

All of these components are provided without warranty, as described in their
licenses. The source links above provide free access on the upstream servers;
redistributors must keep corresponding source available as required by the
licenses, including for any changed bundled versions.
