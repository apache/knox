@REM
@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements. See the NOTICE file distributed with
@REM this work for additional information regarding copyright ownership.
@REM The ASF licenses this file to You under the Apache License, Version 2.0
@REM (the "License"); you may not use this file except in compliance with
@REM the License. You may obtain a copy of the License at
@REM
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM
@ECHO OFF

@REM Knox PID
SET PID=0

@REM  Start, stop, status, clean or setup
SET KNOX_LAUNCH_COMMAND=%1

@REM  User Name for setup parameter
SET KNOX_LAUNCH_USER="%USERNAME%"

@REM start/stop script location
SET KNOX_SCRIPT_DIR=%~dp0

SET KNOX_HOME=%KNOX_SCRIPT_DIR:~0,-5%

@REM App name
SET KNOX_NAME=knox

@REM The Knox's jar name
SET KNOX_JAR=%KNOX_SCRIPT_DIR%gateway.jar

@REM Name of PID file
SET PID_DIR=%KNOX_HOME%\pids
SEt PID_FILE=%PID_DIR%\.pid

@REM Name of LOG/OUT/ERR file
SET LOG_DIR=%KNOX_HOME%\logs
SET OUT_FILE=%LOG_DIR%\.out
SET ERR_FILE=%LOG_DIR%\.err

:main 
	IF "%1" =="start" (
		CALL :knoxStart
		GOTO :EOF
	)

	IF "%1"=="stop" (
		CALL :knoxStop
		GOTO :EOF
	)	

	IF "%1"=="status" (
		CALL :knoxStatus
	GOTO :EOF
	)	

	IF "%1"=="clean" (
		CALL :knoxClean
		GOTO :EOF
	)
	   
	IF "%1"=="setup" (
		CALL :setupEnv %KNOX_LAUNCH_USER%
		GOTO :EOF	
	)
	IF "%1" =="help" (
		CALL :printHelp
		GOTO :EOF
	)
	ECHO "Usage: $0 {start|stop|status|clean|setup [USER_NAME]}\n"
	goto :EOF

:knoxStart 
	call :createLogFiles
	call :getPID
	IF %ERRORLEVEL%==0  (
		ECHO "Knox is already running with PID=%PID%.\n"
		GOTO :EOF
	) 
	ECHO "Starting Knox "
	DEL "%PID_FILE%"
	start   javaw  -jar "%KNOX_JAR%">> "%OUT_FILE%" 2>>"%ERR_FILE%"
	echo "getting pid"
	rem for /f "tokens=2 delims=," %%A in ('tasklist -v /fo csv /nh /fi "WINDOWTITLE  eq KnoxGateway"') do (
	for /f "tokens=2 delims=," %%A in ('tasklist -v /fo csv /nh /fi "Imagename  eq javaw.exe"') do (
	  echo %%A>"%PID_FILE%"
	)
	CALL :getPID
	CALL :knoxIsRunning %PID%
	IF  %ERRORLEVEL%==0 (
		ECHO "failed"
		Exit /B 1
	)
	ECHO "succeeded with PID %PID%
	Exit /B 0
   
@REM Removed the Knox PID file if Knox is not run
:knoxClean 
	CALL :getPID
	CALL :knoxIsRunning %PID%
	IF  %ERRORLEVEL%==0 (
		CALL :deleteLogFiles
		GOTO :EOF
	) 
	ECHO "Can't clean files. Knox is running with PID=%PID%"
	Exit /B 1
   
:createLogFiles 
	dir "%LOG_DIR%" >NUL 2>NUL
	IF not %errorlevel% ==0 (  
		ECHO "Can't find log dir" 
	Exit /B %1
	) 
	dir "%OUT_FILE%" >NUL 2>NUL
		IF not %errorlevel% ==0 (  
		ECHO. 2>"%OUT_FILE%"
	)
	dir "%ERR_FILE%" >NUL 2>NUL
	IF not %errorlevel% ==0 (  
		ECHO. 2>"%ERR_FILE%"
	)
	Exit /B 0

:getPID 
	dir "%PID_DIR%" >NUL 2>NUL
	IF NOT %ERRORLEVEL% ==0 (
		ECHO "Can't find pid dir. "
		Exit /B 1
	)
	dir  "%PID_FILE%" >NUL 2>NUL
	IF NOT %ERRORLEVEL% ==0 (
		SET PID=0
		Exit /B 1
	)	
	SET /p PID=<"%PID_FILE%"
	Exit /B 0

:knoxIsRunning 
	IF %1==0 (
		Exit /B 0
	)
	tasklist /FI "PID eq %1" |find ":" > nul
	IF  %ERRORLEVEL%==0 (
		Exit /B 0
	)
	Exit /B 1

:setDirPermission 
	SET dirName=%1
	SET uName=%2
	dir %dirName% >NUL 2>NUL
	IF NOT %ERRORLEVEL% ==0 ( 
		ECHO "making dir" %dirName%
		mkdir  %dirName% 
	)
	IF NOT %ERRORLEVEL% ==0 (
		ECHO "Can't access or create %dirName%
		Exit /B %1
	)	
	GOTO :EOF

:knoxStop {
	echo pid %PID%
	call   :getPID
	call :knoxIsRunning %PID%
	IF  %ERRORLEVEL% ==0 (
		echo "Knox is not running."
		GOTO :EOF
	)
	ECHO "Stopping Knox %PID% "
	taskkill /F /PID  %PID%
	IF NOT %ERRORLEVEL% ==0 (
		ECHO  "failed. \n"
		Exit /B 1
	)
	DEL /F "%PID_FILE%"
	echo "succeeded.\n"
	GOTO :EOF

:knoxStatus {
	CALL :getPID
	IF not %ERRORLEVEL% ==0 (
		echo "Knox is not running. No pid file found.\n"
		GOTO :EOF
	) 
	CALL :knoxIsRunning %PID%
	IF %ERRORLEVEL% ==1 (
		echo "knox is running with PID=%PID%.\n"
		Exit /B 1
	)
	ECHO "is not running.\n"
	GOTO :EOF

:deleteLogFiles 
	DEL /F /Q "%PID_FILE%" 
	ECHO "Removed the Knox PID file: %PID_FILE%"
	DEL /F /Q "%OUT_FILE%"
	ECHO "Removed the Knox OUT file: %OUT_FILE%"
	DEL /F  /Q "%ERR_FILE%"
	ECHO "Removed the Knox ERR file: %ERR_FILE%"
	GOTO :EOF

:setupEnv 
	SET uName=%1
	IF [%uName%]==[""] (
		uName="%USERNAME%" 
	)  	
	CALL  :setDirPermission "%PID_DIR%" %uName%
	CALL  :setDirPermission "%LOG_DIR%" %uName%
	java -DGATEWAY_HOME="%KNOX_HOME%" -jar "%KNOX_JAR%" -persist-master -nostart
	GOTO :EOF

:printHelp 
	java -jar "%KNOX_JAR%" -help
	GOTO :EOF
	



 


