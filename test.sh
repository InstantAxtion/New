#!/usr/bin/env bash
# Runs the game's tests on a desktop JVM (no Android device needed), split across the CPU's cores.
#   ./test.sh            all tests, in parallel
#   ./test.sh 10.23      only the tests whose names start with "10.23"
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build/test
mkdir -p build/test
javac -nowarn -encoding UTF-8 -d build/test $(find tests/stubs tests/src -name '*.java') \
    $(find src -name '*.java' ! -name MainActivity.java ! -name Sound.java) 2>&1 | grep -v '^Note:' || true
[ -f build/test/com/instantaxtion/zombiesandbox/GameTests.class ] || { echo "compile failed"; exit 1; }
run() { java -Djava.awt.headless=true -Xmx2g -cp build/test com.instantaxtion.zombiesandbox.GameTests 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'; }
if [ $# -gt 0 ]; then
    ONLY="$1" run
    exit "${PIPESTATUS[0]}"
fi
N="${SHARDS:-$(nproc)}"
[ "$N" -gt 6 ] && N=6
pids=()
for ((i = 0; i < N; i++)); do
    SHARDS=$N SHARD=$i run > "build/test/shard$i.log" &
    pids+=($!)
done
status=0
for p in "${pids[@]}"; do wait "$p" || status=1; done
cat build/test/shard*.log | grep -v ' passed, ' || true
grep -h ' passed, ' build/test/shard*.log | awk '{p += $1; f += $3} END {print p " passed, " f " failed"}'
grep -q '^FAIL' build/test/shard*.log && status=1
exit $status
