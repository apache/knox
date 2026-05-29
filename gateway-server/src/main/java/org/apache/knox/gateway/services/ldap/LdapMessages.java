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

    @Message(level = MessageLevel.DEBUG,
            text = "Backend user found: {0}")
    void ldapUserEntry(String user);

    @Message(level = MessageLevel.DEBUG,
            text = "Backend user not found: {0}")
    void ldapUserNull(String username);

    @Message(level = MessageLevel.ERROR,
            text = "Failed to copy attribute: {0}")
    void ldapAttributeCopyError(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.DEBUG, text = "LDAP authentication succeeded for user: {0}")
    void ldapAuthSucceeded(String user);

    @Message(level = MessageLevel.WARN, text = "LDAP authentication failed for user: {0}")
    void ldapAuthFailed(String user, @StackTrace(level = MessageLevel.INFO) Throwable cause);

    @Message(level = MessageLevel.INFO,
            text = "Reloading LDAP configuration")
    void ldapReloadingConfig();

    @Message(level = MessageLevel.ERROR,
            text = "Failed to reload LDAP service: {0}")
    void ldapServiceReloadFailed(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.DEBUG, text = "Recursive group search enabled: {0}, max depth: {1}")
    void ldapRecursiveGroupSearchConfig(boolean enabled, int maxDepth);

    @Message(level = MessageLevel.DEBUG, text = "Recursive group search for user {0} found {1} groups at depth {2}")
    void ldapRecursiveGroupSearchProgress(String user, int count, int depth);

    @Message(level = MessageLevel.DEBUG, text = "Recursive group search for user {0} completed. Total groups found: {1}")
    void ldapRecursiveGroupSearchFinished(String user, int count);

    @Message(level = MessageLevel.WARN, text = "Recursive group search for user {0} reached max depth {1}")
    void ldapRecursiveGroupSearchMaxDepthReached(String user, int maxDepth);

    @Message(level = MessageLevel.DEBUG, text = "Cycle detected in recursive group search for user {0} at group {1}")
    void ldapRecursiveGroupSearchCycleDetected(String user, String groupDn);

    @Message(level = MessageLevel.DEBUG, text = "Created skeleton group entry for {0} as actual group entry was not found in the backend")
    void ldapSkeletonGroupEntryCreated(String groupDn);
}
