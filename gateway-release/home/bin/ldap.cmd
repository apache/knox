@echo off
REM Licensed to the Apache Software Foundation (ASF) under one or more
REM contributor license agreements. See the NOTICE file distributed with
REM this work for additional information regarding copyright ownership.
REM The ASF licenses this file to You under the Apache License, Version 2.0
REM (the "License"); you may not use this file except in compliance with
REM the License. You may obtain a copy of the License at
REM
REM http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

REM Script location
set APP_BIN_DIR=%~dp0

REM  The app's home dir
set APP_HOME_DIR=%APP_BIN_DIR:~0,-5%

REM The apps conf dir
set APP_CONF_DIR=%APP_HOME_DIR%\conf

REM The app's jar name
set APP_JAR=%APP_BIN_DIR%ldap.jar

if not exist "%JAVA_HOME%"\bin\java.exe (
  echo Error: JAVA_HOME is incorrectly set.
  exit /b 1
)
set JAVA=%JAVA_HOME%\bin\java

if "%1" == "--service" (
  @echo ^<service^>
  @echo   ^<id^>knox-demo-ldap^</id^>
  @echo   ^<name^>knox-demo-ldap^</name^>
  @echo   ^<description^>This service runs the Knox demonstration LDAP server^</description^>
  @echo   ^<executable^>%JAVA%^</executable^>
  @echo   ^<arguments^>-jar "%APP_JAR%" "%APP_CONF_DIR%"^</arguments^>
  @echo ^</service^>
  exit /b 0
)

"%JAVA%" -jar "%APP_JAR%" "%APP_CONF_DIR%"
exit /b %ERRORLEVEL%