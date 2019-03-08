#!/usr/bin/env bash

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
APP_LABEL=KnoxCLI

# The app's name
APP_NAME=knoxcli

# Start/stop script location
APP_BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# The app's jar name
APP_JAR="$APP_BIN_DIR/knoxcli.jar"

# Setup the common environment
. $APP_BIN_DIR/knox-env.sh

# Source common functions
. $APP_BIN_DIR/knox-functions.sh

function main {
   checkJava

   #printf "Starting $APP_LABEL \n"
   #printf "$@"

   $JAVA $KNOX_CLI_MEM_OPTS $KNOX_CLI_DBG_OPTS $KNOX_CLI_LOG_OPTS -jar $APP_JAR $@ || exit 1

   return 0
}

#Starting main
main $@
