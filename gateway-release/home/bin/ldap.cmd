@ECHO OFF
REM  Licensed to the Apache Software Foundation (ASF) under one or more
REM  contributor license agreements.  See the NOTICE file distributed with
REM  this work for additional information regarding copyright ownership.
REM  The ASF licenses this file to You under the Apache License, Version 2.0
REM  (the "License"); you may not use this file except in compliance with
REM  the License.  You may obtain a copy of the License at
REM
REM     http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

REM App name
SET APP_LABEL=LDAP

REM App name
SET APP_NAME=ldap

REM App name
SET APP_JAR_NAME=ldap.jar

REM start/stop script location
SET APP_BIN_DIR=%~dp0

REM The app's jar name
SET APP_JAR=%APP_BIN_DIR%%APP_JAR_NAME%

REM  The app's home dir
SET APP_HOME_DIR=%APP_BIN_DIR:~0,-5%

REM The apps home dir
SET APP_CONF_DIR=%APP_HOME_DIR%\conf

REM The app's log dir
SET APP_LOG_DIR=%APP_HOME_DIR%\logs

REM The app's Log4j options
SET APP_LOG_OPTS=

REM The app's memory options
SET APP_MEM_OPTS=

REM The app's debugging options
SET APP_DBG_OPTS==

REM Start, stop, status, clean
SET APP_LAUNCH_COMMAND=%1

REM The app's PID
SET APP_PID=0


REM The name of the PID file
SET APP_PID_DIR=%APP_HOME_DIR%\pids
SET APP_PID_FILE=%APP_PID_DIR%\%APP_NAME%.pid

REM Name of LOG/OUT/ERR file
SET APP_OUT_FILE=%APP_LOG_DIR%\%APP_NAME%.out
SET APP_ERR_FILE=%APP_LOG_DIR%\%APP_NAME%.err

REM The start wait time
SET APP_START_WAIT_TIME=2

REM  The kill wait time limit
SET APP_KILL_WAIT_TIME=10

REM Setup the common environment  
REM . $APP_BIN_DIR/knox-env.sh

:main 
	IF "%1" =="start" (
		CALL :appStart
		GOTO :EOF
	)

	IF "%1"=="stop" (
		CALL :appStop
		GOTO :EOF
	)	

	IF "%1"=="status" (
		CALL :appStatus
	GOTO :EOF
	)	

	IF "%1"=="clean" (
		CALL :appClean
		GOTO :EOF
	)
	   
	IF "%1" =="help" (
		CALL :printHelp
		GOTO :EOF
	)
	ECHO "Usage: %1 {start|stop|status|clean|setup [USER_NAME]}"
	goto :EOF

:appStart 
   call :createLogFiles
   call :getPID 
   	IF %ERRORLEVEL%==0  (
		ECHO "%APP_LABEL% is already running with PID=%APP_PID%.\n"
		GOTO :EOF
	) 
   
   ECHO "Starting %APP_LABEL% "
   
   DEL "%APP_PID_FILE%"
   
   start   javaw  %APP_MEM_OPTS% %APP_DBG_OPT% %APP_LOG_OPTS%  -jar  "%APP_JAR%"  "%APP_CONF_DIR%">> "%APP_OUT_FILE%" 2>>"%APP_ERR_FILE%"
   for /f "tokens=2 delims=," %%A in ('tasklist -v /fo csv /nh /fi "Imagename  eq javaw.exe"') do (
	echo %%A>"%APP_PID_FILE%"
	)
	CALL :getPID
	echo "pid=%APP_PID%
	Set i=0
	: start
	CALL :appIsRunning %APP_PID%
	IF  %ERRORLEVEL%==0 (
		 GOTO :break      
	)
	IF %i% leq %APP_START_WAIT_TIME% (
    ping 224.244.244.244 -n 1 -w 1 > nul 
    set /a i=%i%+1
    GOTO :start	
	 )
	:break
	CALL :appIsRunning %APP_PID%
	IF  %ERRORLEVEL%==0 (
		ECHO "failed"
		DEL /F /Q "%APP_PID_FILE%" 
		Exit /B 1
	)
	ECHO "succeeded with PID %APP_PID%
	Exit /B 0

: appStop 
	echo pid %APP_PID%
	call   :getPID
	call :appIsRunning %APP_PID%
	IF  %ERRORLEVEL% ==0 (
		echo "%APP_NAME% is not running."
		GOTO :EOF
	)
	ECHO "Stopping %APP_NAME% %APP_PID% "
	taskkill /F /PID  %APP_PID%
	IF NOT %ERRORLEVEL% ==0 (
		ECHO  "failed. \n"
		Exit /B 1
	)
	DEL /F "%APP_PID_FILE%"
	echo "succeeded.\n"
	GOTO :EOF

:appStatus 
   
  CALL :getPID
	IF not %ERRORLEVEL% ==0 (
		echo " %APP_LABEL% is not running. No pid file found.\n"
		GOTO :EOF
	) 
	CALL :appIsRunning %APP_PID%
	IF %ERRORLEVEL% ==1 (
		echo " %APP_LABEL% is running with PID=%APP_PID%.\n"
		Exit /B 1
	)
	ECHO "%APP_LABEL% is not running.\n"
	GOTO :EOF
	
:appClean 

	CALL :getPID
	CALL :appIsRunning %APP_PID%
	IF  %ERRORLEVEL%==0 (
		CALL :deleteLogFiles
		GOTO :EOF
	) 
	ECHO "Can't clean files. %APP_LABEL% is running with PID=%APP_PID%"
	Exit /B 1

:getPID 
	dir "%APP_PID_DIR%" >NUL 2>NUL
	IF NOT %ERRORLEVEL% ==0 (
		ECHO "Can't find pid dir. "
		Exit /B 1
	)
	dir  "%APP_PID_FILE%" >NUL 2>NUL
	IF NOT %ERRORLEVEL% ==0 (
		SET APP_PID=0
		Exit /B 1
	)	
	SET /p APP_PID=<"%APP_PID_FILE%"
	Exit /B 0
	
:appIsRunning 
	IF %1==0 (
		Exit /B 0
	)
	tasklist /FI "PID eq %1" |find ":" > nul
	IF  %ERRORLEVEL%==0 (
		Exit /B 0
	)
	Exit /B 1

:createLogFiles 
	dir "%APP_LOG_DIR%" >NUL 2>NUL
	IF not %errorlevel% ==0 (  
		ECHO "Can't find log dir" 
	Exit /B %1
	) 
	dir "%APP_OUT_FILE%" >NUL 2>NUL
		IF not %errorlevel% ==0 (  
		ECHO. 2>"%APP_OUT_FILE%"
	)
	dir "%APP_ERR_FILE%" >NUL 2>NUL
	IF not %errorlevel% ==0 (  
		ECHO. 2>"%APP_ERR_FILE%"
	)
	Exit /B 0
	
:deleteLogFiles 
	DEL /F /Q "%APP_PID_FILE%" 
	ECHO "Removed the  PID file: %APP_PID_FILE%"
	DEL /F /Q "%APP_OUT_FILE%"
	ECHO "Removed the OUT file: %APP_OUT_FILE%"
	DEL /F  /Q "%APP_ERR_FILE%"
	ECHO "Removed the  ERR file: %APP_ERR_FILE%"
	GOTO :EOF

:printHelp 
	ECHO "Usage: %1 {start|stop|status|clean}"
	GOTO :EOF
	



  


- 
