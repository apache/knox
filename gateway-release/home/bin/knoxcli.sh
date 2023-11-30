#!/usr/bin/env bash
# shellcheck disable=SC1090
#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# The app's label
export APP_LABEL=KnoxCLI

# Start/stop script location
APP_BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# The app's jar name
APP_JAR="$APP_BIN_DIR/knoxcli.jar"

# Setup the common environment
. "$APP_BIN_DIR"/knox-env.sh

# Source common functions
. "$APP_BIN_DIR"/knox-functions.sh

# JAVA options used by the JVM
declare -a APP_JAVA_OPTS

function addAppJavaOpts {
    options_array=$(echo "${1}" | tr " " "\n")
    for option in ${options_array}
    do
       APP_JAVA_OPTS+=("$option")
    done
}

function buildAppJavaOpts {
    if [ -n "$KNOX_CLI_MEM_OPTS" ]; then
      addAppJavaOpts "${KNOX_CLI_MEM_OPTS}"
    fi

    if [ -n "$KNOX_CLI_LOG_OPTS" ]; then
      addAppJavaOpts "${KNOX_CLI_LOG_OPTS}"
    fi

    if [ -n "$KNOX_CLI_DBG_OPTS" ]; then
      addAppJavaOpts "${KNOX_CLI_DBG_OPTS}"
    fi

    if [ -n "$APP_JAVA_LIB_PATH" ]; then
      addAppJavaOpts "${APP_JAVA_LIB_PATH}"
    fi

    # Add properties to enable Knox to run on JDK 17
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    CHECK_VERSION_17="17"
    if [[ "$JAVA_VERSION" == *"$CHECK_VERSION_17"* ]]; then
        echo "Java version is $CHECK_VERSION_17. Adding properties to enable Knox to run on JDK 17"
        addAppJavaOpts " --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.naming/com.sun.jndi.ldap=ALL-UNNAMED --add-opens java.base/sun.security.util=ALL-UNNAMED"
    fi

    # echo "APP_JAVA_OPTS =" "${APP_JAVA_OPTS[@]}"
}

function main {
   checkJava
   buildAppJavaOpts
   $JAVA "${APP_JAVA_OPTS[@]}" -jar "$APP_JAR" "$@" || exit 1
   return 0
}

#Starting main
main "$@"
