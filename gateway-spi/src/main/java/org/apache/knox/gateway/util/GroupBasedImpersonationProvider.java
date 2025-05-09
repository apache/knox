/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AccessControlList;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.security.authorize.DefaultImpersonationProvider;
import org.apache.hadoop.util.MachineList;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_IMPERSONATION_MODE;
import static org.apache.knox.gateway.util.AuthFilterUtils.IMPERSONATION_MODE;

/**
 * An extension of Hadoop's DefaultImpersonationProvider that adds support for group-based impersonation.
 * This provider allows users who belong to specific groups to impersonate other users.
 */
public class GroupBasedImpersonationProvider extends DefaultImpersonationProvider {
    private static final GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);
    private static final String CONF_HOSTS = ".hosts";
    private static final String CONF_USERS = ".users";
    private static final String CONF_GROUPS = ".groups";
    private final Map<String, AccessControlList> proxyGroupsAcls = new HashMap<>();
    /* is the proxy user configuration enabled  */
    boolean isProxyUserEnabled = true;
    /* is the proxy group configuration enabled, defaulting it to false for backwards compatibility  */
    boolean isProxyGroupEnabled;
    private Map<String, MachineList> groupProxyHosts = new HashMap<>();
    private String groupConfigPrefix;
    private String impersonationMode = DEFAULT_IMPERSONATION_MODE;

    public GroupBasedImpersonationProvider() {
        super();
    }

    public GroupBasedImpersonationProvider(boolean isProxyUserEnabled, boolean isProxyGroupEnabled) {
        super();
        this.isProxyUserEnabled = isProxyUserEnabled;
        this.isProxyGroupEnabled = isProxyGroupEnabled;
    }

    @Override
    public Configuration getConf() {
        return super.getConf();
    }

    @Override
    public void setConf(Configuration conf) {
        super.setConf(conf);
    }

    public void init(String configurationPrefix, String proxyGroupPrefix) {
        super.init(configurationPrefix);
        initGroupBasedProvider(proxyGroupPrefix);
    }

    private void initGroupBasedProvider(String proxyGroupPrefix) {
        impersonationMode = getConf().get(IMPERSONATION_MODE, DEFAULT_IMPERSONATION_MODE);
        groupConfigPrefix = proxyGroupPrefix +
                (proxyGroupPrefix.endsWith(".") ? "" : ".");

        // constructing regex to match the following patterns:
        //   $configPrefix.[ANY].users
        //   $configPrefix.[ANY].groups
        //   $configPrefix.[ANY].hosts
        //
        String prefixRegEx = groupConfigPrefix.replace(".", "\\.");
        String usersGroupsRegEx = prefixRegEx + "[\\S]*(" +
                Pattern.quote(CONF_USERS) + "|" + Pattern.quote(CONF_GROUPS) + ")";
        String hostsRegEx = prefixRegEx + "[\\S]*" + Pattern.quote(CONF_HOSTS);

        // get list of users and groups per proxygroup
        // Map of <hadoop.proxygroup.[VIRTUAL_GROUP].users|groups, group1,group2>
        Map<String, String> allMatchKeys =
                getConf().getValByRegex(usersGroupsRegEx);

        for (Map.Entry<String, String> entry : allMatchKeys.entrySet()) {
            //aclKey = hadoop.proxygroup.[VIRTUAL_GROUP]
            String aclKey = getAclKey(entry.getKey());

            if (!proxyGroupsAcls.containsKey(aclKey)) {
                proxyGroupsAcls.put(aclKey, new AccessControlList(
                        allMatchKeys.get(aclKey + CONF_USERS),
                        allMatchKeys.get(aclKey + CONF_GROUPS)));
            }
        }

        // get hosts per proxygroup
        allMatchKeys = getConf().getValByRegex(hostsRegEx);
        for (Map.Entry<String, String> entry : allMatchKeys.entrySet()) {
            groupProxyHosts.put(entry.getKey(),
                    new MachineList(entry.getValue()));
        }
    }

    private String getAclKey(String key) {
        int endIndex = key.lastIndexOf('.');
        if (endIndex != -1) {
            return key.substring(0, endIndex);
        }
        return key;
    }

    @Override
    public void authorize(UserGroupInformation user, InetAddress remoteAddress) throws AuthorizationException {

        boolean isProxyUserAuthorized = false;
        boolean isProxyGroupAuthorized = false;
        if (isProxyUserEnabled) {
            /* authorize proxy user */
            isProxyUserAuthorized = checkProxyUserAuthorization(user, remoteAddress);
            /* In case of AND no reason to check for proxy groups if isProxyUserAuthorized=false */
            if("AND".equalsIgnoreCase(impersonationMode) && !isProxyUserAuthorized) {
                throw new AuthorizationException("User: " + user.getRealUser().getUserName()
                        + " is not allowed to impersonate " + user.getUserName());
            }
        }

        if (isProxyGroupEnabled) {
            /* check for proxy group impersonation */
            isProxyGroupAuthorized = checkProxyGroupAuthorization(user, remoteAddress);
        }

        if("AND".equalsIgnoreCase(impersonationMode)) {
            if (isProxyUserAuthorized && isProxyGroupAuthorized) {
                LOG.successfulImpersonation(user.getRealUser().getUserName(), user.getUserName());
            } else {
                throw new AuthorizationException("User: " + user.getRealUser().getUserName()
                        + " is not allowed to impersonate " + user.getUserName());
            }

        } else {
            /* OR */
            if (isProxyUserAuthorized || isProxyGroupAuthorized) {
                LOG.successfulImpersonation(user.getRealUser().getUserName(), user.getUserName());
            } else {
                throw new AuthorizationException("User: " + user.getRealUser().getUserName()
                        + " is not allowed to impersonate " + user.getUserName());
            }
        }
    }

    /**
     * Helper method to check if the user is authorized to impersonate
     * Returns true if the user is authorized, false otherwise.
     * @param user
     * @param remoteAddress
     * @return
     */
    private boolean checkProxyUserAuthorization(final UserGroupInformation user, final InetAddress remoteAddress) {
        try {
            super.authorize(user, remoteAddress);
            return true;
        } catch (final AuthorizationException e) {
            LOG.failedToImpersonateUser(user.getRealUser().getUserName(), remoteAddress.getHostAddress());
            return false;
        }
    }

    /**
     * Helper method to check if the group a given user belongs to is authorized to impersonate
     * Returns true if the user is authorized, false otherwise.
     * @param user
     * @param remoteAddress
     * @return
     */
    private boolean checkProxyGroupAuthorization(final UserGroupInformation user, final InetAddress remoteAddress) {
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }

        final UserGroupInformation realUser = user.getRealUser();
        if (realUser == null) {
            return true;
        }

        // Get the real user's groups (both real and virtual)
        Set<String> realUserGroups = new HashSet<>();
        if (user.getRealUser().getGroupNames() != null) {
            Collections.addAll(realUserGroups, user.getRealUser().getGroupNames());
        }

        boolean proxyGroupFound = false;
        // Check if any of the real user's groups have permission to impersonate the proxy user
        for (String group : realUserGroups) {
            final AccessControlList acl = proxyGroupsAcls.get(groupConfigPrefix +
                    group);

            if (acl == null || !acl.isUserAllowed(user)) {
                continue;
            } else {
                proxyGroupFound = true;
                break;
            }
        }

        if (!proxyGroupFound) {
            LOG.failedToImpersonateGroups(realUser.getUserName(), realUserGroups.toString(), user.getUserName());
            return false;
        }

        boolean proxyGroupHostFound = false;
        for (final String group : realUserGroups) {
            final MachineList machineList = groupProxyHosts.get(groupConfigPrefix + group + CONF_HOSTS);

            if (machineList == null || !machineList.includes(remoteAddress)) {
                continue;
            } else {
                proxyGroupHostFound = true;
                break;
            }
        }

        if (!proxyGroupHostFound) {
            LOG.failedToImpersonateGroupsFromAddress(realUser.getUserName(), realUserGroups.toString(), user.getUserName(), remoteAddress.toString());
            return false;
        }

        /* all checks pass */
        return true;
    }
}
