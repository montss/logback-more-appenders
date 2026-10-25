#!/usr/bin/env bash
# Spring Boot compatibility check for one row of the README matrix.
#
# Runs the compat app (built from compat/spring-boot-app) with one of the example configs and fails unless:
#   1. the app's events appear on the console,
#   2. Logback reported no configuration problems (no "ERROR in" / "WARN in" status lines), and
#   3. Fluentd received the events, tagged logs.compat-check.
#
# Usage: compat/check.sh <example-dir> [fluentd-container]
#   example-dir:       spring-boot-2.7 or spring-boot-3.x (a folder under examples/)
#   fluentd-container: name of the running Fluentd container (default: compat-fluentd)
# Run from the repository root, with Fluentd listening on localhost:24224 (see compat/fluentd/fluent.conf).
set -euo pipefail

example="$1"
container="${2:-compat-fluentd}"
config="examples/$example/logback-spring.xml"
jar="compat/spring-boot-app/target/spring-boot-compat-app-1.jar"
token="compat-$(date +%s)-$RANDOM"

[ -f "$config" ] || { echo "::error::No example config at $config"; exit 1; }
[ -f "$jar" ] || { echo "::error::Build the compat app first: $jar is missing"; exit 1; }

echo "Running the compat app with $config (token $token)"
if ! output=$(java -jar "$jar" --logging.config="file:$config" "$token" 2>&1); then
  echo "$output"
  echo "::error::The compat app failed to run"
  exit 1
fi
echo "$output"

if grep -E "(ERROR|WARN) in ch\.qos\.logback" <<<"$output"; then
  echo "::error::Logback reported configuration problems with $config"
  exit 1
fi

console=$(grep -c "compat-check .* $token" <<<"$output" || true)
if [ "$console" -lt 3 ]; then
  echo "::error::Expected 3 events on the console, found $console"
  exit 1
fi

received=0
for _ in $(seq 1 30); do
  received=$(docker logs "$container" 2>&1 | grep "logs.compat-check" | grep -c "$token" || true)
  [ "$received" -ge 3 ] && break
  sleep 1
done
docker logs "$container" 2>&1 | grep "logs.compat-check" | grep "$token" || true
if [ "$received" -lt 3 ]; then
  echo "::error::Expected 3 events in Fluentd, found $received"
  exit 1
fi
echo "OK: $config works, Fluentd received $received events"
