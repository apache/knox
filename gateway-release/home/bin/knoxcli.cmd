@ECHO OFF
REM  Licensed to the Apache Software Foundation (ASF) under one or more
REM  contributor license agreements.  See the NOTICE file distributed with
REM  this work for additional information regarding copyright ownership.
REM  The ASF licenses this file to You under the Apache License, Version 2.0
REM  (the "License"); you may not use this file except in compliance with
REM  the License.  You may obtain a copy of the License at

REM      http://www.apache.org/licenses/LICENSE-2.0

REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

REM The app's label
SET APP_LABEL=KnoxCLI

REM The app's name
REM APP_NAME=knoxcli

REM Start/stop script location
SET APP_BIN_DIR=%~dp0

REM The app's jar name
SET APP_JAR=%APP_BIN_DIR%knoxcli.jar

REM The apps home dir
SET APP_HOME_DIR=%APP_BIN_DIR:~0,-5%

REM The apps home dir
SET APP_CONF_DIR=%APP_HOME_DIR%\conf

REM The app's log dir
SET APP_LOG_DIR=%APP_HOME_DIR%\logs

REM The app's logging options
SET APP_LOG_OPTS=

REM The app's memory options
SET APP_MEM_OPTS=

REM The app's debugging options
SET APP_DBG_OPTS=

REM  Name of LOG/OUT/ERR file
SET APP_OUT_FILE=%APP_LOG_DIR%\%APP_NAME%.out
SET APP_ERR_FILE=%APP_LOG_DIR%\%APP_NAME%.err


: main 
   ECHO "Starting %APP_LABEL% "
   
    java %APP_MEM_OPTS% %APP_DBG_OPT% %APP_LOG_OPTS% -jar "%APP_JAR%"  %*
	
	IF NOT %ERRORLEVEL% ==0 (
		Exit /B 1
		)

 Exit /B 0

:printHelp 
   java -jar "%APP_JAR%" -help
 Exit /B 0
