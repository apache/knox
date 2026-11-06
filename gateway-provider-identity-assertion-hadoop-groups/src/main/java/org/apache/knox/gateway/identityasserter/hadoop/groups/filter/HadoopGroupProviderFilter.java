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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.security.auth.Subject;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.LdapGroupsMapping;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.identityasserter.common.filter.CommonIdentityAssertionFilter;
import org.apache.hadoop.security.GroupMappingServiceProvider;
import org.apache.hadoop.security.Groups;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.ldap.KnoxLDAPService;
import org.apache.knox.gateway.services.ldap.UserGroupsLookupResult;

/**
 * A filter that integrates the Hadoop {@link GroupMappingServiceProvider} for
 * looking up group membership of the authenticated (asserted) identity.
 *
 * @since 0.11.0
 */
public class HadoopGroupProviderFilter extends CommonIdentityAssertionFilter {

  /**
   * Logging
   */
  public static final HadoopGroupProviderMessages LOG = MessagesFactory.get(HadoopGroupProviderMessages.class);

  static final String USE_LDAP_SERVICE = "use.ldap.service";

  /**
   * A regex matching principals known not to exist in LDAP (e.g. workload identities): their groups are not looked up
   */
  static final String GROUP_LOOKUP_SKIP_PRINCIPAL_PATTERN = "group.lookup.skip.principal.pattern";

  /**
   * Configuration object needed by for hadoop classes
   */
  private Configuration hadoopConfig;

  /**
   * Hadoop Groups implementation.
   */
  private Groups hadoopGroups;

  private KnoxLDAPService ldapService;

  private Pattern groupLookupSkipPattern;

  /* create an instance */
  public HadoopGroupProviderFilter() {
    super();
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);

    final String groupLookupSkipPatternString = filterConfig.getInitParameter(GROUP_LOOKUP_SKIP_PRINCIPAL_PATTERN);
    if (groupLookupSkipPatternString != null && !groupLookupSkipPatternString.isBlank()) {
      try {
        groupLookupSkipPattern = Pattern.compile(groupLookupSkipPatternString);
      } catch (final PatternSyntaxException e) {
        throw new ServletException("Invalid " + GROUP_LOOKUP_SKIP_PRINCIPAL_PATTERN + ": " + groupLookupSkipPatternString, e);
      }
      LOG.groupLookupSkipPatternConfigured(groupLookupSkipPatternString);
    }

    if (shouldUseLdapService(filterConfig)) {
      final GatewayServices services = (GatewayServices) filterConfig.getServletContext().getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      ldapService = services.getService(ServiceType.LDAP_SERVICE);
    } else {
      try {
        hadoopConfig = new Configuration(false);

        if (filterConfig.getInitParameterNames() != null) {
          for (final Enumeration<String> keys = filterConfig.getInitParameterNames(); keys.hasMoreElements(); ) {
            final String key = keys.nextElement();
            hadoopConfig.set(key, filterConfig.getInitParameter(key));

          }
        }
        hadoopGroups = new Groups(hadoopConfig);
      } catch (final Exception e) {
        throw new ServletException(e);
      }
    }
  }

  private boolean shouldUseLdapService(final FilterConfig filterConfig) {
    final String groupMappingsProvider = filterConfig.getInitParameter(GroupMappingServiceProvider.GROUP_MAPPING_CONFIG_PREFIX);
    final boolean ldapGroupsMappingEnabled = LdapGroupsMapping.class.getName().equals(groupMappingsProvider);
    final boolean useLdapService = Boolean.parseBoolean(filterConfig.getInitParameter(USE_LDAP_SERVICE));
    final GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final boolean ldapEnabled = gatewayConfig != null && gatewayConfig.isLDAPEnabled();
    return  ldapGroupsMappingEnabled && useLdapService && ldapEnabled;
  }

  /**
   * Query the Hadoop implementation of {@link Groups} to retrieve groups for
   * provided user.
   */
  @Override
  public String[] mapGroupPrincipals(final String mappedPrincipalName, final Subject subject, ServletRequest request) {
    if (groupLookupSkipPattern != null && mappedPrincipalName != null && groupLookupSkipPattern.matcher(mappedPrincipalName).matches()) {
      // Known not to be in LDAP: a lookup would only cost a round trip to find nothing
      LOG.groupLookupSkipped(mappedPrincipalName, groupLookupSkipPattern.pattern());
      return new String[0];
    }

    /* return the groups as seen by Hadoop */
    String[] groups;
    try {
      final List<String> groupList = hadoopGroups(mappedPrincipalName, request);
      LOG.groupsFound(mappedPrincipalName, groupList.toString());
      groups = groupList.toArray(new String[0]);

    } catch (final Exception e) {
      if (e.toString().contains("No groups found for user")) {
        /* no groups found move on */
        LOG.noGroupsFound(mappedPrincipalName);
      } else {
        /* Log the error and return empty group */
        LOG.errorGettingUserGroups(mappedPrincipalName, e);
      }
      groups = new String[0];
    }
    return groups;
  }

  protected List<String> hadoopGroups(String mappedPrincipalName, ServletRequest request) throws Exception {
    if (ldapService == null) {
      return hadoopGroups == null ? List.of() : hadoopGroups.getGroups(mappedPrincipalName);
    } else {
      LOG.useKnoxLDAPService();
      final UserGroupsLookupResult result = ldapService.lookupUserGroups(mappedPrincipalName);
      // Having the roles lookup interceptor configured is not enough: it only looks up roles for a user it
      // finds in LDAP. Otherwise, leave the lookup to the downstream consumers (e.g. KNOX-AUTH-SERVICE).
      if (result.rolesLookupApplied()) {
        LOG.rolesLookupApplied(mappedPrincipalName);
        if (request != null) {
          request.setAttribute(ROLES_LOOKUP_EXECUTED, Boolean.TRUE.toString());
        }
      } else {
        LOG.rolesLookupNotApplied(mappedPrincipalName);
      }
      return result.groups();
    }
  }

  @Override
  public String mapUserPrincipal(final String principalName) {
    /* return the passed principal */
    return principalName;
  }

}
