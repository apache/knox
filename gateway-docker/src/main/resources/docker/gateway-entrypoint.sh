#!/usr/bin/env bash

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

# This script can be configured with the following env variables:
# - KNOX_MASTER_SECRET - (optional) master secret for knox in a file, default value is 'knox'
# - MASTER_SECRET - (optional) master secret for knox, value not a file location , default value is 'knox'
# - KNOX_CERT - (optional) the location of a public PEM-encoded certificate file for the gateway
# - KNOX_KEY - (optional) the location of a private PEM-encoded key file for the gateway
# - KEYSTORE_PASSWORD_FILE - (optional) the location of a file containing the passphrase to use for generated keystores, default randomly generated base 64 string
# - ALIAS_PASSPHRASE - (optional) Keystore signing password
# - CA_FILE - (optional) the location of a file containing the PEM-encoded CA bundle for Knox to use
# - KEYSTORE_DIR - (optional) a location for generated JKS files, default /home/knox/knox/data/security/keystores
# - LDAP_PASSWORD_FILE - (optional) the location of a file containing ldap bind password.
# - LDAP_BIND_PASSWORD - (optional) ldap bind password value (not file location).
# - CUSTOM_CERT - (optional) the location of a file containing the custom certs


set -e
set -o pipefail

## Helper function used to import certs into truststore
## Function takes cert file as argument
importMultipleCerts() {
  FILE=$1
  ALIAS_PASSPHRASE=$(/bin/cat "${KEYSTORE_PASSWORD_FILE}")
  # number of certs in the PEM file
  CERTS=$(/bin/grep 'END CERTIFICATE' "$FILE"| /usr/bin/wc -l)
  # For every cert in the PEM file, extract it and import into the JKS keystore
  # awk command:
  # step 1), if line is in the desired cert, print the line
  # step 2), increment counter when last line of cert is found
  for N in $(/usr/bin/seq 0 $(($CERTS - 1))); do
    ALIAS="${FILE%.*}-$N"
    /bin/cat "$FILE" |
      /usr/bin/awk "n==$N { print }; /END CERTIFICATE/ { n++ }" |
      /usr/bin/keytool -import \
              -trustcacerts \
              -alias "$ALIAS" \
              -keystore "${KEYSTORE_DIR}"/truststore.jks \
              -storepass "$ALIAS_PASSPHRASE" \
              -noprompt
  done
}

export GATEWAY_SERVER_RUN_IN_FOREGROUND=true


# Create Master secret
if [[ -n ${KNOX_MASTER_SECRET} ]]
then
  MASTER_SECRET=$(/bin/cat "${KNOX_MASTER_SECRET}" 2>/dev/null)
fi

if [[ -n ${MASTER_SECRET} ]]
then
  echo "Using provided knox master secret"
  /home/knox/knox/bin/knoxcli.sh create-master --master "${MASTER_SECRET}"
else
  /home/knox/knox/bin/knoxcli.sh create-master --master knox
fi

if [[ -n ${LDAP_PASSWORD_FILE} ]]
then
  LDAP_BIND_PASSWORD=$(/bin/cat "${LDAP_PASSWORD_FILE}" 2>/dev/null)
fi

if [[ -n ${LDAP_BIND_PASSWORD} ]]
then
  echo "Using provided LDAP bind password"
  /home/knox/knox/bin/knoxcli.sh create-alias ldap-bind-password --value "${LDAP_BIND_PASSWORD}"
fi

# If keystore dir is empty use default one
if [[ -z ${KEYSTORE_DIR} ]]
then
  KEYSTORE_DIR="/home/knox/knox/data/security/keystores"
fi

if [[ -n ${KEYSTORE_PASSWORD_FILE} ]] && [[ -f ${KEYSTORE_PASSWORD_FILE} ]]
then
  echo "Using provided keystore password file"
  ALIAS_PASSPHRASE=$(/bin/cat "${KEYSTORE_PASSWORD_FILE}" 2>/dev/null)
else
   # If keystore password is not provided use master secret as alias passphrase
   ALIAS_PASSPHRASE=${MASTER_SECRET}
fi

if [[ -n ${KNOX_CERT} ]] && [[ -f ${KNOX_CERT} ]]
then
  KNOX_CERT_EXIST="true"
fi

if [[ -n ${KNOX_KEY} ]] && [[ -f ${KNOX_KEY} ]]
then
  KNOX_KEY_EXIST="true"
fi

## This will be useful when we want to provide signing material.
## In order to use this you will need to also update gateway-site.xml properties such as (key:value):
## gateway.signing.key.alias=keystore
## gateway.signing.keystore.name=keystore.jks
## gateway.tls.key.alias=keystore
## gateway.tls.keystore.path=/home/knox/knox/data/security/keystores/keystore.jks

# Import Knox identity Key
if [[ -n ${ALIAS_PASSPHRASE} ]] && [[ -n ${KNOX_CERT_EXIST} ]] && [[ -n ${KNOX_KEY_EXIST} ]]
then
  echo "Using provided key to setup Knox keystore"
  # Create JCEKS aliases for Knox
  /home/knox/knox/bin/knoxcli.sh create-alias signing.keystore.password --value "${ALIAS_PASSPHRASE}"
  /home/knox/knox/bin/knoxcli.sh create-alias gateway-identity-keystore-password --value "${ALIAS_PASSPHRASE}"

  /usr/bin/openssl pkcs12 -export \
    -in "${KNOX_CERT}" \
    -inkey "${KNOX_KEY}" \
    -passout file:"${KEYSTORE_PASSWORD_FILE}" \
    -out /tmp/keystore.p12 \
    -name keystore

  # Create signing JKS
  /usr/bin/keytool -importkeystore \
    -destkeystore "${KEYSTORE_DIR}"/keystore.jks \
    -deststorepass "${ALIAS_PASSPHRASE}" \
    -srckeystore /tmp/keystore.p12 \
    -srcstoretype PKCS12 \
    -srcstorepass "${ALIAS_PASSPHRASE}" \
    -alias keystore \
    -noprompt

  /bin/rm -rf /tmp/keystore.p12
fi

# Create a truststore including CA certificate
if [[ -n $CA_FILE ]] && [[ -f ${CA_FILE} ]]
then
  echo "Creating truststore with provided CA certificate/s."
  importMultipleCerts "$CA_FILE"
fi

# Import custom certs one by one
if [[ -n $CUSTOM_CERT ]] && [[ -f ${CUSTOM_CERT} ]]
then
  echo "Importing Custom certs."
  importMultipleCerts "$CUSTOM_CERT"
fi

# Add Amazon Root CA 1
/usr/bin/keytool -importcert \
  -keystore ${KEYSTORE_DIR}/truststore.jks \
  -alias amazon-ca-1 \
  -file /home/knox/cacrts/AmazonRootCA1.cer \
  -storepass "${ALIAS_PASSPHRASE}" \
  -noprompt || true

# Add Amazon Root CA 2
/usr/bin/keytool -importcert \
  -keystore ${KEYSTORE_DIR}/truststore.jks \
  -alias amazon-ca-2 \
  -file /home/knox/cacrts/AmazonRootCA2.cer \
  -storepass "${ALIAS_PASSPHRASE}" \
  -noprompt || true

# Add Amazon Root CA 3
/usr/bin/keytool -importcert \
  -keystore ${KEYSTORE_DIR}/truststore.jks \
  -alias amazon-ca-3 \
  -file /home/knox/cacrts/AmazonRootCA3.cer \
  -storepass "${ALIAS_PASSPHRASE}" \
  -noprompt || true

# Add Amazon Root CA 4
/usr/bin/keytool -importcert \
  -keystore ${KEYSTORE_DIR}/truststore.jks \
  -alias amazon-ca-4 \
  -file /home/knox/cacrts/AmazonRootCA4.cer \
  -storepass "${ALIAS_PASSPHRASE}" \
  -noprompt || true

export KNOX_GATEWAY_DBG_OPTS="${KNOX_GATEWAY_DBG_OPTS}"

echo "Starting Knox gateway ..."
/home/knox/knox/bin/gateway.sh start
