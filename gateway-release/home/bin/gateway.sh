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
APP_LABEL=Gateway

# The app's name
APP_NAME=gateway

# Start/stop script location
APP_BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Setup the common environment
. $APP_BIN_DIR/knox-env.sh

# Source common functions
. $APP_BIN_DIR/knox-functions.sh

# The app's jar name
APP_JAR="$APP_BIN_DIR/gateway.jar"

# The app's conf dir
DEFAULT_APP_CONF_DIR="$APP_HOME_DIR/conf"
APP_CONF_DIR=${KNOX_GATEWAY_CONF_DIR:-$DEFAULT_APP_CONF_DIR}

# The app's data dir
DEFAULT_APP_DATA_DIR="$APP_HOME_DIR/data"
APP_DATA_DIR=${KNOX_GATEWAY_DATA_DIR:-$DEFAULT_APP_DATA_DIR}

# The app's log dir
DEFAULT_APP_LOG_DIR="$APP_HOME_DIR/logs"
APP_LOG_DIR=${KNOX_GATEWAY_LOG_DIR:-$DEFAULT_APP_LOG_DIR}

# The app's logging options
APP_LOG_OPTS="$KNOX_GATEWAY_LOG_OPTS"

# The app's memory options
APP_MEM_OPTS="$KNOX_GATEWAY_MEM_OPTS"

# The app's debugging options
APP_DBG_OPTS="$KNOX_GATEWAY_DBG_OPTS"

#dynamic library path
DEFAULT_JAVA_LIB_PATH="-Djava.library.path=$APP_HOME_DIR/ext/native"
APP_JAVA_LIB_PATH=${KNOX_GATEWAY_JAVA_LIB_PATH:-$DEFAULT_JAVA_LIB_PATH}

# Name of PID file
DEFAULT_APP_PID_DIR="$APP_HOME_DIR/pids"
APP_PID_DIR=${KNOX_GATEWAY_PID_DIR:-$DEFAULT_APP_PID_DIR}
APP_PID_FILE="$APP_PID_DIR/$APP_NAME.pid"

# Name of LOG/OUT/ERR file
APP_OUT_FILE="$APP_LOG_DIR/$APP_NAME.out"
APP_ERR_FILE="$APP_LOG_DIR/$APP_NAME.err"

DEFAULT_APP_RUNNING_IN_FOREGROUND="$GATEWAY_SERVER_RUN_IN_FOREGROUND"
APP_RUNNING_IN_FOREGROUND=${KNOX_GATEWAY_RUNNING_IN_FOREGROUND:-$DEFAULT_APP_RUNNING_IN_FOREGROUND}

APP_JAVA_OPTS="$APP_JAVA_LIB_PATH $APP_MEM_OPTS $APP_DBG_OPTS $APP_LOG_OPTS"

function main {
   checkJava

   case "$1" in
      setup)
         setupEnv
         ;;
      start)
         if [ "$2" = "--printEnv" ]; then
           printEnv
         fi
         checkEnv
         appStart
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
      help)
         printHelp
         ;;
      *)
         printf "Usage: $0 {start|stop|status|clean}\n"
         ;;
   esac
}

function setupEnv {
   checkEnv
   $JAVA -jar $APP_JAR -persist-master -nostart
   return 0
}

function checkReadDir {
    if [ ! -e "$1" ]; then
        printf "Directory $1 does not exist.\n"
        exit 1
    fi
    if [ ! -d "$1" ]; then
        printf "File $1 is not a directory.\n"
        exit 1
    fi
    if [ ! -r "$1" ]; then
        printf "Directory $1 is not readable by current user $USER.\n"
        exit 1
    fi
    if [ ! -x "$1" ]; then
        printf "Directory $1 is not executable by current user $USER.\n"
        exit 1
    fi
}

function checkWriteDir {
    checkReadDir $1
    if [ ! -w "$1" ]; then
        printf "Directory $1 is not writable by current user $USER.\n"
        exit 1
    fi
}

function checkEnv {
    # Make sure not running as root
    if [ "`id -u`" -eq "0" ]; then
        echo "This command $0 must not be run as root."
        exit 1
    fi

    checkWriteDir $APP_LOG_DIR
    checkWriteDir $APP_PID_DIR
}

function printHelp {
   $JAVA -jar $APP_JAR -help
   return 0
}

#Starting main
main $@
