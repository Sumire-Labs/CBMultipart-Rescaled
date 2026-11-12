#!/usr/bin/env bash
# Run the distributable jars without Gradle's MCP classes or compiler classpath.
set -euo pipefail
cd "$(dirname "$0")/../.."
repo="$PWD"
cleanroom_version=$(sed -n 's/^cleanroom_version *= *//p' gradle.properties | tr -d '\r')
test -n "$cleanroom_version"
./gradlew stageProductionSmoke --console=plain
server="$repo/run/production-smoke"
mkdir -p "$server/mods"
curl --fail --location --retry 3 \
  "https://github.com/CleanroomMC/Cleanroom/releases/download/$cleanroom_version/cleanroom-$cleanroom_version-installer.jar" \
  --output "$server/installer.jar"
java -jar "$server/installer.jar" server --version "$cleanroom_version" \
  --dir "$server" --no-provision-java --memory 2G --no-gui --yes
# This is the isolated fixture above; remove older-version test jars on repeat runs.
find "$server/mods" -maxdepth 1 -type f -name '*.jar' -delete
cp "$repo"/build/production-smoke/mods/*.jar "$server/mods/"
cd "$server"
printf 'eula=true\n' > eula.txt
printf '%s\n' 'server-ip=127.0.0.1' 'server-port=0' 'online-mode=false' \
  'level-name=smoke' 'level-type=FLAT' 'generate-structures=false' \
  'view-distance=3' 'max-players=1' > server.properties
rm -f production-smoke-result.txt
# No legacy Forge mods are installed, so Fugue is intentionally absent here.
timeout 180s java -Dfml.queryResult=confirm -Dterminal.jline=false \
  -Dcbmultipart.smoke.result=production-smoke-result.txt \
  @user_jvm_args.txt "@libraries/com/cleanroommc/cleanroom/$cleanroom_version/unix_args.txt" nogui \
  | tee production-smoke.log
test "$(tr -d '\r\n' < production-smoke-result.txt)" = PASS
