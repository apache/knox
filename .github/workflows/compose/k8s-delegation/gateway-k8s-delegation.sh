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
#
# Custom knox entrypoint used by the compose stack (docker-compose.yml).
#
# The RFC 8693 token-exchange path fetches a dynamically-registered issuer's
# OIDC discovery document and JWKS to verify the subject token's signature.
# Both fetches (Nimbus JWKS retrieval + OIDC discovery) use the JVM default
# SSLContext / cacerts -- NOT the gateway.httpclient.truststore.* config -- so
# for Knox to trust the throwaway k3s cluster's HTTPS endpoints we must import
# its serving CA into cacerts before the gateway starts.
#
# The k8s-bootstrap service exports that CA to the shared k3s-output volume
# (mounted read-only at /k3s) and knox depends_on it with
# service_completed_successfully, so the file is present by the time this runs.
set -e

CA_CERT=/k3s/k3s-ca.crt
echo "Waiting for k3s CA cert at ${CA_CERT}..."
until [ -f "${CA_CERT}" ]; do sleep 2; done

# Additive import -- does not remove the default CAs. Fresh container on every CI
# run, so the alias never pre-exists.
keytool -importcert -noprompt -alias k3s-ca \
  -keystore "${JAVA_HOME}/lib/security/cacerts" -storepass changeit \
  -file "${CA_CERT}"
echo "Imported k3s CA into JVM cacerts."

# Hand off to the standard entrypoint (LDAPS dev cert + token JWK + gateway start).
exec /gateway.sh
