# Gaming Setup mod (Fabric 1.21.11) - all in one

Easiest way to get the jar (no installs):
1. Create a free GitHub account, click New repository.
2. Upload ALL files and folders from this project (including the hidden ".github" folder) and commit.
3. Open the Actions tab, wait for the green tick, open the run, download "gamingsetup-jar" from Artifacts. The jar inside (not "-sources") goes in .minecraft/mods.
You also need Fabric Loader + Fabric API for 1.21.11.

Or build locally: install JDK 21 and Gradle 9.2, then run `gradle build` here. Jar appears in build/libs.

Use: creative inventory > Functional Blocks. Put a redstone block within 3 blocks of the PC, right-click the PC to start it.
Link Cable: right-click monitor, then the PC (don't sneak).
