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

############################
##### common variables #####
############################

# The app's home dir
# shellcheck disable=SC2153
APP_HOME_DIR=$(dirname "$APP_BIN_DIR")
export APP_HOME_DIR

# The app's PID
APP_PID=0

# The start wait time
APP_START_WAIT_TIME=2

# The kill wait time limit
APP_KILL_WAIT_TIME=10

#dynamic library path
DEFAULT_JAVA_LIB_PATH="-Djava.library.path=$APP_HOME_DIR/ext/native"
APP_JAVA_LIB_PATH=${KNOX_GATEWAY_JAVA_LIB_PATH:-$DEFAULT_JAVA_LIB_PATH}

# JAVA options used by the JVM
declare -a APP_JAVA_OPTS

#status-based test related variables
DEFAULT_APP_STATUS_TEST_RETRY_ATTEMPTS=5
DEFAULT_APP_STATUS_TEST_RETRY_SLEEP=2s

############################
##### common functions #####
############################

JAVA_VERSION_PATTERNS=( "1.6.0_31/bin/java$" "1.6.0_.*/bin/java$" "1.6.0.*/bin/java$" "1.6\..*/bin/java$" "/bin/java$" )

function findJava() {
  # Check to make sure any existing JAVA var is valid.
  if [ "$JAVA" != "" ]; then
    if [ ! -x "$JAVA" ]; then
      JAVA=""
    fi
  fi
  
  # Try to use JAVA_HOME to find java.
  if [ "$JAVA" == "" ]; then
    if [ "$JAVA_HOME" != "" ]; then
      JAVA=$JAVA_HOME/bin/java
      if [ ! -x "$JAVA" ]; then
        JAVA=""
      fi
    fi
  fi

  # Try to find java on PATH.
  if [ "$JAVA" == "" ]; then
    JAVA=$(command -v java 2>/dev/null)
    if [ ! -x "$JAVA" ]; then
      JAVA=""
    fi
  fi

  # Use the search patterns to find java.
  if [ "$JAVA" == "" ]; then
    for pattern in "${JAVA_VERSION_PATTERNS[@]}"; do
      # shellcheck disable=SC2207
      JAVAS=( $(find /usr -executable -name java -print 2> /dev/null | grep "$pattern" | head -n 1 ) )
      if [ -x "$JAVA" ]; then
        JAVA=${JAVAS[1]}
        break
      else
        JAVA=""
      fi
    done
  fi
}

function checkJava() {
  findJava

  if [[ -z $JAVA ]]; then
    echo "Warning: JAVA is not set and could not be found." 1>&2
  fi
}

function printEnv() {
    if [ -n "$APP_CONF_DIR" ]; then
      echo "APP_CONF_DIR = $APP_CONF_DIR"
    fi
    
    if [ -n "$APP_LOG_DIR" ]; then
      echo "APP_LOG_DIR = $APP_LOG_DIR"
    fi

    if [ -n "$APP_DATA_DIR" ]; then
      echo "APP_DATA_DIR = $APP_DATA_DIR"
    fi

    if [ -n "$APP_MEM_OPTS" ]; then
      echo "APP_MEM_OPTS = $APP_MEM_OPTS"
    fi

    if [ -n "$APP_LOG_OPTS" ]; then
      echo "APP_LOG_OPTS = $APP_LOG_OPTS"
    fi

    if [ -n "$APP_DBG_OPTS" ]; then
      echo "APP_DBG_OPTS = $APP_DBG_OPTS"
    fi

    if [ -n "$APP_PID_DIR" ]; then
      echo "APP_PID_DIR = $APP_PID_DIR"
    fi

    if [ -n "$APP_JAVA_LIB_PATH" ]; then
      echo "APP_JAVA_LIB_PATH = $APP_JAVA_LIB_PATH"
    fi

    if [ -n "$APP_JAR" ]; then
      echo "APP_JAR = $APP_JAR"
    fi
}

function addAppJavaOpts {
    options_array=$(echo "${1}" | tr " " "\n")
    for option in ${options_array}
    do
       APP_JAVA_OPTS+=("$option")
    done
}

function buildAppJavaOpts {
    if [ -n "$APP_MEM_OPTS" ]; then
      addAppJavaOpts "${APP_MEM_OPTS}"
    fi

    if [ -n "$APP_LOG_OPTS" ]; then
      addAppJavaOpts "${APP_LOG_OPTS}"
    fi

    if [ -n "$APP_DBG_OPTS" ]; then
      addAppJavaOpts "${APP_DBG_OPTS}"
    fi

    if [ -n "$APP_JAVA_LIB_PATH" ]; then
      addAppJavaOpts "${APP_JAVA_LIB_PATH}"
    fi

    # echo "APP_JAVA_OPTS =" "${APP_JAVA_OPTS[@]}"
}

function appIsRunningByPID {
   if [ "$1" -eq 0 ]; then return 0; fi

   ps -p "$1" > /dev/null

   if [ $? -eq 1 ]; then
     return 0
   else
     return 1
   fi
}

function appIsRunningByStatus {
   retryAttempts=${APP_STATUS_TEST_RETRY_ATTEMPTS:-$DEFAULT_APP_STATUS_TEST_RETRY_ATTEMPTS}
   retrySleep=${APP_STATUS_TEST_RETRY_SLEEP:-$DEFAULT_APP_STATUS_TEST_RETRY_SLEEP}

   #echo "Retry attempts = $retryAttempts"
   #echo "Retry sleep = $retrySleep"

   statusCheck=0
   for ((i=1; i<=retryAttempts; i++))
   do
     #echo "$i. try"

     if grep -Fxqs "STARTED" "$APP_DATA_DIR"/gatewayServer.status; then
       statusCheck=1
       break
     fi

     sleep "$retrySleep"
   done

   return $statusCheck
}

#returns 0 if not running and 1 if running
function appIsRunning {
   appIsRunningByPID "$1"
   if [ $? -eq 1 ]; then
     #echo "PID check succeeded"
     if [[ "$TEST_APP_STATUS" = "true" ]]; then
       #echo "Checking status..."
       appIsRunningByStatus
       if [ $? -eq 1 ]; then
         #echo "Status check passed"
         return 1;
       else
         #echo "Status check NOT passsed"
         return 0;
       fi
     else
       return 1;
     fi
   fi;

   return 0
}

# Returns 0 if the app is running and sets the $PID variable
# TODO: this may be a false indication: it may happen the process started but it'll return with a <>0 exit code due to validation errors; this should be fixed ASAP
function getPID {
   if [ ! -d "$APP_PID_DIR" ]; then
      printf "Can't find PID dir.\n"
      exit 1
   fi
   if [ ! -f "$APP_PID_FILE" ]; then
     APP_PID=0
     return 1
   fi

   APP_PID="$(<"$APP_PID_FILE")"

   ps -p "$APP_PID" > /dev/null
   # if the exit code was 1 then it isn't running
   if [ "$?" -eq "1" ];
   then
     return 1
   fi

   return 0
}

function appStart {
   buildAppJavaOpts

   if [ "$APP_RUNNING_IN_FOREGROUND" == true ]; then
      exec "$JAVA" "${APP_JAVA_OPTS[@]}" -jar "$APP_JAR" "$@"
   else
      if getPID; then
         printf "%s is already running with PID %d.\n" "$APP_LABEL" "$APP_PID"
         exit 0
      fi

      printf "Starting %s " "$APP_LABEL"

      rm -f "$APP_PID_FILE"

      nohup "$JAVA" "${APP_JAVA_OPTS[@]}" -jar "$APP_JAR" "$@" >>"$APP_OUT_FILE" 2>>"$APP_ERR_FILE" & printf %s $!>"$APP_PID_FILE" || exit 1

      ##give a second to the JVM to start and run validation
      sleep 1

      getPID
      for ((i=0; i<APP_START_WAIT_TIME*10; i++)); do
         appIsRunning "$APP_PID"
         if [ $? -eq 1 ]; then
            break
         fi
         sleep 0.1
      done
      appIsRunning "$APP_PID"
      if [ $? -ne 1 ]; then
         printf "failed.\n"
         rm -f "$APP_PID_FILE"
         exit 1
      fi
      printf "succeeded with PID %s.\n" "$APP_PID"
      return 0
   fi
}

function appStop {
   getPID
   if appIsRunning "$APP_PID"; then
     printf "%s is not running.\n" "$APP_LABEL"
     rm -f "$APP_PID_FILE"
     return 0
   fi

   printf "Stopping %s with PID %d " "$APP_LABEL" "$APP_PID"

   if ! appKill "$APP_PID" >>"$APP_OUT_FILE" 2>>"$APP_ERR_FILE"; then
     printf "failed. \n"
     exit 1
   else
     rm -f "$APP_PID_FILE"
     printf "succeeded.\n"
     return 0
   fi
}

function appStatus {
   printf "%s " "$APP_LABEL"
   getPID
   if [ "$?" -eq "1" ]; then
     printf "is not running. No PID file found.\n"
     return 0
   fi

   appIsRunning "$APP_PID"
   if [ "$?" -eq "1" ]; then
     printf "is running with PID %d.\n" "$APP_PID"
     exit 1
   else
     printf "is not running.\n"
     return 0
   fi
}

# Removing the app's PID/ERR/OUT files if app is not running
function appClean {
   getPID
   if appIsRunning "$APP_PID"; then
     deleteLogFiles
     return 0
   else
     printf "Can't clean files.  %s is running with PID %d.\n" "$APP_LABEL" "$APP_PID"
     exit 1
   fi
}

function appKill {
   local localPID=$1
   kill "$localPID" || return 1

   for ((i=0; i<APP_KILL_WAIT_TIME*10; i++)); do
      if appIsRunning "$localPID"; then return 0; fi
      sleep 0.1
   done

   kill -s KILL "$localPID" || return 1
   for ((i=0; i<APP_KILL_WAIT_TIME*10; i++)); do
      if appIsRunning "$localPID"; then return 0; fi
      sleep 0.1
   done

   return 1
}

function deleteLogFiles {
     rm -f "$APP_PID_FILE"
     printf "Removed the %s PID file.\n" "$APP_LABEL"

     rm -f "$APP_OUT_FILE"
     printf "Removed the %s OUT file.\n" "$APP_LABEL"

     rm -f "$APP_ERR_FILE"
     printf "Removed the %s ERR file.\n" "$APP_LABEL"
}
