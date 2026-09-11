# Packaged Fabric server smoke test

Run the final, remapped JAR in a clean dedicated server rather than Loom's development classpath:

```bash
scripts/fabric-packaged-smoke.sh
```

The script reads the pinned Minecraft, Loader, Fabric API, and Fabric Installer versions from `fabric/gradle.properties`, builds `remapJar`, downloads reusable dependencies into `${XDG_CACHE_HOME:-~/.cache}/mekanism-fabric-smoke`, and creates an EULA-accepted server only under a temporary directory. It passes only after the server log lists mod id `mekanism` and reports `Done (`. The server process and temporary directory are removed on every exit.

Diagnostic logs are retained under `build/fabric-packaged-smoke/<UTC timestamp>-<pid>/`. Set `FABRIC_SMOKE_KEEP_SERVER=1` to retain the disposable server directory as well. Other useful overrides are `FABRIC_SMOKE_TIMEOUT`, `FABRIC_SMOKE_CACHE_DIR`, `FABRIC_SMOKE_LOG_DIR`, and `FABRIC_INSTALLER_VERSION`.

This gate performs no Docker, deployment queue, or production-server operations.
