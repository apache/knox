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
# Generates the dev-only single-EKU keystore fixtures used by the
# test_single_eku_mtls.py integration test:
#   - host-client_keystore.jks : a key pair whose certificate carries ONLY the
#                                 clientAuth EKU (the single-EKU client identity)
#   - global_truststore.jks    : the server truststore trusting that client cert
#
# These are committed, self-signed, dev-only fixtures (NOT secrets). The store
# password is the same dev password the apache/knox-dev keystores use so the
# master-secret fallback can resolve them. JKS is used only for this dev
# fixture; production keystore type comes from config and is never hardcoded in
# Knox Java code.
set -euo pipefail
DIR="${1:?usage: gen-single-eku-keystores.sh <output-dir>}"
PASS="horton"
mkdir -p "$DIR"

# Client identity: a key pair whose cert carries ONLY the clientAuth EKU
keytool -genkeypair -alias gateway-httpclient-key -keyalg RSA -keysize 2048 \
  -dname "CN=knox-client" -ext "eku=clientAuth" -validity 3650 \
  -keystore "$DIR/host-client_keystore.jks" -storetype JKS \
  -storepass "$PASS" -keypass "$PASS"

# Export the client cert and import it into the server truststore (clients trusted by Knox)
keytool -exportcert -alias gateway-httpclient-key -rfc \
  -keystore "$DIR/host-client_keystore.jks" -storepass "$PASS" \
  -file "$DIR/client.cer"
keytool -importcert -noprompt -alias knox-client \
  -keystore "$DIR/global_truststore.jks" -storetype JKS -storepass "$PASS" \
  -file "$DIR/client.cer"
rm -f "$DIR/client.cer"

# PEM client material for the Python tests container (requests cert=(cert, key)).
keytool -exportcert -alias gateway-httpclient-key -rfc \
  -keystore "$DIR/host-client_keystore.jks" -storepass "$PASS" \
  -file "$DIR/client-cert.pem"
keytool -importkeystore -srckeystore "$DIR/host-client_keystore.jks" \
  -srcstoretype JKS -srcstorepass "$PASS" -srcalias gateway-httpclient-key \
  -destkeystore "$DIR/client.p12" -deststoretype PKCS12 -deststorepass "$PASS"
openssl pkcs12 -in "$DIR/client.p12" -nocerts -nodes -passin pass:"$PASS" \
  | openssl pkey -out "$DIR/client-key.pem"
rm -f "$DIR/client.p12"

echo "Generated single-EKU dev keystores in $DIR"
