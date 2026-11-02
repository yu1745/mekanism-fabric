![Mekanism Logo](logo.png)

# Mekanism for Minecraft 1.21 — Fabric port #

**This branch is the Fabric port of Mekanism**, converted from the official NeoForge `v1.21.1-10.7.19.85` source.
The four release JARs build, the server path and the save format are exercised by the gates below, and the mod is
playable, with bounded verification: EMI/JEI and Jade/WTHIT integrations are exercised, while large networks,
shader compatibility and several long-run or gameplay combinations remain unverified.

| I want to… | Read |
|---|---|
| install it and start playing | [`docs/INSTALL.md`](docs/INSTALL.md) — requirements, the four JARs, install steps |
| know what changed in this build | [`CHANGELOG.md`](CHANGELOG.md) |
| see how far the port is | [`docs/milestones/README.md`](docs/milestones/README.md) — milestone table and release limits |
| take a NeoForge world across | [`docs/INSTALL.md`](docs/INSTALL.md#save-compatibility-with-the-neoforge-build) |
| report where it differs from upstream | [`docs/API_DELTAS.md`](docs/API_DELTAS.md), [`docs/PORT_MATRIX.md`](docs/PORT_MATRIX.md) |
| build and verify locally | [`docs/Development.md`](docs/Development.md), [`docs/FABRIC_SMOKE.md`](docs/FABRIC_SMOKE.md) |
| see licences and attribution | [`docs/LICENSES.md`](docs/LICENSES.md) |

```bash
./build.sh --clean          # four release JARs into dist/ + build-info.json
./gradlew -p fabric test    # 457 JVM tests
scripts/release-check.sh    # server-side release gates (clean build, packaged, paired, rejection, machine, Tech Reborn)
```

Verification notes are summarized in the milestone documents under `docs/milestones/`.

## Upstream Mekanism ##

The text below describes the mod itself and is unchanged from the upstream project.

Mekanism is an independent Minecraft add-on featuring high-tech machinery that can be used to create powerful tools, 
armor, and weapons. You can find more detail on the features on the [**Official Wiki**](https://wiki.aidancbrady.com/wiki/Mekanism).
With features ranging from jetpacks and balloons to factories and energy cubes, the mod does not have a single unifying goal.
Nevertheless, these features combine coherently to create a rich gameplay experience that players will enjoy.

Mekanism uses a tier-based system to organize the majority of its core features, like energy cubes and factories.
There are four tiers: basic, advanced, elite, and ultimate. Players can upgrade their components to the next tier
by placing each component in a crafting grid and surrounding it with the necessary resources for the upgrade or in
world by using tier installers.

After using Mekanism for a while, players can obtain a near-indestructible suit of Refined Obsidian Armor, receive five 
ingots for each ore mined, fly with hydrogen-powered jetpacks, and have cute robotic friends following them around while they mine. :)

# Discord #

Mekanism has a [discord server](https://discord.gg/nmSjMGc) where you can chat with other Mekanism users and the developers. Join us!

# Translating #

If you would like to help translate Mekanism, you can do so through [Crowdin](https://crowdin.com/project/mekanism).

# License #

Mekanism is licensed under the MIT license. You may use it in modpacks, reviews, or any other form as long as you abide by the terms below. 

Copyright 2017-2025 Aidan C. Brady

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

# Maven #
Mekanism v10+ is also available via [ModMaven](https://modmaven.dev/) for developers wishing to make use of our API. Big thanks to K4Unl for hosting.

Update your `build.gradle` file to include the following: 

```groovy
repositories {
    maven { url 'https://modmaven.dev/' }
}

dependencies {
    compileOnly "mekanism:Mekanism:${mekanism_version}:api"
    
    // If you want to test/use Mekanism & its modules during `runClient` invocation, use the following
    runtimeOnly fg.deobf("mekanism:Mekanism:${mekanism_version}")// Mekanism
    runtimeOnly fg.deobf("mekanism:Mekanism:${mekanism_version}:additions")// Mekanism: Additions
    runtimeOnly fg.deobf("mekanism:Mekanism:${mekanism_version}:generators")// Mekanism: Generators
    runtimeOnly fg.deobf("mekanism:Mekanism:${mekanism_version}:tools")// Mekanism: Tools
}
```

Add the following to your `gradle.properties` file (see [Maven](https://modmaven.dev/mekanism/Mekanism/) for the list of available versions):

```properties
mekanism_version=1.21.1-10.7.15.80
```

# Credits #

  * [aidancbrady](https://twitter.com/aidancbrady) - [Patreon](https://www.patreon.com/user?u=260704)
  * thommy101
  * [thiakil](https://twitter.com/thiakil)
  * [pupnewfster](https://twitter.com/pupnewfster) - [Patreon](https://www.patreon.com/pupnewfster), [Ko-fi](https://ko-fi.com/pupnewfster)
  * dizzyd  
  * unpairedbracket
  * [CyanideX](https://twitter.com/theCyanideX) (artwork)
  * [Ridanisaurus](https://twitter.com/Ridanisaurus) (artwork)
  * Cheapshot (artwork)
  * Archadia (artwork)
  * micdoodle
  * Bluexin
  * JaSpr

## YourKit ##
YourKit supports open source projects with innovative and intelligent tools for monitoring and 
profiling Java and .NET applications. YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler), 
[YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/) and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).
Mekanism uses YourKit for all our profiling needs!
