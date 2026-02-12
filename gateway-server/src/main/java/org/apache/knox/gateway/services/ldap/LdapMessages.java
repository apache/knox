/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.ldap;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger = "org.apache.knox.gateway.services.ldap")
public interface LdapMessages {

    @Message(level = MessageLevel.INFO,
            text = "Starting LDAP service on port {0} with base DN: {1}")
    void ldapServiceStarting(int port, String baseDn);

    @Message(level = MessageLevel.INFO,
            text = "LDAP service started successfully on port {0}")
    void ldapServiceStarted(int port);

    @Message(level = MessageLevel.INFO,
            text = "Stopping LDAP service on port {0}")
    void ldapServiceStopping(int port);

    @Message(level = MessageLevel.INFO,
            text = "LDAP service stopped successfully")
    void ldapServiceStopped();

    @Message(level = MessageLevel.ERROR,
            text = "Failed to start LDAP service: {0}")
    void ldapServiceStartFailed(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to stop LDAP service: {0}")
    void ldapServiceStopFailed(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.INFO,
            text = "Loading backend: {0} (via {1})")
    void ldapBackendLoading(String backendName, String source);

    @Message(level = MessageLevel.WARN,
            text = "Backend ''{0}'' not found, using FileBackend")
    void ldapBackendNotFound(String backendName);

    @Message(level = MessageLevel.WARN,
            text = "Data file not found: {0}, creating sample data")
    void ldapDataFileNotFound(String dataFile);

    @Message(level = MessageLevel.INFO,
            text = "Loaded {0} users from {1}")
    void ldapUsersLoaded(int count, String dataFile);

    @Message(level = MessageLevel.INFO,
            text = "Created sample data file: {0}")
    void ldapSampleDataCreated(String path);

    @Message(level = MessageLevel.DEBUG,
            text = "LDAP Search: {0} | {1}")
    void ldapSearch(String baseDn, String filter);

    @Message(level = MessageLevel.DEBUG,
            text = "LDAP Bind: {0}")
    void ldapBind(String dn);

    @Message(level = MessageLevel.INFO,
            text = "Loaded user from backend: {0}")
    void ldapUserLoaded(String username);

    @Message(level = MessageLevel.INFO,
            text = "Cleaning up old lock file: {0}")
    void ldapCleaningLockFile(String lockFile);
}
