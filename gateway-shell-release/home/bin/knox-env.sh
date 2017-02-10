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

ENV_PID_DIR=""

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

findJava

  if [[ -z $JAVA ]]; then
    echo "Warning: JAVA is not set and could not be found." 1>&2
  fi

