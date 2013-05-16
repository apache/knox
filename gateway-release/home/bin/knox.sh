#!/bin/sh

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

#Knox PID
PID=0

#start, stop, status, clean or setup
KNOX_LAUNCH_COMMAND=$1

#User Name for setup parameter
KNOX_LAUNCH_USER=$2

#start/stop script location
KNOX_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#App name
KNOX_NAME=knox

#The Knox's jar name
KNOX_JAR="$KNOX_SCRIPT_DIR/server.jar"

#Name of PID file
PID_DIR="/var/run/$KNOX_NAME"
PID_FILE="$PID_DIR/$KNOX_NAME.pid"

#Name of LOG/OUT/ERR file
LOG_DIR="/var/log/$KNOX_NAME"
OUT_FILE="$LOG_DIR/$KNOX_NAME.out"
ERR_FILE="$LOG_DIR/$KNOX_NAME.err"

#The max time to wait
MAX_WAIT_TIME=10

function main {
   case "$1" in
      start)  
         knoxStart
         ;;
      stop)   
         knoxStop
         ;;
      status) 
         knoxStatus
         ;;
      clean) 
         knoxClean
         ;;
      setup) 
         setupEnv $KNOX_LAUNCH_USER
         ;;
      *)
         printf "Usage: $0 {start|stop|status|clean|setup [USER_NAME]}\n"
         ;;
   esac
}

function knoxStart {
   createLogFiles

   getPID
   if [ $? -eq 0 ]; then
     printf "Knox is already running with PID=$PID.\n"
     return 0
   fi
  
   printf "Starting Knox "
   
   rm -f $PID_FILE

   nohup java -jar $KNOX_JAR >>$OUT_FILE 2>>$ERR_FILE & printf $!>$PID_FILE || return 1
   
   getPID
   knoxIsRunning $PID
   if [ $? -ne 1 ]; then
      printf "failed.\n"
      return 1
   fi

   printf "succeed with PID=$PID.\n"
   return 0
}

function knoxStop {
   getPID
   knoxIsRunning $PID
   if [ $? -eq 0 ]; then
     printf "Knox is not running.\n"
     return 0
   fi
  
   printf "Stopping Knox [$PID] "
   knoxKill $PID >>$OUT_FILE 2>>$ERR_FILE 

   if [ $? -ne 0 ]; then 
     printf "failed. \n"
     return 1
   else
     rm -f $PID_FILE
     printf "succeed.\n"
     return 0
   fi
}

function knoxStatus {
   printf "Knox "
   getPID
   if [ $? -eq 1 ]; then
     printf "is not running. No pid file found.\n"
     return 0
   fi

   knoxIsRunning $PID
   if [ $? -eq 1 ]; then
     printf "is running with PID=$PID.\n"
     return 1
   else
     printf "is not running.\n"
     return 0
   fi
}

# Removed the Knox PID file if Knox is not run
function knoxClean {
   getPID
   knoxIsRunning $PID
   if [ $? -eq 0 ]; then 
     deleteLogFiles
     return 0
   else
     printf "Can't clean files the Knox is run with PID=$PID.\n" 
     return 1    
   fi
}

# Returns 0 if the Knox is running and sets the $PID variable.
function getPID {
   if [ ! -f $PID_FILE ]; then
     PID=0
     return 1
   fi
   
   PID="$(<$PID_FILE)"
   return 0
}

function knoxIsRunning {
   if [ $1 -eq 0 ]; then return 0; fi

   ps -p $1 > /dev/null

   if [ $? -eq 1 ]; then
     return 0
   else
     return 1
   fi
}

function knoxKill {
   local localPID=$1
   kill $localPID || return 1
   for ((i=0; i<MAX_WAIT_TIME*10; i++)); do
      knoxIsRunning $localPID
      if [ $? -eq 0 ]; then return 0; fi
      sleep 0.1
   done   

   kill -s KILL $localPID || return 1
   for ((i=0; i<MAX_WAIT_TIME*10; i++)); do
      knoxIsRunning $localPID
      if [ $? -eq 0 ]; then return 0; fi
      sleep 0.1
   done

   return 1
}

function createLogFiles {
   if [ ! -f "$OUT_FILE" ]; then touch $OUT_FILE; fi
   if [ ! -f "$ERR_FILE" ]; then touch $ERR_FILE; fi   
}

function deleteLogFiles {
     rm -f $PID_FILE
     printf "Removed the Knox PID file: $PID_FILE.\n"
     
     rm -f $OUT_FILE
     printf "Removed the Knox OUT file: $OUT_FILE.\n"
     
     rm -f $ERR_FILE
     printf "Removed the Knox ERR file: $ERR_FILE.\n"  
}

function setDirPermission {
   local dirName=$1
   local userName=$2

   if [ ! -d "$dirName" ]; then mkdir -p $dirName; fi
   if [ $? -ne 0 ]; then
      printf "Can't access or create \"$dirName\" folder.\n"
      return 1
   fi

   chown -f $userName $dirName
   if [ $? -ne 0 ]; then
      printf "Can't change owner of \"$dirName\" folder to \"$userName\" user.\n" 
      return 1
   fi

   chmod o=rwx $dirName 
   if [ $? -ne 0 ]; then
      printf "Can't grant rwx permission to \"$userName\" user on \"$dirName\"\n" 
      return 1
   fi

   return 0
}

function setupEnv {
   local userName=$1
   
   if [ -z $userName ]; then 
      printf "Empty user name is not allowed. Parameters: setup [USER_NAME]\n"
      return 1
   fi

   id -u $1 >/dev/null 2>&1
   if [ $? -eq 1 ]; then
      printf "\"$userName\" is not valid user name. Parameters: setup [USER_NAME]\n"
      return 1
   fi

   setDirPermission $PID_DIR $userName
   setDirPermission $LOG_DIR $userName

   java -jar $KNOX_JAR -persist-master 
}

#Starting main
main $KNOX_LAUNCH_COMMAND
