#!/bin/sh
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
# KNOX-3359: single-EKU container entrypoint.
#
# The dev keystore fixtures (host-client_keystore.jks / server-identity_keystore.jks
# / global_truststore.jks) are protected with the dev password "horton". The
# baked apache/knox-dev image resolves keystore passwords from credential-store
# aliases, falling back to the (image-specific) master secret when an alias is
# absent. The mounted fixtures do NOT use the master secret as their password,
# so relying on the fallback would fail to open them. We therefore create every
# alias single-EKU resolves explicitly, then start the gateway. Keystore TYPE
# (JKS) comes from gateway-site.xml (config), never hardcoded in Knox Java code.
set -e

PASS="horton"

# Create the credential-store aliases the single-EKU config resolves:
#   - gateway-httpclient-keystore-password    : client keystore store password
#     (the client key passphrase alias falls back to this one)
#   - gateway-httpclient-truststore-password  : outbound HTTP client truststore
#   - gateway-truststore-password             : server truststore password
#   - gateway-identity-keystore-password      : server identity keystore password
#   - gateway-identity-passphrase             : server identity key passphrase
#
# Use the batch create-aliases command so all five aliases are created in a
# SINGLE knoxcli JVM boot. Creating them one-per-invocation cost ~6s of JVM
# startup each (~30s total), which delayed the gateway bind past the CI wait
# window and caused flaky "connection refused" failures in the mTLS suite.
/knox-runtime/bin/knoxcli.sh create-aliases \
    --alias gateway-httpclient-keystore-password --value "$PASS" \
    --alias gateway-httpclient-truststore-password --value "$PASS" \
    --alias gateway-truststore-password --value "$PASS" \
    --alias gateway-identity-keystore-password --value "$PASS" \
    --alias gateway-identity-passphrase --value "$PASS"

exec java -jar /knox-runtime/bin/gateway.jar
