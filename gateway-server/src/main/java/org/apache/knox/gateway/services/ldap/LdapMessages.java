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

    @Message(level = MessageLevel.ERROR,
            text = "Interceptor type ''{0}'' for interceptor ''{1}'' not found")
    void ldapInterceptorNotFound(String interceptorType, String interceptorName);

    @Message(level = MessageLevel.ERROR,
            text = "Interceptor type not found for interceptor ''{0}''")
    void ldapInterceptorTypeNotFound(String interceptorName);

    @Message(level = MessageLevel.INFO,
            text = "Creating LDAP interceptor: {0} (via {1})")
    void ldapInterceptorCreating(String interceptorName, String source);

    @Message(level = MessageLevel.INFO,
            text = "Loading backend: {0} (via {1})")
    void ldapBackendLoading(String backendName, String source);

    @Message(level = MessageLevel.ERROR,
            text = "Backend type ''{0}'' for backend ''{1}'' not found")
    void ldapBackendNotFound(String backendType, String backendName);

    @Message(level = MessageLevel.ERROR,
            text = "Backend type not found for backend ''{0}''")
    void ldapBackendTypeNotFound(String backendName);

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

    @Message(level = MessageLevel.ERROR,
            text = "LDAP Search failed: {0} | {1}, {2}")
    void ldapSearchFailed(String baseDn, String filter, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.DEBUG,
            text = "LDAP Bind: {0}")
    void ldapBind(String dn);

    @Message(level = MessageLevel.INFO,
            text = "Loaded user from backend: {0}")
    void ldapUserLoaded(String username);

    @Message(level = MessageLevel.ERROR,
            text = "LDAP Lookup failed: {0}, {1}")
    void ldapLookupFailed(String dn, @StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.INFO,
            text = "AttributeType not found: {0}")
    void ldapAttributeTypeNotFound(String attribute);

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

    @Message(level = MessageLevel.DEBUG, text = "Recursive group search for entry {0} found {1} group(s) ({2}) at depth {3}")
    void ldapRecursiveGroupSearchProgress(String entryName, int count, String groups, int depth);

    @Message(level = MessageLevel.DEBUG, text = "Recursive group search for entry {0} completed. Total groups found: {1}")
    void ldapRecursiveGroupSearchFinished(String entryName, int count);

    @Message(level = MessageLevel.WARN, text = "Recursive group search for entry {0} reached max depth {1}")
    void ldapRecursiveGroupSearchMaxDepthReached(String entryName, int maxDepth);

    @Message(level = MessageLevel.DEBUG, text = "Cycle detected in recursive group search for entry {0} at group {1}")
    void ldapRecursiveGroupSearchCycleDetected(String entryName, String groupDn);

    @Message(level = MessageLevel.WARN, text = "Expected entry for group {0} not found in entry cache")
    void ldapRecursiveGroupSearchExpectedGroupNotInCache(String groupDn);

    @Message(level = MessageLevel.DEBUG, text = "Created skeleton group entry for {0} as actual group entry was not found in the backend")
    void ldapSkeletonGroupEntryCreated(String groupDn);

    @Message(level = MessageLevel.DEBUG, text = "Found {1} parent(s) in cache for group {0}")
    void ldapRecursiveGroupSearchCacheHit(String groupDn, int count);

    @Message(level = MessageLevel.DEBUG, text = "Added parent {1} to cache for group {0}")
    void ldapRecursiveGroupSearchCacheAdd(String groupDn, String parentDn);

    @Message(level = MessageLevel.INFO, text = "Reloading LDAP roles lookup configuration...")
    void ldapRolesLookupReloadingConfig();

    @Message(level = MessageLevel.ERROR, text = "Failed to reload LDAP roles lookup: {0}")
    void ldapRolesLookupReloadFailed(@StackTrace(level = MessageLevel.DEBUG) Exception e);

    @Message(level = MessageLevel.INFO, text = "LDAP roles lookup is enabled with strategy: {0}")
    void ldapRolesLookupEnabled(String strategy);

    @Message(level = MessageLevel.INFO, text = "LDAP roles lookup is disabled")
    void ldapRolesLookupDisabled();

    @Message(level = MessageLevel.DEBUG, text = "LDAP roles lookup for user {0} and groups {1} returned roles: {2}")
    void ldapRolesLookupResult(String user, String groups, String roles);

    @Message(level = MessageLevel.ERROR, text = "Failed to lookup roles for user {0}: {1}")
    void ldapRolesLookupFailed(String user, @StackTrace(level = MessageLevel.DEBUG) Exception e);
}
