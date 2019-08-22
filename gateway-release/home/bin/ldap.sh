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
export APP_LABEL=LDAP

# The app's name
APP_NAME=ldap

# start/stop script location
APP_BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# The app's JAR name
export APP_JAR="$APP_BIN_DIR/ldap.jar"

# Setup the common environment
. "$APP_BIN_DIR"/knox-env.sh

# Source common functions
. "${APP_BIN_DIR}"/knox-functions.sh

# The app's conf dir
DEFAULT_APP_CONF_DIR="$APP_HOME_DIR/conf"
APP_CONF_DIR=${KNOX_LDAP_CONF_DIR:-$DEFAULT_APP_CONF_DIR}

# The app's log dir
DEFAULT_APP_LOG_DIR="$APP_HOME_DIR/logs"
APP_LOG_DIR=${KNOX_LDAP_LOG_DIR:-$DEFAULT_APP_LOG_DIR}

# The app's logging options
export APP_LOG_OPTS="$KNOX_LDAP_LOG_OPTS"

# The app's memory options
export APP_MEM_OPTS="$KNOX_LDAP_MEM_OPTS"

# The app's debugging options
export APP_DBG_OPTS="$KNOX_LDAP_DBG_OPTS"

# The name of the PID file
DEFAULT_APP_PID_DIR="$APP_HOME_DIR/pids"
APP_PID_DIR=${KNOX_LDAP_PID_DIR:-$DEFAULT_APP_PID_DIR}
export APP_PID_FILE="$APP_PID_DIR/$APP_NAME.pid"

#Name of LOG/OUT/ERR file
APP_OUT_FILE="$APP_LOG_DIR/$APP_NAME.out"
APP_ERR_FILE="$APP_LOG_DIR/$APP_NAME.err"

DEFAULT_APP_RUNNING_IN_FOREGROUND="$LDAP_SERVER_RUN_IN_FOREGROUND"
export APP_RUNNING_IN_FOREGROUND=${KNOX_LDAP_RUNNING_IN_FOREGROUND:-$DEFAULT_APP_RUNNING_IN_FOREGROUND}

function main {
   checkJava

   case "$1" in
      start)  
         if [ "$2" = "--printEnv" ]; then
           printEnv
         fi
         createLogFiles
         appStart "$APP_CONF_DIR"
         ;;
      stop)   
         appStop
         ;;
      status) 
         appStatus
         ;;
      clean) 
         appClean
         ;;
      *)
         printHelp
         ;;
   esac
}

function createLogFiles {
   if [ ! -d "$APP_LOG_DIR" ]; then
      printf "Can't find log dir.\n"
      exit 1
   fi
   if [ ! -f "$APP_OUT_FILE" ]; then touch "$APP_OUT_FILE"; fi
   if [ ! -f "$APP_ERR_FILE" ]; then touch "$APP_ERR_FILE"; fi
}

function printHelp {
   printf "Usage: %s {start|stop|status|clean}\n" "$0"
   return 0
}

# Starting main
main "$@"
