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
APP_LABEL=KnoxShell

# The app's name
APP_NAME=knoxshell

# Start/stop script location
APP_BIN_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# The app's jar name
APP_JAR="$APP_BIN_DIR/knoxshell.jar"

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
   case "$1" in
      init)
        if [ "$#" -ne 2 ]; then
            echo "Illegal number of parameters."
            printHelp
        else
          $JAVA -cp $APP_JAR org.apache.knox.gateway.shell.KnoxSh init --gateway $2 || exit 1
        fi
         ;;
      list)
        $JAVA -cp $APP_JAR org.apache.knox.gateway.shell.KnoxSh list || exit 1
         ;;
      destroy)
        $JAVA -cp $APP_JAR org.apache.knox.gateway.shell.KnoxSh destroy || exit 1
         ;;
      help)
         printHelp
         ;;
      *)
          $JAVA $APP_MEM_OPTS $APP_DBG_OPTS $APP_LOG_OPTS -jar $APP_JAR $@ || exit 1
         ;;
   esac
   
   return 0
}

function printHelp {
   echo ""
   echo "Apache Knox Client Shell"
   echo "The client shell facility provide a CLI for establishing and managing Apache Knox Sessions"
   echo "and executing the Apache Knox groovy-based DSL scripts. It may also be used to enter an"
   echo "interactive shell where groovy-based DSL and groovy code may be entered and executed in realtime."
   echo ""
   echo "knoxshell usage: "
   echo "   knoxshell.sh [[init <topology-url>|list|destroy|help] | [<script-file-name>]]"
   echo "   ----------------------------------------------------------"
   echo "   init <knox-gateway-url> - requests a session from the knox token service at the url"
   echo "        example: knoxshell.sh init https://localhost:8443/gateway/sandbox"
   echo "   list - lists the details of the cached knox session token"
   echo "        example: knoxshell.sh list"
   echo "   destroy - removes the cached knox session token"
   echo "        example: knoxshell.sh destroy"
   echo "   <script-file-name> - executes the groovy script file"
   echo "        example: knoxshell.sh ~/bin/ls.groovy"
   echo ""
   return 0
}

#Starting main
main $@
