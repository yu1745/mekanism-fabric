#!/usr/bin/env bash
# Rebuild fabric/build/loom-cache/argFiles/runClient after a clean.
#
# Why this exists: `./build.sh --clean` and step 1 of `scripts/release-check.sh` delete `fabric/build/`, which takes
# Loom's DLI argfile with it. `configureClientLaunch` does not write it back - only running the `runClient` Gradle task
# does, and launching a game client through Gradle is forbidden (AGENTS: clients start through mdb and nowhere else).
# Without the argfile `mdb session start` dies with `could not open .../argFiles/runClient`, which is easy to misread
# as a broken client build and has twice tempted a bare `gradlew runClient`.
#
# So build the argfile as a build artifact instead:
#   1. ask Gradle for the project's real `runtimeClasspath` (NOT remapClasspath.txt, which lacks fabric-loader and
#      dev-launch-injector), and
#   2. prepend this project's own class and resource outputs, because `runtimeClasspath` does not contain them - and
#      without them the client starts happily with the development mod absent from the classpath (blocks report
#      "not registered", recipe viewers show no Mekanism entries).
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT=fabric
ARG_FILE="$PROJECT/build/loom-cache/argFiles/runClient"
CP_DUMP=/tmp/mekanism-client-runtime-cp.txt

cleanup() { rm -f /tmp/mekanism-dump-cp.gradle; }
trap cleanup EXIT

cat > /tmp/mekanism-dump-cp.gradle <<'GRADLE'
allprojects {
    tasks.register('dumpRuntimeCp') {
        doLast {
            def cfg = configurations.findByName('runtimeClasspath')
            if (cfg != null) {
                new File("/tmp/cp-${project.name}.txt").text = cfg.files.collect { it.absolutePath }.join(':')
            }
        }
    }
}
GRADLE

./gradlew -p "$PROJECT" -I /tmp/mekanism-dump-cp.gradle dumpRuntimeCp --console=plain --no-configuration-cache >/dev/null

ROOT_CP=/tmp/cp-mekanism-fabric-bootstrap.txt
[ -s "$ROOT_CP" ] || { echo "rebuild-client-argfile: Gradle produced no runtime classpath" >&2; exit 1; }

python3 - "$ARG_FILE" "$ROOT_CP" "$PWD/$PROJECT" <<'PY'
import pathlib, sys

arg_file, cp_dump, project_dir = sys.argv[1], sys.argv[2], pathlib.Path(sys.argv[3])
deps = pathlib.Path(cp_dump).read_text().strip().split(':')
head = [str(project_dir / 'build/classes/java/main'), str(project_dir / 'build/resources/main')]
entries = head + [entry for entry in deps if entry not in head]
out = pathlib.Path(arg_file)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('-classpath\n' + ':'.join(entries) + '\n')
print(f"rebuild-client-argfile: {out} ({len(entries)} entries)")
PY
