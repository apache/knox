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

# The apps home dir
APP_HOME_DIR=`dirname $APP_BIN_DIR`

# The apps home dir
APP_CONF_DIR="$APP_HOME_DIR/conf"

# The app's log dir
APP_LOG_DIR="$APP_HOME_DIR/logs"

# The app's logging options
APP_LOG_OPTS=""

# The app's memory options
APP_MEM_OPTS=""

# The app's debugging options
APP_DBG_OPTS=""

# Name of LOG/OUT/ERR file
APP_OUT_FILE="$APP_LOG_DIR/$APP_NAME.out"
APP_ERR_FILE="$APP_LOG_DIR/$APP_NAME.err"

# Setup the common environment
. $APP_BIN_DIR/knox-env.sh

function main {
   #printf "Starting $APP_LABEL \n"
   #printf "$@"
   
   $JAVA $APP_MEM_OPTS $APP_DBG_OPTS $APP_LOG_OPTS -jar $APP_JAR $@ || exit 1

   return 0
}

function printHelp {
   $JAVA -jar $APP_JAR -help
   return 0
}

#Starting main
main $@
