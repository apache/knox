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
APP_HOME_DIR=`dirname $APP_BIN_DIR`

# The app's PID
APP_PID=0

# The start wait time
APP_START_WAIT_TIME=2

# The kill wait time limit
APP_KILL_WAIT_TIME=10


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
    JAVA=`which java 2>/dev/null`
    if [ ! -x "$JAVA" ]; then
      JAVA=""
    fi
  fi

  # Use the search patterns to find java.
  if [ "$JAVA" == "" ]; then
    for pattern in "${JAVA_VERSION_PATTERNS[@]}"; do
      JAVA=( $(find /usr -executable -name java -print 2> /dev/null | grep "$pattern" | head -n 1 ) )
      if [ -x "$JAVA" ]; then
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
    if [ ! -z "$APP_CONF_DIR" ]; then
      echo "APP_CONF_DIR = $APP_CONF_DIR"
    fi
    
    if [ ! -z "$APP_LOG_DIR" ]; then
      echo "APP_LOG_DIR = $APP_LOG_DIR"
    fi

    if [ ! -z "$APP_DATA_DIR" ]; then
      echo "APP_DATA_DIR = $APP_DATA_DIR"
    fi

    if [ ! -z "$APP_MEM_OPTS" ]; then
      echo "APP_MEM_OPTS = $APP_MEM_OPTS"
    fi

    if [ ! -z "$APP_LOG_OPTS" ]; then
      echo "APP_LOG_OPTS = $APP_LOG_OPTS"
    fi

    if [ ! -z "$APP_DBG_OPTS" ]; then
      echo "APP_DBG_OPTS = $APP_DBG_OPTS"
    fi

    if [ ! -z "$APP_PID_DIR" ]; then
      echo "APP_PID_DIR = $APP_PID_DIR"
    fi

    if [ ! -z "$APP_JAVA_LIB_PATH" ]; then
      echo "APP_JAVA_LIB_PATH = $APP_JAVA_LIB_PATH"
    fi

    if [ ! -z "$APP_JAR" ]; then
      echo "APP_JAR = $APP_JAR"
    fi
}

function appIsRunning {
   if [ $1 -eq 0 ]; then return 0; fi

   ps -p $1 > /dev/null

   if [ $? -eq 1 ]; then
     return 0
   else
     return 1
   fi
}

# Returns 0 if the app is running and sets the $PID variable
# TODO: this may be a false indication: it may happen the process started but it'll return with a <>0 exit code due to validation errors; this should be fixed ASAP
function getPID {
   if [ ! -d $APP_PID_DIR ]; then
      printf "Can't find PID dir.\n"
      exit 1
   fi
   if [ ! -f $APP_PID_FILE ]; then
     APP_PID=0
     return 1
   fi

   APP_PID="$(<$APP_PID_FILE)"

   ps -p $APP_PID > /dev/null
   # if the exit code was 1 then it isn't running
   if [ "$?" -eq "1" ];
   then
     return 1
   fi

   return 0
}

function appStart {
   if [ "$APP_RUNNING_IN_FOREGROUND" == true ]; then
      $JAVA $APP_JAVA_OPTS -jar $APP_JAR $@
   else
      getPID
      if [ $? -eq 0 ]; then
         printf "$APP_LABEL is already running with PID $APP_PID.\n"
         exit 0
      fi

      printf "Starting $APP_LABEL "

      rm -f $APP_PID_FILE

      nohup $JAVA $APP_JAVA_OPTS -jar $APP_JAR $@ >>$APP_OUT_FILE 2>>$APP_ERR_FILE & printf $!>$APP_PID_FILE || exit 1

      ##give a second to the JVM to start and run validation
      sleep 1

      getPID
      for ((i=0; i<APP_START_WAIT_TIME*10; i++)); do
         appIsRunning $APP_PID
         if [ $? -eq 0 ]; then break; fi
         sleep 0.1
      done
      appIsRunning $APP_PID
      if [ $? -ne 1 ]; then
         printf "failed.\n"
         rm -f $APP_PID_FILE
         exit 1
      fi
      printf "succeeded with PID $APP_PID.\n"
      return 0
   fi
}

function appStop {
   getPID
   appIsRunning $APP_PID
   if [ "$?" -eq "0" ]; then
     printf "$APP_LABEL is not running.\n"
     rm -f $APP_PID_FILE
     return 0
   fi

   printf "Stopping $APP_LABEL with PID $APP_PID "
   appKill $APP_PID >>$APP_OUT_FILE 2>>$APP_ERR_FILE

   if [ "$?" -ne "0" ]; then
     printf "failed. \n"
     exit 1
   else
     rm -f $APP_PID_FILE
     printf "succeeded.\n"
     return 0
   fi
}

function appStatus {
   printf "$APP_LABEL "
   getPID
   if [ "$?" -eq "1" ]; then
     printf "is not running. No PID file found.\n"
     return 0
   fi

   appIsRunning $APP_PID
   if [ "$?" -eq "1" ]; then
     printf "is running with PID $APP_PID.\n"
     exit 1
   else
     printf "is not running.\n"
     return 0
   fi
}

# Removing the app's PID/ERR/OUT files if app is not running
function appClean {
   getPID
   appIsRunning $APP_PID
   if [ "$?" -eq "0" ]; then
     deleteLogFiles
     return 0
   else
     printf "Can't clean files.  $APP_LABEL is running with PID $APP_PID.\n"
     exit 1
   fi
}

function appKill {
   local localPID=$1
   kill $localPID || return 1
   for ((i=0; i<APP_KILL_WAIT_TIME*10; i++)); do
      appIsRunning $localPID
      if [ "$?" -eq "0" ]; then return 0; fi
      sleep 0.1
   done

   kill -s KILL $localPID || return 1
   for ((i=0; i<APP_KILL_WAIT_TIME*10; i++)); do
      appIsRunning $localPID
      if [ "$?" -eq "0" ]; then return 0; fi
      sleep 0.1
   done

   return 1
}

function deleteLogFiles {
     rm -f $APP_PID_FILE
     printf "Removed the $APP_LABEL PID file: $APP_PID_FILE.\n"

     rm -f $APP_OUT_FILE
     printf "Removed the $APP_LABEL OUT file: $APP_OUT_FILE.\n"

     rm -f $APP_ERR_FILE
     printf "Removed the $APP_LABEL ERR file: $APP_ERR_FILE.\n"
}
