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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.util.AuthFilterUtils.PROXYGROUP_PREFIX;
import static org.apache.knox.gateway.util.AuthFilterUtils.PROXYUSER_PREFIX;

/**
 * An extension of Hadoop's DefaultImpersonationProvider that adds support for group-based impersonation.
 * This provider allows users who belong to specific groups to impersonate other users.
 */
public class KnoxImpersonationProvider extends DefaultImpersonationProvider {
    private static final GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);
    private static final String CONF_HOSTS = ".hosts";
    private static final String CONF_USERS = ".users";
    private static final String CONF_GROUPS = ".groups";
    private static final String PREFIX_REGEX_EXP = "\\.";
    private static final String USERS_GROUPS_REGEX_EXP = "[\\S]*(" +
            Pattern.quote(CONF_USERS) + "|" + Pattern.quote(CONF_GROUPS) + ")";
    private static final String HOSTS_REGEX_EXP = "[\\S]*" + Pattern.quote(CONF_HOSTS);
    private final Map<String, AccessControlList> proxyGroupsAcls = new HashMap<>();
    private Map<String, MachineList> groupProxyHosts = new HashMap<>();
    private String groupConfigPrefix;
    private boolean doesProxyUserConfigExist = true;
    private boolean doesProxyGroupConfigExist;
    static final String IMPERSONATION_ENABLED_PARAM = ".impersonation.enabled";

    public KnoxImpersonationProvider() {
        super();
    }

    @Override
    public Configuration getConf() {
        return super.getConf();
    }

    @Override
    public void setConf(Configuration conf) {
        super.setConf(conf);
    }

    @Override
    public void init(String configurationPrefix) {
        super.init(configurationPrefix);

        /* Check if user proxy configs are provided */
        doesProxyUserConfigExist = !getFilteredProps(PROXYUSER_PREFIX).isEmpty();

        /* Check if group proxy configs are provided */
        doesProxyGroupConfigExist = !getFilteredProps(PROXYGROUP_PREFIX).isEmpty();

        initGroupBasedProvider(PROXYGROUP_PREFIX);
    }

    /**
     * Returns a filtered map of properties with the specified prefix,
     * excluding the impersonation enabled parameter.
     *
     * @param prefix the prefix to filter properties by
     * @return a map of filtered properties
     */
    private Map<String, String> getFilteredProps(String prefix) {
        return Optional.ofNullable(getConf().getPropsWithPrefix(prefix))
                .orElse(Collections.emptyMap())
                .entrySet()
                .stream()
                .filter(entry -> !IMPERSONATION_ENABLED_PARAM.equals(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void initGroupBasedProvider(final String proxyGroupPrefix) {
        groupConfigPrefix = proxyGroupPrefix +
                (proxyGroupPrefix.endsWith(".") ? "" : ".");

        String prefixRegEx = groupConfigPrefix.replace(".", PREFIX_REGEX_EXP);
        String usersGroupsRegEx = prefixRegEx + USERS_GROUPS_REGEX_EXP;
        String hostsRegEx = prefixRegEx + HOSTS_REGEX_EXP;

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

    /**
     * Authorization based on user and group impersonation policies.
     *
     * @param user the user information attempting the operation, which includes the real
     *             user and the effective impersonated user.
     * @param remoteAddress the remote address from which the user is connecting.
     * @throws AuthorizationException if the user is not authorized based on the
     *                                configured impersonation and group policies.
     */
    @Override
    public void authorize(UserGroupInformation user, InetAddress remoteAddress) throws AuthorizationException {
        authorize(user, remoteAddress, Collections.emptyList());
    }

    /**
     * Authorization based on groups that are provided as a function argument
     *
     * @param user the user information attempting the operation, which includes the real
     *             user and the effective impersonated user.
     * @param groups the list of groups to check for authorization.
     * @param remoteAddress the remote address from which the user is connecting.
     * @throws AuthorizationException if the user is not authorized based on the
     *                                configured impersonation and group policies.
     */
    public void authorize(UserGroupInformation user, InetAddress remoteAddress, List<String> groups) throws AuthorizationException {

        /*
         * There are few cases to consider here:
         *  1. If proxy user config exists and there is impersonation failure:
         *    1.1 if proxy group config exists, try to authorize using groups
         *    1.2 if proxy group config does not exist, throw the original exception
         *  2. If proxy user config exists and there is impersonation success:
         *    2.2 Do not check for proxy group auth
         *  3. If proxy user config does not exist:
         *    3.1 Check for proxy group config and return success or failure based on the results.
         */
        if (doesProxyUserConfigExist) {
            try{
                /* check for proxy user authorization */
                super.authorize(user, remoteAddress);
            } catch (final AuthorizationException e) {
                /*
                 * Log and try group based impersonation.
                 * Since this provider is for groups no need to check if
                 * proxy group config exists, we know it does.
                 */
                LOG.failedToImpersonateUserTryingGroups(user.getUserName(), e.toString());
                /* check for proxy group authorization */
                if(doesProxyGroupConfigExist) {
                    checkProxyGroupAuthorization(user, remoteAddress, groups);
                } else {
                    /* If proxy group config does not exist, throw the original exception */
                    throw e;
                }
            }
        } else if (doesProxyGroupConfigExist) {
            /* check for proxy group authorization */
            checkProxyGroupAuthorization(user, remoteAddress, groups);
        }
    }


    /**
     * Helper method to check if the group a given user belongs to is authorized to impersonate
     * Returns true if the user is authorized, false otherwise.
     *
     * @param user
     * @param remoteAddress
     * @return
     */
    private void checkProxyGroupAuthorization(final UserGroupInformation user, final InetAddress remoteAddress, List<String> groups) throws AuthorizationException {
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }

        final UserGroupInformation realUser = user.getRealUser();
        if (realUser == null) {
            return;
        }

        // Get the real user's groups (both real and virtual)
        Set<String> realUserGroups = new HashSet<>();
        /* Add provided groups */
        if(groups != null && !groups.isEmpty()) {
            realUserGroups.addAll(groups);
        }
        /* Add groups from subject */
        if (user.getRealUser().getGroupNames() != null) {
            Collections.addAll(realUserGroups, user.getRealUser().getGroupNames());
        }

        boolean proxyGroupFound = false;
        // Check if any of the real user's groups have permission to impersonate the proxy user
        proxyGroupFound = isProxyGroupFound(user, realUserGroups, proxyGroupFound);

        if (!proxyGroupFound) {
            LOG.failedToImpersonateGroups(realUser.getUserName(), realUserGroups.toString(), user.getUserName());
            throw new AuthorizationException("User: " + realUser.getUserName()
                    + " with groups " + realUserGroups.toString()
                    + " is not allowed to impersonate " + user.getUserName());
        }

        boolean proxyGroupHostFound = false;
        proxyGroupHostFound = isProxyGroupHostFound(remoteAddress, realUserGroups, proxyGroupHostFound);

        if (!proxyGroupHostFound) {
            LOG.failedToImpersonateGroupsFromAddress(realUser.getUserName(), realUserGroups.toString(), user.getUserName(), remoteAddress.toString());
            throw new AuthorizationException("User: " + realUser.getUserName()
                    + " with groups " + realUserGroups.toString()
                    + " from address " + remoteAddress.toString()
                    + " is not allowed to impersonate " + user.getUserName());
        }

        /* all checks pass */
    }

    private boolean isProxyGroupHostFound(InetAddress remoteAddress, Set<String> realUserGroups, boolean proxyGroupHostFound) {
        for (final String group : realUserGroups) {
            final MachineList machineList = groupProxyHosts.get(groupConfigPrefix + group + CONF_HOSTS);

            if (machineList == null || !machineList.includes(remoteAddress)) {
                continue;
            } else {
                proxyGroupHostFound = true;
                break;
            }
        }
        return proxyGroupHostFound;
    }

    private boolean isProxyGroupFound(UserGroupInformation user, Set<String> realUserGroups, boolean proxyGroupFound) {
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
        return proxyGroupFound;
    }

}
