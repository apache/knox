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

# The embedded Knox LDAP service is exposed over LDAPS (see gateway-site.xml:
# gateway.ldap.ssl.enabled). Provision a dev-only certificate for it and make the
# JVM trust that certificate so the knoxldap Shiro realm - which authenticates over
# ldaps://localhost:33390 via JNDI - can validate it.
KEYSTORE=/knox-runtime/conf/ldaps-keystore.p12
KEYSTORE_PASSWORD=knox-ldap-ssl-password
KEYSTORE_PASSWORD_ALIAS=gateway_ldap_ssl_keystore_password

# 1) Self-signed dev certificate for the embedded LDAP service's secure transport.
#    Store and key passwords must match (ApacheDS opens the store and recovers the
#    key with a single password). PKCS12 matches the JVM default keystore type.
keytool -genkeypair -alias ldaps -keyalg RSA -keysize 2048 \
  -dname "CN=localhost" -ext "san=dns:localhost,dns:knox" -validity 3650 \
  -keystore "$KEYSTORE" -storetype PKCS12 \
  -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD"

# 2) Store the keystore password under the alias the LDAP SSL config resolves.
/knox-runtime/bin/knoxcli.sh create-alias "$KEYSTORE_PASSWORD_ALIAS" --value "$KEYSTORE_PASSWORD"

# 3) Trust that certificate in the JVM default truststore (cacerts) so the JNDI-based
#    Shiro LDAP realm accepts it. This is additive - it does not remove the default CAs.
keytool -exportcert -alias ldaps -rfc \
  -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASSWORD" -file /tmp/ldaps-cert.pem
keytool -importcert -noprompt -alias knox-ldaps \
  -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit \
  -file /tmp/ldaps-cert.pem
rm -f /tmp/ldaps-cert.pem

# Start Knox. Endpoint (hostname) identification is disabled for the embedded LDAPS
# connection - the dev cert is self-signed - but trust is still enforced via cacerts.
java -Dcom.sun.jndi.ldap.object.disableEndpointIdentification=true \
  -jar /knox-runtime/bin/gateway.jar
