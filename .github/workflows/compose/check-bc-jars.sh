#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# KNOX-3566: checks the BouncyCastle jars shipped in the apache/knox-dev image,
# i.e. in the gateway and knoxshell distributions built from this tree. Mixed
# BouncyCastle versions, or a legacy *-jdk15on jar next to the jdk18on ones,
# only fail at runtime, and only on the code paths that hit the mismatch.
#
#   - the gateway (dep/) ships exactly one bcprov, bcpkix and bcutil jar
#   - every BouncyCastle jar is a jdk18on artifact on <bouncycastle.version>
#     from the root pom.xml
#   - no BouncyCastle artifact is shipped twice in the same distribution
#
# Usage (from the repo root): check-bc-jars.sh [image]
set -euo pipefail

IMAGE="${1:-apache/knox-dev:${IMAGE_TAG:-master}}"
POM="$(dirname "$0")/../../../pom.xml"
EXPECTED=$(sed -n 's#.*<bouncycastle.version>\(.*\)</bouncycastle.version>.*#\1#p' "$POM")
if [ -z "$EXPECTED" ]; then
  echo "Could not read <bouncycastle.version> from $POM"
  exit 1
fi

JARS=$(docker run --rm --entrypoint sh "$IMAGE" -c 'find /knox-runtime -name "bc*.jar" | sort')
echo "Expected BouncyCastle version: $EXPECTED"
echo "BouncyCastle jars in $IMAGE:"
echo "${JARS:-  (none)}"

errors=0
fail() {
  echo "ERROR: $1"
  errors=$((errors + 1))
}

for artifact in bcprov bcpkix bcutil; do
  count=$(grep -c "^/knox-runtime/dep/${artifact}-jdk18on-[^/]*\.jar$" <<< "$JARS" || true)
  [ "$count" -eq 1 ] || fail "expected exactly one ${artifact}-jdk18on jar in /knox-runtime/dep, found $count"
done

while read -r jar; do
  [ -n "$jar" ] || continue
  name=$(basename "$jar" .jar)
  case "$name" in
    *-jdk18on-*) ;;
    *) fail "$jar is not a jdk18on artifact"; continue ;;
  esac
  version=${name##*-}
  [ "$version" = "$EXPECTED" ] || fail "$jar is version $version, expected $EXPECTED"
done <<< "$JARS"

# Same artifact twice within a distribution (the gateway is everything outside
# /knox-runtime/knoxshell). Keyed by distribution + artifact name without version.
duplicates=$(sed -E 's#^(/knox-runtime(/knoxshell)?)/.*/([^/]*)-[^-/]*\.jar$#\1 \3#' <<< "$JARS" | sort | uniq -d)
[ -z "$duplicates" ] || fail "BouncyCastle artifacts shipped more than once: $duplicates"

if [ "$errors" -ne 0 ]; then
  echo "$errors BouncyCastle jar check(s) failed"
  exit 1
fi
echo "BouncyCastle jar checks passed"
