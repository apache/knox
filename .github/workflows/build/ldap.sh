#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -e

# Expose the demo LDAP over a secure (LDAPS) transport so the Knox backend can be
# configured to proxy to it over TLS. Generate a self-signed, dev-only certificate on
# each start (the demo LDAP is a throwaway fixture, not a secret). The store and key
# passwords must match: ApacheDS opens the keystore and recovers the key with a single
# password. PKCS12 matches the JVM default keystore type ApacheDS loads it with.
KEYSTORE=/knox-runtime/conf/ldap-keystore.p12
KEYSTORE_PASSWORD=ldap-keystore-password

keytool -genkeypair -alias ldaps -keyalg RSA -keysize 2048 \
  -dname "CN=ldap" -ext "san=dns:ldap,dns:localhost" -validity 3650 \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD"

java -Dldap.ssl.enabled=true \
  -Dldap.keystore.file="$KEYSTORE" \
  -Dldap.keystore.password="$KEYSTORE_PASSWORD" \
  -jar /knox-runtime/bin/ldap.jar /knox-runtime/conf