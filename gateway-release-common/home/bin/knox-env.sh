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

######################################################
#### GATEWAY SERVER RELATED ENVIRONMENT VARIABLES ####
######################################################

## KNOX_GATEWAY_CONF_DIR indicates where the gateway server should read its configuration from.
## Defaults to {GATEWAY_HOME}/conf
# export KNOX_GATEWAY_CONF_DIR=""

## KNOX_GATEWAY_DATA_DIR indicates where the gateway server should find it's data directory.
## This folder contains security, deployment and topology specific artifacts and requires read/write access at runtime.
## Defaults to {GATEWAY_HOME}/data
# export KNOX_GATEWAY_DATA_DIR=""

## KNOX_GATEWAY_LOG_DIR indicates where the gateway server should write its own error/standard output messages to.
## Defaults to {GATEWAY_HOME}/logs
# export KNOX_GATEWAY_LOG_DIR=""

## KNOX_GATEWAY_LOG_OPTS is a placeholder to set the gateway server's Log4j options (e.g. "-Dlog4j.debug -Dlog4j.configurationFile=foobar.xml")
## Defaults to an empty string
# export KNOX_GATEWAY_LOG_OPTS=""

## KNOX_GATEWAY_MEM_OPTS is a placeholder to allow customization of the gateway server's JVM memory settings (e.g. "-Xms512m -Xmx1024m")
## Defaults to an empty string
# export KNOX_GATEWAY_MEM_OPTS=""

## KNOX_GATEWAY_DBG_OPTS is a placeholder to allow customization of the gateway server's JVM debug settings (e.g. "-Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y")
## Defaults to an empty string
# export KNOX_GATEWAY_DBG_OPTS=""

## KNOX_GATEWAY_JAVA_LIB_PATH allows customization of java.library.path System property, which is used by the gateway's JVM to search native libraries.
## defaults to {GATEWAY_HOME}/ext/native
# export KNOX_GATEWAY_JAVA_LIB_PATH=""

## KNOX_GATEWAY_PID_DIR indicates the folder where the gateway server's places its PID file (in case it's being executed in the background).
## Since status check relies on the PID file being read by the script it's very important to have read/write access at runtime to this folder.
## Defaults to {GATEWAY_HOME}/pids
# export KNOX_GATEWAY_PID_DIR=""


################################################
#### KNOX-CLI RELATED ENVIRONMENT VARIABLES ####
################################################

## KNOX_CLI_LOG_OPTS is a placeholder to set KnoxCLI's Log4j options (e.g. "-Dlog4j.debug -Dlog4j.configurationFile=foobar.xml")
## Defaults to an empty string
# export KNOX_CLI_LOG_OPTS=""

## KNOX_CLI_MEM_OPTS is a placeholder to allow customization of KnoxCLI's JVM memory settings (e.g. "-Xms512m -Xmx1024m")
## Defaults to an empty string
# export KNOX_CLI_MEM_OPTS=""

## KNOX_CLI_DBG_OPTS is a placeholder to allow customization of KnoxCLI's JVM debug settings (e.g. "-Xdebug -Xrunjdwp:transport=dt_socket,address=5006,server=y,suspend=y")
## Defaults to an empty string
# export KNOX_CLI_DBG_OPTS=""


########################################################
#### KNOX LDAP SERVER RELATED ENVIRONMENT VARIABLES ####
########################################################

## KNOX_LDAP_CONF_DIR indicates where the test LDAP server should read its configuration from (including user.ldif)
## Defaults to {GATEWAY_HOME}/conf
# export KNOX_LDAP_CONF_DIR=""

## KNOX_LDAP_LOG_DIR indicates where the test LDAP server should write its own error/standard output messages to.
## Defaults to {GATEWAY_HOME}/logs
# export KNOX_LDAP_LOG_DIR=""

## KNOX_LDAP_LOG_OPTS is a placeholder to set the test LDAP server's Log4j options (e.g. "-Dlog4j.debug -Dlog4j.configurationFile=foobar.xml")
## Defaults to an empty string
# export KNOX_LDAP_LOG_OPTS=""

## KNOX_LDAP_MEM_OPTS is a placeholder to allow customization of the test LDAP server's JVM memory settings (e.g. "-Xms512m -Xmx1024m")
## Defaults to an empty string
# export KNOX_LDAP_MEM_OPTS=""

## KNOX_LDAP_DBG_OPTS is a placeholder to allow customization of the test LDAP server's JVM debug settings (e.g. "-Xdebug -Xrunjdwp:transport=dt_socket,address=5007,server=y,suspend=y")
## Defaults to an empty string
# export KNOX_LDAP_DBG_OPTS=""


##################################################
#### KNOX-SHELL RELATED ENVIRONMENT VARIABLES ####
##################################################

## KNOX_SHELL_LOG_OPTS is a placeholder to set KnoxShell's Log4j options (e.g. "-Dlog4j.debug -Dlog4j.configurationFile=foobar.xml")
## Defaults to an empty string
# export KNOX_SHELL_LOG_OPTS=""

## KNOX_SHELL_MEM_OPTS is a placeholder to allow customization of KnoxShell's JVM memory settings (e.g. "-Xms512m -Xmx1024m")
## Defaults to an empty string
# export KNOX_SHELL_MEM_OPTS=""

## KNOX_SHELL_DBG_OPTS is a placeholder to allow customization of KnoxShell's JVM debug settings (e.g. "-Xdebug -Xrunjdwp:transport=dt_socket,address=5008,server=y,suspend=y")
## Defaults to an empty string
# export KNOX_SHELL_DBG_OPTS=""
