@echo off
REM  Licensed to the Apache Software Foundation (ASF) under one or more
REM  contributor license agreements.  See the NOTICE file distributed with
REM  this work for additional information regarding copyright ownership.
REM  The ASF licenses this file to You under the Apache License, Version 2.0
REM  (the "License"); you may not use this file except in compliance with
REM  the License.  You may obtain a copy of the License at
REM
REM      http://www.apache.org/licenses/LICENSE-2.0
REM
REM  Unless required by applicable law or agreed to in writing, software
REM  distributed under the License is distributed on an "AS IS" BASIS,
REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM  See the License for the specific language governing permissions and
REM  limitations under the License.

REM Start/stop script location
set APP_BIN_DIR=%~dp0

REM The app's jar name
set APP_JAR=%APP_BIN_DIR%knoxcli.jar

if not exist "%JAVA_HOME%"\bin\java.exe (
  echo Error: JAVA_HOME is incorrectly set.
  exit /B 1
)
set JAVA=%JAVA_HOME%\bin\java

"%JAVA%" -jar "%APP_JAR%" %*

if not %ERRORLEVEL% == 0 (
  exit /B %ERRORLEVEL%
)

exit /B 0
