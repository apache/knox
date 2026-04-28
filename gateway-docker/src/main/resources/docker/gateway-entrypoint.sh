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
# - DATABASE_CONNECTION_USER - (optional) gateway database user
# - DATABASE_CONNECTION_PASSWORD - (optional) gateway database password
# - DATABASE_CONNECTION_TRUSTSTORE_PASSWORD - (optional) gateway database ssl truststore password
# - CUSTOM_CERT - (optional) the location of a file containing the custom certs
# - TRUSTSTORE_IMPORTS - (optional) - a string containing  one or more of the following: {aliasIdForImport:PEMEncodedTrustCertificateFileLocation} separated by space(s).
#   Example:
#   TRUSTSTORE_IMPORTS="myRootCA:/mountedpath/enterprise_root_cert.pem myBizPartnerCA:/mountedpath/mybiz_partner_cert.pem"
#   Description:
#   This will import the specified certificates into the truststore JKS with the provided alias name(s) during the container startup process.


set -e
set -o pipefail

## Helper function used to import certs into truststore
## Function takes cert file as argument
## At this time ALIAS_PASSPHRASE is already initialized
importMultipleCerts() {
  FILE=$1
  local import_failed=0
  # number of certs in the PEM file
  CERTS=$(/bin/grep 'END CERTIFICATE' "$FILE"| /usr/bin/wc -l)
  # For every cert in the PEM file, extract it and import into the JKS keystore
  # awk command:
  # step 1), if line is in the desired cert, print the line
  # step 2), increment counter when last line of cert is found
  for N in $(/usr/bin/seq 0 $((CERTS - 1))); do
    ALIAS="${FILE%.*}-$N"
    # Make import idempotent across restarts when truststore is persisted.
    keytool -delete \
            -alias "$ALIAS" \
            -keystore "${KEYSTORE_DIR}"/truststore.jks \
            -storepass "$ALIAS_PASSPHRASE" \
            > /dev/null 2>&1 || true
    if ! /bin/cat "$FILE" |
      /usr/bin/awk "n==$N { print }; /END CERTIFICATE/ { n++ }" |
      keytool -import \
              -trustcacerts \
              -alias "$ALIAS" \
              -keystore "${KEYSTORE_DIR}"/truststore.jks \
              -storepass "$ALIAS_PASSPHRASE" \
              -noprompt; then
      echo "WARN: Unable to import certificate alias [${ALIAS}] from [${FILE}]. Continuing startup..."
      import_failed=1
    fi
  done

  return "$import_failed"
}

## Helper function to save an alias
## Function takes alias name, environment variable value, and optional default value
saveAlias() {
  local alias_name=$1
  local env_var_value=$2
  local default_value=$3

  if [[ -n ${env_var_value} ]]; then
    echo "Creating alias ${alias_name} using provided value..."
    /home/knox/knox/bin/knoxcli.sh create-alias "${alias_name}" --value "${env_var_value}"
  elif [[ -n ${default_value} ]]; then
    echo "Creating alias ${alias_name} using default value..."
    /home/knox/knox/bin/knoxcli.sh create-alias "${alias_name}" --value "${default_value}"
  fi
}

export GATEWAY_SERVER_RUN_IN_FOREGROUND=true


# Create Master secret
if [[ -n ${KNOX_MASTER_SECRET} ]]
then
  MASTER_SECRET=$(/bin/cat "${KNOX_MASTER_SECRET}" 2> /dev/null)
fi

if [[ -n ${MASTER_SECRET} ]]
then
  echo "Using provided knox master secret [env:MASTER_SECRET]"
else
  echo "Using default knox master secret"
  MASTER_SECRET="!apacheknox!"
fi
/home/knox/knox/bin/knoxcli.sh create-master --master "${MASTER_SECRET}"

if [[ -n ${LDAP_PASSWORD_FILE} ]]
then
  LDAP_BIND_PASSWORD=$(/bin/cat "${LDAP_PASSWORD_FILE}" 2> /dev/null)
fi

saveAlias ldap-bind-password "${LDAP_BIND_PASSWORD}"
saveAlias gateway_database_user "${DATABASE_CONNECTION_USER}"
saveAlias gateway_database_password "${DATABASE_CONNECTION_PASSWORD}"
saveAlias gateway_database_ssl_truststore_password "${DATABASE_CONNECTION_TRUSTSTORE_PASSWORD}"

# RemoteAuthProvider truststore password
saveAlias rap_truststore_password "${RAP_TRUSTSTORE_PASSWORD}"

if [[ -n ${KNOX_TOKEN_HASH_KEY} ]]
then
  saveAlias knox.token.hash.key "${KNOX_TOKEN_HASH_KEY}"
else
  echo "Generating knox.token.hash.key alias ..."
  /home/knox/knox/bin/knoxcli.sh generate-jwk --saveAlias knox.token.hash.key
fi

# If keystore dir is empty use default one
if [[ -z ${KEYSTORE_DIR} ]]
then
  KEYSTORE_DIR="/home/knox/knox/data/security/keystores"
fi

if [[ -n ${KEYSTORE_PASSWORD_FILE} ]] && [[ -f ${KEYSTORE_PASSWORD_FILE} ]]
then
  echo "Setting ALIAS_PASSPHRASE from provided keystore password file: ${KEYSTORE_PASSWORD_FILE}"
  ALIAS_PASSPHRASE=$(/bin/cat "${KEYSTORE_PASSWORD_FILE}" 2> /dev/null)
else
   # If keystore password is not provided use master secret as alias passphrase
   echo "Setting ALIAS_PASSPHRASE to MASTER_SECRET"
   ALIAS_PASSPHRASE="${MASTER_SECRET}"
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
  saveAlias signing.keystore.password "${ALIAS_PASSPHRASE}"
  saveAlias gateway-identity-keystore-password "${ALIAS_PASSPHRASE}"

  /usr/bin/openssl pkcs12 -export \
    -in "${KNOX_CERT}" \
    -inkey "${KNOX_KEY}" \
    -passout file:"${KEYSTORE_PASSWORD_FILE}" \
    -out /tmp/keystore.p12 \
    -name keystore

  # Create signing JKS
  keytool -importkeystore \
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
  if ! importMultipleCerts "$CA_FILE"; then
    echo "WARN: Unable to import CA certs from [${CA_FILE}]. Continuing startup..."
  fi
fi

# Import custom certs one by one
if [[ -n $CUSTOM_CERT ]] && [[ -f ${CUSTOM_CERT} ]]
then
  echo "Importing Custom certs."
  if ! importMultipleCerts "$CUSTOM_CERT"; then
      echo "WARN: Unable to import custom certs from [${CUSTOM_CERT}]. Continuing startup..."
    fi
fi

# This default was set to emulate the existing behaviour
# Customer should be able to override this by specifying this via Docker environment settings
if [[ -z ${TRUSTSTORE_IMPORTS} ]]
then
	TRUSTSTORE_IMPORTS="
	 amazon-ca-1:/home/knox/cacrts/AmazonRootCA1.cer
     amazon-ca-2:/home/knox/cacrts/AmazonRootCA2.cer
     amazon-ca-3:/home/knox/cacrts/AmazonRootCA3.cer
	   amazon-ca-4:/home/knox/cacrts/AmazonRootCA4.cer
     isrgrootx1:/home/knox/cacrts/isrgrootx1.pem
     isrgrootx2:/home/knox/cacrts/isrg-root-x2.pem"
fi

for certinfo in ${TRUSTSTORE_IMPORTS}
do
    aliasId=$(echo "${certinfo}" | awk -F: '{ print $1 }')
    certPath=$(echo "${certinfo}" | awk -F: '{ print $2 }')
    if [[ -n "${aliasId}" ]] && [[ -n "${certPath}" ]] && [[ -f "${certPath}" ]]
    then
        echo "INFO: Importing certificate [${certPath}] into truststore"
        # Replace existing alias to keep imports idempotent.
        keytool -delete \
            -keystore "${KEYSTORE_DIR}"/truststore.jks \
            -alias "${aliasId}" \
            -storepass "${ALIAS_PASSPHRASE}" \
            > /dev/null 2>&1 || true
        keytool -importcert \
            -keystore "${KEYSTORE_DIR}"/truststore.jks \
            -alias "${aliasId}" \
            -file "${certPath}" \
            -storepass "${ALIAS_PASSPHRASE}" \
            -noprompt || echo "WARN: Unable to import certificate [${certPath}] with alias [${aliasId}]. Continuing startup..."
    else
        echo "ERROR: certificate [${certinfo}] not found. Not importing into truststore"
    fi
done

export KNOX_GATEWAY_DBG_OPTS="${KNOX_GATEWAY_DBG_OPTS} -Djavax.net.ssl.trustStore=${KEYSTORE_DIR}/truststore.jks -Djavax.net.ssl.trustStorePassword=${ALIAS_PASSPHRASE}"

echo "Starting Knox gateway ..."
/home/knox/knox/bin/gateway.sh start
